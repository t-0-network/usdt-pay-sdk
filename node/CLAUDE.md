# Node

## Build and test

```bash
cd node && npm install && npm run build && npm run typecheck && npm test
```

CI: `ci-node.yaml`.

## Provider SDK

`@t-0/provider-sdk` is pinned exact in `node/sdk/package.json`. Bump deliberately,
not as drive-by. `@connectrpc/connect` is pinned to exactly the version
provider-sdk pins (in the SDK, and as a dev dependency of the starters), so a
project installs one copy; move it in the same commit as a provider-sdk bump.
Dependabot ignores it. `npm ls @connectrpc/connect` must show a single version.

## Proto sync

Stubs are committed under `node/sdk/src/gen/` so consumers need no `buf`.
`generate-clients.yaml` runs `buf generate --clean` in `node/sdk`.

- `payRegistry` in `node/sdk/src/registry.ts` must list **every** pay proto file
  explicitly. A new proto file in the sync means a new entry there.
- `node/sdk/src/index.ts` re-exports the generated modules with `export *`; ES
  semantics silently drop a name exported by two of them.
  `node/sdk/test/exports.test.ts` catches collisions.
