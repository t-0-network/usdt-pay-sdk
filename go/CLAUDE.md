# Go

## Build and test

```bash
cd go/sdk && go build ./... && go test -v ./...
```

CI: `ci-go.yaml`.

## Proto sync

Stubs are committed under `go/sdk/gen/` so consumers need no `buf`.
`generate-clients.yaml` runs `buf generate --clean` in `go/sdk`.

`go/sdk/buf.gen.yaml` uses managed mode with `go_package_prefix` to set import
paths — the pay protos have no `go_package` option.

## Provider SDK

`github.com/t-0-network/provider-sdk/go` is pinned exact in `go/sdk/go.mod`.
Bump deliberately, not as drive-by.

## Starter

`go/starter/acquirer/` has a single `go.mod` and `go.sum` with the real module
path. `release.yaml` bumps the SDK pin to the current release. No `.tmpl` files
in the tree — the sync tool creates the `.tmpl` rename only in the CLI's embed
directory. Develop against the local SDK with `-replace` per `docs/CLI.md`.
