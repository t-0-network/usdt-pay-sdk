package network.t0.pay.issuer;

/** Everything the starter reads from the environment. */
public record Config(
        String privateKey,
        String networkPublicKey,
        String tzeroEndpoint,
        int port
) {
}
