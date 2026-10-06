import assert from "node:assert/strict";
import { test } from "node:test";
import { createClient, IssuerService } from "../src/index.js";

const PRIVATE_KEY = "0x" + "ab".repeat(32);

test("a missing endpoint is refused, not replaced by the provider client's default API", () => {
  assert.throws(() => createClient(undefined as unknown as string, PRIVATE_KEY, IssuerService), /base URL is not set/);
  assert.throws(() => createClient("", PRIVATE_KEY, IssuerService), /base URL is not set/);
});
