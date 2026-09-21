package usdtpay

import (
	"errors"

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
	opts ...network.ClientOption,
) (T, error) {
	if endpoint == "" {
		var zero T
		return zero, errors.New("endpoint is required")
	}
	opts = append(opts, network.WithBaseURL(endpoint))
	return network.NewServiceClient(network.PrivateKeyHexed(privateKeyHex), newClient, opts...)
}
