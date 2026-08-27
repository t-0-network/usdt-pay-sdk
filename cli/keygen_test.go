package main

import (
	"encoding/hex"
	"strings"
	"testing"
)

func TestGenerateKeyPair(t *testing.T) {
	kp, err := generateKeyPair()
	if err != nil {
		t.Fatal(err)
	}

	// Private key: 0x + 64 hex chars = 32 bytes
	if !strings.HasPrefix(kp.PrivateKey, "0x") {
		t.Errorf("private key missing 0x prefix: %s", kp.PrivateKey)
	}
	privBytes, err := hex.DecodeString(strings.TrimPrefix(kp.PrivateKey, "0x"))
	if err != nil {
		t.Fatalf("private key not valid hex: %v", err)
	}
	if len(privBytes) != 32 {
		t.Errorf("private key length = %d, want 32", len(privBytes))
	}

	// Public key: 0x + 130 hex chars = 65 bytes, starts with 04
	if !strings.HasPrefix(kp.PublicKey, "0x") {
		t.Errorf("public key missing 0x prefix: %s", kp.PublicKey)
	}
	pubBytes, err := hex.DecodeString(strings.TrimPrefix(kp.PublicKey, "0x"))
	if err != nil {
		t.Fatalf("public key not valid hex: %v", err)
	}
	if len(pubBytes) != 65 {
		t.Errorf("public key length = %d, want 65", len(pubBytes))
	}
	if pubBytes[0] != 0x04 {
		t.Errorf("public key prefix = 0x%02x, want 0x04", pubBytes[0])
	}
}

func TestGenerateKeyPairUniqueness(t *testing.T) {
	kp1, err := generateKeyPair()
	if err != nil {
		t.Fatal(err)
	}
	kp2, err := generateKeyPair()
	if err != nil {
		t.Fatal(err)
	}
	if kp1.PrivateKey == kp2.PrivateKey {
		t.Error("two generated keypairs have identical private keys")
	}
}
