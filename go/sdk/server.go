package usdtpay

import (
	"net/http"

	"github.com/t-0-network/provider-sdk/go/provider"
)

// StartServer starts a pay callback server with signature verification.
// Every inbound request is verified against networkPublicKey.
func StartServer(
	addr string,
	networkPublicKey string,
	handlers ...provider.BuildHandler,
) (provider.ServerShutdownFn, error) {
	h, err := NewHTTPHandler(networkPublicKey, handlers...)
	if err != nil {
		return nil, err
	}
	return provider.StartServer(h, provider.WithAddr(addr))
}

// NewHTTPHandler creates the pay handler for mounting into an existing server.
// Every inbound request is verified against networkPublicKey.
func NewHTTPHandler(
	networkPublicKey string,
	handlers ...provider.BuildHandler,
) (http.Handler, error) {
	return provider.NewHttpHandlerWithOptions(
		provider.NetworkPublicKeyHexed(networkPublicKey),
		[]provider.HttpHandlerOption{provider.WithSDKVersion(SDKVersion)},
		handlers...,
	)
}
