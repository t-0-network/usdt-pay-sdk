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
