#!/usr/bin/env bash
# Validates all Node version sites against $VERSION.
# Expects: VERSION and ERRORS set, check() defined by the caller.
# Used by both release.yaml and publish.yaml.

check "node/sdk/package.json .version" \
  "$(node -p "require('./node/sdk/package.json').version")" "$VERSION"
check "package-lock.json packages.sdk.version" \
  "$(node -p "require('./node/package-lock.json').packages['sdk'].version")" "$VERSION"

for d in node/starter/*/; do
  STARTER=$(basename "$d")
  check "node/starter/$STARTER/package.json .version" \
    "$(node -p "require('./node/starter/$STARTER/package.json').version")" "$VERSION"
  check "node/starter/$STARTER/package.json SDK pin" \
    "$(node -p "require('./node/starter/$STARTER/package.json').dependencies['@t-0/usdt-pay-sdk']")" "^$VERSION"
  check "package-lock.json starter/$STARTER .version" \
    "$(node -p "require('./node/package-lock.json').packages['starter/$STARTER'].version")" "$VERSION"
  check "package-lock.json starter/$STARTER SDK pin" \
    "$(node -p "require('./node/package-lock.json').packages['starter/$STARTER'].dependencies['@t-0/usdt-pay-sdk']")" "^$VERSION"
done
