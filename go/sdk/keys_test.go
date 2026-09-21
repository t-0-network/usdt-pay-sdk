package usdtpay

import (
	"strings"
	"testing"
)

func TestPublicKeyFromPrivateKey_RoundTrip(t *testing.T) {
	// A known test key — the exact hex is arbitrary but must be a valid scalar.
	priv := "0x4c0883a69102937d6231471b5dbb6204fe512961708279f23efb0fecd891d2f2"
	pub, err := PublicKeyFromPrivateKey(priv)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.HasPrefix(pub, "0x04") {
		t.Fatalf("public key %s does not start with 0x04", pub)
	}
	// Uncompressed key: 65 bytes = 130 hex chars + "0x" prefix = 132 chars.
	if len(pub) != 132 {
		t.Fatalf("public key length = %d, want 132", len(pub))
	}

	// Same key without 0x prefix.
	pub2, err := PublicKeyFromPrivateKey(priv[2:])
	if err != nil {
		t.Fatalf("unexpected error without 0x prefix: %v", err)
	}
	if pub != pub2 {
		t.Fatalf("public key differs with/without 0x prefix")
	}
}

func TestPublicKeyFromPrivateKey_InvalidKey(t *testing.T) {
	_, err := PublicKeyFromPrivateKey("not-a-key")
	if err == nil {
		t.Fatal("expected error for invalid key")
	}
}
