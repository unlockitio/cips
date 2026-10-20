#!/bin/sh
set -eu

DEMO_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
DEPS_DIR="$DEMO_DIR/.deps"
COMMIT="fc5075093d9b10191aeedab66ff35cadaded9dc8"
BASE_URL="https://raw.githubusercontent.com/hyperledger-labs/splice/$COMMIT/daml/dars"

# One "<file> <sha256>" record per pinned token-standard DAR.
DEPENDENCIES="
splice-api-token-metadata-v1-1.0.0.dar 455eb160cb5abd4ae9918a6fbb9dad471f721adda39f0e5c76feef08d05637fc
splice-api-token-holding-v1-1.0.0.dar ef75f8eb41a65810221784fdb78bb9dfac7cb22245aba14fa7cb7f69c34e0175
"

mkdir -p "$DEPS_DIR"

check() {
  dar="$1"
  expected="$2"
  if command -v sha256sum >/dev/null 2>&1; then
    actual=$(sha256sum "$dar" | cut -d ' ' -f 1)
  else
    actual=$(shasum -a 256 "$dar" | cut -d ' ' -f 1)
  fi
  [ "$actual" = "$expected" ] || {
    printf '%s\n' "SHA-256 mismatch for $dar: expected $expected, got $actual" >&2
    return 1
  }
}

printf '%s\n' "$DEPENDENCIES" | while read -r file sha256; do
  [ -n "$file" ] || continue
  dar="$DEPS_DIR/$file"
  if [ -f "$dar" ] && check "$dar" "$sha256"; then
    continue
  fi
  partial="$dar.part"
  trap 'rm -f "$partial"' EXIT HUP INT TERM
  curl --fail --location --silent --show-error "$BASE_URL/$file" --output "$partial"
  mv "$partial" "$dar"
  check "$dar" "$sha256"
  trap - EXIT HUP INT TERM
done
