# usdt-pay

`usdt-pay` is the scaffolder: `usdt-pay init` creates a standalone acquirer or issuer project from
the starters in this repo, with a fresh keypair and a ready `.env`.

## Install

macOS and Linux — `install.sh` downloads the latest release and runs `usdt-pay` with whatever
arguments follow `--`, so one line installs and creates the project:

```bash
curl -fsSL https://raw.githubusercontent.com/t-0-network/usdt-pay-sdk/master/cli/install.sh | sh -s -- init --lang=<language> --role=<role> <project-name>
```

Install only:

```bash
curl -fsSL https://raw.githubusercontent.com/t-0-network/usdt-pay-sdk/master/cli/install.sh | sh
```

Windows (PowerShell) — `install.ps1` forwards its arguments the same way; run it with none to
install only:

```powershell
iwr -Uri https://raw.githubusercontent.com/t-0-network/usdt-pay-sdk/master/cli/install.ps1 -OutFile install.ps1
.\install.ps1 init --lang=<language> --role=<role> <project-name>
```

The binary lands in `/usr/local/bin` when that is writable, otherwise in `~/.local/bin` — the
script prints the `export PATH=...` line to add if that directory is not on your `PATH`. On
Windows it lands in `%LOCALAPPDATA%\usdt-pay\usdt-pay.exe` and the script adds that directory to
your user `PATH`. Check the install:

```bash
usdt-pay version
```

## Create a project

```bash
usdt-pay init --lang=<language> --role=<role> <project-name>
```

| `--lang` | `--role` | Starter |
|---|---|---|
| `java` | `acquirer` | [`java/starter/acquirer/`](../java/starter/acquirer/) |
| `node` | `issuer` | [`node/starter/issuer/`](../node/starter/issuer/) |
| `python` | `acquirer` | [`python/starter/acquirer/`](../python/starter/acquirer/) |

Optional flags, before or after the project name:

| Flag | Effect |
|---|---|
| `--dir <path>` | Where to create the project; the default is `<project-name>` under the current directory. |
| `--no-color` | Plain output, for logs and CI. |

## What you get

- The starter for your language and role, extracted from the copy embedded in the binary and
  named after your project.
- A `Dockerfile` and `.dockerignore` written for a standalone project, which builds against the
  published SDK.
- `.env` with a freshly generated secp256k1 private key in `PROVIDER_PRIVATE_KEY`, its public key
  recorded on the comment line under it, mode `0600`.
- `NETWORK_PUBLIC_KEY=` left empty in `.env` — your t-0 onboarding contact gives you the value.
- The public key to share with t-0 and the next steps, printed at the end: `cd` into the
  project, fill in `NETWORK_PUBLIC_KEY`, run it.

Then follow the README in the new project.

## Generate a keypair

`init` writes the project's keypair itself. For a key outside a scaffold — a rotation — `keygen`
prints a fresh pair and exits:

```bash
usdt-pay keygen
```

Put the private key in `PROVIDER_PRIVATE_KEY` in `.env` (and replace the public key on the
comment line under it), and give the new public key to your t-0 onboarding contact.

## Maintainer notes

`cli/` is a product instance of provider-sdk's unified CLI. The files listed in provider-sdk's
`.github/workflows/cli-sync-config/usdt-pay-sdk.yaml` are overwritten by every sync PR, so a fix
to any of them goes to provider-sdk first; everything else under `cli/` is repo-owned.
`go generate ./...` copies the starters into `internal/embed/` at build time, which is how the
binary ships the starters of the commit it was built from. `go test ./...` instantiates every
starter and checks the keypair, the `.env`, Dockerfile and `.dockerignore` of each. `ci-cli.yaml`
additionally builds and tests every scaffold against the SDK built from the tree. Which file does
what, and adding a starter or a language: [`docs/CLI.md`](../docs/CLI.md).
