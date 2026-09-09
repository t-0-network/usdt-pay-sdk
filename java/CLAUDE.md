# Java

## Build and test

```bash
cd java && ./gradlew build --no-daemon
```

CI: `ci-java.yaml`.

## Proto sync

`java/sdk/src/main/proto/tzero` is a symlink into the root `proto/`. Java sources
import generated classes by package (`network.t0.pay.proto.tzero.v1.pay[.<role>]`),
so a message moving packages or losing a name prefix breaks starter/test imports.
Fix the imports; the contract is upstream's to change.

Stubs are generated at build time (`bufGenerate`, not committed).
