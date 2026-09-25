#!/bin/sh
set -eu

DEMO_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
DEPS_DIR="$DEMO_DIR/.deps"
DAR="$DEPS_DIR/splice-api-token-metadata-v1-1.0.0.dar"
URL="https://raw.githubusercontent.com/hyperledger-labs/splice/fc5075093d9b10191aeedab66ff35cadaded9dc8/daml/dars/splice-api-token-metadata-v1-1.0.0.dar"
SHA256="455eb160cb5abd4ae9918a6fbb9dad471f721adda39f0e5c76feef08d05637fc"

mkdir -p "$DEPS_DIR"

check() {
  if command -v sha256sum >/dev/null 2>&1; then
    actual=$(sha256sum "$DAR" | cut -d ' ' -f 1)
  else
    actual=$(shasum -a 256 "$DAR" | cut -d ' ' -f 1)
  fi
  [ "$actual" = "$SHA256" ] || {
    printf '%s\n' "SHA-256 mismatch for $DAR: expected $SHA256, got $actual" >&2
    return 1
  }
}

if [ -f "$DAR" ] && check; then
  exit 0
fi

partial="$DAR.part"
trap 'rm -f "$partial"' EXIT HUP INT TERM
curl --fail --location --silent --show-error "$URL" --output "$partial"
mv "$partial" "$DAR"
check
trap - EXIT HUP INT TERM
