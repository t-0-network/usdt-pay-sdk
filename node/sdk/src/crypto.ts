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

export {
  createRequestVerifier,
  DEFAULT_TOLERANCE_MS,
  rejectRequest,
  verifySignature,
  computeDigest,
  keccak256,
  parsePublicKey,
  publicKeysEqual,
  NetworkHeaders,
} from "@t-0/provider-sdk";
export type {
  CreateVerifierOptions,
  RejectedRequest,
  RequestVerifier,
  VerifyRequest,
  VerifyRequestFailure,
  VerifyRequestResult,
} from "@t-0/provider-sdk";
