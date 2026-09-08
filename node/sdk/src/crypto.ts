/**
 * Framework-agnostic request verification and decoding, for mounting the pay
 * endpoints into a stack this SDK does not own — Effect, Hono, Koa, raw `http`,
 * anything that can hand you the raw request bytes.
 *
 * If you run Express/Fastify/raw-http and just want the endpoints mounted, use
 * {@link createHandler} from the package root instead — it does all of
 * this for you. This module is for everyone else.
 *
 * The recommended entry point is {@link createRequestDecoder}: one call that
 * verifies the signature, detects the content type, deserializes the protobuf
 * or JSON body, runs protovalidate, and gives you back a typed message plus an
 * `encodeResponse` function that answers in the same wire format.
 *
 * For lower-level control, {@link createRequestVerifier} and
 * {@link rejectRequest} are still exported.
 *
 * The one rule that cannot be broken: **verify the exact bytes that arrived.**
 * No body parsers, no decompression, no re-serialized protobuf — protobuf
 * encoding is not canonical, so re-encoding may change the signed bytes, and
 * then the signature no longer matches.
 *
 * Every provider-sdk import here uses `@t-0/provider-sdk/crypto`, not the
 * package root: the root re-exports the Connect node adapter and drags
 * `node:http`/`node:https` into whatever imports it, which is exactly what a
 * non-Node-http stack does not want. `test/crypto-isolation.test.ts` keeps it
 * that way.
 */

import {
  createRequestDecoder as createBaseRequestDecoder,
  type CreateVerifierOptions,
  type RequestDecoder,
} from "@t-0/provider-sdk/crypto";
import { payRegistry } from "./registry.js";

/**
 * One-call request decoder with the pay contract's registry baked in.
 * Create once at startup, call per request. The raw bytes rule applies:
 * pass the exact wire bytes, no body parsers or decompression.
 */
export function createRequestDecoder(opts: CreateVerifierOptions): RequestDecoder {
  return createBaseRequestDecoder({ ...opts, registry: payRegistry });
}

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
} from "@t-0/provider-sdk/crypto";
export type {
  CreateVerifierOptions,
  RejectedRequest,
  RequestVerifier,
  VerifyRequest,
  VerifyRequestFailure,
  VerifyRequestResult,
  IncomingRequest,
  IncomingHeaders,
  WireFormat,
  WireResponse,
  DecodeRequestResult,
  DecodeRequestFailure,
  DecodeError,
  Violation,
  RequestDecoder,
} from "@t-0/provider-sdk/crypto";
