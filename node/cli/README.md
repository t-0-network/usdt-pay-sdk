# @t-0/usdt-pay-starter-ts

Scaffolds a [USDt Pay](https://usdt-pay-docs.t-0.network/) integration in TypeScript.
One generator for the whole Node side — pick the role you are integrating as. The Java
analog is `usdt-pay-init.jar`, which works the same way.

```bash
npx @t-0/usdt-pay-starter-ts my-issuer --starter issuer
```

That creates `./my-issuer` from the issuer starter, generates your secp256k1 keypair,
writes it into `.env`, and prints the public half — which is what you send to the t-0
team, since they cannot accept your calls until they have it.

```
Usage: usdt-pay-starter-ts [project-name] --starter <role> [options]

Options:
  -s, --starter <role>   Required. Which role to scaffold.
  -d, --directory <dir>  Where to create the project (defaults to the current directory)
      --no-color         Disable colored output
  -h, --help             Show this help
  -V, --version          Show the version
```

Omit the project name and it asks. `--starter` is **required**, and running without it
lists what this package carries — issuer, acquirer and lp are different integrations,
and which one you get should not be inferred. A default would also be a contract that
changes under you: it would mean "issuer" today and, once an acquirer starter is added,
"acquirer", because the role list is sorted.

The scaffolded project pins `@t-0/usdt-pay-sdk` at the same version as this package —
they are released together — and is not a workspace member of anything, so
`npm install && npm run dev` in it works standalone. Its `README.md` is a numbered path
from "prints my public key" to "settled a real sale"; start there.

## What ships here

`template/<role>` is generated at pack time from each directory under
[`node/starter/`](https://github.com/t-0-network/usdt-pay-sdk/tree/master/node/starter)
in the SDK repo, which are live workspace members that CI builds and tests against the
SDK. There is no separate template copy to drift — you receive the starter that is
tested on every commit. That listing *is* the set of roles on offer: adding one is
adding a directory, not editing a registry here.

`overlay/<role>` holds the few files that cannot ship verbatim, and is applied over the
template. In-repo, a starter's `Dockerfile` builds with `node/` as its context so it can
resolve the SDK workspace next to it; a scaffolded project resolves the SDK from npm and
builds from its own directory instead.

MIT — see [LICENSE](https://github.com/t-0-network/usdt-pay-sdk/blob/master/LICENSE).
