# usdt-pay-init

Creates a [USDt Pay](https://usdt-pay-docs.t-0.network/) integration project in Java,
from the starter for the role you are integrating as.

The jar is attached to every
[release](https://github.com/t-0-network/usdt-pay-sdk/releases). The unversioned name
always points at the latest:

```bash
curl -LO https://github.com/t-0-network/usdt-pay-sdk/releases/latest/download/usdt-pay-init.jar
java -jar usdt-pay-init.jar my-acquirer acquirer
```

## What you get

`./my-acquirer` — a standalone Gradle project, not a subproject of anything:

```
my-acquirer/
├── .env                 your generated PROVIDER_PRIVATE_KEY, ready to use — keep it
├── .env.example         the template it was written from
├── .gitignore           excludes .env
├── .dockerignore        keeps .env out of the image
├── build.gradle.kts     resolving the SDK from Maven Central
├── gradle.properties    usdtPaySdkVersion — the SDK version you are on
├── settings.gradle.kts  named after your project
├── README.md            the numbered path from "prints my public key" to "settled a real sale"
├── Dockerfile
├── gradlew, gradle/     the wrapper, so no local Gradle is needed
└── src/                 the starter's handlers and outbound calls, and its tests
```

The command also generates your secp256k1 keypair, writes the private half into
`.env`, and prints the public half. That public key is what t-0 registers you by —
they cannot accept your calls until they have it.

**`.env` holds the only copy of your private key.** It is gitignored, and nothing
prints it again. Do not overwrite it from `.env.example`.

## Prerequisites

- **Java 21 or newer to run what you build.** The jar itself runs on 17, and the
  Gradle build provisions its own 21 toolchain via `api.foojay.io` if your JDK is
  older — but the binary it produces needs a 21 runtime. If your network blocks that
  resolver, or you want to drop the starter to 17, see
  [Java 21](../README.md#java-21).
- **The t-0 network public key** — an uncompressed secp256k1 key, `0x04…` and 130 hex
  digits. It comes from your t-0 onboarding contact. The project reads it as
  `NETWORK_PUBLIC_KEY` and will not start without it.

## After scaffolding

```bash
cd my-acquirer
# add NETWORK_PUBLIC_KEY to .env — your t-0 onboarding contact gives you this
./gradlew installDist

# Run from this directory — .env is read from the working directory.
./build/install/acquirer/bin/acquirer
```

It prints your public key and starts the callback server. Then work through the
project's own `README.md`: it is a numbered path through each phase of the
integration.

The SDK version is yours to move. `gradle.properties` pins it exactly — raise
`usdtPaySdkVersion` there when you decide to, or override once with
`-PusdtPaySdkVersion=<version>`. Deliberately not a `+` range: an SDK bump is your
decision, not a surprise.

## Roles

| Role | Ships in | Command |
|---|---|---|
| `acquirer` | this jar | `java -jar usdt-pay-init.jar my-acquirer acquirer` |
| `issuer` | `@t-0/usdt-pay-starter-ts` (Node) | `npx @t-0/usdt-pay-starter-ts my-issuer issuer` |

Run the jar with no role and it lists what it carries.

## Usage

```
Usage: usdt-pay-init [project-name] <role>

Options:
  -d, --directory <dir>  Where to create the project (defaults to the current directory)
      --no-color         Disable colored output
  -h, --help             Show this help
  -V, --version          Show the version
```

The role is required and comes last; there is no default — acquirer, issuer and lp
are different integrations, and a default would be a contract that changes underneath
callers as starters are added.

## Working inside the SDK repo instead

Before 1.0 the contract still moves, and cloning
[usdt-pay-sdk](https://github.com/t-0-network/usdt-pay-sdk) keeps you on the SDK as it
changes: the starters there build against the `java/` Gradle build rather than a
published version. Scaffolding is the standalone route — a pinned SDK you upgrade
deliberately. The [repo README](https://github.com/t-0-network/usdt-pay-sdk#readme)
covers both.

The starters this jar ships are copied from that repo at build time, so what you
scaffold is the code its CI builds and tests.

MIT — see [LICENSE](https://github.com/t-0-network/usdt-pay-sdk/blob/master/LICENSE).
