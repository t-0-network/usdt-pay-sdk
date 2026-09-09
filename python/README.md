# Python

Python SDK and starter for the **t-0 QR payment flow**.

## Starter

Python 3.13 or newer. Scaffold with the [CLI](../cli/README.md):

```bash
curl -fsSL https://raw.githubusercontent.com/t-0-network/usdt-pay-sdk/master/cli/install.sh | sh -s -- init --lang=python --role=acquirer my-acquirer
```

| Role | Template |
|---|---|
| `acquirer` | [starter/acquirer/](starter/acquirer/) |

After scaffolding:

```bash
cd my-acquirer
# add NETWORK_PUBLIC_KEY to .env — your t-0 onboarding contact gives you this
uv sync && uv run python -m acquirer.main
```

Then follow your project's README.

## SDK

```bash
pip install t0-usdt-pay-sdk
```

Create a client, make a call:

```python
from t0_usdt_pay_sdk import create_client
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import AcquirerServiceClient

t0 = create_client(endpoint, private_key_hex, AcquirerServiceClient)
response = await t0.create_payment_intent(request)
```

Serve callbacks (async):

```python
from t0_usdt_pay_sdk import create_asgi_app, handler
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import AcquirerCallbackServiceASGIApplication

app = create_asgi_app(
    network_public_key,
    handler(AcquirerCallbackServiceASGIApplication, callback_impl),
)
```

Serve callbacks (sync):

```python
from t0_usdt_pay_sdk import create_wsgi_app, handler_sync
from t0_usdt_pay_sdk.api.tzero.v1.pay.acquirer.acquirer_connect import AcquirerCallbackServiceWSGIApplication

app = create_wsgi_app(
    network_public_key,
    handler_sync(AcquirerCallbackServiceWSGIApplication, callback_impl),
)
```

See [sdk/](sdk/) for `create_client`, `create_asgi_app`, `create_wsgi_app` and `crypto` in full.
