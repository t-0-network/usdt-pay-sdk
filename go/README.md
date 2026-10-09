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
t0, err := usdtpay.CreateClient(baseURL, privateKeyHex,
    acquirerconnect.NewAcquirerServiceClient)
```

When the key lives in an HSM or KMS and never reaches this process, build the
client with provider-sdk directly and give it a `crypto.SignFn` (from
`github.com/t-0-network/provider-sdk/go/crypto`) instead of a key:

```go
t0, err := network.NewServiceClient("", acquirerconnect.NewAcquirerServiceClient,
    network.WithBaseURL(baseURL), network.WithSignatureFunction(signFn))
```

Start a callback server:

```go
shutdown, err := usdtpay.StartServer(":8080", networkPublicKey,
    provider.Handler(acquirerconnect.NewAcquirerCallbackServiceHandler, handler))
defer shutdown(context.Background())
```

Every response is validated against the contract's `buf.validate` constraints on
the way out. To check one inside a handler first, for instance to answer with the
`failure` arm instead of an opaque `CodeInternal`, call provider-sdk's
`provider.Validate`:

```go
resp, err := provider.Validate(&issuer.CreatePaymentInstructionsResponse{ /* ... */ })
if err != nil {
    return nil, err
}
return connect.NewResponse(resp), nil
```

An invalid response gives `CodeInternal` "response validation failed: <field path>:
<message>", the error the server would return for it. The contract's custom rules
(`valid_address`, `valid_tx_hash`) resolve with no setup.

Requires Go 1.27 or newer. The API reference, with the generated protobuf stubs, is
on [pkg.go.dev](https://pkg.go.dev/github.com/t-0-network/usdt-pay-sdk/go/sdk).
