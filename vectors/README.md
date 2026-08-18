# Signature vectors

`signature-v1.json` pins the t-0 request signature as data — payload, timestamp, digest,
signature — so an SDK can prove it interoperates instead of proving it runs. Every value
in it is 0x-prefixed lowercase hex; every timestamp is decimal milliseconds since the
epoch.

## The scheme

```
digest    = keccak256(payload || uint64le(timestampMs))
signature = secp256k1 ECDSA over that 32-byte digest — RFC 6979 deterministic k
            (HMAC-SHA256), low-S, r[32] || s[32] || v[1]
```

| Header | Value |
|---|---|
| `X-Public-Key` | `0x` + hex of the uncompressed 65-byte key (`0x04` + x + y) |
| `X-Signature` | `0x` + hex of the signature |
| `X-Signature-Timestamp` | decimal milliseconds |

A request is refused unless its timestamp is within 60 000 ms of the verifier's clock and
its signature verifies under the key the verifier was configured with. The protocol
context around that is in the
[authentication reference](https://usdt-pay-docs.t-0.network/docs/integration-guidance/protocol/authentication/).

**`payload` is the request body as it goes on the wire, before any decoding.** Which bytes
those are depends on the transport, which is why the file carries all three:

| Transport | Body |
|---|---|
| Connect, `application/proto` | the serialized message |
| Connect, `application/json` | its JSON form — what the Node SDK's client sends |
| gRPC | the framed body: `0x00` + `uint32be(length)` + the serialized message |

The Java provider verifies against the unframed body first and the gRPC-framed body second,
because the network signs below the framer when it calls over gRPC and above it when it
calls over Connect.

Determinism is what lets a fixture name exact signature bytes: with RFC 6979 there is no
nonce to differ, so BouncyCastle, noble and dcrd produce the same `r` and `s` for the same
key and digest. Java and Go then append the recovery byte and the Node client stops at 64;
every verifier takes 64 or 65 bytes and ignores `v`.

## What the file holds

| Key | Contents |
|---|---|
| `keys` | `network` — the key a verifier is configured to trust; `impostor` — the one it is not |
| `trustedKey` | which of those the verification cases are configured against |
| `message` | the fields the payloads were built from, for a consumer that wants to rebuild the request |
| `signing` | payload + timestamp → digest + signature |
| `verification` | a presented request → accepted, or refused with a reason |
| `divergences` | inputs today's SDKs answer differently; asserted by nobody |

### `signing`

`digest` and `signature` are what an implementation must produce from `payload`,
`timestampMs` and `keys.network.privateKey`. `framing` and `contentType` say where such a
body comes from; neither is an input to the digest.

`leading-zero-r` is the padding case: its `r` starts with a zero byte, so a signer that
trims leading zeros instead of padding both components to a fixed 32 bytes fails it and
passes everything else.

### `verification`

Each case is self-contained: `payload`, `timestampMs`, `publicKey`, `signature`, and
`nowMs` — the verifier's clock, defaulting to `timestampMs`. `accept` is what a provider
does with it; a refused case names the Connect code in `reason`.

`check` says which layer decides, so a suite that only reaches one of them knows what it
can run:

| `check` | Decided by |
|---|---|
| `signature` | the curve, over the digest |
| `identity` | the presented key against the trusted one, before any curve arithmetic |
| `timestamp` | the 60-second window, before the body is read |

## Who runs them

- [`java/sdk/src/test/java/network/t0/pay/signature/SignatureVectorsTest.java`](../java/sdk/src/test/java/network/t0/pay/signature/SignatureVectorsTest.java)
  re-derives every digest and signature with BouncyCastle and checks the `signature` cases.
  The vectors were produced with a different curve library, so agreement there is two
  implementations agreeing on bytes, not one library agreeing with itself.
- [`node/sdk/test/signature_vectors.test.ts`](../node/sdk/test/signature_vectors.test.ts)
  replays every verification case against a real `createUsdtPayServer` under a fake clock,
  and checks that `createUsdtPayClient` puts the `connect-json` payload and its signature
  on the wire.

The Go SDK is the next consumer: `encoding/json` reads the file, and the `signature` and
`timestamp` cases map onto `provider.newSignatureVerifierMiddleware` as directly as they do
onto the other two.

Changing the scheme means new fixtures. Sign the new payloads with any one implementation
and run both suites: they re-derive every byte, so a fixture only its own library agrees
with fails immediately.

## Divergences

Three inputs get different answers from the three SDKs today. `divergences` carries the
bytes and what each side does with them; no suite asserts them, because a fixture that
fails a shipped SDK is a bug report, not a fixture.

| Case | Java | Node | Go |
|---|---|---|---|
| `clock-behind-by-more-than-the-window` — a timestamp further ahead than the window allows | reject | **accept** | reject |
| `high-s-signature` — the malleated twin of a valid signature, `s` replaced by `n − s` | **accept** | reject | **accept** |
| `public-key-header-without-0x` — the prefix the published spec calls optional | accept | accept | **reject** |

Java and Go compare `|now − timestamp|`; the Node provider measures only how far in the
past a timestamp is, so a future one never expires. Node's curve library refuses a
non-canonical `s`, and the Java and Go verifiers read `r` and `s` without looking at which
half of the order `s` falls in. Go's header parser drops the first two characters of every
hex header whether or not they are `0x`, so a prefixless key loses a byte — send the prefix.
