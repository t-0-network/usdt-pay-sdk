package usdtpay

import (
	"testing"

	"github.com/t-0-network/provider-sdk/go/crypto"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/acquirer/acquirerconnect"
)

const testPrivateKey = "0x4c0883a69102937d6231471b5dbb6204fe512961708279f23efb0fecd891d2f2"

func TestCreateClient_RejectsEmptyEndpoint(t *testing.T) {
	if _, err := CreateClient("", testPrivateKey, acquirerconnect.NewAcquirerServiceClient); err == nil {
		t.Fatal("expected error for empty endpoint")
	}
}

func TestCreateClientWithSigner(t *testing.T) {
	signFn, err := crypto.NewSignerFromHex(testPrivateKey)
	if err != nil {
		t.Fatalf("NewSignerFromHex: %v", err)
	}
	if _, err := CreateClientWithSigner("https://example.invalid", signFn, acquirerconnect.NewAcquirerServiceClient); err != nil {
		t.Fatalf("CreateClientWithSigner: %v", err)
	}
	if _, err := CreateClientWithSigner("", signFn, acquirerconnect.NewAcquirerServiceClient); err == nil {
		t.Fatal("expected error for empty endpoint")
	}
}
