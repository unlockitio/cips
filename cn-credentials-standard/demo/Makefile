SHELL := /bin/sh

.PHONY: build daml-test demo-index up seed smoke-test down clean

build:
	cd ../interfaces && daml build
	cd daml && daml build
	docker compose build api

daml-test:
	cd daml && daml script --dar .daml/dist/canton-network-credentials-demo-0.1.0.dar --script-name Canton.Network.Credentials.Tests:main --ledger-host localhost --ledger-port "$${LEDGER_PORT:-6865}" --upload-dar yes

demo-index:
	docker compose exec -T postgres psql -U "$${POSTGRES_USER:-pqs}" -d "$${POSTGRES_DB:-pqs}" -c "call create_index_for_contract('credential_id_idx', 'canton-network-credentials-interfaces:Canton.Network.Credentials.V1:Credential', '(payload ->> ''id'')', 'btree', 'text_ops');"

up:
	docker compose up -d postgres canton pqs api

seed:
	./scripts/seed.sh

smoke-test:
	./scripts/smoke-test.sh

down:
	docker compose down

clean:
	cd daml && daml clean
	cd ../interfaces && daml clean
	cd java-api && mvn clean
