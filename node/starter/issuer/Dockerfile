# Build context is node/, because the starter compiles against the SDK next to it —
# a context narrower than the workspace root cannot resolve it:
#
#   cd node && docker build -f starter/issuer/Dockerfile -t usdt-pay-issuer .
#
FROM node:22-alpine AS build

WORKDIR /src
COPY . .
RUN npm ci && npm run build

FROM node:22-alpine

WORKDIR /app
COPY --from=build /src ./
# --ignore-scripts, or prune drops typescript and then re-runs the sdk's prepare
# script against a toolchain it just deleted.
RUN npm prune --omit=dev --ignore-scripts

EXPOSE 8080
USER node
WORKDIR /app/starter/issuer

# Configuration comes from the environment: docker run --env-file .env ...
# The image deliberately carries no .env — your private key does not belong in it.
CMD ["node", "dist/index.js"]
