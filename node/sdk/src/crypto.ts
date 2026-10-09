/**
 * Everything the package root exports except the code that runs on `node:http`:
 * `createClient`, `createHandler` and `createServer`, and provider-sdk's `validate`
 * and `DEFAULT_MAX_BODY_SIZE`, which provider-sdk exports only from its root. This
 * is the module for mounting the pay endpoints into a stack this SDK does not own —
 * Effect, Hono, Koa, edge runtimes, anything that can hand you the raw request bytes.
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
 * non-Node-http stack does not want. `@connectrpc/connect` and the generated
 * stubs load neither. `test/crypto-isolation.test.ts` keeps it that way, and
 * `test/exports.test.ts` checks that this module and the root differ by exactly
 * the names above.
 */

import {
  createRequestDecoder as createBaseRequestDecoder,
  type CreateVerifierOptions,
  type RequestDecoder,
} from "@t-0/provider-sdk/crypto";
import { payRegistry } from "./registry.js";
import { SDK_VERSION } from "./version.js";

/**
 * One-call request decoder with the pay contract's registry baked in.
 * Create once at startup, call per request. The raw bytes rule applies:
 * pass the exact wire bytes, no body parsers or decompression.
 */
export function createRequestDecoder(opts: Pick<CreateVerifierOptions, "networkPublicKey">): RequestDecoder {
  return createBaseRequestDecoder({ networkPublicKey: opts.networkPublicKey, registry: payRegistry, version: SDK_VERSION });
}

export {
  createRequestVerifier,
  rejectRequest,
  verifySignature,
  computeDigest,
  keccak256,
  publicKeysEqual,
  NetworkHeaders,
  // The public key t-0 knows you by, derived from the private key you sign with: the
  // uncompressed (65-byte) key as 0x-prefixed hex. Print it at startup and send it to the
  // t-0 team — that is step 1 of every role's integration.
  publicKeyFromPrivateKey,
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
  SignerFunction,
  Signature,
} from "@t-0/provider-sdk/crypto";

export { SDK_VERSION, payRegistry };
// The error type and codes a handler throws and a client catches, and the handler
// type, from the one copy of @connectrpc/connect that provider-sdk runs on.
export { Code, ConnectError } from "@connectrpc/connect";
export type { ServiceImpl } from "@connectrpc/connect";

// The same four generated modules index.ts re-exports. `export *` silently drops a
// name two of them export; `test/exports.test.ts` fails the build on such a collision.
export * from "./gen/tzero/v1/pay/common_pb.js";
export * from "./gen/tzero/v1/pay/issuer/issuer_pb.js";
export * from "./gen/tzero/v1/pay/acquirer/acquirer_pb.js";
export * from "./gen/tzero/v1/pay/lp/lp_pb.js";
