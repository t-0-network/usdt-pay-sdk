# Build context is this project directory:
#
#   docker build -t usdt-pay-acquirer .
#
FROM golang:1.27-alpine AS builder

WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o /service ./cmd

# Runs as the image's non-root user (uid 65532).
FROM gcr.io/distroless/base:nonroot

WORKDIR /app
COPY --from=builder /service /service

EXPOSE 8080

# Configuration comes from the environment: docker run --env-file .env ...
# The image deliberately carries no .env — your private key does not belong in it.
ENTRYPOINT ["/service"]
