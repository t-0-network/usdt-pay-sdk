import http from "node:http";
import type { DescService } from "@bufbuild/protobuf";
import { createRegistry } from "@bufbuild/protobuf";
import type { ServiceImpl } from "@connectrpc/connect";
import { createService, nodeAdapter, signatureValidation } from "@t-0/provider-sdk";
import { file_tzero_v1_pay_issuer_issuer } from "./gen/tzero/v1/pay/issuer/issuer_pb.js";
import { file_tzero_v1_pay_acquirer_acquirer } from "./gen/tzero/v1/pay/acquirer/acquirer_pb.js";
import { file_tzero_v1_pay_lp_lp } from "./gen/tzero/v1/pay/lp/lp_pb.js";
import { file_tzero_v1_pay_common } from "./gen/tzero/v1/pay/common_pb.js";
import { file_tzero_v1_pay_validate } from "./gen/tzero/v1/pay/validate_pb.js";

/**
 * Registry covering the pay contract protos. Every file is listed explicitly:
 * `createRegistry` does NOT walk a file's imports, so relying on the service
 * files to pull in common.proto and validate.proto leaves the custom
 * predefined-rule extensions (`valid_tx_hash`, `valid_address`) unresolvable
 * at validation time. A new proto file added by a sync must be added here.
 */
export const payRegistry = createRegistry(
  file_tzero_v1_pay_issuer_issuer,
  file_tzero_v1_pay_acquirer_acquirer,
  file_tzero_v1_pay_lp_lp,
  file_tzero_v1_pay_common,
  file_tzero_v1_pay_validate,
);

/**
 * Where you mount the callback services t-0 calls on you. One `service()` call
 * per service you implement.
 */
export interface UsdtPayRouter {
  service<T extends DescService>(service: T, implementation: Partial<ServiceImpl<T>>): void;
}

/**
 * The server a pay participant runs so t-0 can call it: acquirers host §7/§11/§13/§15,
 * issuers host §5, LPs host §8.
 *
 * Every inbound request is signature-verified against the t-0 network public key
 * before it reaches your handler, and every response is validated against the
 * contract's constraints on the way out.
 *
 * ```ts
 * const server = await createUsdtPayServer(port, networkPublicKey, (r) => {
 *   r.service(IssuerCallbackService, issuerCallbackHandler);
 * });
 * ```
 *
 * Two things this exists to keep you from getting wrong:
 *
 * - **The hasher has to see the raw bytes.** Signatures are verified against the
 *   bytes that arrived, never against a re-serialization — protobuf encoding is not
 *   canonical, so a re-encoded message is a different message to secp256k1. That is
 *   what `signatureValidation` wrapping the adapter does, and forgetting it is a
 *   verification bug you would only notice in production. Here it is not optional.
 * - **You do not import a type called `provider`** to run a server that is not a
 *   provider's. This delegates to the transport in `@t-0/provider-sdk`, which is
 *   signed Connect and carries nothing provider-specific; the wrapper goes away once
 *   the transport ships as a package of its own.
 *
 * It serves the services you register, plus the standard `grpc.health.v1.Health` the
 * transport mounts on every server it builds. Health is behind the same signature
 * check as everything else, so only t-0 can call it.
 *
 * @param port             the port to listen on; t-0 must be able to reach it
 * @param networkPublicKey t-0's public key — inbound calls that do not verify
 *                         against it are refused
 * @param register         mounts your callback handlers
 * @returns the listening server. `close()` it to shut down; `address()` reports the
 *          bound port, which is what you want when `port` was 0.
 */
export function createUsdtPayServer(
  port: number,
  networkPublicKey: string,
  register: (router: UsdtPayRouter) => void,
): Promise<http.Server> {
  const server = http.createServer(
    signatureValidation(
      nodeAdapter(
        createService(networkPublicKey, register, {
          registry: payRegistry,
        }),
      ),
    ),
  );

  // Resolve on `listening` rather than handing back a server that is not up yet:
  // `address()` is null until then, and a port already in use has to surface as a
  // rejected promise instead of an unhandled 'error' event.
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(port, () => {
      server.removeListener("error", reject);
      resolve(server);
    });
  });
}
