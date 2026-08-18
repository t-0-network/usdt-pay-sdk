import { create } from "@bufbuild/protobuf";
import { type Decimal, DecimalSchema } from "@t-0/usdt-pay-sdk";

/**
 * `Decimal` is `unscaled * 10^exponent` — 123.45 is unscaled=12345n, exponent=-2.
 *
 * **Money never travels as a `number` here, and it should not become one on your side
 * either.** `unscaled` is a 64-bit integer, so it is a `bigint`; a USDt amount routed
 * through a JS float loses cents at amounts a POS actually rings up. Everything below
 * is integer arithmetic on purpose — there is no `toNumber`.
 *
 * The cheapest correct thing you can do with an amount t-0 sent you is *not convert
 * it*: §6 has to report exactly the amount §5 carried, so pass that `Decimal` through
 * untouched rather than rebuilding it.
 */

/** The contract constrains exponent to this range; anything else is rejected on the wire. */
const MIN_EXPONENT = -8;
const MAX_EXPONENT = 8;

const INT64_MIN = -(2n ** 63n);
const INT64_MAX = 2n ** 63n - 1n;

/** No exponent notation: `1e9` would have to be a `number` first, which is the point. */
const PLAIN_DECIMAL = /^-?\d+(\.\d+)?$/;

/**
 * @param value a plain decimal string — `"100000.00"`, `"-0.5"`, `"7"`
 * @throws RangeError if the value carries more precision, or more magnitude, than the
 *         contract can hold. Round to the precision you mean before calling this;
 *         truncating money silently is not this function's decision to make.
 */
export function decimalFromString(value: string): Decimal {
  if (!PLAIN_DECIMAL.test(value)) {
    throw new RangeError(`'${value}' is not a plain decimal number`);
  }

  const [whole, fraction = ""] = value.split(".");
  const exponent = -fraction.length;
  if (exponent < MIN_EXPONENT) {
    throw new RangeError(
      `${value} needs exponent ${exponent}, outside the contract's [${MIN_EXPONENT}, ${MAX_EXPONENT}] — round it first`,
    );
  }

  // "-0.5" splits to whole="-0", fraction="5", and BigInt("-05") is -5n.
  const unscaled = BigInt(whole + fraction);
  if (unscaled < INT64_MIN || unscaled > INT64_MAX) {
    throw new RangeError(`${value} does not fit the contract's 64-bit unscaled value`);
  }

  return create(DecimalSchema, { unscaled, exponent });
}

/**
 * Exact decimal string, for logs and for chain-native URIs that want a human amount.
 *
 * Handles a positive exponent too: t-0 may send a round magnitude as unscaled=10n,
 * exponent=8 rather than as a billion zeros.
 */
export function decimalToString(value: Decimal): string {
  const negative = value.unscaled < 0n;
  const sign = negative ? "-" : "";
  let digits = (negative ? -value.unscaled : value.unscaled).toString();

  if (value.exponent >= 0) {
    return sign + digits + "0".repeat(value.exponent);
  }

  // Pad so there is at least one digit left of the point: 5n@-1 has to read "0.5".
  const shortBy = 1 - (digits.length + value.exponent);
  if (shortBy > 0) {
    digits = "0".repeat(shortBy) + digits;
  }

  const point = digits.length + value.exponent;
  return sign + digits.slice(0, point) + "." + digits.slice(point);
}

/**
 * The amount in a chain's smallest unit — what an ERC-681 URI carries. USDt is 6
 * decimals on the deployments in this contract.
 *
 * @throws RangeError if the amount is finer than `decimals` can express, rather than
 *         rounding a customer's money away behind your back
 */
export function decimalToUnits(value: Decimal, decimals: number): bigint {
  const shift = value.exponent + decimals;
  if (shift < 0) {
    throw new RangeError(
      `${decimalToString(value)} has more precision than ${decimals} decimals can carry`,
    );
  }
  return value.unscaled * 10n ** BigInt(shift);
}
