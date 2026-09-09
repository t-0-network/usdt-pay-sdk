# Python

## Build and test

```bash
cd python && uv sync --all-packages && uv run pytest -v && uv run ruff check . && uv run ruff format --check .
```

CI: `ci-python.yaml`.

## Proto sync

Stubs are committed under `python/sdk/src/t0_usdt_pay_sdk/api/`. A new proto file
in the sync means a new import in `python/sdk/src/t0_usdt_pay_sdk/registry.py`.

`generate-clients.yaml` runs `buf generate --clean` in `python/sdk`.

## Provider SDK

`t0-provider-sdk==1.1.41` is pinned exact in `python/sdk/pyproject.toml`. Bump
deliberately, not as drive-by. `buf.validate` descriptors are owned by
provider-sdk's `api/buf/` package — this SDK ships no `buf/` directory.
