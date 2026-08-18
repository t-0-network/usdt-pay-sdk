// Copies node/starter/issuer into template/ so npm can tarball it.
//
// Runs from `prepack`, which fires on both `npm pack` and `npm publish` — so the
// published template is always the starter as it is on the tagged commit. The
// starter stays the single source of truth: it is a live workspace member that CI
// builds and tests against the local SDK, exactly as java/starter/acquirer is a live
// Gradle subproject that usdt-pay-init.jar packs at build time.
//
// template/ is generated, and .gitignore'd. Never edit it.
import { cpSync, existsSync, renameSync, rmSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const packageRoot = dirname(dirname(fileURLToPath(import.meta.url)));
const source = join(packageRoot, "..", "starter", "issuer");
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

if (!existsSync(source)) {
  throw new Error(`starter not found at ${source}`);
}

rmSync(target, { recursive: true, force: true });

cpSync(source, target, {
  recursive: true,
  filter: (src) => !isExcluded(src.slice(src.lastIndexOf("/") + 1)),
});

// npm drops a nested `.gitignore` from the tarball and also reads it as ignore rules,
// so it ships undotted; the CLI restores the name when it scaffolds.
const dotted = join(target, ".gitignore");
if (existsSync(dotted)) {
  renameSync(dotted, join(target, "gitignore"));
}

// stderr, not stdout: `npm pack --json` writes the file listing to stdout and a
// stray line here makes it unparseable.
console.error(`packed ${relative(packageRoot, source)} -> template/`);
