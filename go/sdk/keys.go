package usdtpay

import "github.com/t-0-network/provider-sdk/go/crypto"

// PublicKeyFromPrivateKey derives the uncompressed public key (0x04…, 65 bytes
// as hex) from a hex private key. Call it early so a malformed key fails at
// startup rather than on the first request. It is provider-sdk's
// crypto.PublicKeyFromPrivateKey.
func PublicKeyFromPrivateKey(privateKeyHex string) (string, error) {
	return crypto.PublicKeyFromPrivateKey(privateKeyHex)
}
