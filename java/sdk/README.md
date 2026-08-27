# usdt-pay-sdk-java

Java SDK for the **t-0 QR payment flow** — generated gRPC stubs for all three
roles, over a transport that signs what you send and verifies what arrives.

Start from a role's starter rather than from here:
[`java/starter/acquirer`](../starter/acquirer/).

## Install

`network.t-0:usdt-pay-sdk-java` on Maven Central. Scaffolding a project with the
CLI (`usdt-pay init`) adds this dependency for you.

**Gradle:**

```kotlin
dependencies {
    implementation("network.t-0:usdt-pay-sdk-java:$usdtPaySdkVersion")
}
```

**Maven:**

```xml
<dependency>
    <groupId>network.t-0</groupId>
    <artifactId>usdt-pay-sdk-java</artifactId>
    <version>${usdtPaySdkVersion}</version>
</dependency>
```

## Serving the callbacks t-0 pushes to you

Extend the generated `*ImplBase` for your role and hand it to `UsdtPayServer`,
which verifies every inbound request against t-0's public key before it reaches
your handler:

```java
var server = UsdtPayServer.create(config.port(), config.networkPublicKey())
        .addService(new AcquirerCallbackHandler())
        .start();
```

Verification runs over the bytes that arrived — protobuf encoding is not
canonical, so a re-serialized message is a different message to secp256k1.

Mount one service per role edge you implement — `AcquirerCallbackImplBase`,
`IssuerCallbackImplBase`, `LpCallbackImplBase`.

## Calling t-0

All 15 endpoints are unary request/response — nothing in this protocol streams,
which is why the starters use the blocking stub everywhere, and why you probably
should too.

**Blocking is the primary path, and on Java 21+ it is also the scalable one.** A
blocking gRPC call parks on `LockSupport.park` inside grpc's `ThreadlessExecutor`,
which is a lock-free queue and holds no monitor. So a virtual thread making one of
these calls *unmounts* its carrier instead of pinning it: run the `internal/`
helpers on `Executors.newVirtualThreadPerTaskExecutor()` and a blocked call costs
you a continuation, not a platform thread. Straight-line code and non-blocking
scaling are not a trade here.

**Give the stub a default deadline where you build it.** `CallDeadline` is a
`ClientInterceptor` that applies one per call:

```java
var t0 = BlockingNetworkClient.create(endpoint, signer,
        channel -> AcquirerServiceGrpc.newBlockingStub(channel)
                .withInterceptors(new CallDeadline(Duration.ofSeconds(10))));
```

Do **not** use `stub.withDeadlineAfter(...)` for this. A gRPC `Deadline` is an
absolute instant, not a per-call duration, so a stub built once that way works
until the deadline passes and then fails every later call with
`DEADLINE_EXCEEDED`. A call that sets its own deadline still wins —
`CallDeadline` leaves it alone, which is how the acquirer starter gives
`GetPaymentQuote` a shorter one at the call site.

`BlockingNetworkClient.create(endpoint, signer, stubFactory, timeoutSeconds)`
looks like the built-in knob for this. As of provider-sdk-java 1.1.25 it is not:
the argument is accepted and never read.

## Non-blocking

`FutureNetworkClient` is the reference non-blocking client — same signing, same
endpoint. Every RPC is unary, so each call hands back one `ListenableFuture`:

```java
var t0 = FutureNetworkClient.create(endpoint, signer, AcquirerServiceGrpc::newFutureStub);
ListenableFuture<CreatePaymentIntentResponse> pending = t0.stub().createPaymentIntent(request);
```

If your code speaks `CompletableFuture`, bridge it. The part worth copying
carefully is cancellation reaching the RPC rather than stopping at the wrapper:

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

`directExecutor()` completes the future on a gRPC network thread, so anything
you chain onto it runs there too. Keep continuations trivial or hand
`thenApplyAsync` your own executor — blocking work in a `thenApply` stalls the
channel for every other call on it.

`AsyncNetworkClient` also ships, giving you `StreamObserver` callbacks. Named
here so you do not have to wonder: it buys nothing for this protocol.
`StreamObserver` is the shape streaming needs, and collecting a single response
through `onNext`/`onError`/`onCompleted` is strictly more work than reading a
future.

## Checked failures

`newBlockingV2Stub` is the same blocking call with a *checked* exception — its
methods `throws StatusException`, so forgetting to handle a failed call is a
compile error instead of a production surprise. It extends
`AbstractBlockingStub`, so it drops into `BlockingNetworkClient` with nothing
else changed:

```java
var t0 = BlockingNetworkClient.create(endpoint, signer, AcquirerServiceGrpc::newBlockingV2Stub);
```

For unary calls the exception type is the *only* difference:
`blockingV2UnaryCall` delegates straight to the same `blockingUnaryCall` the
classic stub uses and converts the resulting `StatusRuntimeException` into a
checked `StatusException`. No experimental machinery is involved. The starters
stay on the classic stub for two reasons: `Outcome` already forces the three
cases to be handled, and a checked exception cannot escape a lambda — the
acquirer demo's `.ifPresent(quote -> …)` would need a try/catch inside every
one. Take V2 if you would rather have the compiler enforce it than a return type.

## Generated code

The stubs are generated at build time from `proto/` with
[buf](https://buf.build). `./gradlew build` regenerates them, and nothing under
`gen/` or `build/` belongs in git.

## Build

```bash
cd java/
./gradlew :sdk:build
```

Compiled for Java 17, consumable on 17+.
