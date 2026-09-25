#!/bin/sh
set -eu

printf '%s\n' "Native host sandbox shutdown was removed. Use 'make down' to stop the shared Compose application without deleting its volume." >&2
exit 1
