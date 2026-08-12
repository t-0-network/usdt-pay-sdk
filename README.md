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
| **Acquirer** | Own the merchant. Price the sale, open the intent, show the QR, learn when it settles. | [`java/starter/acquirer`](java/starter/acquirer) |
| **Issuer** | Reserve deposit addresses, watch the chain for the customer's USDt, settle on-chain. | [`java/starter/issuer`](java/starter/issuer) |
| **Liquidity Provider** | Price USDt↔local fiat, take the per-sale obligation, settle fiat over bank rails. | [`java/starter/lp`](java/starter/lp) |

Not sure which you are? The acquirer talks to the POS, the issuer talks to the
blockchain, the LP talks to a bank. t-0 sits in the middle and no two of you ever
talk directly.

The LP only exists in **fiat settlement mode**. An acquirer settled in USDt has no
LP, gets its USDt straight from the issuer, and skips §3, §11 and §12 entirely.
Your mode is fixed at onboarding.

## Quick start

The starters build and run on **Java 21**. If your JDK is older the build still
works — Gradle provisions a 21 toolchain itself — but the binary needs a 21 runtime.
The SDK jar is compiled for 17, so consuming it does not force you off 17.

Provisioning downloads a JDK through `api.foojay.io`. If your build network does not
allow that, install a 21 JDK yourself and the resolver stays out of the way — or put
the starter back on 17: change `JavaLanguageVersion.of(21)` to `of(17)` in its
`build.gradle.kts` and the `eclipse-temurin:21-*` tags in its `Dockerfile`. Nothing in
the starter code uses a language feature newer than 17.

```bash
git clone git@github.com:t-0-network/usdt-pay-sdk.git
cd usdt-pay-sdk/java
./gradlew build                                  # SDK + all three starters

./gradlew :starter:acquirer:installDist

cd starter/acquirer
cp .env.example .env
# fill in PRIVATE_KEY (openssl rand -hex 32) and NETWORK_PUBLIC_KEY (from the t-0 team)

# Run from this directory — .env is read from the working directory.
./build/install/acquirer/bin/acquirer
```

Then work through that starter's README — it is a numbered path from "prints my
public key" to "settled a real sale".

## What is in here

```
proto/tzero/v1/          protocol definitions, snapshot-synced from the t-0 backend
└── pay/                 acquirer.proto, issuer.proto, lp.proto, common.proto
                         (self-contained: the pay contract shares no types with
                          tzero.v1.common, so your generated code carries exactly
                          one Decimal and one Blockchain)

java/                    Java SDK (built on 21, consumable on 17) + one starter per role
├── sdk/                 network.t-0:usdt-pay-sdk-java — generated stubs for all three roles
└── starter/{acquirer,issuer,lp}
```

The Java stubs are **generated at build time** by [buf](https://buf.build) and are
not committed. `./gradlew build` regenerates them; nothing under `gen/` or `build/`
belongs in git.

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

All 15 endpoints are unary request/response — nothing in this protocol streams. That
is why the starters use the blocking stub everywhere, and why you probably should too.

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
        channel -> IssuerServiceGrpc.newBlockingStub(channel)
                .withInterceptors(new CallDeadline(Duration.ofSeconds(10))));
```

Do **not** use `stub.withDeadlineAfter(...)` for this. A gRPC `Deadline` is an
absolute instant, not a per-call duration, so a stub built once that way works until
the deadline passes and then fails every later call with `DEADLINE_EXCEEDED`. A call
that sets its own deadline still wins — `CallDeadline` leaves it alone, which is how
the starters give §3 a shorter one and §9/§10 a longer one.

`BlockingNetworkClient.create(endpoint, signer, stubFactory, timeoutSeconds)` looks
like the built-in knob for this. As of provider-sdk-java 1.1.25 it is not: the
argument is accepted and never read.

### If you need non-blocking

`FutureNetworkClient` is the reference non-blocking client — same signing, same
endpoint. Every RPC is unary, so each call hands back one `ListenableFuture`:

```java
var t0 = FutureNetworkClient.create(endpoint, signer, IssuerServiceGrpc::newFutureStub);
ListenableFuture<PaymentReceivedResponse> pending = t0.stub().paymentReceived(request);
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

### If you want the compiler to enforce the failure path

`newBlockingV2Stub` is the same blocking call with a *checked* exception — its methods
`throws StatusException`, so forgetting to handle a failed call is a compile error
instead of a production surprise. It extends `AbstractBlockingStub`, so it drops into
`BlockingNetworkClient` with nothing else changed:

```java
var t0 = BlockingNetworkClient.create(endpoint, signer, IssuerServiceGrpc::newBlockingV2Stub);
```

For unary calls the exception type is the *only* difference: `blockingV2UnaryCall`
delegates straight to the same `blockingUnaryCall` the classic stub uses and converts
the resulting `StatusRuntimeException` into a checked `StatusException`. No
experimental machinery is involved. The starters stay on the classic stub for two
reasons: `Outcome` already forces the three cases to be handled, and a checked
exception cannot escape a lambda — the acquirer demo's `.ifPresent(quote -> …)` would
need a try/catch inside every one. Take V2 if you would rather the compiler enforced
it than a return type.

## Testing your integration

You do not need t-0 to reach you, or a running server, to test the half of this that
holds your logic. Both directions stub cleanly.

**Inbound — your callback handler is a plain object.** It extends a generated
`*ImplBase`, so a test constructs it and calls the method directly, asserting on what
it hands the `StreamObserver`. No server, no signing, no network:

```java
var responses = new ArrayList<ExecuteQuoteResponse>();
new LpCallbackHandler().executeQuote(
        ExecuteQuoteRequest.newBuilder().setExecutionId(1).setQuoteId(7).build(),
        new StreamObserver<>() {
            public void onNext(ExecuteQuoteResponse r) { responses.add(r); }
            public void onError(Throwable t) { throw new AssertionError(t); }
            public void onCompleted() { }
        });

assertEquals(1, responses.size());
```

The test worth writing first is redelivery: call the handler **twice** with the same
request and assert your own store holds one row. That is the at-least-once contract,
and it is the one that costs money to get wrong.

**Outbound — fake t-0, not the stub.** The generated stubs are `final` with private
constructors, so they cannot be subclassed or mocked. Stand a fake service up in
memory and hand the helper a real stub pointed at it. One test dependency,
`io.grpc:grpc-inprocess`:

```java
String name = InProcessServerBuilder.generateName();
Server server = InProcessServerBuilder.forName(name).directExecutor()
        .addService(new LpServiceGrpc.LpServiceImplBase() {
            @Override public void publishQuote(
                    PublishQuoteRequest request,
                    StreamObserver<PublishQuoteResponse> observer) {
                observer.onError(Status.UNAVAILABLE.asRuntimeException());
            }
        })
        .build().start();

var t0 = LpServiceGrpc.newBlockingStub(
        InProcessChannelBuilder.forName(name).directExecutor().build());

assertTrue(PublishQuote.publish(t0, Duration.ofSeconds(90)).shouldRetry());
```

`java/starter/lp/src/test/java/network/t0/pay/lp/internal/PublishQuoteTest.java` works
this out for all three outcomes — copy its shape. Write the one above first: it is the
`Outcome.Unknown` branch, which a happy-path test against a sandbox never reaches and
which decides whether you publish a second quote by accident.

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

Go and Node SDKs land as sibling directories — `go/` and `node/` — served by the
same `proto/` at the root. Names and versioning are fixed now so you can plan
against them:

| Ecosystem | Artifact | Versioning | Transport |
|---|---|---|---|
| Java | `network.t-0:usdt-pay-sdk-java` | Maven, `X.Y.Z` | grpc-java |
| Go | `github.com/t-0-network/usdt-pay-sdk/go` | Git tags `go/vX.Y.Z` | Connect |
| Node | `@t-0/usdt-pay-sdk` | npm, `X.Y.Z` | Connect |

Neither the Go nor the Node code exists yet. Until 1.0 the Java artifact is not
published either — clone this repo and let the starters build against the local
`:sdk` project.

## License

MIT — see [LICENSE](LICENSE).
