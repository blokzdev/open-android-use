PROJECT ?=
SLUG ?=

.PHONY: init build app test smoke android-build android-npm android-test android-smoke companion-build check-docs check-repo ci release-package npm-build npm-publish new-history new-plan

init:
	@if [ -z "$(PROJECT)" ]; then echo "用法: make init PROJECT=项目名"; exit 1; fi
	./scripts/init-project.sh "$(PROJECT)"

build:
	swift build

app:
	./scripts/build-open-computer-use-app.sh debug

test:
	swift test

smoke:
	./scripts/run-tool-smoke-tests.sh

android-build:
	./scripts/build-open-android-use.sh

android-npm:
	node ./scripts/npm/build-android-package.mjs

android-test:
	cd apps/OpenAndroidUse && go test ./...

companion-build:
	./scripts/build-open-android-use-companion.sh

android-smoke:
	./scripts/run-android-smoke-tests.sh

check-docs:
	./scripts/check-docs.sh

check-repo:
	./scripts/check-docs.sh
	./scripts/check-repo-hygiene.sh

ci:
	./scripts/ci.sh

release-package:
	./scripts/release-package.sh

npm-build:
	node ./scripts/npm/build-packages.mjs

npm-publish:
	node ./scripts/npm/publish-packages.mjs

new-history:
	@if [ -z "$(SLUG)" ]; then echo "用法: make new-history SLUG=变更名"; exit 1; fi
	./scripts/new-history.sh "$(SLUG)"

new-plan:
	@if [ -z "$(SLUG)" ]; then echo "用法: make new-plan SLUG=计划名"; exit 1; fi
	./scripts/new-exec-plan.sh "$(SLUG)"
