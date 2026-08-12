import assert from "node:assert/strict";
import { test } from "node:test";
import { create } from "@bufbuild/protobuf";
import { DecimalSchema } from "@t-0/usdt-pay-sdk";
import { decimalFromString, decimalToString, decimalToUnits } from "../src/internal/decimals.js";

const wire = (unscaled: bigint, exponent: number) =>
  create(DecimalSchema, { unscaled, exponent });

test("round trips through the wire form", () => {
  const value = decimalFromString("100000.00");

  assert.equal(value.unscaled, 10_000_000n);
  assert.equal(value.exponent, -2);
  assert.equal(decimalToString(value), "100000.00");
});

test("keeps a value smaller than one readable", () => {
  assert.equal(decimalToString(decimalFromString("0.5")), "0.5");
  assert.equal(decimalToString(decimalFromString("-0.05")), "-0.05");
  assert.equal(decimalFromString("-0.05").unscaled, -5n);
});

test("reads a round magnitude t-0 sent as a positive exponent", () => {
  // 1e9 arrives as unscaled=10, exponent=8 rather than as a billion zeros.
  assert.equal(decimalToString(wire(10n, 8)), "1000000000");
});

test("refuses precision the contract cannot carry", () => {
  // Nine decimal places needs exponent -9; the contract stops at -8. The caller has
  // to decide how to round rather than have it silently truncated.
  assert.throws(() => decimalFromString("0.000000001"), RangeError);
  assert.equal(decimalToString(decimalFromString("0.00000001")), "0.00000001");
});

test("refuses magnitudes too large for the unscaled int64", () => {
  assert.throws(() => decimalFromString("9223372036854775808"), RangeError);
  assert.equal(decimalFromString("9223372036854775807").unscaled, 9_223_372_036_854_775_807n);
});

test("refuses anything that is not a plain decimal", () => {
  // Exponent notation would have to be a float first, which is the whole point.
  assert.throws(() => decimalFromString("1e9"), RangeError);
  assert.throws(() => decimalFromString(""), RangeError);
});

test("converts to chain units exactly", () => {
  // What an ERC-681 URI carries: 123.45 USDt at 6 decimals.
  assert.equal(decimalToUnits(decimalFromString("123.45"), 6), 123_450_000n);
  assert.equal(decimalToUnits(wire(10n, 8), 6), 1_000_000_000_000_000n);
});

test("refuses to round a customer's money away", () => {
  // 7 decimal places into a 6-decimal chain unit: that last digit is money.
  assert.throws(() => decimalToUnits(decimalFromString("0.1234567"), 6), RangeError);
});
