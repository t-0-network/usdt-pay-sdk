package usdtpay

import "github.com/t-0-network/provider-sdk/go/network"

// CreateClient creates a signed ConnectRPC client for a t-0 pay service,
// signing every request with the given hex private key.
//
// baseURL is required — the provider SDK defaults to api.t-0.network, which
// is a different API.
//
// For a key that lives in an HSM or KMS, or for any other client option, call
// network.NewServiceClient directly (network.WithSignatureFunction,
// network.WithBaseURL).
func CreateClient[T any](
	baseURL string,
	privateKeyHex string,
	newClient network.ClientFactory[T],
) (T, error) {
	return network.NewServiceClient(network.PrivateKeyHexed(privateKeyHex), newClient, network.WithBaseURL(baseURL))
}
