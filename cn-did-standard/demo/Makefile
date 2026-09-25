SHELL := /bin/sh
override COMPOSE_PROJECT_NAME := cn-did-demo
export COMPOSE_PROJECT_NAME
COMPOSE := docker compose

.PHONY: up build daml-build daml-test java-test bft-reader-test e2e bft-reader-fault-e2e ps logs logs-canton logs-pqs logs-did-api logs-bft-reader seed projection-check did-smoke-test bft-reader-e2e smoke-test down reset

up:
	$(COMPOSE) up --build

build: daml-build java-test bft-reader-test
	$(COMPOSE) build

daml-build:
	./scripts/fetch-dependencies.sh
	dpm build --all

daml-test: daml-build
	cd test && dpm test

java-test:
	cd java-api && mvn test

bft-reader-test:
	cd bft-reader/java-api && mvn test

seed:
	$(COMPOSE) run --rm did-seed

projection-check:
	$(COMPOSE) run --rm did-projection-waiter

did-smoke-test:
	./scripts/smoke-test.sh

bft-reader-e2e:
	./scripts/bft-reader-e2e.sh

smoke-test: did-smoke-test bft-reader-e2e

e2e:
	./scripts/e2e.sh

bft-reader-fault-e2e:
	./scripts/bft-reader-fault-e2e.sh

ps:
	$(COMPOSE) ps -a

logs:
	$(COMPOSE) logs

logs-canton:
	$(COMPOSE) logs did-canton

logs-pqs:
	$(COMPOSE) logs did-pqs

logs-did-api:
	$(COMPOSE) logs did-api-1 did-api-2 did-api-3 did-api-4

logs-bft-reader:
	$(COMPOSE) logs bft-reader-api

down:
	$(COMPOSE) down

reset:
	@printf '%s\n' 'WARNING: reset removes only cn-did-demo containers, network, and volumes.'
	@printf '%s' 'Type RESET-DID-DEMO to continue: '; read answer; [ "$$answer" = 'RESET-DID-DEMO' ]
	$(COMPOSE) down --volumes
