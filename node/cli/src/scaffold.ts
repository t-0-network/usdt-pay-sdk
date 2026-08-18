import { randomBytes } from "node:crypto";
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { publicKeyFromPrivateKey } from "@t-0/usdt-pay-sdk";

/** A keypair, both halves hex-encoded without a 0x prefix. */
export interface KeyPair {
  privateKeyHex: string;
  publicKeyHex: string;
}

/**
 * The roles this package carries a starter for, sorted.
 *
 * The `template/` listing *is* the set of roles on offer — there is no registry to
 * update when a starter is added, the same arrangement `usdt-pay-init.jar` has.
 */
export function availableStarters(templateDir: string): string[] {
  if (!existsSync(templateDir)) return [];
  return readdirSync(templateDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort();
}

/**
 * Generates the secp256k1 keypair a new project signs its requests with. The public
 * half is what the t-0 team registers you by.
 *
 * The derivation goes through the SDK's own `publicKeyFromPrivateKey` rather than
 * reaching for a curve library directly — same reason `KeyGenerator.java` derives
 * through `Signer`: a key that works here has to work in the runtime.
 */
export function generateKeyPair(): KeyPair {
  // A random 32 bytes is a valid secp256k1 scalar with overwhelming probability, but
  // not certainly. Retrying is cheaper than handing someone a key the runtime rejects.
  for (;;) {
    const privateKeyHex = randomBytes(32).toString("hex");
    try {
      const publicKeyHex = publicKeyFromPrivateKey(privateKeyHex).replace(/^0x/, "");
      return { privateKeyHex, publicKeyHex };
    } catch {
      // not a valid secret — draw again
    }
  }
}

/**
 * Writes the project's `.env` from its `.env.example`, with the generated keypair
 * filled in. The `.env.example` contract is shared with the Java starters, so this is
 * `EnvFileWriter.java` in TypeScript, down to the anchoring.
 */
export function writeEnvFile(targetDir: string, keyPair: KeyPair): void {
  const example = readFileSync(join(targetDir, ".env.example"), "utf8");

  // Line-anchored so this cannot hit a PRIVATE_KEY mentioned in a comment, and so it
  // fills the empty assignment rather than appending to a set one.
  const content = example.replace(
    /^PRIVATE_KEY=$/m,
    `PRIVATE_KEY=${keyPair.privateKeyHex}\n` +
      `\n# The public half of the key above — send this to the t-0 team.` +
      `\n# 0x${keyPair.publicKeyHex}`,
  );

  writeFileSync(join(targetDir, ".env"), content);
}

/** Lowercases, turns runs of whitespace into hyphens, drops everything else. */
export function sanitizeProjectName(name: string): string {
  return name
    .toLowerCase()
    .replace(/\s+/g, "-")
    .replace(/[^a-z0-9-]/g, "");
}

/** Stamps the project's own name into its `package.json`, leaving the rest alone. */
function stampProjectName(targetDir: string, projectName: string): void {
  const path = join(targetDir, "package.json");
  const pkg = JSON.parse(readFileSync(path, "utf8"));
  pkg.name = projectName;
  writeFileSync(path, `${JSON.stringify(pkg, null, 2)}\n`);
}

/**
 * Rewrites the instructions that only make sense inside this repo. The in-repo starter
 * builds against the workspace SDK and its Docker context is `node/`; a scaffolded one
 * resolves the SDK from npm and builds from its own directory. `deRepoReadme` in
 * `TemplateExtractor.java` does the same job for the Java starters.
 */
function deRepoReadme(targetDir: string, role: string): void {
  const path = join(targetDir, "README.md");
  if (!existsSync(path)) return;

  let content = readFileSync(path, "utf8");

  content = content.replace(
    "# Install and build from node/ — the starter compiles against the local sdk workspace.\n(cd ../.. && npm install && npm run build)",
    "npm install && npm run build",
  );

  content = content.replace(
    "The build context is `node/`, because the starter compiles against the SDK next to\nit.",
    "The build context is this project directory.",
  );

  content = content.replace(
    `cd ../..                    # node/\ndocker build -f starter/${role}/Dockerfile -t usdt-pay-${role} .\ndocker run -p 8080:8080 --env-file starter/${role}/.env usdt-pay-${role}`,
    `docker build -t usdt-pay-${role} .\ndocker run -p 8080:8080 --env-file .env usdt-pay-${role}`,
  );

  writeFileSync(path, content);
}

/**
 * Copies the packed starter for `role` into `targetDir`, names it, and writes its
 * `.env`.
 *
 * @param packageRoot this package's root, holding `template/` and `overlay/`
 * @returns the generated keypair, whose public half the caller must show the user
 */
export function scaffold(
  targetDir: string,
  projectName: string,
  role: string,
  packageRoot: string,
): KeyPair {
  const template = join(packageRoot, "template", role);
  if (!existsSync(template)) {
    throw new Error(`no starter named '${role}' in this package`);
  }

  mkdirSync(targetDir, { recursive: true });
  cpSync(template, targetDir, { recursive: true });

  // Optional, and applied second so it wins over the template: the files that cannot
  // ship verbatim because the in-repo form depends on the workspace layout.
  const overlay = join(packageRoot, "overlay", role);
  if (existsSync(overlay)) {
    cpSync(overlay, targetDir, { recursive: true });
  }

  // npm drops a nested `.gitignore` from the tarball, so the template ships it
  // undotted and it gets its name back here. Without this a scaffolded project
  // commits its own `.env` on the first `git add -A`.
  const undotted = join(targetDir, "gitignore");
  if (existsSync(undotted)) {
    renameSync(undotted, join(targetDir, ".gitignore"));
  }

  stampProjectName(targetDir, projectName);
  deRepoReadme(targetDir, role);

  const keyPair = generateKeyPair();
  writeEnvFile(targetDir, keyPair);
  return keyPair;
}
