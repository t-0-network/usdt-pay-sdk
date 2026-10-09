package usdtpay

import (
	"testing"

	"github.com/t-0-network/provider-sdk/go/crypto"
)

func TestPublicKeyFromPrivateKey_DelegatesToProviderSDK(t *testing.T) {
	priv := "0x4c0883a69102937d6231471b5dbb6204fe512961708279f23efb0fecd891d2f2"
	want, wantErr := crypto.PublicKeyFromPrivateKey(priv)
	got, err := PublicKeyFromPrivateKey(priv)
	if err != wantErr || got != want {
		t.Fatalf("PublicKeyFromPrivateKey = (%q, %v), provider-sdk = (%q, %v)", got, err, want, wantErr)
	}
}
