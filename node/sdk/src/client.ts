import type { DescService } from "@bufbuild/protobuf";
import { createClient as createProviderClient, type Client, type SignerFunction } from "@t-0/provider-sdk";

/**
 * A client for the t-0 endpoints your role calls. Every request is signed with your
 * private key; t-0 knows you by the matching public key.
 *
 * ```ts
 * const t0 = createClient(config.privateKey, config.tzeroEndpoint, IssuerService);
 * const response = await t0.paymentReceived(request);
 * ```
 *
 * **`baseUrl` is required on purpose.** The underlying provider client defaults to
 * `https://api.t-0.network`, which is a different API from this one — a pay
 * participant that omitted the base URL would sign perfectly valid requests and send
 * them to the wrong host.
 *
 * Every call gets provider-sdk's default deadline; a `{ timeoutMs }` in a call's
 * second argument replaces it for that call.
 *
 * @param signer    your secp256k1 private key as hex or raw bytes, or a signing function if the
 *                  key lives in an HSM or KMS and never reaches this process
 * @param baseUrl   t-0 API base URL — e.g. `https://usdt-pay-api-sandbox.t-0.network`
 * @param service   the service descriptor for your role — `IssuerService`,
 *                  `AcquirerService` or `LpService`
 */
export function createClient<T extends DescService>(
  signer: string | Buffer | SignerFunction,
  baseUrl: string,
  service: T,
): Client<T> {
  return createProviderClient(signer, baseUrl, service);
}
