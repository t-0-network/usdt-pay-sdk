# Build context is this project directory — the scaffolded project resolves the SDK
# from npm, not from a workspace next to it:
#
#   docker build -t usdt-pay-issuer .
#   docker run -p 8080:8080 --env-file .env usdt-pay-issuer
#
FROM node:24-alpine AS build

WORKDIR /app
COPY package.json ./
RUN npm install
COPY . .
RUN npm run build

FROM node:24-alpine

WORKDIR /app
COPY --from=build /app ./
RUN npm prune --omit=dev --ignore-scripts

EXPOSE 8080
USER node

# Configuration comes from the environment: docker run --env-file .env ...
# The image deliberately carries no .env — your private key does not belong in it.
CMD ["node", "dist/index.js"]
