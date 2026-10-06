package usdtpay

import (
	"github.com/t-0-network/provider-sdk/go/crypto"
	"github.com/t-0-network/provider-sdk/go/network"
)

// CreateClient creates a signed ConnectRPC client for a t-0 pay service,
// signing every request with the given hex private key.
//
// endpoint is required — the provider SDK defaults to api.t-0.network, which
// is a different API.
func CreateClient[T any](
	endpoint string,
	privateKeyHex string,
	newClient network.ClientFactory[T],
) (T, error) {
	return network.NewServiceClient(network.PrivateKeyHexed(privateKeyHex), newClient, network.WithBaseURL(endpoint))
}

// CreateClientWithSigner is CreateClient for a key that lives in an HSM or KMS
// and never reaches this process: signFn signs every request instead.
func CreateClientWithSigner[T any](
	endpoint string,
	signFn crypto.SignFn,
	newClient network.ClientFactory[T],
) (T, error) {
	return network.NewServiceClient("", newClient, network.WithBaseURL(endpoint), network.WithSignatureFunction(signFn))
}
