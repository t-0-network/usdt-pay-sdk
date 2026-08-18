import assert from "node:assert/strict";
import { test } from "node:test";
import { noResultVariant } from "../src/internal/outcome.js";

test("an unrecognised result variant is unknown, not rejected", () => {
  const outcome = noResultVariant();

  assert.equal(outcome.kind, "unknown");
  assert.equal(outcome.shouldRetry, true);
});
