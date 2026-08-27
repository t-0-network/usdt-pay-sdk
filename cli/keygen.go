package main

import (
	"encoding/hex"
	"fmt"

	"github.com/decred/dcrd/dcrec/secp256k1/v4"
)

type KeyPair struct {
	PrivateKey string // hex with 0x prefix
	PublicKey  string // hex with 0x prefix, uncompressed (65 bytes)
}

func generateKeyPair() (KeyPair, error) {
	privKey, err := secp256k1.GeneratePrivateKey()
	if err != nil {
		return KeyPair{}, fmt.Errorf("generating private key: %w", err)
	}
	pubKey := privKey.PubKey()
	return KeyPair{
		PrivateKey: "0x" + hex.EncodeToString(privKey.Serialize()),
		PublicKey:  "0x" + hex.EncodeToString(pubKey.SerializeUncompressed()),
	}, nil
}
