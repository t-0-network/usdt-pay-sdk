import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { after, before, describe, it } from "node:test";
import { publicKeyFromPrivateKey } from "@t-0/usdt-pay-sdk";
import { generateKeyPair, sanitizeProjectName, scaffold } from "../src/scaffold.js";

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

  before(() => {
    // prepack generates template/; the test needs it whether or not a pack ran.
    execFileSync("node", [join(packageRoot, "scripts", "pack-template.mjs")], { stdio: "ignore" });
    workdir = mkdtempSync(join(tmpdir(), "usdt-pay-starter-"));
  });

  after(() => rmSync(workdir, { recursive: true, force: true }));

  it("writes a named project with a usable .env and no stray secrets", () => {
    const target = join(workdir, "my-issuer");
    const keyPair = scaffold(target, "my-issuer", templateDir);

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

  it("carries the example but never a real .env or build output", () => {
    // The packed template is what gets published. A dev machine's starter directory
    // can hold live keys, so this is the assertion that matters most here.
    assert.ok(existsSync(join(templateDir, ".env.example")));
    assert.ok(!existsSync(join(templateDir, ".env")), "template must not ship a .env");
    assert.ok(!existsSync(join(templateDir, "node_modules")));
    assert.ok(!existsSync(join(templateDir, "dist")));
  });
});
