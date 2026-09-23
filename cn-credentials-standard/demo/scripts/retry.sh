#!/bin/sh
set -eu

label=${1:-operation}
timeout_seconds=${2:-180}
shift 2

started=$(date +%s)
attempt=0
while :; do
  attempt=$((attempt + 1))
  if "$@"; then
    exit 0
  fi
  now=$(date +%s)
  if [ $((now - started)) -ge "$timeout_seconds" ]; then
    printf '%s\n' "Timed out after ${timeout_seconds}s waiting for ${label} (${attempt} attempts)." >&2
    exit 1
  fi
  sleep 2
done
