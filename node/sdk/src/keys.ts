// The public key t-0 knows you by, derived from the private key you sign with: the
// uncompressed (65-byte) key as 0x-prefixed hex. Print it at startup and send it to the
// t-0 team — that is step 1 of every role's integration.
export { publicKeyFromPrivateKey } from "@t-0/provider-sdk/crypto";
