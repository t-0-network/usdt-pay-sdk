import assert from "node:assert/strict";
import { test } from "node:test";

import * as common from "../src/gen/tzero/v1/pay/common_pb.js";
import * as issuer from "../src/gen/tzero/v1/pay/issuer/issuer_pb.js";
import * as acquirer from "../src/gen/tzero/v1/pay/acquirer/acquirer_pb.js";
import * as lp from "../src/gen/tzero/v1/pay/lp/lp_pb.js";

// index.ts re-exports these four generated modules with `export *`. The proto
// packages are separate (tzero.v1.pay, .issuer, .acquirer, .lp), and ES module
// semantics EXCLUDE an ambiguous name from `export *` silently instead of
// erroring — a message added to two packages under the same name would simply
// disappear from the public API. This test makes that loud.
//
// Every generated message ships a runtime `<Name>Schema` const alongside its
// type-only export, and services/enums/file descriptors are consts too, so
// checking runtime keys covers type collisions as well.
test("generated modules re-exported from index.ts have disjoint export names", () => {
  const modules: Record<string, object> = { common, issuer, acquirer, lp };
  const seen = new Map<string, string>();
  for (const [moduleName, mod] of Object.entries(modules)) {
    for (const key of Object.keys(mod)) {
      const prev = seen.get(key);
      assert.equal(
        prev,
        undefined,
        `export "${key}" exists in both ${prev} and ${moduleName}; ` +
          `\`export *\` in index.ts would silently drop it — re-export it explicitly`,
      );
      seen.set(key, moduleName);
    }
  }
});

// Names a project needs from provider-sdk or @connectrpc/connect are re-exported, so
// user code imports only this package and installs one copy of connect.
test("the root re-exports what a handler and a client need", async () => {
  const root = await import("../src/index.js");
  assert.equal(typeof root.SDK_VERSION, "string");
  assert.equal(typeof root.DEFAULT_MAX_BODY_SIZE, "number");
  assert.equal(typeof root.validate, "function");
  assert.equal(typeof root.ConnectError, "function");
  assert.equal(root.Code.Internal, 13);
  assert.equal(typeof root.publicKeyFromPrivateKey, "function");
});

// ./crypto exports everything the root does except the code that runs on node:http.
// provider-sdk exports validate and DEFAULT_MAX_BODY_SIZE only from its root, which
// loads node:http, so they stay root-only here too.
test("./crypto exports every root name but the node:http ones, as the same bindings", async () => {
  const root: Record<string, unknown> = await import("../src/index.js");
  const crypto: Record<string, unknown> = await import("../src/crypto.js");
  const rootOnly = Object.keys(root).filter((key) => !(key in crypto));
  assert.deepEqual(rootOnly.sort(), [
    "DEFAULT_MAX_BODY_SIZE",
    "createClient",
    "createHandler",
    "createServer",
    "validate",
  ]);
  for (const key of Object.keys(crypto)) {
    assert.ok(key in root, `./crypto exports "${key}", which the root does not`);
    assert.equal(root[key], crypto[key], `"${key}" is a different binding in the root and in ./crypto`);
  }
});
