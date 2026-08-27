# usdt-pay-sdk

SDKs for the t-0 QR payment flow (`tzero.v1.pay`), in Java and Node today. The
contract lives in `proto/` and is snapshot-synced from `t-0-network/backend` —
protos are not authored here.

## Layout

```
proto/tzero/v1/pay/      the contract: common.proto + validate.proto in package
                         tzero.v1.pay; acquirer/, issuer/, lp/ as per-role packages
java/                    Gradle build: sdk, starter/acquirer
node/                    npm workspace: sdk, starter/issuer
cli/                     unified scaffolder (Go) — `usdt-pay init`
docs/RELEASE_AND_PUBLISH.md   the release process — read before touching versions,
                              tags, or the publish workflows
```

Java stubs are generated at build time (`bufGenerate`, not committed). Node stubs
are committed under `node/sdk/src/gen/` so consumers need no `buf`.

## Build and test

```bash
# Node — always from node/, the lockfile and workspace root
cd node && npm install && npm run build && npm run typecheck && npm test

# Java — wrapper only, never a local gradle
cd java && ./gradlew build --no-daemon
```

CI (`ci.yaml`) runs exactly these two builds; if they pass locally the tree is
releasable.

## The proto sync

A bot PR from the backend adds/updates `proto/` and regenerates `node/sdk/src/gen/`
(`generate-clients.yaml`, `buf generate --clean` in `node/sdk`). When handling one:

- `java/sdk/src/main/proto/tzero` is a symlink into the root `proto/` — Java follows
  automatically, but Java sources import generated classes by package
  (`network.t0.pay.proto.tzero.v1.pay[.<role>]`), so a message moving packages or
  losing a name prefix breaks starter/test imports. Fix the imports; the contract
  is upstream's to change.
- `payRegistry` in `node/sdk/src/server.ts` must list **every** pay proto file
  explicitly — `createRegistry` does not walk imports, and a file missing there
  makes protovalidate's custom predefined rules (`valid_tx_hash`, `valid_address`)
  unresolvable at runtime, failing responses with `Code.Internal`. A new proto
  file in the sync means a new entry there.
- `node/sdk/src/index.ts` re-exports the generated modules with `export *`; ES
  semantics silently drop a name exported by two of them.
  `node/sdk/test/exports.test.ts` fails on such a collision — resolve it with an
  explicit re-export, not by deleting the test.

## Signatures

Same scheme as provider-sdk: Keccak256 over the raw request bytes plus a 64-bit
little-endian timestamp, secp256k1-signed, carried in `X-Signature` /
`X-Public-Key` / `X-Signature-Timestamp`. Verification must see the exact wire
bytes — protobuf encoding is not canonical, so anything that re-serializes or
decompresses the body breaks it. The transport in `@t-0/provider-sdk` (pinned
exact in `node/sdk/package.json`; bump deliberately, not as drive-by) does this
for both directions; `@t-0/usdt-pay-sdk/crypto` exposes it for standalone
integrations.

## Releasing

`docs/RELEASE_AND_PUBLISH.md` is the process. The two rules that are never bent:

- **Never `git tag vX.Y.Z` and never trigger `publish.yaml` by hand.** A release
  is `gh workflow run release.yaml -f bump=… --ref master`, and the tag it pushes
  fires the publish. Both registries are immutable; there is no undo.
- **A failed publish is recovered with "Re-run failed jobs" on that run** — a
  re-dispatch of `release.yaml` would mint the next version and strand the tag.

Version sites (all moved together by `release.yaml`, validated twice) are listed
in the doc. Adding an ecosystem? The doc's "Adding an ecosystem" section is the
checklist.
