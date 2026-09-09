# t0-usdt-pay-sdk

Python SDK for the **t-0 QR payment flow** — the contract under
`proto/tzero/v1/pay/`. Generated Connect clients and servers for all three roles,
over a transport that signs what you send and verifies what arrives.

Start from a scaffolded project rather than from here: `usdt-pay init --lang=python --role=acquirer <project-name>`
(install and every flag: [`cli/README.md`](https://github.com/t-0-network/usdt-pay-sdk/blob/master/cli/README.md)).
The project's README is the
[acquirer integration guide](https://github.com/t-0-network/usdt-pay-sdk/blob/master/python/starter/acquirer/README.md).

## Install

```bash
pip install t0-usdt-pay-sdk
```

Scaffolding a project with the CLI (`usdt-pay init`) adds this dependency for you.

## Serving the callbacks t-0 pushes to you

### Async (ASGI / uvicorn)

```python
from t0_usdt_pay_sdk import create_asgi_app, handler
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import AcquirerCallbackServiceASGIApplication

app = create_asgi_app(
    network_public_key,
    handler(AcquirerCallbackServiceASGIApplication, my_callback_impl),
)
```

### Sync (WSGI / gunicorn / waitress)

```python
from t0_usdt_pay_sdk import create_wsgi_app, handler_sync
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import AcquirerCallbackServiceWSGIApplication

app = create_wsgi_app(
    network_public_key,
    handler_sync(AcquirerCallbackServiceWSGIApplication, my_callback_impl),
)
```

Every inbound request is verified against t-0's public key before it reaches your
handler, and every response is validated against the contract's `buf.validate`
constraints on the way out. Verification runs over the bytes that arrived — protobuf
encoding is not canonical, so a re-serialized message is a different message to
secp256k1, and the SDK wires the raw-body hasher in for you.

Mount one service per role edge you implement — `AcquirerCallbackService`,
`IssuerCallbackService`, `LpCallbackService`.

## Calling t-0

```python
from t0_usdt_pay_sdk import create_client
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import AcquirerServiceClient

t0 = create_client(endpoint, private_key_hex, AcquirerServiceClient)
response = await t0.create_payment_intent(request)
```

`endpoint` is required: the underlying provider client defaults to a different t-0
API, and a pay participant that omitted it would sign perfectly valid requests and
send them to the wrong host.

`signer` takes a hex private key, or a `SignFn` when the key lives in an HSM
or KMS and never reaches this process.

For sync code, use `create_client_sync` with the `*ClientSync` class.

## Standalone signature verification

For integrators mounting into their own stack, the `crypto` module exposes the same
primitives the SDK uses internally:

```python
from t0_usdt_pay_sdk.crypto import (
    signature_verification_middleware,  # ASGI
    signature_verification_middleware_wsgi,  # WSGI
    SignatureErrorInterceptor,  # async interceptor — REJECTS
    SignatureErrorInterceptorSync,  # sync interceptor — REJECTS
    signature_error_var,  # the contextvar the middleware records to
    new_verify_signature,
    legacy_keccak256,
    new_signer_from_hex,
    verify_signature,
)
```

The middleware records; the interceptor rejects. Both are needed — mount the
middleware around the ASGI/WSGI app and add the interceptor to your service
application, or guard with an explicit `signature_error_var` check.

## The `buf.validate` namespace

`buf.validate` has one owner: `t0-provider-sdk`. Its `api/buf/` package provides the
descriptors that the pay rules reference (`valid_tx_hash`, `valid_address`). Importing
`t0_provider_sdk` (which this SDK's `__init__` does before anything else) puts that
copy in `sys.modules` and in the descriptor pool. This SDK ships no `buf/` directory.

## Importing generated messages

Always import via the package path:

```python
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer import acquirer_pb2
```

The bare `from tzero.v1.pay.acquirer import acquirer_pb2` also works (via `sys.path`)
but produces a **different module object**, so mixing the two paths in one program
creates two copies of every message class and `isinstance` checks break.

## The public key t-0 knows you by

```python
from t0_usdt_pay_sdk import public_key_from_private_key

print(public_key_from_private_key(private_key_hex))
```

Send it to the t-0 team — that is step 1 of every role's integration.

## Generated code

`src/t0_usdt_pay_sdk/api/` is checked in, so neither the starters nor a consumer
needs `buf` installed. Regenerate it after a proto sync:

```bash
cd python/sdk
buf generate --clean
```

## Build

```bash
cd python
uv sync --all-packages
uv build --package t0-usdt-pay-sdk
```
