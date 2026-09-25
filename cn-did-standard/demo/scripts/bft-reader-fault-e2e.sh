#!/bin/sh
set -eu

# Real HTTP adapters on ephemeral loopback ports; no Docker or ledger mutation.
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
mvn -f "$script_dir/../bft-reader/java-api/pom.xml" -Dtest=ReaderComponentTest test
