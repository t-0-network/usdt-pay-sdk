# usdt-pay-sdk

SDKs and starter projects for the **t-0 QR payment flow** — the protocol under
`proto/tzero/v1/pay/`.

> **0.x — the contract still moves.** Fields and endpoints can change without a
> deprecation window until 1.0. Pin what you build against and read the release
> notes before upgrading.

## Which role are you?

Three companies meet in this flow, and each integrates a different half of it.
Pick yours and go straight to its starter.

| You are | You do | Start here |
|---|---|---|
| **Acquirer** | Own the merchant. Price the sale, open the intent, show the QR, learn when it settles. | [Quick start — Java](#java--the-acquirer-starter) |
| **Issuer** | Reserve deposit addresses, watch the chain for the customer's USDt, settle on-chain. | [Quick start — Node](#node--the-issuer-starter) |
| **Liquidity Provider** | Price USDt↔local fiat, take the per-sale obligation, settle fiat over bank rails. | Starter not published yet — see [Roadmap](#roadmap) |

Not sure which you are? The acquirer talks to the POS, the issuer talks to the
blockchain, the LP talks to a bank. t-0 sits in the middle and no two of you ever
talk directly.

The LP only exists in **fiat settlement mode**. An acquirer settled in USDt has no
LP, gets its USDt straight from the issuer, and skips §3, §11 and §12 entirely.
Your mode is fixed at onboarding.

## Quick start

Two routes to a working project. The choice is the same in both languages:

| | **Scaffold** | **Clone** |
|---|---|---|
| What you get | a standalone project of your own, pinning the published SDK | this repo, the starter building against the local SDK |
| Keys | generated for you, written to `.env` | you generate and fill them in |
| When the contract moves | you bump the SDK deliberately | you `git pull` and follow it |
| Pick it when | you are starting your own service | **recommended before 1.0** — the contract still moves and you want to track it |

Either way you end up in the same place: a starter whose README is a numbered path
from "prints my public key" to "settled a real sale".

### Node — the issuer starter

Node 22 or newer.

**Scaffold:**

```bash
npx @t-0/usdt-pay-starter-ts my-issuer issuer

cd my-issuer
# add NETWORK_PUBLIC_KEY to .env — the t-0 team gives you this.
# Your PRIVATE_KEY is already there; do not overwrite .env from the example.
npm install && npm run build
npm start
```

**Clone:**

```bash
git clone git@github.com:t-0-network/usdt-pay-sdk.git
cd usdt-pay-sdk/node
npm install && npm run build                     # SDK + the issuer starter

cd starter/issuer
cp .env.example .env
# fill in PRIVATE_KEY (openssl rand -hex 32) and NETWORK_PUBLIC_KEY (from the t-0 team)

# Run from this directory — .env is read from the working directory.
npm start
```

Either way, a working start prints your public key and then blocks on the callback
server:

```
Issuer public key: 0x04…
Callback server listening on port 8080
```

Send that public key to the t-0 team — they cannot call you until they have it.

Then work through the starter's README, which walks the integration phase by phase:
your own project's `README.md` if you scaffolded, or
[`node/starter/issuer/README.md`](node/starter/issuer/README.md) if you cloned. They
differ — the scaffolded copy documents your standalone project, and its run steps do
not overwrite the `.env` holding your key.

### Java — the acquirer starter

The starters build and run on **Java 21**. If your JDK is older the build still
works — Gradle provisions a 21 toolchain itself — but the binary needs a 21 runtime.
The SDK jar is compiled for 17, so consuming it does not force you off 17.

Provisioning downloads a JDK through `api.foojay.io`. If your build network does not
allow that, install a 21 JDK yourself and the resolver stays out of the way — or put
the starter back on 17: change `JavaLanguageVersion.of(21)` to `of(17)` in its
`build.gradle.kts` and the `eclipse-temurin:21-*` tags in its `Dockerfile`. Nothing in
the starter code uses a language feature newer than 17.

**Scaffold** — with `usdt-pay-init.jar`, attached to each
[release](https://github.com/t-0-network/usdt-pay-sdk/releases):

```bash
java -jar usdt-pay-init.jar my-acquirer acquirer

cd my-acquirer
# add NETWORK_PUBLIC_KEY to .env; PRIVATE_KEY is already there.
./gradlew installDist
```

**Clone:**

```bash
git clone git@github.com:t-0-network/usdt-pay-sdk.git
cd usdt-pay-sdk/java
./gradlew build                                  # SDK + the acquirer starter

./gradlew :starter:acquirer:installDist

cd starter/acquirer
cp .env.example .env
# fill in PRIVATE_KEY (openssl rand -hex 32) and NETWORK_PUBLIC_KEY (from the t-0 team)

# Run from this directory — .env is read from the working directory.
./build/install/acquirer/bin/acquirer
```

Then work through the starter's README — the same numbered path, for the acquirer's
half of the flow: your own project's `README.md` if you scaffolded, or
[`java/starter/acquirer/README.md`](java/starter/acquirer/README.md) if you cloned.

### Both generators take the same arguments

```
usdt-pay-init.jar    [project-name] <role>
usdt-pay-starter-ts  [project-name] <role>
```

The role is required and comes last, with no default — acquirer, issuer and lp are
different integrations. Run either with no role and it lists the ones it carries. Omit
the project name and it asks.

## What is in here

```
proto/tzero/v1/          protocol definitions, snapshot-synced from the t-0 backend
└── pay/                 acquirer.proto, issuer.proto, lp.proto, common.proto
                         (self-contained: the pay contract shares no types with
                          tzero.v1.common, so your generated code carries exactly
                          one Decimal and one Blockchain)

java/                    Java SDK (built on 21, consumable on 17) + a starter
├── sdk/                 network.t-0:usdt-pay-sdk-java — generated stubs for all three roles
└── starter/acquirer

node/                    Node SDK + a starter, as one npm workspace
├── sdk/                 @t-0/usdt-pay-sdk — generated Connect code for all three roles
└── starter/issuer
```

Both SDKs generate their code from `proto/` with [buf](https://buf.build), and they
keep it differently. Java generates at build time: `./gradlew build` regenerates the
stubs, and nothing under `gen/` or `build/` belongs in git. Node checks its generated
code in under `node/sdk/src/gen/`, so nobody needs buf installed to build a starter;
`npm run buf:generate` refreshes it after a proto sync.

## Before you write code

Two things bite every integration, in both directions:

**Callbacks are delivered at least once.** t-0 retries until you acknowledge, and
your acknowledgment means *"recorded, stop retrying"*. A handler that returns
success without a durable write throws the event away — nothing redelivers it.
Write first under the callback's dedup key, return second.

**Every state-changing call has an idempotency key.** Retry with the *original* key
and identical content. A rejection is itself an acknowledgment — stop retrying —
but it never consumes the key: correct the fields and resend the same key. A fresh
key on a retry is a second sale, a second settlement, a second obligation.

Each starter README lists the keys for its role, and
[Idempotency & reliability](https://usdt-pay-docs.t-0.network/docs/integration-guidance/idempotency/)
states the rules both directions. The full contract — every field, every decline
code — is the API reference:
[acquirer](https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_acquirer/) ·
[issuer](https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_issuer/) ·
[LP](https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_lp/) ·
[shared types](https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/pay_common/).

## Calling t-0

All 15 endpoints are unary request/response — nothing in this protocol streams.

### Java

Every endpoint being unary is why the starters use the blocking stub everywhere, and
why you probably should too.

**Blocking is the primary path, and on Java 21+ it is also the scalable one.** A
blocking gRPC call parks on `LockSupport.park` inside grpc's `ThreadlessExecutor`,
which is a lock-free queue and holds no monitor. So a virtual thread making one of
these calls *unmounts* its carrier instead of pinning it: run the `internal/` helpers
on `Executors.newVirtualThreadPerTaskExecutor()` and a blocked call costs you a
continuation, not a platform thread. Straight-line code and non-blocking scaling are
not a trade here.

**Give the stub a default deadline where you build it.** `CallDeadline` is a
`ClientInterceptor` that applies one per call:

```java
var t0 = BlockingNetworkClient.create(endpoint, signer,
        channel -> AcquirerServiceGrpc.newBlockingStub(channel)
                .withInterceptors(new CallDeadline(Duration.ofSeconds(10))));
```

Do **not** use `stub.withDeadlineAfter(...)` for this. A gRPC `Deadline` is an
absolute instant, not a per-call duration, so a stub built once that way works until
the deadline passes and then fails every later call with `DEADLINE_EXCEEDED`. A call
that sets its own deadline still wins — `CallDeadline` leaves it alone, which is how
the acquirer starter gives §3 a shorter one at the call site.

`BlockingNetworkClient.create(endpoint, signer, stubFactory, timeoutSeconds)` looks
like the built-in knob for this. As of provider-sdk-java 1.1.25 it is not: the
argument is accepted and never read.

#### If you need non-blocking

`FutureNetworkClient` is the reference non-blocking client — same signing, same
endpoint. Every RPC is unary, so each call hands back one `ListenableFuture`:

```java
var t0 = FutureNetworkClient.create(endpoint, signer, AcquirerServiceGrpc::newFutureStub);
ListenableFuture<CreatePaymentIntentResponse> pending = t0.stub().createPaymentIntent(request);
```

If your code speaks `CompletableFuture`, bridge it. The part worth copying carefully
is cancellation reaching the RPC rather than stopping at the wrapper:

```java
static <T> CompletableFuture<T> toCompletable(ListenableFuture<T> future) {
    CompletableFuture<T> bridged = new CompletableFuture<>() {
        @Override public boolean cancel(boolean mayInterrupt) {
            future.cancel(mayInterrupt);   // cancel the call, not just this wrapper
            return super.cancel(mayInterrupt);
        }
    };
    Futures.addCallback(future, new FutureCallback<T>() {
        @Override public void onSuccess(T value) { bridged.complete(value); }
        @Override public void onFailure(Throwable t) { bridged.completeExceptionally(t); }
    }, MoreExecutors.directExecutor());
    return bridged;
}
```

`directExecutor()` completes the future on a gRPC network thread, so anything you
chain onto it runs there too. Keep continuations trivial or hand `thenApplyAsync` your
own executor — blocking work in a `thenApply` stalls the channel for every other call
on it.

`AsyncNetworkClient` also ships, giving you `StreamObserver` callbacks. Named here so
you do not have to wonder: it buys nothing for this protocol. `StreamObserver` is the
shape streaming needs, and collecting a single response through
`onNext`/`onError`/`onCompleted` is strictly more work than reading a future.

#### If you want the compiler to enforce the failure path

`newBlockingV2Stub` is the same blocking call with a *checked* exception — its methods
`throws StatusException`, so forgetting to handle a failed call is a compile error
instead of a production surprise. It extends `AbstractBlockingStub`, so it drops into
`BlockingNetworkClient` with nothing else changed:

```java
var t0 = BlockingNetworkClient.create(endpoint, signer, AcquirerServiceGrpc::newBlockingV2Stub);
```

For unary calls the exception type is the *only* difference: `blockingV2UnaryCall`
delegates straight to the same `blockingUnaryCall` the classic stub uses and converts
the resulting `StatusRuntimeException` into a checked `StatusException`. No
experimental machinery is involved. The starters stay on the classic stub for two
reasons: `Outcome` already forces the three cases to be handled, and a checked
exception cannot escape a lambda — the acquirer demo's `.ifPresent(quote -> …)` would
need a try/catch inside every one. Take V2 if you would rather the compiler enforced
it than a return type.

### Node

Every call is an `await` that returns the response message, and the deadline rides
along as a per-call option:

```ts
const t0 = createUsdtPayClient(endpoint, privateKeyHex, IssuerService);
const response = await t0.paymentReceived(request, { timeoutMs: 10_000 });
```

A Connect timeout is a duration evaluated per call, so there is nothing to install
where the client is built and each call site picks its own — which is how the issuer
starter gives §9 more room than the rest.

`endpoint` is required. The provider client this delegates to defaults to a different
t-0 API, and a pay participant that omitted it would sign perfectly valid requests
and send them to the wrong host.

## Testing your integration

You do not need t-0 to reach you, or a running server, to test the half of this that
holds your logic. Both directions stub cleanly.

The inbound test worth writing first is redelivery: call your handler **twice** with
the same dedup key and assert your own store holds one row. That is the at-least-once
contract, and it is the one that costs money to get wrong.

### Java

**Inbound — your callback handler is a plain object.** It extends a generated
`*ImplBase`, so a test constructs it and calls the method directly, asserting on what
it hands the `StreamObserver`. No server, no signing, no network:

```java
var responses = new ArrayList<PaymentAuthorizedResponse>();
new AcquirerCallbackHandler().paymentAuthorized(
        PaymentAuthorizedRequest.newBuilder().setPaymentIntentId(1).setPaymentRef("sale-1").build(),
        new StreamObserver<>() {
            public void onNext(PaymentAuthorizedResponse r) { responses.add(r); }
            public void onError(Throwable t) { throw new AssertionError(t); }
            public void onCompleted() { }
        });

assertEquals(1, responses.size());
```

**Outbound — fake t-0, not the stub.** The generated stubs are `final` with private
constructors, so they cannot be subclassed or mocked. Stand a fake service up in
memory and hand the helper a real stub pointed at it. One test dependency,
`io.grpc:grpc-inprocess`:

```java
String name = InProcessServerBuilder.generateName();
Server server = InProcessServerBuilder.forName(name).directExecutor()
        .addService(new AcquirerServiceGrpc.AcquirerServiceImplBase() {
            @Override public void createPaymentIntent(
                    CreatePaymentIntentRequest request,
                    StreamObserver<CreatePaymentIntentResponse> observer) {
                observer.onError(Status.UNAVAILABLE.asRuntimeException());
            }
        })
        .build().start();

var t0 = AcquirerServiceGrpc.newBlockingStub(
        InProcessChannelBuilder.forName(name).directExecutor().build());

assertTrue(CreatePaymentIntent.create(
        t0, "sale-1", idempotencyKey, Decimals.of("100000.00"), quoteId).shouldRetry());
```

`java/starter/acquirer/src/test/java/network/t0/pay/acquirer/internal/CreatePaymentIntentTest.java`
works this out for all three outcomes — copy its shape. Write the one above first: it is
the `Outcome.Unknown` branch, which a happy-path test against a sandbox never reaches and
which decides whether you open a second intent for one sale by accident.

### Node

**Inbound — boot the real callback server on port 0.** It costs a few milliseconds
and puts signing, verification and response validation in the loop, so the test
exercises what t-0 will:

```ts
const server = await createUsdtPayServer(0, publicKeyFromPrivateKey(networkKey), (r) => {
  r.service(IssuerCallbackService, issuerCallbackHandler);
});
const t0 = createUsdtPayClient(`http://127.0.0.1:${(server.address() as AddressInfo).port}`,
                               networkKey, IssuerCallbackService);
```

**Outbound — fake t-0 in memory.** `createRouterTransport` from `@connectrpc/connect`
stands a service up with no socket, and the helper takes a real client pointed at it:

```ts
const t0 = createClient(IssuerService, createRouterTransport(({ service }) => {
  service(IssuerService, {
    async settlementSent() { throw new ConnectError("t-0 is down", Code.Unavailable); },
  } as ServiceImpl<typeof IssuerService>);
}));

assert.equal((await reportSettlementSent(t0, settlement)).shouldRetry, true);
```

`node/starter/issuer/test/` works both out — `settlement_sent.test.ts` covers all
three outcomes, `callback_server.test.ts` the inbound side. Write the `unknown` one
first, for the same reason as in Java.

Point `TZERO_ENDPOINT` at a sandbox only once both sides pass on their own.

## Documentation

- Per-role integration guides: the README in each starter directory.
- Full documentation: <https://usdt-pay-docs.t-0.network/docs/introduction/> — including
  the field-level [API reference](https://usdt-pay-docs.t-0.network/docs/integration-guidance/api-reference/),
  [request authentication](https://usdt-pay-docs.t-0.network/docs/integration-guidance/protocol/authentication/)
  and [idempotency](https://usdt-pay-docs.t-0.network/docs/integration-guidance/idempotency/).
- The `§N` numbers in the code and READMEs here are this repo's own shorthand for the
  endpoints, used so comments can point at a call without restating its semantics.
  Each starter README opens with a table mapping its numbers to RPC names, and the RPC
  name is what the API reference is organised by.

## Roadmap

Each ecosystem is a sibling directory served by the same `proto/` at the root. Names
and versioning are fixed so you can plan against them:

| Ecosystem | Artifact | Versioning | Transport | Starter |
|---|---|---|---|---|
| Java | `network.t-0:usdt-pay-sdk-java` | Maven, `X.Y.Z` | grpc-java | acquirer |
| Node | `@t-0/usdt-pay-sdk` | npm, `X.Y.Z` | Connect | issuer |
| Go | `github.com/t-0-network/usdt-pay-sdk/go` | Git tags `go/vX.Y.Z` | Connect | — |

**One starter per role, each written once: the acquirer's is Java, the issuer's is
Node.** The LP's follows.

That mapping is about where the worked example lives, not about what you may build
in. Every SDK generates code for all three roles, so an issuer on Java and an
acquirer on Node are both first-class — read the starter for your role, in whichever
language it happens to be, and write yours in the one you deploy.

Both SDKs are published — `network.t-0:usdt-pay-sdk-java` on Maven Central,
`@t-0/usdt-pay-sdk` on npm — alongside the two generators. See
[docs/RELEASE_AND_PUBLISH.md](docs/RELEASE_AND_PUBLISH.md) for how a release is cut.

## Staying current on 0.x

The contract moves before 1.0, so pick up changes deliberately rather than by
surprise:

- **Cloned?** `git pull`, rebuild, and read the release notes for the tag you moved
  to. Your starter follows the SDK in the same commit.
- **Scaffolded?** Your project carries its own SDK version — a `^` range on
  `@t-0/usdt-pay-sdk` in `package.json`, an exact `usdtPaySdkVersion` in
  `gradle.properties`. Raise it, read the release notes for
  every version you skipped, and rebuild. To see what changed in the starter itself,
  scaffold a throwaway project at the new version and diff it against yours.

Releases are listed at
[github.com/t-0-network/usdt-pay-sdk/releases](https://github.com/t-0-network/usdt-pay-sdk/releases).

## License

MIT — see [LICENSE](LICENSE).
