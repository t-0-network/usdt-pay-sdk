# Build context is this project directory — the scaffolded project resolves the SDK
# from npm, not from a workspace next to it:
#
#   npm install                 # writes package-lock.json; the image installs from it
#   docker build -t usdt-pay-issuer .
#   docker run -p 8080:8080 --env-file .env usdt-pay-issuer
#
# .dockerignore beside this file keeps .env, .git and the host node_modules out of the
# context, which is what makes the COPY below safe.
FROM node:22-alpine AS build

WORKDIR /app
# The lockfile, and `npm ci` rather than `npm install`: this image must run the exact
# SDK build you tested against, not whatever patch resolves the day it is built. That
# needs a package-lock.json, so run `npm install` once before the first docker build.
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:22-alpine

WORKDIR /app
COPY --from=build /app ./
RUN npm prune --omit=dev --ignore-scripts

EXPOSE 8080
USER node

# Configuration comes from the environment: docker run --env-file .env ...
# The image deliberately carries no .env — your private key does not belong in it.
CMD ["node", "dist/index.js"]
