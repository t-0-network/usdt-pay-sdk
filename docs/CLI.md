# The unified CLI (`usdt-pay`)

`cli/` is a product instance of provider-sdk's unified scaffolder. The binary is `usdt-pay`;
`usdt-pay init --lang=<language> --role=<role> <project-name>` creates a standalone project from
one of the starters in this repo, generates a secp256k1 keypair for it and writes its `.env`.
The first half of this document is the reference for using it; the second half, every section
headed "Maintainers", is how the pieces fit and what to do when they change.

## Overview

| | |
|---|---|
| Binary | `usdt-pay` (`usdt-pay.exe` on Windows) |
| Commands | `init`, `keygen`, `version`, `help` |
| Languages and roles | `java`/`acquirer`, `node`/`issuer` |
| Releases | assets `usdt-pay-<os>-<arch>[.exe]` on the GitHub Release `vX.Y.Z`, uploaded by `publish-cli` in `publish.yaml` |
| Installers | `cli/install.sh` (macOS, Linux) and `cli/install.ps1` (Windows) download the latest release |
| Source | `cli/` — eight files synced from provider-sdk, the rest repo-owned (see "Maintainers — ownership and sync") |
| Short version | [`cli/README.md`](../cli/README.md) — install and create a project |

## Installation

### `install.sh` — macOS and Linux

```bash
curl -fsSL https://raw.githubusercontent.com/t-0-network/usdt-pay-sdk/master/cli/install.sh | sh -s -- init --lang=<language> --role=<role> <project-name>
```

The script is `#!/bin/sh` with `set -eu` and needs `curl` or `wget`. Step by step:

1. Picks the asset from `uname`: `Linux*` → `linux`, `Darwin*` → `darwin`; `x86_64`/`amd64` →
   `amd64`, `aarch64`/`arm64` → `arm64`. Anything else stops with `Error: unsupported OS: <os>`
   or `Error: unsupported architecture: <arch>`, exit 1.
2. Downloads `https://github.com/t-0-network/usdt-pay-sdk/releases/latest/download/usdt-pay-<os>-<arch>`
   into a temporary directory and makes it executable.
3. Installs to `/usr/local/bin` when that directory is writable, otherwise to `~/.local/bin`
   (created if missing), and prints `Installed usdt-pay to <dir>/usdt-pay`.
4. If `<dir>` is not on `$PATH`, prints the line to add and leaves your shell configuration
   alone:

   ```
   <dir> is not in your PATH. Add it permanently:
     echo 'export PATH="<dir>:$PATH"' >> ~/.bashrc  # or ~/.zshrc
   ```

5. Runs `<dir>/usdt-pay` with every argument after `sh -s --`; the script's exit status is the
   binary's. With no arguments it prints the usage line instead:

   ```
   Usage:
     usdt-pay init --lang=<language> --role=<role> <project-name>
   ```

Install only, with no project:

```bash
curl -fsSL https://raw.githubusercontent.com/t-0-network/usdt-pay-sdk/master/cli/install.sh | sh
```

### `install.ps1` — Windows

```powershell
iwr -Uri https://raw.githubusercontent.com/t-0-network/usdt-pay-sdk/master/cli/install.ps1 -OutFile install.ps1
.\install.ps1 init --lang=<language> --role=<role> <project-name>
```

1. Picks `usdt-pay-windows-arm64.exe` when `PROCESSOR_ARCHITECTURE` is `ARM64`, otherwise
   `usdt-pay-windows-amd64.exe`, and downloads it from
   `https://github.com/t-0-network/usdt-pay-sdk/releases/latest/download/` with
   `Invoke-WebRequest`. A failed download stops with `Failed to download: ...`, exit 1.
2. Installs to `%LOCALAPPDATA%\usdt-pay\usdt-pay.exe` and prints `Installed to <path>`.
3. If that directory is not on the user `Path`, appends it persistently
   (`[Environment]::SetEnvironmentVariable("Path", ..., "User")`), updates the current session
   and prints `Added <dir> to user PATH (restart your terminal for it to take effect)`.
4. Runs `usdt-pay.exe` with every argument given to the script and exits with its status. With no
   arguments it prints the same usage line as `install.sh`.

### Pinning a release

Both installers fetch `releases/latest`. To install one version, download its asset directly —
the assets are the ones `publish-cli` uploads, named `usdt-pay-<os>-<arch>[.exe]`:

```bash
curl -fsSL -o /usr/local/bin/usdt-pay https://github.com/t-0-network/usdt-pay-sdk/releases/download/v<version>/usdt-pay-<os>-<arch>
chmod +x /usr/local/bin/usdt-pay
usdt-pay version   # prints "usdt-pay <version>"
```

## Commands

`usdt-pay` with no command prints the usage and exits 2; an unknown command prints
`unknown command "<name>"`, the usage, and exits 2:

```
Usage: usdt-pay <command> [options]

Commands:
  init [options] <project-name>  Initialize a new usdt-pay project (acquirer or issuer)
  keygen                         Generate a new secp256k1 keypair
  version                        Show version

Languages: java, node

Init options (before or after the project name):
  --lang string        Language/ecosystem (required)
  --role string        Role (required)
  --dir string         Target directory (default: ./<project-name>)
  --no-color           Disable colored output
  --version            Show version
```

### `usdt-pay init`

```
usdt-pay init --lang=<language> --role=<role> <project-name> [--dir <path>] [--no-color]
```

`<project-name>` is the one positional argument. Flags go before or after it, as `--flag=value`
or `--flag value`. `init --help` prints Go's flag summary in single-dash form; the flags are:

| Flag | Default | Accepted values |
|---|---|---|
| `--lang` | required | `java`, `node` — the directories under `cli/internal/embed/` |
| `--role` | required | a role that exists for the language: `acquirer` for `java`, `issuer` for `node` |
| `--dir` | `<project-name>` under the current directory | any path, used verbatim; it may exist if it is empty |
| `--no-color` | off | plain `[INFO]`/`[OK]`/`[ERROR]` prefixes and box drawing without ANSI color. An `init` flag: `usdt-pay --no-color init ...` is `unknown command "--no-color"` |
| `--version` | | prints `usdt-pay init <version>` and exits 0 |

**The project name** is lower-cased and trimmed, spaces become `-`, and every other character
outside `a-z`, `0-9`, `-`, `_` is dropped: `My Issuer!` becomes `my-issuer`. After any leading
`-` or `_`, the first character must be a letter (`_abc` is accepted; `_3abc` and `___` are not).
The sanitized name is the default directory, the value of
`rootProject.name` in `settings.gradle.kts` (Java) or `"name"` in `package.json` (Node), and the
replacement for `my-provider` throughout the template; its PascalCase form replaces
`MyProvider`.

**Validation** runs in this order and stops at the first failure. Messages go to stderr with an
`[ERROR]` prefix:

| Condition | Message | Exit |
|---|---|---|
| no project name | `project name is required` followed by `Usage: usdt-pay init <project-name> --lang=<language>` | 2 |
| name sanitizes to nothing (`@@@`) | `invalid project name — use only lowercase letters, numbers, hyphens, underscores` | 1 |
| first character after any leading `-`/`_` is a digit, or there is none (`3rd-provider`, `_3abc`, `___`) | `project name must start with a letter (got "3rd-provider")` | 1 |
| no `--lang` | `--lang is required (options: java, node)` | 2 |
| unknown `--lang` (`go`) | `unknown language "go" (options: java, node)` | 1 |
| no `--role` | `--role is required` | 2 |
| target directory exists and is non-empty | `directory "<path>" already exists and is non-empty` | 1 |
| `--role` with no starter for the language (`--lang=java --role=issuer`) | `scaffolding: template not found for lang=java role=issuer (available roles: acquirer)` — after the banner and the first two `[INFO]` lines, because the role is resolved during extraction | 1 |

A successful `init` exits 0.

### `usdt-pay keygen`

Prints a fresh secp256k1 keypair and exits 0 — the private key as `0x` + 64 hex characters, the
public key uncompressed, `0x04` + 128 hex characters:

```
Private key: 0x<64 hex>
Public key:  0x04<128 hex>
```

`init` generates the project's keypair itself; `keygen` is for a key outside a scaffold.

### `usdt-pay version`

Prints `usdt-pay <version>` and exits 0. Release binaries carry the release version from
`-ldflags "-X main.Version=X.Y.Z"`; a local `go build` prints `usdt-pay dev`. `--version` and
`-v` are aliases.

### `usdt-pay help`

Prints the usage above and exits 0. `--help` and `-h` are aliases.

## What `init` writes

In order, with the line the CLI prints for each step:

1. `[INFO] Extracting template files...` — the starter for `<lang>/<role>` is extracted from the
   copy embedded in the binary. `my-provider` and `MyProvider` are replaced in file names and text
   files; `.jar`, `.png`, `.zip` and the other binary extensions are copied raw. `gradlew` and
   `*.sh` are written `0755`, everything else `0666` before umask (`0644` under the usual `022`).
2. `[INFO] Applying product overlay...` — `cli/overlay/<lang>/<role>/` is copied over the
   extracted files verbatim: a `Dockerfile` and a `.dockerignore` written for a standalone
   project (see "Maintainers — the overlay").
3. `[INFO] Generating secp256k1 keypair...` — a fresh key for this project.
4. `[INFO] Creating .env file...` — `.env` is `.env.example` with the first active
   `PROVIDER_PRIVATE_KEY=` line filled in and a comment recording the public key inserted right
   under it:

   ```
   PROVIDER_PRIVATE_KEY=0x<64 hex>
   # Public key for the line above (share it with t-0): 0x04<128 hex>
   ```

   `.env` is written with mode `0600`. `NETWORK_PUBLIC_KEY=` stays empty for you to fill in.
   `.env.example` stays in the project as it was, with `PROVIDER_PRIVATE_KEY=` empty.

Then the completion output. Java, `--no-color`, `<project-dir>` standing for the absolute path of
the new project, `<dir>` for the `--dir` value exactly as given (the project name when `--dir` is
omitted — `./<name>` is cleaned to `<name>`), and the key shortened:

```

+-----------------------------------------------------------+
|                  Project Created Successfully!            |
+-----------------------------------------------------------+

Your project is ready at: <project-dir>

Your public key (share with T-0 team):
0x049e80...f3b49b

Next Steps:

  1. Navigate to your project:
     cd <dir>

  2. Add NETWORK_PUBLIC_KEY to .env — your t-0 onboarding contact gives you this

  3. Run the application:
     ./gradlew run

```

Step 2 is `NextSteps` in `cli/config.go`. Step 3 is per language:

| `--lang` | Step 3 |
|---|---|
| `java` | `Run the application:` / `./gradlew run` |
| `node` | `Install dependencies and run:` / `npm install && npm run dev` |

The tree the Java scaffold leaves behind, for orientation (the Node one has `package.json`,
`tsconfig.json`, `tsconfig.test.json`, `src/`, `test/` in place of the Gradle files):

```
.dockerignore   .env   .env.example   .gitignore   Dockerfile   README.md
build.gradle.kts   settings.gradle.kts   gradle.properties   gradle/   gradlew   gradlew.bat
src/main/java/network/t0/pay/acquirer/   src/main/resources/   src/test/java/
```

## Starters

| `--lang` / `--role` | Source in this repo | What it is | Entry files | Run | Test |
|---|---|---|---|---|---|
| `java` / `acquirer` | `java/starter/acquirer/` | Acquirer callback server and client: prices the sale, opens the intent, learns when it settles. Gradle, Java 21 toolchain, SDK `network.t-0:usdt-pay-sdk-java` from Maven Central, pinned by `usdtPaySdkVersion` in `gradle.properties` | `build.gradle.kts`, `settings.gradle.kts`, `gradlew`, `src/main/java/network/t0/pay/acquirer/Main.java` | `./gradlew run` | `./gradlew test` |
| `node` / `issuer` | `node/starter/issuer/` | Issuer callback server and client: reserves deposit addresses, reports the customer's USDt, settles on-chain. npm, Node 22, SDK `@t-0/usdt-pay-sdk` from the npm registry | `package.json`, `tsconfig.json`, `src/index.ts` | `npm install && npm run dev` | `npm test` |

Each scaffold ships its README — the integration guide for the role.

## Maintainers — ownership and sync

Eight files are upstream-owned and overwritten by every sync PR from provider-sdk. The list is
provider-sdk's `.github/workflows/cli-sync-config/usdt-pay-sdk.yaml`:

```
cli/main.go
cli/scaffold.go
cli/keygen.go
cli/keygen_test.go
cli/env.go
cli/go.mod
cli/go.sum
cli/internal/sync/main.go
```

A bug in any of them is fixed in provider-sdk first; the next sync carries it here. A local patch
survives exactly until that sync. `go.mod` is one of the eight, which is why the module path
reads `github.com/t-0-network/provider-sdk/cli`.

Everything else under `cli/` is repo-owned:

| File | Purpose |
|---|---|
| `config.go` | the product's `CLIConfig`: `ProductName`, `Command`, `Description`, `RoleRequired`, `DefaultRole`, `Languages`, `NextSteps`, `OverlayFS` |
| `overlay.go` | one `//go:embed all:overlay` — the overlay files as an `embed.FS` |
| `overlay/<lang>/<role>/` | the standalone `Dockerfile` and `.dockerignore` for each starter |
| `generate.go` | the `go generate` directive that embeds the starters |
| `internal/embed/.gitignore` | keeps the generated embed tree out of the repo |
| `starters_test.go`, `scaffold_test.go` | this product's tests |
| `install.sh`, `install.ps1` | installer scripts |
| `README.md` | the user-facing how-to |

The synced code knows this product only through `CLIConfig`:

```go
var Config = CLIConfig{
	ProductName:  "usdt-pay",
	Command:      "usdt-pay init",
	Description:  "a new usdt-pay project (acquirer or issuer)",
	RoleRequired: true,
	DefaultRole:  "",
	Languages:    []string{"java", "node"},
	NextSteps:    []string{"Add NETWORK_PUBLIC_KEY to .env — your t-0 onboarding contact gives you this"},
	OverlayFS:    overlayFiles,
}
```

Three fields are the seams for product behavior. `OverlayFS` — when set, the synced scaffolder
copies it over the extracted template itself, verbatim, right after extraction. `NextSteps` — one
printed line each, between `cd <dir>` and the per-language run command. `PostScaffold
func(ScaffoldOpts) error` — the hook for anything beyond copying files, run after the overlay.

**A sync PR** that is green on `go build` proves that `cli/` still compiles. After one, in
`cli/`:

```bash
go generate ./... && go build ./... && go test ./...
```

The tests are what tell you the product still works. If `CLIConfig` gained or lost a field,
`config.go` is where to follow. If `starters_test.go` fails on the overlay checks, the sync
changed how `OverlayFS` is applied; that is an upstream conversation, not a local patch.

## Maintainers — embedding the starters

`cli/generate.go` is one directive:

```go
//go:generate go run ./internal/sync java/acquirer=java/starter/acquirer node/issuer=node/starter/issuer
```

`go generate ./...` runs `internal/sync`, which copies each `<lang>/starter/<role>/` in the tree
into `cli/internal/embed/<lang>/<role>/`, skipping `node_modules`, `dist`, `build`, `.gradle`,
`.env` and every `.env.*` except `.env.example`. `scaffold.go` embeds that directory with
`//go:embed all:internal/embed`. The directory is generated and ignored
(`cli/internal/embed/.gitignore`), so every build — local, `ci-go.yaml`, `ci-scaffold.yaml`,
`publish-cli` — runs `go generate` first, and the binary ships the starters of the commit it was
built from. A starter edited in the tree reaches the CLI on the next `go generate`.

## Maintainers — the overlay

A starter is a live project inside this repo. The overlay replaces one of its files and adds
another:

- `Dockerfile` — replaced. The in-repo one builds with the workspace as its context and compiles
  the SDK from source; the overlay one builds with the project directory as its context and
  resolves the SDK from a registry (Maven Central for Java, the npm registry for Node).
- `.dockerignore` — added; the starters carry none. It keeps `.env`, and with it the private key,
  out of the image build context.

`cli/overlay/<lang>/<role>/` holds the standalone versions:

```
cli/overlay/java/acquirer/Dockerfile
cli/overlay/java/acquirer/.dockerignore
cli/overlay/node/issuer/Dockerfile
cli/overlay/node/issuer/.dockerignore
```

They are copied over the extracted template byte for byte — the placeholder and file-name
transforms of the template step are skipped — so what is in that directory is exactly what a
user gets. Every starter must have an overlay with `Dockerfile` and `.dockerignore`;
`TestOverlay_EveryStarterHasOne` in `starters_test.go` fails otherwise, and `ci-scaffold.yaml`
`cmp`s every overlay file against the scaffold.

## Maintainers — working on a starter in the tree

In the tree a starter is a workspace member and compiles against the SDK next to it, so the
commands differ from the ones a scaffolded project's README gives. `.env` is yours to create
here — the generated key and `.env` are what `usdt-pay init` writes into a scaffold, and the tree
is what it scaffolds from:

```bash
cp .env.example .env      # then fill in PROVIDER_PRIVATE_KEY and NETWORK_PUBLIC_KEY
```

`usdt-pay keygen` prints a keypair for `PROVIDER_PRIVATE_KEY`.

### Java — `java/starter/acquirer/`

```bash
# Build from the java/ root — the starter compiles against the local :sdk project.
(cd ../.. && ./gradlew :starter:acquirer:installDist)

# Run from here: .env is read from the working directory.
./build/install/acquirer/bin/acquirer
```

Tests:

```bash
(cd ../.. && ./gradlew :starter:acquirer:test)
```

Docker — the build context is the repository root, because `java/sdk/src/main/proto` is a
symlink into `proto/` and a narrower context cannot resolve it:

```bash
cd ../../..                 # repository root
docker build -f java/starter/acquirer/Dockerfile -t usdt-pay-acquirer .
docker run -p 8080:8080 --env-file java/starter/acquirer/.env usdt-pay-acquirer
```

### Node — `node/starter/issuer/`

```bash
# Install and build from node/ — the starter compiles against the local sdk workspace.
(cd ../.. && npm install && npm run build)

# Run from here: .env is read from the working directory.
npm start
```

`npm run dev` runs the same thing under `tsx watch` while you are editing; `npm test` runs the
tests.

Docker — the build context is `node/`, because the starter compiles against the SDK next to it:

```bash
cd ../..                    # node/
docker build -f starter/issuer/Dockerfile -t usdt-pay-issuer .
docker run -p 8080:8080 --env-file starter/issuer/.env usdt-pay-issuer
```

Whole-workspace builds are the three commands in `CLAUDE.md`, "Build and test" — the same ones
`ci-java.yaml`, `ci-node.yaml` and `ci-go.yaml` run.

## Maintainers — tests and CI

- **`cli/starters_test.go`** — derives its targets from the tree: the embedded starters must be
  exactly the `<lang>/starter/<role>/` directories, and `Config.Languages` must list exactly the
  languages that have one, so a new starter is covered without editing the test and an unwired
  one fails it. `TestConfig_OverlayWired` requires `Config.OverlayFS`;
  `TestOverlay_EveryStarterHasOne` requires `Dockerfile` and `.dockerignore` under
  `overlay/<lang>/<role>/`. `TestRun_InstantiatesEveryStarter` runs the CLI end to end for each:
  - `.gitignore` and the language's entry files are there, `dot-gitignore` is not;
  - every overlay file lands byte for byte.

  `TestRun_WritesFreshPrivateKey` instantiates each starter twice: `.env` holds a fresh key —
  `0x` + 64 hex, a valid secp256k1 scalar, unique across instantiations — and the public key
  printed to the user and the one recorded in `.env` both derive from it; `.env.example` keeps
  its empty `PROVIDER_PRIVATE_KEY=`; `NETWORK_PUBLIC_KEY` is present and empty; `.env` is
  `0600`.
- **`cli/scaffold_test.go`** — embed path handling (`embed.FS` rejects backslash paths, every
  language in `Config.Languages` has a non-empty embed directory) and the name helpers
  (`sanitizeProjectName`, `toPascalCase`).
- **`cli/keygen_test.go`** — synced from provider-sdk; the keypair generator.
- **`.github/workflows/ci-go.yaml`** — `go generate ./...`, `go build ./...`, `go test ./...` in
  `cli/`: the tests above, run in CI. Triggered by `cli/**`.
- **`.github/workflows/ci-scaffold.yaml`** — triggered by `cli/**`, `java/**`, `node/**` and
  `proto/**`, because the starters and SDKs it builds are all inputs. One job:
  1. `go generate ./...`, then `go build -ldflags "-X main.Version=dev" -o ../usdt-pay .` in
     `cli/`.
  2. For every `cli/internal/embed/<lang>/<role>/`: `./usdt-pay init "test-<lang>-<role>"
     --lang=<lang> --role=<role> --no-color --dir=scaffold-<lang>-<role>`; requires
     `.gitignore`, a `PROVIDER_PRIVATE_KEY=0x` line in `.env`, a `cli/overlay/<lang>/<role>/`
     directory, and `cmp`s every file in it against the scaffold. Needs no registry, so a bot
     sync that drops the overlay seam fails here on every event.
  3. Publishes this tree's SDKs locally: Java through `./gradlew :sdk:publishToMavenLocal
     -Pversion=0.0.0-local -Dmaven.repo.local="${RUNNER_TEMP}/m2"` plus an init script adding
     `mavenLocal()`; Node through `npm ci`, `npm run build -w sdk` and `npm pack -w sdk`.
  4. Builds and runs each scaffold's own tests against those: Java with `./gradlew test
     --init-script ... -Dmaven.repo.local=... -PusdtPaySdkVersion=0.0.0-local`, Node with
     `npm install --no-save --package-lock=false <tarball> && npm run build && npm test`. A
     language without a case fails the loop with
     `no build-and-test case for lang=<lang> — add one to this step`.

  It builds against the SDK in the tree rather than the published one because a scaffolded
  project pins the published SDK, and between a proto sync and the next release the starters use
  SDK changes that reach the registry only at that release. The released CLI always embeds a
  starter that matches the SDK released with it, so the tree is the right thing to prove.

## Maintainers — adding a starter or a language

1. The starter itself under `java/starter/<role>/` or `node/starter/<role>/`, wired into
   `cli/generate.go` as `<lang>/<role>=<lang>/starter/<role>`. Its `.env.example` needs an active
   `PROVIDER_PRIVATE_KEY=` line; the scaffolder records the public key under it by itself. Use
   `my-provider` as the project name in `settings.gradle.kts` / `package.json`; the scaffolder
   replaces it.
2. `cli/overlay/<lang>/<role>/` with `Dockerfile` and `.dockerignore` that build the project
   standalone, against the published SDK.
3. A new language: add it to `Languages` in `config.go`, its entry files to `entryFiles` in
   `starters_test.go` (`requireEntryFiles` fails a language that is not listed), a build-and-test
   case to the loop in `ci-scaffold.yaml`, and the per-language run step in `printCompletion` —
   that one lives in the synced `main.go`, so it goes through provider-sdk. Everything else in
   `starters_test.go` picks the language up from the tree.
4. The version sites and publish jobs in `docs/RELEASE_AND_PUBLISH.md`, "Starters" and "Adding
   an ecosystem".
5. `README.md` at the repo root, `cli/README.md` and the "Starters" table above.

## Maintainers — releasing the CLI

The CLI is released with everything else: `release.yaml` tags `vX.Y.Z`, and `publish-cli` in
`publish.yaml` runs `go generate`, cross-compiles `linux`, `darwin` and `windows` × `amd64` and
`arm64` with `-ldflags "-X main.Version=X.Y.Z"`, and uploads the six binaries to that Release as
`usdt-pay-<os>-<arch>[.exe]`. `install.sh` and `install.ps1` always fetch `releases/latest`, so a
release is live for every user the moment the upload lands. The process, its rules and its
recovery paths: [`RELEASE_AND_PUBLISH.md`](RELEASE_AND_PUBLISH.md), "`publish-cli`".
