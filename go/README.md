# Go

Go SDK and starter for the **t-0 USDt pay flow**.

## Starter

Scaffold with the [CLI](../cli/README.md):

```bash
curl -fsSL https://raw.githubusercontent.com/t-0-network/usdt-pay-sdk/master/cli/install.sh | sh -s -- init --lang=go --role=acquirer my-acquirer
```

| Role | Template |
|---|---|
| `acquirer` | [starter/acquirer/](starter/acquirer/) |

After scaffolding:

```bash
cd my-acquirer
go run ./cmd
```

Then follow your project's README.

## SDK

[`github.com/t-0-network/usdt-pay-sdk/go/sdk`](https://pkg.go.dev/github.com/t-0-network/usdt-pay-sdk/go/sdk)

```bash
go get github.com/t-0-network/usdt-pay-sdk/go/sdk
```

Create a client, make a call:

```go
t0, err := usdtpay.CreateClient(endpoint, privateKeyHex,
    acquirerconnect.NewAcquirerServiceClient)
```

Start a callback server:

```go
shutdown, err := usdtpay.StartServer(":8080", networkPublicKey,
    provider.Handler(acquirerconnect.NewAcquirerCallbackServiceHandler, handler))
defer shutdown(context.Background())
```

Requires Go 1.27 or newer. See [sdk/](sdk/) for client patterns, server setup and
the generated protobuf stubs.
