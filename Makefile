# FusionLOS. `make up` starts Temporal and MongoDB, `make demo` starts api, worker and web in tmux.

# Local settings (see .env.example). Every variable in .env is passed to the processes started here.
-include .env
export

# Optional: point JAVA_HOME and MAVEN_HOME at local installs. Otherwise java and mvn come from PATH.
ifneq ($(JAVA_HOME),)
export PATH := $(JAVA_HOME)/bin:$(PATH)
endif
ifneq ($(MAVEN_HOME),)
export PATH := $(MAVEN_HOME)/bin:$(PATH)
endif

DURATIONS ?= demo-durations
JAR := backend/target/fusion-los-0.1.0.jar
JAVA_OPTS ?= -Xmx384m
BACKEND_SOURCES := backend/pom.xml $(shell find backend/src/main -type f 2>/dev/null)

.PHONY: up down build test test-replay api worker kill-worker deploy-v2 deploy-v1 restart-worker export-history web demo stop seed reset

up:
	docker compose up -d --wait

down:
	docker compose down

build: $(JAR)

$(JAR): $(BACKEND_SOURCES)
	cd backend && mvn -q -B package -DskipTests

test:
	cd backend && mvn -B test

# Replays the saved histories in histories/ against the current code.
test-replay:
	cd backend && mvn -B test -Dtest=ReplayTest

# Each process runs from its own copy of the jar, so a rebuild (make deploy-v2) never pulls classes
# out from under a running JVM.
api: $(JAR)
	cp $(JAR) backend/target/run-api.jar
	java $(JAVA_OPTS) -jar backend/target/run-api.jar --spring.profiles.active=api,$(DURATIONS)

worker: $(JAR)
	cp $(JAR) backend/target/run-worker.jar
	java $(JAVA_OPTS) -jar backend/target/run-worker.jar --spring.profiles.active=worker,$(DURATIONS)

kill-worker:
	pkill -9 -f 'profiles.active=[w]orker' || true

# Restart the worker in tmux session fusion-los-worker as build v2 (PEP check) or back to v1.
deploy-v2:
	scripts/deploy-v2.sh v2

deploy-v1:
	scripts/deploy-v2.sh v1

# Bring the worker back after make kill-worker. BUILD=v2 after a deploy.
BUILD ?= v1
restart-worker:
	scripts/deploy-v2.sh $(BUILD)

# Save an application's histories for ReplayTest: make export-history ID=APP-2026-000123 LABEL=v1
export-history:
	scripts/export-history.sh $(ID) $(LABEL)

web:
	cd frontend && npm install --no-fund --no-audit && npm run dev

# One tmux session per process: fusion-los-api, fusion-los-worker, fusion-los-web. Attach with tmux attach -t <name>.
demo: build
	@for p in api worker web; do \
	  tmux has-session -t fusion-los-$$p 2>/dev/null && echo "fusion-los-$$p already running" || \
	  { tmux new-session -d -s fusion-los-$$p -c "$(CURDIR)" "make $$p; exec $${SHELL:-sh}" && echo "started fusion-los-$$p"; }; \
	done

stop:
	-pkill -TERM -f 'profiles.active=[a]pi'
	-pkill -TERM -f 'profiles.active=[w]orker'
	-tmux kill-session -t fusion-los-api
	-tmux kill-session -t fusion-los-worker
	-tmux kill-session -t fusion-los-web

# Needs the api and worker running. Seed starts 30 real workflows and drives them with signals.
API_URL ?= http://127.0.0.1:8080

# Seed and reset are operations actions: sign in as ops.dave first (pass LOS_LOGIN_PASSWORD if one is set).
COOKIES := backend/target/ops-session.txt
OPS_LOGIN = curl -fsS -c $(COOKIES) -o /dev/null -H 'Content-Type: application/json' \
	-d '{"userId":"ops.dave","password":"$(LOS_LOGIN_PASSWORD)"}' '$(API_URL)/api/auth/login'

seed:
	@$(OPS_LOGIN)
	curl -fsS -b $(COOKIES) -X POST '$(API_URL)/api/admin/seed?count=30' && echo

reset:
	@$(OPS_LOGIN)
	curl -fsS -b $(COOKIES) -X POST '$(API_URL)/api/admin/reset' && echo
