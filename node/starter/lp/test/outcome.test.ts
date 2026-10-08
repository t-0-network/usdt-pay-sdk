import assert from "node:assert/strict";
import { test } from "node:test";
import { Code, ConnectError } from "@t-0/usdt-pay-sdk";
import { noResultVariant, outcomeFromError } from "../src/internal/outcome.js";

test("an unrecognised result variant is unknown, not rejected", () => {
  const outcome = noResultVariant();

  assert.equal(outcome.kind, "unknown");
  assert.equal(outcome.shouldRetry, true);
});

/**
 * t-0 read the request and refused it: the same bytes would be refused again, so these
 * are answers, not lost calls. The reason names the code and carries t-0's message.
 */
for (const [code, name] of [
  [Code.InvalidArgument, "InvalidArgument"],
  [Code.Unauthenticated, "Unauthenticated"],
  [Code.PermissionDenied, "PermissionDenied"],
  [Code.Unimplemented, "Unimplemented"],
  [Code.FailedPrecondition, "FailedPrecondition"],
] as const) {
  test(`${name} is rejected, not retried`, () => {
    const outcome = outcomeFromError(new ConnectError("amount_usdt is required", code));

    assert.equal(outcome.kind, "rejected");
    assert.equal(outcome.shouldRetry, false);
    assert.equal(outcome.kind === "rejected" && outcome.reason, `${name}: amount_usdt is required`);
  });
}

/** Any other code leaves open whether t-0 committed the call: retry the same key. */
for (const code of [Code.Unavailable, Code.DeadlineExceeded, Code.Internal, Code.Unknown]) {
  test(`${Code[code]} is unknown, which must be retried`, () => {
    const outcome = outcomeFromError(new ConnectError("t-0 is down", code));

    assert.equal(outcome.kind, "unknown");
    assert.equal(outcome.shouldRetry, true);
  });
}

test("an error that is not an answer from t-0 surfaces instead of becoming an outcome", () => {
  const bug = new TypeError("cannot read properties of undefined");

  assert.throws(() => outcomeFromError(bug), (thrown) => thrown === bug);
});
