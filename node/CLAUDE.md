# Node

## Build and test

```bash
cd node && npm install && npm run build && npm run typecheck && npm test
```

CI: `ci-node.yaml`.

## Proto sync

Stubs are committed under `node/sdk/src/gen/` so consumers need no `buf`.
`generate-clients.yaml` runs `buf generate --clean` in `node/sdk`.

- `payRegistry` in `node/sdk/src/registry.ts` must list **every** pay proto file
  explicitly. A new proto file in the sync means a new entry there.
- `node/sdk/src/index.ts` re-exports the generated modules with `export *`; ES
  semantics silently drop a name exported by two of them.
  `node/sdk/test/exports.test.ts` catches collisions.
