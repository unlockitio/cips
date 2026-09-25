#!/bin/sh
set -eu

printf '%s\n' "This upload helper is retired. Run 'make daml-build', then './scripts/start-sandbox.sh'; dpm sandbox uploads model/.daml/dist/canton-network-did-demo-0.1.0.dar." >&2
exit 1
