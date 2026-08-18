import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, readdirSync, readFileSync, rmSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { after, before, describe, it } from "node:test";
import { publicKeyFromPrivateKey } from "@t-0/usdt-pay-sdk";
import {
  availableStarters,
  generateKeyPair,
  sanitizeProjectName,
  scaffold,
} from "../src/scaffold.js";

const packageRoot = dirname(dirname(fileURLToPath(import.meta.url)));
const templateDir = join(packageRoot, "template");

describe("sanitizeProjectName", () => {
  it("lowercases, hyphenates whitespace and drops the rest", () => {
    assert.equal(sanitizeProjectName("My Issuer"), "my-issuer");
    assert.equal(sanitizeProjectName("acme_pay!"), "acmepay");
    assert.equal(sanitizeProjectName("!!!"), "");
  });
});

describe("generateKeyPair", () => {
  it("returns a 32-byte secret whose public half the SDK agrees with", () => {
    const { privateKeyHex, publicKeyHex } = generateKeyPair();
    assert.match(privateKeyHex, /^[0-9a-f]{64}$/);
    // Unprefixed on both halves, matching KeyGenerator.java's record.
    assert.match(publicKeyHex, /^04[0-9a-f]{128}$/);
    assert.equal(publicKeyFromPrivateKey(privateKeyHex), `0x${publicKeyHex}`);
  });

  it("does not repeat itself", () => {
    assert.notEqual(generateKeyPair().privateKeyHex, generateKeyPair().privateKeyHex);
  });
});

describe("scaffold", () => {
  let workdir: string;

  /** Runs the real CLI. stdio "pipe" also makes stdin a pipe, which is the point below. */
  const run = (args: string[]) => {
    const cli = join(packageRoot, "bin", "cli.js");
    try {
      execFileSync("node", [cli, ...args], { cwd: workdir, encoding: "utf8", stdio: "pipe" });
      return { code: 0, err: "" };
    } catch (e) {
      const error = e as { status: number; stderr: string };
      return { code: error.status, err: error.stderr };
    }
  };

  before(() => {
    // prepack generates template/; the test needs it whether or not a pack ran.
    execFileSync("node", [join(packageRoot, "scripts", "pack-template.mjs")], { stdio: "ignore" });
    workdir = mkdtempSync(join(tmpdir(), "usdt-pay-starter-"));
  });

  after(() => rmSync(workdir, { recursive: true, force: true }));

  it("offers exactly the declared starters, in order", () => {
    // Spelled out rather than "contains issuer". The listing IS the role set, and
    // `npx @t-0/usdt-pay-starter-ts <name> <role>` is a published invocation — a role
    // appearing, vanishing or being renamed is a CLI API change, so it has to be made
    // here deliberately. publish.yaml asserts the same set against the tarball.
    assert.deepEqual(availableStarters(templateDir), ["issuer"]);
  });

  it("refuses a role it does not carry", () => {
    assert.throws(
      () => scaffold(join(workdir, "nope"), "nope", "does-not-exist", packageRoot),
      /no starter named 'does-not-exist'/,
    );
  });

  it("requires the role, as the last positional, and never guesses it", () => {
    // Not a style preference: availableStarters() sorts, so a default would silently
    // change role the day a starter sorting before the current one is added.
    const bare = run(["--no-color"]);
    assert.equal(bare.code, 1);
    assert.match(bare.err, /A role is required, and comes last\. Available: issuer/);

    // A name with no role reads the name as the role and fails — which is the point:
    // it never quietly scaffolds whichever role happens to sort first.
    const nameOnly = run(["some-project", "--no-color"]);
    assert.equal(nameOnly.code, 1);
    assert.match(nameOnly.err, /No starter named 'some-project'/);
    assert.ok(!existsSync(join(workdir, "some-project")), "nothing scaffolded");

    // Swapping the two positionals fails loudly rather than scaffolding a project
    // named after a role.
    const swapped = run(["issuer", "some-project", "--no-color"]);
    assert.equal(swapped.code, 1);
    assert.match(swapped.err, /No starter named 'some-project'/);

    const tooMany = run(["a", "b", "c", "--no-color"]);
    assert.equal(tooMany.code, 1);
    assert.match(tooMany.err, /Too many arguments/);
  });

  it("refuses to prompt into a pipe rather than exiting 0 with nothing", () => {
    // Non-TTY stdin is CI, a piped script and a Dockerfile. `rl.question` never
    // resolves there: the event loop drained and node exited 0 having printed the
    // prompt and created nothing, so the failure landed on the caller's next line.
    const piped = run(["issuer", "--no-color"]);
    assert.equal(piped.code, 1);
    assert.match(piped.err, /stdin is not a terminal/);
    assert.match(piped.err, /Usage: usdt-pay-starter-ts/);
    assert.ok(!existsSync(join(workdir, "issuer")), "nothing scaffolded");
  });

  it("sends the acquirer role to the Java initializer instead of dead-ending", () => {
    // The acquirer starter is real, it just ships in usdt-pay-init.jar. "Available:
    // issuer" answered the one person who knew exactly which role they wanted with
    // nowhere to go.
    const acquirer = run(["acquirer", "--no-color"]);
    assert.equal(acquirer.code, 1);
    assert.match(acquirer.err, /usdt-pay-init\.jar/);
    assert.doesNotMatch(acquirer.err, /No starter named 'acquirer'/);
  });

  it("writes a named project with a usable .env and no stray secrets", () => {
    const target = join(workdir, "my-issuer");
    const keyPair = scaffold(target, "my-issuer", "issuer", packageRoot);

    assert.equal(JSON.parse(readFileSync(join(target, "package.json"), "utf8")).name, "my-issuer");

    // The undotted template file gets its name back, so the scaffolded project does
    // not commit its own .env on the first `git add -A`.
    assert.ok(existsSync(join(target, ".gitignore")), ".gitignore restored");
    assert.ok(!existsSync(join(target, "gitignore")), "undotted copy removed");
    assert.match(readFileSync(join(target, ".gitignore"), "utf8"), /^\.env$/m);

    const env = readFileSync(join(target, ".env"), "utf8");
    assert.match(env, new RegExp(`^PRIVATE_KEY=${keyPair.privateKeyHex}$`, "m"));
    assert.match(env, new RegExp(`^# 0x${keyPair.publicKeyHex}$`, "m"));
    // The rest of .env.example survives, and only the empty assignment was filled.
    assert.match(env, /^NETWORK_PUBLIC_KEY=$/m);
    assert.match(env, /^PORT=8080$/m);
    assert.equal(env.match(/^PRIVATE_KEY=/gm)?.length, 1);
  });

  it("replaces the in-repo Dockerfile and README instructions", () => {
    const target = join(workdir, "standalone");
    scaffold(target, "standalone", "issuer", packageRoot);

    // The overlay wins over the template: the in-repo Dockerfile's build context is
    // node/, which does not exist for a scaffolded project.
    const dockerfile = readFileSync(join(target, "Dockerfile"), "utf8");
    assert.match(dockerfile, /Build context is this project directory/);
    assert.doesNotMatch(dockerfile, /starter\/issuer\/Dockerfile/);

    // The lockfile, so the image runs the SDK build that was tested and not whatever
    // patch resolves on build day. `npm ci` needs it copied in.
    assert.match(dockerfile, /COPY package\.json package-lock\.json \.\//);
    assert.match(dockerfile, /^RUN npm ci$/m);

    // Shipped alongside, and the only reason `COPY . .` does not put .env in a layer.
    assert.ok(existsSync(join(target, ".dockerignore")), ".dockerignore shipped");
    assert.match(readFileSync(join(target, ".dockerignore"), "utf8"), /^\.env$/m);

    const readme = readFileSync(join(target, "README.md"), "utf8");
    assert.doesNotMatch(readme, /cd \.\.\/\.\./, "no repo-relative cd left in the README");
    assert.doesNotMatch(readme, /-f starter\/issuer\/Dockerfile/);

    // The one that loses data if it regresses: .env already holds the generated
    // private key, and the in-repo README's first run step overwrites it from the
    // blank example. The public half has already been sent to t-0 by then.
    assert.doesNotMatch(readme, /cp \.env\.example \.env/, "would destroy the generated key");
    assert.doesNotMatch(readme, /openssl rand -hex 32/, "the key is already generated");
    assert.match(readme, /Do not\n# overwrite it/);
  });

  it("tells nobody, anywhere, to write over the .env holding their key", () => {
    const target = join(workdir, "no-bad-advice");
    scaffold(target, "no-bad-advice", "issuer", packageRoot);

    // The whole tree, not just the README: the last of these lived in a config.ts
    // error message, where it surfaces exactly when a rattled developer will follow
    // it. .env holds the only copy of the generated private key, and its public half
    // is already with the t-0 team by the time any of this is read.
    const banned = ["Put your private key", "Copy .env.example", "cp .env.example .env"];
    for (const entry of readdirSync(target, { recursive: true, withFileTypes: true })) {
      if (!entry.isFile()) continue;
      const file = join(entry.parentPath, entry.name);
      const content = readFileSync(file, "utf8");
      for (const phrase of banned) {
        assert.ok(!content.includes(phrase), `${file} still says '${phrase}'`);
      }
    }
  });

  it("carries the example but never a real .env or build output", () => {
    // The packed template is what gets published. A dev machine's starter directory
    // can hold live keys, so this is the assertion that matters most here.
    for (const role of availableStarters(templateDir)) {
      const dir = join(templateDir, role);
      assert.ok(existsSync(join(dir, ".env.example")), `${role}: .env.example`);
      assert.ok(!existsSync(join(dir, ".env")), `${role}: must not ship a .env`);
      assert.ok(!existsSync(join(dir, "node_modules")), `${role}: node_modules`);
      assert.ok(!existsSync(join(dir, "dist")), `${role}: dist`);
    }
  });
});
