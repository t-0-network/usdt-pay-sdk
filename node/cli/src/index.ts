import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, rmSync } from "node:fs";
import { createInterface } from "node:readline/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { availableStarters, sanitizeProjectName, scaffold } from "./scaffold.js";

const BLUE = "\u001b[34m";
const GREEN = "\u001b[32m";
const YELLOW = "\u001b[33m";
const RED = "\u001b[31m";
const RESET = "\u001b[0m";

const here = dirname(fileURLToPath(import.meta.url));
// dist/index.js -> the package root
const packageRoot = resolve(here, "..");
const templateDir = join(packageRoot, "template");

let useColor = process.stdout.isTTY === true && !process.env.NO_COLOR;

function color(code: string, text: string): string {
  return useColor ? `${code}${text}${RESET}` : text;
}

function info(message: string): void {
  console.log(`${color(BLUE, "[INFO]")} ${message}`);
}

function success(message: string): void {
  console.log(`${color(GREEN, "[OK]")} ${message}`);
}

function fail(message: string): never {
  console.error(`${color(RED, "[ERROR]")} ${message}`);
  process.exit(1);
}

function version(): string {
  return JSON.parse(readFileSync(join(packageRoot, "package.json"), "utf8")).version;
}

function usage(): string {
  return [
    "Usage: usdt-pay-starter-ts [project-name] <role>",
    "",
    "Creates a USDt Pay project in TypeScript from one of the starters this package carries.",
    `Roles: ${availableStarters(templateDir).join(", ")}`,
    "",
    "The role is required and comes last. Give the project name before it, or omit the",
    "name and you will be asked for it.",
    "",
    "Options:",
    "  -d, --directory <dir>  Where to create the project (defaults to the current directory)",
    "      --no-color         Disable colored output",
    "  -h, --help             Show this help",
    "  -V, --version          Show the version",
  ].join("\n");
}

interface Args {
  projectName: string;
  directory: string;
  starter: string;
}

function parseArgs(argv: string[]): Args {
  let directory = process.cwd();
  const positional: string[] = [];

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    switch (arg) {
      case "-h":
      case "--help":
        console.log(usage());
        return process.exit(0);
      case "-V":
      case "--version":
        console.log(version());
        return process.exit(0);
      case "--no-color":
        useColor = false;
        break;
      case "-d":
      case "--directory": {
        const value = argv[++i];
        if (!value) fail("--directory needs a path");
        directory = resolve(value);
        break;
      }
      default:
        if (arg.startsWith("-")) fail(`Unknown option '${arg}'.\n\n${usage()}`);
        positional.push(arg);
    }
  }

  // The role is required, so a lone positional can only be it — there is nothing else
  // it could have been. With two, the name comes first and the role last.
  switch (positional.length) {
    case 0:
      return { projectName: "", directory, starter: "" };
    case 1:
      return { projectName: "", directory, starter: positional[0] };
    case 2:
      return { projectName: positional[0], directory, starter: positional[1] };
    default:
      fail(`Too many arguments: ${positional.join(" ")}\n\n${usage()}`);
  }
}

async function ask(question: string): Promise<string> {
  // With stdin not a terminal — CI, a piped script, a Dockerfile — `rl.question` never
  // resolves: the event loop drains and node exits 0 having created nothing, so the
  // failure surfaces at the caller's next line rather than here. Refuse instead.
  if (!process.stdin.isTTY) {
    fail(`Cannot ask for the project name: stdin is not a terminal. Pass it as an argument.\n\n${usage()}`);
  }
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  try {
    return await rl.question(question);
  } finally {
    rl.close();
  }
}

/**
 * Required, deliberately — issuer, acquirer and lp are different integrations, and
 * which one you get is not something to infer.
 *
 * Defaulting it would be a contract that changes under you: with one starter packed a
 * bare invocation would silently mean "issuer", and the day an acquirer starter lands
 * it would mean "acquirer", because `availableStarters` sorts. Anything scripted
 * against it would switch roles without a single character changing.
 */
function resolveStarter(starters: string[], requested: string): string {
  if (!requested) {
    fail(`A role is required, and comes last. Available: ${starters.join(", ")}`);
  }
  const role = requested.trim().toLowerCase();
  if (!starters.includes(role)) {
    // Asking for a role that exists elsewhere in the SDK is not the same mistake as a
    // typo, and answering both with "Available: issuer" dead-ends the one person who
    // knew exactly what they wanted. The starters split by language, not by role.
    if (role === "acquirer") {
      fail(
        "The acquirer starter ships in the Java initializer, not in this package:\n" +
          "  curl -LO https://github.com/t-0-network/usdt-pay-sdk/releases/latest/download/usdt-pay-init.jar\n" +
          "  java -jar usdt-pay-init.jar my-acquirer acquirer",
      );
    }
    fail(`No starter named '${requested}'. Available: ${starters.join(", ")}`);
  }
  return role;
}

function header(): void {
  console.log("");
  console.log(color(BLUE, "+-----------------------------------------------------------+"));
  console.log(`${color(BLUE, "|")}            USDt Pay SDK - Node Initializer                ${color(BLUE, "|")}`);
  console.log(color(BLUE, "+-----------------------------------------------------------+"));
  console.log("");
}

function completion(targetDir: string, publicKeyHex: string): void {
  console.log("");
  console.log(color(GREEN, "Project created at ") + color(BLUE, targetDir));
  console.log("");
  console.log(color(YELLOW, "Your public key — send it to the t-0 team, they cannot accept your calls until they have it:"));
  console.log(color(BLUE, `0x${publicKeyHex}`));
  console.log("");
  console.log(color(YELLOW, "Next steps:"));
  console.log("");
  console.log(`  1. ${color(BLUE, `cd ${targetDir}`)}`);
  console.log(`  2. Put the t-0 network public key in ${color(BLUE, ".env")} (NETWORK_PUBLIC_KEY)`);
  // All three, in order: `npm start` runs dist/index.js, so it fails until the build
  // has run. Suggesting a command that cannot work yet is how a first run ends.
  console.log(`  3. ${color(BLUE, "npm install")}`);
  console.log(`  4. ${color(BLUE, "npm run build")}`);
  console.log(`  5. ${color(BLUE, "npm start")} — prints your public key and serves callbacks`);
  console.log("");
  console.log(`Then read ${color(BLUE, "README.md")} for the phases.`);
  console.log("");
  console.log(`Docs: ${color(BLUE, "https://usdt-pay-docs.t-0.network/")}`);
  console.log("");
}

async function main(): Promise<void> {
  const args = parseArgs(process.argv.slice(2));
  header();

  const starters = availableStarters(templateDir);
  if (starters.length === 0) {
    fail("This package carries no starters — it was built wrong.");
  }

  const role = resolveStarter(starters, args.starter);

  const raw = args.projectName || (await ask("Enter your project name: "));
  const projectName = sanitizeProjectName(raw);
  if (!projectName) {
    fail("Invalid project name. Use only letters, numbers, and hyphens.");
  }

  const targetDir = join(args.directory, projectName);
  if (existsSync(targetDir)) {
    fail(`Directory '${targetDir}' already exists. Please choose a different name.`);
  }

  info(`Creating ${role} project: ${projectName}`);
  let keyPair;
  try {
    info("Extracting starter...");
    keyPair = scaffold(targetDir, projectName, role, packageRoot);
    success("Starter extracted, keypair generated, environment configured");
  } catch (error) {
    // A half-written project holding a half-written .env is worse than none.
    rmSync(targetDir, { recursive: true, force: true });
    throw error;
  }

  try {
    execFileSync("git", ["init", "--quiet"], { cwd: targetDir, stdio: "ignore" });
  } catch {
    // No git, or not wanted here. Not a reason to fail a scaffold.
  }

  completion(targetDir, keyPair.publicKeyHex);
}

main().catch((error: unknown) => {
  fail(`Failed to initialize project: ${error instanceof Error ? error.message : String(error)}`);
});
