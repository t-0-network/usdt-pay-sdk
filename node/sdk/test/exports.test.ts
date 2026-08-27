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
