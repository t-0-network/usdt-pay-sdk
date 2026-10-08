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

When the key lives in an HSM or KMS and never reaches this process, build the
client with provider-sdk directly and give it the signer instead of a key:

```go
t0, err := network.NewServiceClient("", acquirerconnect.NewAcquirerServiceClient,
    network.WithBaseURL(endpoint), network.WithSignatureFunction(signFn))
```

Start a callback server:

```go
shutdown, err := usdtpay.StartServer(":8080", networkPublicKey,
    provider.Handler(acquirerconnect.NewAcquirerCallbackServiceHandler, handler))
defer shutdown(context.Background())
```

Requires Go 1.27 or newer. The API reference, with the generated protobuf stubs, is
on [pkg.go.dev](https://pkg.go.dev/github.com/t-0-network/usdt-pay-sdk/go/sdk).
