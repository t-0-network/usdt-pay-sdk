# Java

Java SDK and starter for the **t-0 QR payment flow**.

## SDK

[`network.t-0:usdt-pay-sdk-java`](https://central.sonatype.com/artifact/network.t-0/usdt-pay-sdk-java)
on Maven Central — see [sdk/](sdk/) for Gradle and Maven dependency snippets.

Create a client, make a call:

```java
var t0 = BlockingNetworkClient.create(endpoint, signer,
        channel -> AcquirerServiceGrpc.newBlockingStub(channel)
                .withInterceptors(new CallDeadline(Duration.ofSeconds(10))));

var response = t0.stub().createPaymentIntent(request);
```

Compiled for Java 17, consumable on 17+. See [sdk/](sdk/) for client patterns:
blocking and non-blocking stubs, `CallDeadline`, V2 checked exceptions.

## Starter

Download `usdt-pay-init.jar` from
[Releases](https://github.com/t-0-network/usdt-pay-sdk/releases):

```bash
curl -LO https://github.com/t-0-network/usdt-pay-sdk/releases/latest/download/usdt-pay-init.jar
java -jar usdt-pay-init.jar my-acquirer acquirer
```

| Role | Template |
|---|---|
| `acquirer` | [starter/acquirer/](starter/acquirer/) |

After scaffolding:

```bash
cd my-acquirer
# add NETWORK_PUBLIC_KEY to .env — your t-0 onboarding contact gives you this
./gradlew installDist
./build/install/acquirer/bin/acquirer
```

Then follow your project's README.
See [cli/](cli/) for scaffolder details.

### Java 21

The starters build and run on **Java 21**. If your JDK is older the build still
works — Gradle provisions a 21 toolchain itself via `api.foojay.io` — but the binary
needs a 21 runtime. The SDK jar is compiled for 17, so consuming it does not force
you off 17.

If your build network blocks `api.foojay.io`, install a 21 JDK yourself and the
resolver stays out of the way — or put the starter back on 17: change
`JavaLanguageVersion.of(21)` to `of(17)` in its `build.gradle.kts` and the
`eclipse-temurin:21-*` tags in its `Dockerfile`. Nothing in the starter code uses a
language feature newer than 17.
