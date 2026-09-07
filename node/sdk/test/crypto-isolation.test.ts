import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { existsSync } from "node:fs";
import path from "node:path";
import { test } from "node:test";

// `@t-0/usdt-pay-sdk/crypto` exists so that Hono/Effect/Koa/edge stacks can
// verify requests without the package root's Connect node adapter, which pulls
// in `node:http`/`node:https`. One import from `@t-0/provider-sdk` (the root,
// not `/crypto`) in src/crypto.ts silently brings them back (#69). Each case
// loads a module in a fresh child process and reads `process.moduleLoadList`,
// which records every built-in the process has loaded.

const sdkDir = path.resolve(import.meta.dirname, "..");
const PROBE = "process.moduleLoadList.filter((m) => /^NativeModule https?$/.test(m))";

function loadedHttpModules(args: string[], code: string): string[] {
  const out = execFileSync(process.execPath, [...args, "-e", code], {
    cwd: sdkDir,
    encoding: "utf8",
  });
  return JSON.parse(out) as string[];
}

function esmProbe(specifier: string): string[] {
  return loadedHttpModules(
    ["--import", "tsx", "--input-type=module"],
    `await import(${JSON.stringify(specifier)}); console.log(JSON.stringify(${PROBE}));`,
  );
}

test("the detector sees node:http when the package root is loaded", () => {
  // Positive control: without this, the two assertions below would also pass
  // on a probe that reports nothing.
  assert.deepEqual(esmProbe("./src/index.ts"), ["NativeModule http", "NativeModule https"]);
});

test("src/crypto.ts loads neither node:http nor node:https", () => {
  assert.deepEqual(esmProbe("./src/crypto.ts"), []);
});

const cjsCrypto = path.join(sdkDir, "lib", "cjs", "crypto.js");

test(
  "lib/cjs/crypto.js resolves @t-0/provider-sdk/crypto under require and stays http-free",
  { skip: existsSync(cjsCrypto) ? false : "lib/cjs missing — run `npm run build` first" },
  () => {
    const loaded = loadedHttpModules(
      [],
      `require(${JSON.stringify(cjsCrypto)}); console.log(JSON.stringify(${PROBE}));`,
    );
    assert.deepEqual(loaded, []);
  },
);
