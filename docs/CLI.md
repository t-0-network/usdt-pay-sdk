# The unified CLI (`usdt-pay init`)

`cli/` is a product instance of provider-sdk's unified scaffolder. `usdt-pay init <name>
--lang=<java|node> --role=<role>` creates a standalone project from one of the starters in this
repo, generates a secp256k1 keypair for it and writes its `.env`.

## What `init` does

1. Extracts the starter for `<lang>/<role>` from the templates embedded in the binary.
   `go generate ./...` (`cli/generate.go`) copies `java/starter/acquirer/` and
   `node/starter/issuer/` into `cli/internal/embed/<lang>/<role>/` — gitignored, produced at
   build time — so the CLI always ships the starters of the commit it was built from.
2. Writes the overlay for that `<lang>/<role>` over the extracted files (below).
3. Generates a keypair and writes `.env` from the starter's `.env.example`: the private key goes
   into `PROVIDER_PRIVATE_KEY`, the matching public key replaces the `# your_public_key_here`
   comment right under it and is printed at the end, `NETWORK_PUBLIC_KEY` is left for the user to
   fill in from their onboarding contact. `.env` is written `0600`.

## What is synced and what is owned here

Eight files are upstream-owned and overwritten by every sync PR from provider-sdk — the list is
provider-sdk's `.github/workflows/cli-sync-config/usdt-pay-sdk.yaml`:

`main.go`, `scaffold.go`, `keygen.go`, `keygen_test.go`, `env.go`, `go.mod`, `go.sum`,
`internal/sync/main.go`

A bug in any of them is fixed in provider-sdk first; the next sync carries it here. A local patch
survives exactly until that sync.

Everything else under `cli/` is repo-owned:

| File | Purpose |
|---|---|
| `config.go` | the product's `CLIConfig`: `ProductName`, `Command`, `RoleRequired`, `Languages`, `OverlayFS` |
| `overlay.go` | one `//go:embed all:overlay` — the overlay files as an `embed.FS` |
| `overlay/<lang>/<role>/` | the files that cannot ship verbatim (below) |
| `generate.go` | the `go generate` step that embeds the starters |
| `starters_test.go`, `scaffold_test.go` | this product's tests (below) |
| `install.sh`, `install.ps1` | installer scripts |

The synced code knows nothing about this product except through `CLIConfig`. `OverlayFS` is
the seam for product files: when set, the synced scaffolder applies it itself after template
extraction. `PostScaffold func(ScaffoldOpts) error` exists for anything else and is unused here.

## Overlays

A starter is a live project inside this repo, and some of its files only make sense there:

- `Dockerfile` — the in-repo one builds with the workspace as its context and compiles the SDK
  from source; a scaffolded project has no workspace and resolves the SDK from a registry.
- `.dockerignore` — keeps `.env`, and with it the private key, out of the image build context.
- `README.md` — the in-repo one is written for the monorepo layout.

`cli/overlay/<lang>/<role>/` holds the standalone versions. They are copied over the extracted
template verbatim — no placeholder or filename transforms — so what is in that directory is
exactly what a user gets. Every starter must have an overlay with at least `Dockerfile` and
`.dockerignore`; `starters_test.go` fails otherwise.

## What proves it works

A sync PR that is green on `go build` proves only that `cli/` still compiles. The scaffolder is
proven by:

- **`cli/starters_test.go`** — requires the embedded starters to be exactly the
  `<lang>/starter/<role>/` directories in the tree and `Config.Languages` to list exactly the
  languages that have one (a new starter is covered without editing the test, and an unwired one
  fails it), then runs the CLI end to end for each:
  - `.gitignore` and the language's entry files are there, `dot-gitignore` is not;
  - the overlay exists and every one of its files lands byte for byte;
  - `.env` holds a fresh key: `0x` + 64 hex, a valid secp256k1 scalar, unique across
    instantiations, and the public key printed to the user and the one recorded in `.env` both
    derive from it; `.env.example` keeps its placeholders; `.env` is `0600`.
- **`cli/scaffold_test.go`** — embed path handling and the name helpers.
- **`.github/workflows/ci-go.yaml`** — `go generate`, `go build`, `go test` in `cli/`; the
  tests above, run in CI. Triggered by `cli/**`.
- **`.github/workflows/ci-scaffold.yaml`** — builds the binary, scaffolds every embedded
  `<lang>/<role>` with it, `cmp`s the overlay, and builds and runs the tests of each scaffolded
  project **against the SDK in this tree**: Java through `publishToMavenLocal` into an isolated
  `maven.repo.local` under version `0.0.0-local` (not published anywhere) plus an init script
  adding `mavenLocal()`, Node through `npm pack` and installing the tarball. Not against the
  published SDK: between a proto sync and the next release the starters use SDK changes that are
  not published yet, while the released CLI always embeds a starter that matches the SDK released
  with it. Triggered by `cli/**`, `java/**`, `node/**` and `proto/**`, because the starters and
  SDKs it builds are all inputs. A language without a case fails the loop loudly.

## Handling a sync PR

- `go generate ./... && go build ./... && go test ./...` in `cli/` — the tests are what tells
  you the product still works, not the build.
- If `CLIConfig` gained or lost a field, `config.go` is where to follow.
- If `starters_test.go` fails on the overlay checks, the sync changed how `OverlayFS` is applied;
  that is an upstream conversation, not a local patch.

## Adding a starter

1. The starter itself under `java/starter/<role>/` or `node/starter/<role>/`, wired into
   `cli/generate.go`. Its `.env.example` needs `PROVIDER_PRIVATE_KEY=` and the
   `# your_public_key_here` line.
2. `cli/overlay/<lang>/<role>/` with `Dockerfile`, `.dockerignore` and `README.md`.
3. A new language: add it to `Languages` in `config.go` and a build-and-test case to the loop in
   `ci-scaffold.yaml`. Nothing in the tests needs editing.
4. The version sites and publish jobs in `docs/RELEASE_AND_PUBLISH.md`, "Starters".
