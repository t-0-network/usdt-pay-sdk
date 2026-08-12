import { secp256k1 } from "@noble/curves/secp256k1.js";

/**
 * The public key t-0 knows you by, derived from the private key you sign with.
 *
 * Print it at startup and send it to the t-0 team — that is step 1 of every role's
 * integration. Calling this early also fails a malformed key at startup rather than
 * on the first request.
 *
 * @param privateKey 32 bytes as hex, 0x prefix optional
 * @returns the uncompressed (65-byte) public key as 0x-prefixed hex
 * @throws Error if the key is not a valid secp256k1 secret
 */
export function publicKeyFromPrivateKey(privateKey: string): string {
  const hex = privateKey.replace(/^0x/, "");
  if (!/^[0-9a-fA-F]{64}$/.test(hex)) {
    throw new Error("private key must be 64 hex characters (32 bytes)");
  }

  const key = Buffer.from(hex, "hex");
  if (!secp256k1.utils.isValidSecretKey(key)) {
    throw new Error("private key is not a valid secp256k1 secret");
  }

  return "0x" + Buffer.from(secp256k1.getPublicKey(key, false)).toString("hex");
}
