export * from "./client.js";
export * from "./server.js";
export * from "./keys.js";
// Also importable as `@t-0/usdt-pay-sdk/crypto` — same module, and the subpath
// is the one to use where only verification is wanted and tree-shaking matters.
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

export type { Client, HandlerContext } from "@connectrpc/connect";
export type { SignerFunction, Signature } from "@t-0/provider-sdk";
