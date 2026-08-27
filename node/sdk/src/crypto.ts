/**
 * Framework-agnostic request verification, for mounting the pay endpoints into a
 * stack this SDK does not own — Effect, Hono, Koa, raw `http`, anything that can
 * hand you the raw request bytes.
 *
 * If you run Express/Fastify/raw-http and just want the endpoints mounted, use
 * {@link createHandler} from the package root instead — it does all of
 * this for you. This module is for everyone else: verify the signature yourself
 * with {@link createRequestVerifier}, answer failures with {@link rejectRequest},
 * and decode/encode the protobuf bodies with `fromBinary`/`toBinary`.
 *
 * The one rule that cannot be broken: **verify the exact bytes that arrived.**
 * No body parsers, no decompression, no re-serialized protobuf — protobuf
 * encoding is not canonical, so a re-encoded message is a different message to
 * secp256k1.
 */

// Everything comes off provider-sdk's root entry, not its ./crypto subpath: the
// CJS build resolves with node10 semantics, which cannot see `exports` subpaths.
export {
  createRequestVerifier,
  DEFAULT_TOLERANCE_MS,
  verifySignature,
  computeDigest,
  keccak256,
  parsePublicKey,
  publicKeysEqual,
  NetworkHeaders,
} from "@t-0/provider-sdk";
export type {
  CreateVerifierOptions,
  VerifyRequest,
  VerifyRequestResult,
  VerifyRequestFailure,
  RequestVerifier,
} from "@t-0/provider-sdk";

import type { VerifyRequestFailure } from "@t-0/provider-sdk";

/** What {@link rejectRequest} tells you to send back: a ready-to-write HTTP error. */
export interface RejectedRequest {
  /** HTTP status code. */
  status: number;
  /** Headers to set on the response. */
  headers: Record<string, string>;
  /** Response body, already serialized. */
  body: string;
}

// Statuses and codes mirror what the SDK's own transport answers for the same
// failures (Connect unary errors): malformed input is invalid_argument/400, a
// signature or key that does not check out is unauthenticated/401.
const REJECTIONS: Record<VerifyRequestFailure, { status: number; code: string; message: string }> = {
  invalid_timestamp: {
    status: 400,
    code: "invalid_argument",
    message: "X-Signature-Timestamp header is missing or not a unix-millis timestamp",
  },
  timestamp_out_of_range: {
    status: 400,
    code: "invalid_argument",
    message: "X-Signature-Timestamp is outside the accepted clock window",
  },
  invalid_public_key: {
    status: 400,
    code: "invalid_argument",
    message: "X-Public-Key header is not a well-formed secp256k1 public key",
  },
  unknown_public_key: {
    status: 401,
    code: "unauthenticated",
    message: "X-Public-Key is not the t-0 network public key",
  },
  invalid_signature_format: {
    status: 400,
    code: "invalid_argument",
    message: "X-Signature header is not a well-formed signature",
  },
  signature_failed: {
    status: 401,
    code: "unauthenticated",
    message: "signature does not verify against the request bytes",
  },
};

/**
 * Maps a verification failure to the HTTP error the caller should see, in the
 * wire format the network expects (a Connect unary error: JSON body, status
 * matching the code). Send it verbatim:
 *
 * ```ts
 * const result = verifyRequest({ body, signatureHeader, publicKeyHeader, timestampHeader });
 * if (!result.valid) {
 *   const err = rejectRequest(result.reason);
 *   return respond(err.body, err.status, err.headers);
 * }
 * ```
 *
 * `VerifyRequestFailure` is an open union — a newer provider-sdk may report
 * reasons this version has never heard of. Those map to unauthenticated/401:
 * when in doubt, the request stays out.
 */
export function rejectRequest(reason: VerifyRequestFailure): RejectedRequest {
  const rejection = REJECTIONS[reason] ?? {
    status: 401,
    code: "unauthenticated",
    message: `request rejected: ${reason}`,
  };
  return {
    status: rejection.status,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code: rejection.code, message: rejection.message }),
  };
}
