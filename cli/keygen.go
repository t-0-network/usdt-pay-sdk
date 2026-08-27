package main

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"

	"github.com/decred/dcrd/dcrec/secp256k1/v4"
)

type KeyPair struct {
	PrivateKey string // hex with 0x prefix
	PublicKey  string // hex with 0x prefix, uncompressed (65 bytes)
}

func generateKeyPair() (KeyPair, error) {
	var privKeyBytes [32]byte
	for {
		if _, err := rand.Read(privKeyBytes[:]); err != nil {
			return KeyPair{}, fmt.Errorf("generating random bytes: %w", err)
		}
		privKey := secp256k1.PrivKeyFromBytes(privKeyBytes[:])
		if privKey == nil {
			continue
		}
		// Verify the key is valid (not zero, not >= curve order)
		pubKey := privKey.PubKey()
		if pubKey == nil {
			continue
		}
		return KeyPair{
			PrivateKey: "0x" + hex.EncodeToString(privKey.Serialize()),
			PublicKey:  "0x" + hex.EncodeToString(pubKey.SerializeUncompressed()),
		}, nil
	}
}
