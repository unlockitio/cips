SHELL := /bin/sh
override COMPOSE_PROJECT_NAME := cn-credential-demo
export COMPOSE_PROJECT_NAME
COMPOSE := docker compose

.PHONY: up build daml-build daml-test java-test e2e ps logs logs-canton logs-pqs logs-credentials-api seed projection-check credentials-smoke-test smoke-test down reset

up:
	$(COMPOSE) up --build

build: daml-build java-test
	$(COMPOSE) build

daml-build:
	./scripts/fetch-dependencies.sh
	dpm build --all

daml-test: daml-build
	cd test && dpm test

java-test:
	cd java-api && mvn test

seed:
	$(COMPOSE) run --rm credential-seed

projection-check:
	$(COMPOSE) run --rm credential-projection-waiter

credentials-smoke-test:
	./scripts/smoke-test.sh

smoke-test: credentials-smoke-test

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
	$(COMPOSE) logs credential-api

down:
	$(COMPOSE) down

reset:
	@printf '%s\n' 'WARNING: reset removes only cn-credential-demo containers, network, and volumes.'
	@printf '%s' 'Type RESET-CREDENTIAL-DEMO to continue: '; read answer; [ "$$answer" = 'RESET-CREDENTIAL-DEMO' ]
	$(COMPOSE) down --volumes
