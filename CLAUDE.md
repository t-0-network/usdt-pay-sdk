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
cli/                     unified scaffolder (Go) — `usdt-pay init`; docs/CLI.md
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

# CLI — generate embeds the starters; the tests instantiate every one of them
cd cli && go generate ./... && go build ./... && go test ./...
```

CI (`ci-java.yaml`, `ci-node.yaml`, `ci-go.yaml`) runs exactly these builds; if they pass locally the tree is
releasable. `ci-scaffold.yaml` additionally scaffolds every starter with the built CLI and runs
each scaffold's tests against the SDKs built from the tree (`docs/CLI.md`).

## The proto sync

A bot PR from the backend adds/updates `proto/` and regenerates `node/sdk/src/gen/`
(`generate-clients.yaml`, `buf generate --clean` in `node/sdk`). When handling one:

- `java/sdk/src/main/proto/tzero` is a symlink into the root `proto/` — Java follows
  automatically, but Java sources import generated classes by package
  (`network.t0.pay.proto.tzero.v1.pay[.<role>]`), so a message moving packages or
  losing a name prefix breaks starter/test imports. Fix the imports; the contract
  is upstream's to change.
- `payRegistry` in `node/sdk/src/registry.ts` must list **every** pay proto file
  explicitly — `createRegistry` does not walk imports, and a file missing there
  makes protovalidate's custom predefined rules (`valid_tx_hash`, `valid_address`)
  unresolvable at runtime, failing responses with `Code.Internal`. A new proto
  file in the sync means a new entry there.
- `node/sdk/src/index.ts` re-exports the generated modules with `export *`; ES
  semantics silently drop a name exported by two of them.
  `node/sdk/test/exports.test.ts` fails on such a collision — resolve it with an
  explicit re-export, not by deleting the test.

## The CLI sync

`cli/` is a product instance of provider-sdk's unified CLI. Eight files are
synced from upstream and overwritten by every sync PR — `main.go`, `scaffold.go`,
`keygen.go`, `keygen_test.go`, `env.go`, `go.mod`, `go.sum`,
`internal/sync/main.go` — so a bug in them is fixed in provider-sdk first.
Everything else under `cli/` is repo-owned. A green sync PR proves only that
`cli/` compiles; `go test ./...` in `cli/` is what proves the scaffolder still
works. How the pieces fit — embedded starters, the tests, what
`ci-scaffold.yaml` verifies, adding a starter: `docs/CLI.md`.

## Starter READMEs — no repo-checkout instructions

The CLI (`usdt-pay init`) is the only documented way to create a project. No
user-facing README or doc may describe or imply running a starter from a repo
checkout — no `cp .env.example`, no workspace-relative builds (`cd ../..`), no
Docker with the repository as context, no "if you cloned the repo" conditionals.
The starter READMEs (`java/starter/acquirer/README.md`,
`node/starter/issuer/README.md`) are embedded verbatim by the CLI and must be
written exclusively for a scaffolded standalone project. Maintainer-only
workspace commands belong in `docs/CLI.md`, nowhere else.

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
