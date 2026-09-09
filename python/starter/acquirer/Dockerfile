# Build context is this project directory:
#
#   docker build -t usdt-pay-acquirer .
#
FROM python:3.13-slim AS builder

COPY --from=ghcr.io/astral-sh/uv:0.9 /uv /usr/local/bin/uv

WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN uv sync --no-dev --frozen --no-install-project

COPY . .
RUN uv sync --no-dev --frozen

FROM python:3.13-slim

RUN useradd --create-home appuser
USER appuser
WORKDIR /home/appuser

COPY --from=builder /app/.venv /home/appuser/.venv
ENV PATH="/home/appuser/.venv/bin:$PATH"

EXPOSE 8080

# Configuration comes from the environment: docker run --env-file .env ...
# The image deliberately carries no .env — your private key does not belong in it.
ENTRYPOINT ["python", "-m", "acquirer.main"]
