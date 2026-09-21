# usdt-pay-sdk

SDKs for the t-0 USDt pay flow (`tzero.v1.pay`), in Go, Java, Node and Python. The
contract lives in `proto/` and is snapshot-synced from `t-0-network/backend` —
protos are not authored here.

## Layout

```
proto/tzero/v1/pay/      the contract (common, validate, acquirer/, issuer/, lp/)
go/                      Go: sdk, starter/acquirer — see go/CLAUDE.md
java/                    Gradle: sdk, starter/acquirer — see java/CLAUDE.md
node/                    npm workspace: sdk, starter/* — see node/CLAUDE.md
python/                  uv workspace: sdk, starter/acquirer — see python/CLAUDE.md
cli/                     unified scaffolder (Go) — `usdt-pay init`; docs/CLI.md
docs/RELEASE_AND_PUBLISH.md   the release process
```

Java stubs are generated at build time (`bufGenerate`, not committed). Go and Node
stubs are committed (`go/sdk/gen/`, `node/sdk/src/gen/`) so consumers need no `buf`.

## Build and test

```bash
# Node — always from node/, the lockfile and workspace root
cd node && npm install && npm run build && npm run typecheck && npm test

# Java — wrapper only, never a local gradle
cd java && ./gradlew build --no-daemon

# Python — uv workspace from python/
cd python && uv sync --all-packages && uv run pytest -v

# Go — from go/sdk/
cd go/sdk && go build ./... && go test -v ./...

# CLI — generate embeds the starters; the tests instantiate every one of them
cd cli && go generate ./... && go build ./... && go test ./...
```

CI (`ci-go.yaml`, `ci-java.yaml`, `ci-node.yaml`, `ci-python.yaml`, `ci-cli.yaml`) runs exactly these builds; if they pass locally the tree is
releasable. `ci-cli.yaml` additionally scaffolds every starter with the built CLI and runs each
scaffold's tests against the SDKs built from the tree on Linux; the Windows job
scaffolds the two Node starters and the Go acquirer (essential-files check, no
test run). Details: `docs/CLI.md`.

## The proto sync

A bot PR from the backend adds/updates `proto/` and regenerates `go/sdk/gen/` and
`node/sdk/src/gen/` (`generate-clients.yaml`, `buf generate --clean` in each).
When handling one:

- `go/sdk/proto` is a symlink into the root `proto/`. `buf.gen.yaml` uses managed
  mode with `go_package_prefix` — the pay protos have no `go_package` option.
  Generated stubs land in `go/sdk/gen/`.
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

`cli/` is a product instance of provider-sdk's unified CLI. The following files
are synced from upstream and overwritten by every sync PR — `main.go`,
`scaffold.go`, `keygen.go`, `keygen_test.go`, `env.go`, `go.mod`, `go.sum`,
`internal/sync/main.go`, `internal/gomod/gomod.go`, `internal/gomod/gomod_test.go`,
`config_test.go`, `scaffold_test.go` — so a bug in them is fixed in
provider-sdk first. Everything else under `cli/` is repo-owned. A sync PR is
proven by `ci-cli.yaml`; reproduce locally with `go generate && go test` in
`cli/`. How the pieces fit — embedded starters, the tests, what `ci-cli.yaml`
verifies, adding a starter: `docs/CLI.md`.

The Go starter has exactly one `go.mod`/`go.sum` with the real module path; the
scaffolder reads it from the embedded `go.mod.tmpl` and replaces the module path
with `--module` at scaffold time. Never add a `.tmpl` next to it in the tree.

## README section order

In each language README (`go/README.md`, `java/README.md`, `node/README.md`, `python/README.md`),
the **Starter** section must come before the **SDK** section. Developers start
with the starter, not the SDK directly.

## Starter READMEs — no repo-checkout instructions

The CLI (`usdt-pay init`) is the only documented way to create a project. No
user-facing README or doc may describe or imply running a starter from a repo
checkout. The starter READMEs are embedded verbatim by the CLI and must be
written exclusively for a scaffolded standalone project. Maintainer-only
workspace commands belong in `docs/CLI.md`, nowhere else.

## Starters ship working defaults

Every starter runs end to end against the sandbox out of the box: hardcoded example values
(a demo sale at startup in the acquirer starters, example deposit addresses in the issuer, a
fixed FX quote published on a timer in the LP) and callbacks that accept by default.
Production access is granted only after a sandbox assessment, so an example value cannot
reach production. Do not gate a starter behind a decline-until-implemented handler, do not
add tests that "hold the line" against the success path, and do not flag hardcoded demo
values as a risk or ask about them. Simplicity over defensiveness.

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
