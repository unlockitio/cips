#!/bin/sh
set -eu

JSON_API_URL=${JSON_API_URL:-http://credential-canton:7575}

for dar in \
  /artifacts/canton-network-credentials-interfaces-0.1.0.dar \
  /artifacts/canton-network-credentials-demo-0.1.0.dar
do
  [ -f "$dar" ] || {
    printf '%s\n' "DAR is missing from the credential artifact volume: $dar" >&2
    exit 1
  }

  if ! /workspace/scripts/retry.sh "Canton upload of $(basename "$dar")" "${UPLOAD_TIMEOUT_SECONDS:-240}" \
    curl --fail --silent --show-error \
      --request POST \
      --header 'Content-Type: application/octet-stream' \
      --data-binary "@$dar" \
      "$JSON_API_URL/v2/packages"; then
    printf '%s\n' "DAR upload failed for $dar." >&2
    exit 1
  fi
done

printf '%s\n' "Credential interface and model DARs uploaded to the credential Canton ledger."
