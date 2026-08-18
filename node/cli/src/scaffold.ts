import { randomBytes } from "node:crypto";
import { cpSync, existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { publicKeyFromPrivateKey } from "@t-0/usdt-pay-sdk";

/** A keypair, both halves hex-encoded without a 0x prefix. */
export interface KeyPair {
  privateKeyHex: string;
  publicKeyHex: string;
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
 * Copies the packed starter into `targetDir`, names it, and writes its `.env`.
 *
 * @returns the generated keypair, whose public half the caller must show the user
 */
export function scaffold(targetDir: string, projectName: string, templateDir: string): KeyPair {
  mkdirSync(targetDir, { recursive: true });
  cpSync(templateDir, targetDir, { recursive: true });

  // npm drops a nested `.gitignore` from the tarball, so the template ships it
  // undotted and it gets its name back here. Without this a scaffolded project
  // commits its own `.env` on the first `git add -A`.
  const undotted = join(targetDir, "gitignore");
  if (existsSync(undotted)) {
    renameSync(undotted, join(targetDir, ".gitignore"));
  }

  stampProjectName(targetDir, projectName);

  const keyPair = generateKeyPair();
  writeEnvFile(targetDir, keyPair);
  return keyPair;
}
