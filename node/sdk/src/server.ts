import http from "node:http";
import { createHandler as createProviderHandler, type Router } from "@t-0/provider-sdk";
import { SDK_VERSION } from "./version.js";
import { payRegistry } from "./registry.js";

export { payRegistry } from "./registry.js";
export type { Router } from "@t-0/provider-sdk";

/**
 * The pay endpoints as a plain `(req, res)` handler, for mounting into an HTTP
 * server you already run instead of letting {@link createServer} own one.
 * Signature verification, response validation and the health service are all
 * inside — it is the same handler that server wraps.
 *
 * ```ts
 * const app = express();
 * // Mount BEFORE any body parser — the handler must see the raw bytes.
 * app.use("/sda/payments/t0", createHandler(networkPublicKey, (r) => {
 *   r.service(IssuerCallbackService, issuerCallbackHandler);
 * }));
 * app.use(express.json()); // parsers for the rest of the app go after
 * ```
 *
 * Two constraints, both about bytes:
 *
 * - **Mount it before anything that touches the body.** The signature is
 *   verified against the raw bytes as they stream in; `express.json()` and
 *   friends consume the stream and the handler would see nothing. The same goes
 *   for anything that decompresses.
 * - **Mounting under a prefix works because Express strips it** from `req.url`
 *   before the handler routes on the Connect path
 *   (`/<package>.<Service>/<Method>`). A framework that passes the full URL
 *   through needs the handler mounted at the root, or the prefix removed first.
 *
 * For a stack that cannot hand this handler Node's `(req, res)` pair at all
 * (Effect, Hono, …), drop a level: `@t-0/usdt-pay-sdk/crypto` has the request
 * decoder this handler uses, for wiring up yourself.
 *
 * @param networkPublicKey t-0's public key — inbound calls that do not verify
 *                         against it are refused
 * @param register         mounts your callback handlers
 */
export function createHandler(
  networkPublicKey: string,
  register: (router: Router) => void,
): (request: http.IncomingMessage, response: http.ServerResponse) => void {
  return createProviderHandler(networkPublicKey, register, {
    registry: payRegistry,
    version: SDK_VERSION,
  });
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
 * const server = await createServer(port, networkPublicKey, (r) => {
 *   r.service(IssuerCallbackService, issuerCallbackHandler);
 * });
 * ```
 *
 * Two things this exists to keep you from getting wrong:
 *
 * - **The hasher has to see the raw bytes.** Signatures are verified against the
 *   bytes that arrived, never against a re-serialization — protobuf encoding is not
 *   canonical, so a re-encoded message is a different message to secp256k1.
 *   {@link createHandler} wires the raw-body hasher in for you.
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
export function createServer(
  port: number,
  networkPublicKey: string,
  register: (router: Router) => void,
): Promise<http.Server> {
  const server = http.createServer(createHandler(networkPublicKey, register));

  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(port, () => {
      server.removeListener("error", reject);
      resolve(server);
    });
  });
}
