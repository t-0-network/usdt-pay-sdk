export * from "./client.js";
export * from "./server.js";
export { SDK_VERSION } from "./version.js";
// Also importable as `@t-0/usdt-pay-sdk/crypto` — same module, and the subpath
// is the one to use where the decoder is wanted without pulling in the server's
// node:http dependency.
export * from "./crypto.js";

// The pay contract is split across four proto packages (common, issuer, acquirer,
// lp), so identical message names in two packages COULD collide here — and ES
// module semantics silently drop an ambiguous `export *` name instead of erroring.
// The names are disjoint today; `test/exports.test.ts` fails the build if a future
// sync introduces a collision that would silently vanish from this barrel.
export * from "./gen/tzero/v1/pay/common_pb.js";
export * from "./gen/tzero/v1/pay/issuer/issuer_pb.js";
export * from "./gen/tzero/v1/pay/acquirer/acquirer_pb.js";
export * from "./gen/tzero/v1/pay/lp/lp_pb.js";

export type { Client, HandlerContext, SignerFunction, Signature } from "@t-0/provider-sdk";
// A handler that validates its own response passes the pay registry, or the contract's
// custom rules (`valid_address`, `valid_tx_hash`) cannot be resolved:
// `validate(schema, msg, { registry: payRegistry })`.
export { DEFAULT_MAX_BODY_SIZE, validate } from "@t-0/provider-sdk";
export type { ValidateOptions } from "@t-0/provider-sdk";
// The error type and codes a handler throws and a client catches, and the handler
// type, from the one copy of @connectrpc/connect that provider-sdk runs on.
export { Code, ConnectError } from "@connectrpc/connect";
export type { ServiceImpl } from "@connectrpc/connect";
