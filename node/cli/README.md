# @t-0/usdt-pay-starter-ts

Scaffolds a [USDt Pay](https://usdt-pay-docs.t-0.network/) issuer integration in
TypeScript. The Node analog of `usdt-pay-init.jar`, which does the same for the Java
acquirer starter.

```bash
npx @t-0/usdt-pay-starter-ts my-issuer
```

That creates `./my-issuer` from the issuer starter, generates your secp256k1 keypair,
writes it into `.env`, and prints the public half — which is what you send to the t-0
team, since they cannot accept your calls until they have it.

```
Usage: usdt-pay-starter-ts [project-name] [options]

Options:
  -d, --directory <dir>  Where to create the project (defaults to the current directory)
      --no-color         Disable colored output
  -h, --help             Show this help
  -V, --version          Show the version
```

Omit the project name and it asks.

The scaffolded project pins `@t-0/usdt-pay-sdk` at the same version as this package —
they are released together — and is not a workspace member of anything, so
`npm install && npm run dev` in it works standalone. Its `README.md` is a numbered path
from "prints my public key" to "settled a real sale"; start there.

## What ships here

`template/` is generated at pack time from
[`node/starter/issuer`](https://github.com/t-0-network/usdt-pay-sdk/tree/master/node/starter/issuer)
in the SDK repo, which is a live workspace member that CI builds and tests against the
SDK. There is no separate template copy to drift — you receive the starter that is
tested on every commit.

MIT — see [LICENSE](https://github.com/t-0-network/usdt-pay-sdk/blob/master/LICENSE).
