import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, rmSync } from "node:fs";
import { createInterface } from "node:readline/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { sanitizeProjectName, scaffold } from "./scaffold.js";

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
    "Usage: usdt-pay-starter-ts [project-name] [options]",
    "",
    "Creates a USDt Pay issuer project in TypeScript.",
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
}

function parseArgs(argv: string[]): Args {
  let projectName = "";
  let directory = process.cwd();

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
        if (projectName) fail(`Unexpected argument '${arg}' — the project name was already given as '${projectName}'.`);
        projectName = arg;
    }
  }

  return { projectName, directory };
}

async function promptProjectName(): Promise<string> {
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  try {
    return await rl.question("Enter your project name: ");
  } finally {
    rl.close();
  }
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
  console.log(`  3. ${color(BLUE, "npm install")}, then read ${color(BLUE, "README.md")} for the phases`);
  console.log("");
  console.log(`Docs: ${color(BLUE, "https://usdt-pay-docs.t-0.network/")}`);
  console.log("");
}

async function main(): Promise<void> {
  const args = parseArgs(process.argv.slice(2));
  header();

  if (!existsSync(templateDir)) {
    fail("This package carries no starter — it was built wrong.");
  }

  const raw = args.projectName || (await promptProjectName());
  const projectName = sanitizeProjectName(raw);
  if (!projectName) {
    fail("Invalid project name. Use only letters, numbers, and hyphens.");
  }

  const targetDir = join(args.directory, projectName);
  if (existsSync(targetDir)) {
    fail(`Directory '${targetDir}' already exists. Please choose a different name.`);
  }

  info(`Creating issuer project: ${projectName}`);
  let keyPair;
  try {
    info("Extracting starter...");
    keyPair = scaffold(targetDir, projectName, templateDir);
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
