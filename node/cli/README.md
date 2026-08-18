# @t-0/usdt-pay-starter-ts

Creates a [USDt Pay](https://usdt-pay-docs.t-0.network/) integration project in
TypeScript, from the starter for the role you are integrating as.

```bash
npx @t-0/usdt-pay-starter-ts my-issuer issuer
```

## What you get

`./my-issuer` — a standalone project, not a workspace member of anything:

```
my-issuer/
├── .env                 your generated PRIVATE_KEY, ready to use — keep it
├── .env.example         the template it was written from
├── .gitignore           excludes .env
├── package.json         named after your project, depending on @t-0/usdt-pay-sdk
├── README.md            the numbered path from "prints my public key" to "settled a real sale"
├── Dockerfile
├── src/                 the starter's handlers and outbound calls
└── test/
```

The command also generates your secp256k1 keypair, writes the private half into
`.env`, and prints the public half. That public key is what the t-0 team registers you
by — they cannot accept your calls until they have it.

**`.env` holds the only copy of your private key.** It is gitignored, and nothing
prints it again. Do not overwrite it from `.env.example`.

## Prerequisites

- **Node 22 or newer.**
- **The t-0 network public key**, from the t-0 team. The project reads it as
  `NETWORK_PUBLIC_KEY` and refuses to start without it, so start that conversation
  before you scaffold.

## After scaffolding

```bash
cd my-issuer
# add NETWORK_PUBLIC_KEY to .env — the t-0 team gives you this
npm install
npm start
```

It prints your public key and starts the callback server. Then work through the
project's own `README.md`: it is a numbered path through each phase of the integration.

## Roles

| Role | Ships in | Command |
|---|---|---|
| `issuer` | this package | `npx @t-0/usdt-pay-starter-ts my-issuer issuer` |
| `acquirer` | `usdt-pay-init.jar` (Java) | `java -jar usdt-pay-init.jar my-acquirer acquirer` |

Run this package with no role and it lists what it carries.

## Usage

```
Usage: usdt-pay-starter-ts [project-name] <role>

Options:
  -d, --directory <dir>  Where to create the project (defaults to the current directory)
      --no-color         Disable colored output
  -h, --help             Show this help
  -V, --version          Show the version
```

The role is required and comes last; there is no default. Omit the project name and
you will be asked for it, so `npx @t-0/usdt-pay-starter-ts issuer` is enough to start.

## Working inside the SDK repo instead

Before 1.0 the contract still moves, and cloning
[usdt-pay-sdk](https://github.com/t-0-network/usdt-pay-sdk) keeps you on the SDK as it
changes: the starters there build against the `node/` workspace rather than a published
version. Scaffolding is the standalone route — a pinned SDK you upgrade deliberately.
The [repo README](https://github.com/t-0-network/usdt-pay-sdk#readme) covers both.

The starters this package ships are generated from that repo at publish time, so what
you scaffold is the code its CI builds and tests.

MIT — see [LICENSE](https://github.com/t-0-network/usdt-pay-sdk/blob/master/LICENSE).
