# usdt-pay-sdk

SDKs for the t-0 QR payment flow (`tzero.v1.pay`), in Java, Node and Python. The
contract lives in `proto/` and is snapshot-synced from `t-0-network/backend` —
protos are not authored here.

## Layout

```
proto/tzero/v1/pay/      the contract (common, validate, acquirer/, issuer/, lp/)
java/                    Gradle: sdk, starter/acquirer — see java/CLAUDE.md
node/                    npm workspace: sdk, starter/issuer — see node/CLAUDE.md
python/                  uv workspace: sdk, starter/acquirer — see python/CLAUDE.md
cli/                     unified scaffolder (Go) — `usdt-pay init`; docs/CLI.md
docs/RELEASE_AND_PUBLISH.md   the release process
```

Java stubs are generated at build time (`bufGenerate`, not committed). Node stubs
are committed under `node/sdk/src/gen/` so consumers need no `buf`.

## Build and test

```bash
# Node — always from node/, the lockfile and workspace root
cd node && npm install && npm run build && npm run typecheck && npm test

# Java — wrapper only, never a local gradle
cd java && ./gradlew build --no-daemon

# Python — uv workspace from python/
cd python && uv sync --all-packages && uv run pytest -v

# CLI — generate embeds the starters; the tests instantiate every one of them
cd cli && go generate ./... && go build ./... && go test ./...
```

CI (`ci-java.yaml`, `ci-node.yaml`, `ci-python.yaml`, `ci-cli.yaml`) runs exactly these builds; if they pass locally the tree is
releasable. `ci-cli.yaml` additionally scaffolds every starter with the built CLI and runs each
scaffold's tests against the SDKs built from the tree, on Linux and Windows (`docs/CLI.md`).

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
`ci-cli.yaml` verifies, adding a starter: `docs/CLI.md`.

## README section order

In each language README (`java/README.md`, `node/README.md`, `python/README.md`),
the **Starter** section must come before the **SDK** section. Developers start
with the starter, not the SDK directly.

## Starter READMEs — no repo-checkout instructions

The CLI (`usdt-pay init`) is the only documented way to create a project. No
user-facing README or doc may describe or imply running a starter from a repo
checkout. The starter READMEs are embedded verbatim by the CLI and must be
written exclusively for a scaffolded standalone project. Maintainer-only
workspace commands belong in `docs/CLI.md`, nowhere else.

## Signatures

Keccak256 over the raw request bytes plus a 64-bit LE timestamp, secp256k1-signed,
carried in `X-Signature` / `X-Public-Key` / `X-Signature-Timestamp`. Verification
must see the exact wire bytes — protobuf encoding is not canonical. The transport
in each ecosystem's provider-sdk (pinned exact; bump deliberately, not as drive-by)
handles both directions.

## Releasing

`docs/RELEASE_AND_PUBLISH.md` is the process. The two rules that are never bent:

- **Never `git tag vX.Y.Z` and never trigger `publish.yaml` by hand.**
- **A failed publish is recovered with "Re-run failed jobs" on that run.**

Version sites (all moved together by `release.yaml`, validated twice) are listed
in the doc.
