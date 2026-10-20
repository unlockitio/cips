SHELL := /bin/sh
override COMPOSE_PROJECT_NAME := cn-credential-demo
export COMPOSE_PROJECT_NAME
COMPOSE := docker compose

.PHONY: up build daml-build daml-test java-test e2e ps logs logs-canton logs-pqs logs-credentials-api seed projection-check credentials-smoke-test did-smoke-test bft-reader-e2e cross-stack-name-e2e smoke-test down reset

up:
	$(COMPOSE) up --build

build: daml-build java-test
	$(MAKE) -C ../../cn-did-standard/demo daml-build java-test bft-reader-test
	$(COMPOSE) build

daml-build:
	./scripts/fetch-dependencies.sh
	dpm build --all

daml-test: daml-build
	cd test && dpm test

java-test:
	cd java-api && mvn test
	cd bft-reader/java-api && mvn test

seed:
	$(COMPOSE) run --rm credential-seed
	$(COMPOSE) run --rm did-seed

projection-check:
	$(COMPOSE) run --rm credential-projection-waiter

credentials-smoke-test:
	./scripts/smoke-test.sh

did-smoke-test:
	DID_NAME_RESOLUTION_ENABLED=true COMPOSE_FILE=$(CURDIR)/compose.yaml ../../cn-did-standard/demo/scripts/smoke-test.sh

bft-reader-e2e:
	COMPOSE_FILE=$(CURDIR)/compose.yaml ../../cn-did-standard/demo/scripts/bft-reader-e2e.sh

cross-stack-name-e2e:
	./scripts/cross-stack-name-e2e.sh

smoke-test: credentials-smoke-test did-smoke-test bft-reader-e2e cross-stack-name-e2e

# Owns a disposable stack end to end, including removing its own volumes.
e2e:
	./scripts/e2e.sh

ps:
	$(COMPOSE) ps -a

logs:
	$(COMPOSE) logs

logs-canton:
	$(COMPOSE) logs credential-canton

logs-pqs:
	$(COMPOSE) logs credential-pqs

logs-credentials-api:
	$(COMPOSE) logs credential-api-1

down:
	$(COMPOSE) down

reset:
	@printf '%s\n' 'WARNING: reset removes only cn-credential-demo containers, network, and volumes.'
	@printf '%s' 'Type RESET-CREDENTIAL-DEMO to continue: '; read answer; [ "$$answer" = 'RESET-CREDENTIAL-DEMO' ]
	$(COMPOSE) down --volumes
