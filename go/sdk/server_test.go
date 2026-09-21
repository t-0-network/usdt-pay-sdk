package usdtpay

import (
	"context"
	"testing"
)

func TestNewHTTPHandler_RejectsEmptyKey(t *testing.T) {
	_, err := NewHTTPHandler("")
	if err == nil {
		t.Fatal("expected error for empty network public key")
	}
}

func TestNewHTTPHandler_AcceptsValidKey(t *testing.T) {
	const networkKey = "0x04dd549e27ef8acc1c9d2027d7973b1e5d8e014ce5ac36424ef9f7a3ee8a44029dee77d7acbc8e8980453eadc4e60db153403febd147e14c8444de806943524d3e"
	h, err := NewHTTPHandler(networkKey)
	if err != nil {
		t.Fatalf("NewHTTPHandler: %v", err)
	}
	if h == nil {
		t.Fatal("handler is nil")
	}
}

func TestStartServer_StartsAndShuts(t *testing.T) {
	const networkKey = "0x04dd549e27ef8acc1c9d2027d7973b1e5d8e014ce5ac36424ef9f7a3ee8a44029dee77d7acbc8e8980453eadc4e60db153403febd147e14c8444de806943524d3e"

	shutdown, err := StartServer(":0", networkKey)
	if err != nil {
		t.Fatalf("StartServer: %v", err)
	}
	if err := shutdown(context.Background()); err != nil {
		t.Fatalf("shutdown: %v", err)
	}
}
