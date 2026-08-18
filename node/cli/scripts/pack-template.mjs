// Copies every starter under node/starter/ into template/<role>/ so npm can tarball
// them. That listing *is* the set of roles the CLI offers — adding a starter needs no
// registry here, just a directory, exactly as it needs none in usdt-pay-init.jar.
//
// Runs from `prepack`, which fires on both `npm pack` and `npm publish` — so the
// published templates are always the starters as they are on the tagged commit. The
// starters stay the single source of truth: they are live workspace members that CI
// builds and tests against the local SDK, as java/starter/* are live Gradle
// subprojects that usdt-pay-init.jar packs at build time.
//
// template/ is generated, and .gitignore'd. Never edit it. overlay/ is NOT generated —
// it is committed source, and holds the files that cannot be shipped verbatim.
import { cpSync, existsSync, readdirSync, renameSync, rmSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const packageRoot = dirname(dirname(fileURLToPath(import.meta.url)));
const startersDir = join(packageRoot, "..", "starter");
const target = join(packageRoot, "template");

const EXCLUDED = new Set(["node_modules", "dist", ".git", ".DS_Store"]);

// Anything credential-shaped. A dev's starter directory can hold live keys, and CI is
// not the only place this runs — the one-time npm bootstrap publish happens from a
// maintainer's laptop, where .env.local and stray *.pem are entirely plausible.
// Only .env.example is ever shipped.
const SECRET_LIKE = /^(\.env(\..*)?|\.npmrc|id_[a-z]+|.*\.(pem|key|p12|jks|keystore))$/;

function isExcluded(name) {
  if (name === ".env.example") return false;
  return EXCLUDED.has(name) || SECRET_LIKE.test(name) || name.endsWith(".tsbuildinfo");
}

if (!existsSync(startersDir)) {
  throw new Error(`no starters directory at ${startersDir}`);
}

const roles = readdirSync(startersDir, { withFileTypes: true })
  .filter((e) => e.isDirectory())
  .map((e) => e.name)
  .sort();

if (roles.length === 0) {
  throw new Error(`no starters found under ${startersDir}`);
}

rmSync(target, { recursive: true, force: true });

for (const role of roles) {
  const dest = join(target, role);
  cpSync(join(startersDir, role), dest, {
    recursive: true,
    filter: (src) => !isExcluded(src.slice(src.lastIndexOf("/") + 1)),
  });

  // npm drops a nested `.gitignore` from the tarball and also reads it as ignore
  // rules, so it ships undotted; the CLI restores the name when it scaffolds.
  const dotted = join(dest, ".gitignore");
  if (existsSync(dotted)) {
    renameSync(dotted, join(dest, "gitignore"));
  }
}

// stderr, not stdout: `npm pack --json` writes the file listing to stdout and a stray
// line here makes it unparseable.
console.error(`packed ${roles.length} starter(s) -> template/: ${roles.join(", ")}`);
