#!/bin/sh
set -eu

JSON_API_URL=${JSON_API_URL:-http://credential-canton:7575}
READINESS_TIMEOUT_SECONDS=${READINESS_TIMEOUT_SECONDS:-600}
started=$(date +%s)

while :; do
  if response=$(curl --fail --silent --show-error "$JSON_API_URL/v2/state/connected-synchronizers" 2>/dev/null) \
      && printf '%s' "$response" | grep -q '"synchronizerAlias":"credential-synchronizer"'; then
    printf '%s\n' "Credentials participant is connected to credential-synchronizer."
    exit 0
  fi

  now=$(date +%s)
  if [ $((now - started)) -ge "$READINESS_TIMEOUT_SECONDS" ]; then
    printf '%s\n' "Timed out waiting for the Credentials participant synchronizer connection." >&2
    exit 1
  fi
  sleep 2
done
