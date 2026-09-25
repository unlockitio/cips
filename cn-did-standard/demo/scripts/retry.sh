#!/bin/sh
set -eu

label=$1
timeout=$2
shift 2
started=$(date +%s)

while ! "$@"; do
  now=$(date +%s)
  if [ $((now - started)) -ge "$timeout" ]; then
    printf '%s\n' "Timed out after ${timeout}s: $label" >&2
    exit 1
  fi
  sleep 2
done
