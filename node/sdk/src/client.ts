import type { DescService } from "@bufbuild/protobuf";
import type { Client } from "@connectrpc/connect";
import { createClient as createProviderClient, type SignerFunction } from "@t-0/provider-sdk";

/**
 * A client for the t-0 endpoints your role calls. Every request is signed with your
 * private key; t-0 knows you by the matching public key.
 *
 * ```ts
 * const t0 = createClient(config.tzeroEndpoint, config.privateKey, IssuerService);
 * const response = await t0.paymentReceived(request, { timeoutMs: 10_000 });
 * ```
 *
 * **`endpoint` is required on purpose.** The underlying provider client defaults to
 * `https://api.t-0.network`, which is a different API from this one — a pay
 * participant that omitted the endpoint would sign perfectly valid requests and send
 * them to the wrong host.
 *
 * **Every call has a 15 s deadline by default**, from the provider client. A
 * `{ timeoutMs }` in the second argument replaces it for that call: a Connect timeout
 * is a duration evaluated per call, so each of the `internal/` helpers sets its own and
 * the one slow endpoint can afford more than the rest.
 *
 * @param endpoint  t-0 API base URL — e.g. `https://usdt-pay-api-sandbox.t-0.network`
 * @param signer    your secp256k1 private key as hex, or a signing function if the
 *                  key lives in an HSM or KMS and never reaches this process
 * @param service   the service descriptor for your role — `IssuerService`,
 *                  `AcquirerService` or `LpService`
 */
export function createClient<T extends DescService>(
  endpoint: string,
  signer: string | SignerFunction,
  service: T,
): Client<T> {
  return createProviderClient(signer, endpoint, service);
}
