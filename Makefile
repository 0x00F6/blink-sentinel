# Convenience commands for Android development and GitHub publishing.
.DEFAULT_GOAL := help
.ONESHELL:
SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c
.NOTPARALLEL:

# Set this default repository here, or override it with GIT_REPO on the command line.
git_repo ?= YOUR_GITHUB_USERNAME/blink-sentinel
GIT_MAIN_REPO ?= https://github.com/0x00F6/blink-sentinel.git
git_branch ?= $(GIT_BRANCH)
git_remote ?= $(if $(GIT_REMOTE),$(GIT_REMOTE),origin)
git_commit_message ?= $(if $(GIT_COMMIT_MESSAGE),$(GIT_COMMIT_MESSAGE),chore: publish Blink Sentinel project)
export PUBLISH_GIT_REPO = $(if $(GIT_REPO),$(GIT_REPO),$(git_repo))
export PUBLISH_GIT_BRANCH = $(git_branch)
export PUBLISH_GIT_REMOTE = $(git_remote)
export PUBLISH_COMMIT_MESSAGE = $(git_commit_message)
export PUBLISH_MAIN_REPO = $(GIT_MAIN_REPO)
export PUBLISH_VERSION = $(VERSION)
export PUBLISH_TEST_CLASS = $(TEST)

# Honor the NO_COLOR convention whenever the variable exists, including an empty value.
ifeq ($(origin NO_COLOR),undefined)
COLOR_INFO := \033[0;36m
COLOR_SUCCESS := \033[0;32m
COLOR_WARN := \033[0;33m
COLOR_ERROR := \033[0;31m
COLOR_RESET := \033[0m
else
COLOR_INFO :=
COLOR_SUCCESS :=
COLOR_WARN :=
COLOR_ERROR :=
COLOR_RESET :=
endif

.PHONY: help test test-one lint check debug build release-apk install clean tasks push release

help:
	@set -euo pipefail
	if [[ $${NO_COLOR+x} ]]; then
		escape=$$'\033'
		sed "s/$${escape}\\[[0-9;]*m//g" artwork/terminal-b.ansi.txt
	else
		cat artwork/terminal-b.ansi.txt
	fi
	printf '%b%s%b\n\n' '$(COLOR_SUCCESS)' 'Blink Sentinel' '$(COLOR_RESET)'
	printf '%b%s%b\n' '$(COLOR_INFO)' '🛠️  Android development' '$(COLOR_RESET)'
	printf '%b  🧪 %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make test' '$(COLOR_RESET)' 'Run JVM unit tests'
	printf '%b  🎯 %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make test-one TEST=package.TestName' '$(COLOR_RESET)' 'Run one JVM test class'
	printf '%b  🔎 %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make lint' '$(COLOR_RESET)' 'Run Android lint'
	printf '%b  ✅ %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make check' '$(COLOR_RESET)' 'Run tests and lint'
	printf '%b  📋 %-39s%b %s\n\n' '$(COLOR_SUCCESS)' 'make tasks' '$(COLOR_RESET)' 'List Gradle tasks'
	printf '%b%s%b\n' '$(COLOR_INFO)' '📦 Build and install' '$(COLOR_RESET)'
	printf '%b  🐞 %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make debug' '$(COLOR_RESET)' 'Build the debug APK'
	printf '%b  🏗️  %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make build' '$(COLOR_RESET)' 'Run tests, lint, and build the debug APK'
	printf '%b  📦 %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make release-apk' '$(COLOR_RESET)' 'Build a local release APK without publishing'
	printf '%b  📲 %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make install' '$(COLOR_RESET)' 'Install the debug APK on a connected device'
	printf '%b  🧹 %-39s%b %s\n\n' '$(COLOR_SUCCESS)' 'make clean' '$(COLOR_RESET)' 'Remove Gradle build outputs'
	printf '%b%s%b\n' '$(COLOR_INFO)' '🚀 GitHub publishing' '$(COLOR_RESET)'
	printf '%b  ⬆️  %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make push' '$(COLOR_RESET)' 'Commit changes and push the current branch'
	printf '%b  🏷️  %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make release VERSION=0.1.0' '$(COLOR_RESET)' 'Build and publish a tagged APK release'
	printf '%b  ❔ %-39s%b %s\n\n' '$(COLOR_SUCCESS)' 'make help' '$(COLOR_RESET)' 'Show this help'
	printf '%b%s%b\n' '$(COLOR_WARN)' 'Example: make push GIT_REPO=OWNER/REPOSITORY GIT_BRANCH=main GIT_REMOTE=origin' '$(COLOR_RESET)'
	printf '%b%s%b\n' '$(COLOR_WARN)' 'For a release, provide GIT_MAIN_REPO=OWNER/REPOSITORY; optional: GIT_COMMIT_MESSAGE="message".' '$(COLOR_RESET)'
	printf '%b%s%b\n' '$(COLOR_WARN)' 'Create an empty GitHub repository and authenticate with gh auth login first.' '$(COLOR_RESET)'

test:
	@set -euo pipefail
	if ! ./gradlew testDebugUnitTest; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ JVM unit tests failed.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 Fix the failing test or setup issue above, then retry with: ./gradlew testDebugUnitTest --stacktrace' '$(COLOR_RESET)' >&2
		exit 1
	fi

test-one:
	@set -euo pipefail
	error() {
		printf '%b%s%b\n' '$(COLOR_ERROR)' "❌ ERROR: $$1" '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' "💡 SOLUTION: $$2" '$(COLOR_RESET)' >&2
	}
	if [[ -z "$$PUBLISH_TEST_CLASS" ]]; then
		error 'No test class was provided.' 'Pass TEST=fully.qualified.TestClass, for example: make test-one TEST=dev.homesentinel.PresenceMachineTest.'
		exit 2
	fi
	if ! ./gradlew testDebugUnitTest --tests "$$PUBLISH_TEST_CLASS"; then
		error "Test class '$$PUBLISH_TEST_CLASS' failed or could not be run." 'Check the first Gradle error above, confirm the class name, and rerun with ./gradlew testDebugUnitTest --tests CLASS --stacktrace.'
		exit 1
	fi

lint:
	@set -euo pipefail
	if ! ./gradlew lintDebug; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ Android lint failed.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 Review the lint report under app/build/reports/lint-results-debug.html, fix the reported issues, then rerun make lint.' '$(COLOR_RESET)' >&2
		exit 1
	fi

check: test lint

debug:
	@set -euo pipefail
	if ! ./gradlew assembleDebug; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ Debug APK build failed.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 Check the JDK and Android SDK configuration, resolve the first Gradle error above, then rerun make debug.' '$(COLOR_RESET)' >&2
		exit 1
	fi

build: check debug

release-apk:
	@set -euo pipefail
	if ! ./gradlew assembleRelease; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ Local release APK build failed.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 Check the JDK, SDK, and release build output above, then retry with make release-apk.' '$(COLOR_RESET)' >&2
		exit 1
	fi

install:
	@set -euo pipefail
	if ! ./gradlew installDebug; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ Debug APK installation failed.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 Connect and authorize an Android device, enable USB debugging, confirm adb devices lists it, then rerun make install.' '$(COLOR_RESET)' >&2
		exit 1
	fi

clean:
	@set -euo pipefail
	if ! ./gradlew clean; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ Gradle could not clean the project.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 Check file permissions and the JDK configuration, then rerun make clean.' '$(COLOR_RESET)' >&2
		exit 1
	fi

tasks:
	@set -euo pipefail
	if ! ./gradlew tasks; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ Gradle could not list the project tasks.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 Configure a compatible JDK and Android SDK, then rerun make tasks.' '$(COLOR_RESET)' >&2
		exit 1
	fi

push:
	@set -euo pipefail
	error() {
		printf '%b%s%b\n' '$(COLOR_ERROR)' "❌ ERROR: $$1" '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' "💡 SOLUTION: $$2" '$(COLOR_RESET)' >&2
	}
	repo="$$PUBLISH_GIT_REPO"
	repo="$${repo#https://github.com/}"
	repo="$${repo#http://github.com/}"
	repo="$${repo%.git}"
	if [[ ! "$$repo" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$$ || "$$repo" == YOUR_GITHUB_USERNAME/* ]]; then
		error "GitHub repository '$$PUBLISH_GIT_REPO' is not a valid OWNER/REPOSITORY or GitHub URL." 'Pass GIT_REPO=OWNER/REPOSITORY to make push, or set git_repo near the top of the Makefile.'
		exit 2
	fi
	for required in git gh; do
		if ! command -v "$$required" >/dev/null 2>&1; then
			error "Required command '$$required' is not installed or is missing from PATH." "Install '$$required' and verify it is available by running '$$required --version'."
			exit 1
		fi
	done
	if ! gh auth status >/dev/null 2>&1; then
		error 'GitHub CLI authentication is unavailable or expired.' 'Run gh auth login, complete the browser sign-in, then retry the Make target.'
		exit 1
	fi
	if ! gh repo view "$$repo" >/dev/null 2>&1; then
		error "GitHub repository '$$repo' was not found or your account cannot access it." 'Create the repository on GitHub, verify the owner/name, and confirm your authenticated account has write access.'
		exit 1
	fi
	if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
		if ! git init --initial-branch=main; then
			error 'Could not initialize a local Git repository in this directory.' 'Check directory permissions and run git init --initial-branch=main manually, then retry.'
			exit 1
		fi
		printf '%b%s%b\n' '$(COLOR_INFO)' 'ℹ️  Initialized the local Git repository.' '$(COLOR_RESET)'
	fi
	if git ls-files --error-unmatch local.properties >/dev/null 2>&1; then
		error 'local.properties is tracked and may contain a local Android SDK path.' 'Run git rm --cached local.properties, confirm it remains ignored by .gitignore, then retry.'
		exit 1
	fi
	expected_url="https://github.com/$$repo.git"
	if git remote get-url "$$PUBLISH_GIT_REMOTE" >/dev/null 2>&1; then
		current_url="$$(git remote get-url "$$PUBLISH_GIT_REMOTE")"
		if [[ "$$current_url" != "$$expected_url" && "$$current_url" != "git@github.com:$$repo.git" ]]; then
			error "Git remote '$$PUBLISH_GIT_REMOTE' points to '$$current_url', which does not match '$$expected_url'." "Set the correct GIT_REMOTE or run git remote set-url $$PUBLISH_GIT_REMOTE $$expected_url, then retry."
			exit 1
		fi
	else
		if ! git remote add "$$PUBLISH_GIT_REMOTE" "$$expected_url"; then
			error "Could not add Git remote '$$PUBLISH_GIT_REMOTE'." 'Inspect git remote -v, remove or rename a conflicting remote, and retry.'
			exit 1
		fi
	fi
	current_branch="$$(git branch --show-current)"
	branch="$$current_branch"
	if [[ -n "$$PUBLISH_GIT_BRANCH" ]]; then
		branch="$$PUBLISH_GIT_BRANCH"
		if [[ "$$branch" != "$$current_branch" ]]; then
			error "Requested branch '$$branch' does not match the checked-out branch '$$current_branch'." "Run git switch $$branch, or omit GIT_BRANCH to push the current branch."
			exit 1
		fi
	fi
	if [[ -z "$$branch" ]]; then
		error 'Git is in detached HEAD state, so there is no current branch to publish.' 'Create or switch to a branch with git switch -c main, then retry.'
		exit 1
	fi
	if ! git add --all; then
		error 'Git could not stage the project files.' 'Review the Git error above, check file permissions and ignore rules, then retry git add --all.'
		exit 1
	fi
	if ! git diff --cached --quiet; then
		if ! git var GIT_AUTHOR_IDENT >/dev/null 2>&1; then
			error 'Git author identity is not configured, so the changes cannot be committed.' 'Run git config --global user.name "Your Name" and git config --global user.email "you@example.com", then retry.'
			exit 1
		fi
		if ! git commit -m "$$PUBLISH_COMMIT_MESSAGE"; then
			error 'Git could not create the publishing commit.' 'Review the commit hook/error above, fix the reported issue, then retry make push.'
			exit 1
		fi
	fi
	if ! git push --set-upstream "$$PUBLISH_GIT_REMOTE" "$$branch"; then
		error "Could not push branch '$$branch' to '$$expected_url'." 'Check network access, repository write permission and branch protection. If the remote has independent commits, review them and integrate with git pull --rebase before retrying.'
		exit 1
	fi
	printf '%b%s%b\n' '$(COLOR_SUCCESS)' "✅ Pushed $$branch to github.com/$$repo." '$(COLOR_RESET)'

release:
	@set -euo pipefail
	version="$$PUBLISH_VERSION"
	main_repo="$$PUBLISH_MAIN_REPO"
	repo="$$main_repo"
	repo="$${repo#https://github.com/}"
	repo="$${repo#http://github.com/}"
	repo="$${repo%.git}"
	if [[ -z "$$main_repo" ]]; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ ERROR: GIT_MAIN_REPO is empty; the release destination is unknown.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Set GIT_MAIN_REPO=https://github.com/0x00F6/blink-sentinel.git or pass it on the command line.' '$(COLOR_RESET)' >&2
		exit 2
	fi
	if [[ ! "$$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$$ ]]; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' "❌ ERROR: VERSION '$$version' is not in MAJOR.MINOR.PATCH format." '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Use a version such as make release VERSION=0.1.0 GIT_MAIN_REPO=https://github.com/0x00F6/blink-sentinel.git.' '$(COLOR_RESET)' >&2
		exit 2
	fi
	if [[ ! "$$repo" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$$ ]]; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' "❌ ERROR: GIT_MAIN_REPO '$$main_repo' is not a supported GitHub repository URL or OWNER/REPOSITORY." '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Use GIT_MAIN_REPO=https://github.com/0x00F6/blink-sentinel.git.' '$(COLOR_RESET)' >&2
		exit 2
	fi
	$(MAKE) --no-print-directory push GIT_REPO="$$main_repo" git_branch="$$PUBLISH_GIT_BRANCH" git_remote="$$PUBLISH_GIT_REMOTE" git_commit_message="$$PUBLISH_COMMIT_MESSAGE"
	tag="v$$version"
	if git tag --list "$$tag" | grep -Fxq "$$tag" || gh release view "$$tag" --repo "$$repo" >/dev/null 2>&1; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' "❌ ERROR: Release tag '$$tag' already exists locally or on GitHub." '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Choose the next unused VERSION and check existing tags with git tag --list and GitHub Releases.' '$(COLOR_RESET)' >&2
		exit 1
	fi
	if ! command -v java >/dev/null 2>&1; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ ERROR: Java is not available, so Gradle cannot build the release APK.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Install a compatible full JDK (JDK 21 for this project), set JAVA_HOME, and ensure java is on PATH.' '$(COLOR_RESET)' >&2
		exit 1
	fi
	printf '%b%s%b\n' '$(COLOR_INFO)' 'ℹ️  Building the Android release APK...' '$(COLOR_RESET)'
	if ! ./gradlew assembleRelease; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ ERROR: Gradle failed to assemble the release APK.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Resolve the first Gradle error above; verify JDK 21, Android SDK platform 35, and Build-Tools 35.0.0, then run make release-apk.' '$(COLOR_RESET)' >&2
		exit 1
	fi
	apk="app/build/outputs/apk/release/app-release.apk"
	if [[ ! -f "$$apk" ]]; then
		apk="app/build/outputs/apk/release/app-release-unsigned.apk"
	fi
	if [[ ! -f "$$apk" ]]; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' "❌ ERROR: No release APK was found in app/build/outputs/apk/release/." '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Inspect the Gradle output above and run make release-apk to diagnose the local APK build.' '$(COLOR_RESET)' >&2
		exit 1
	fi
	if [[ "$$apk" == *unsigned.apk ]]; then
		printf '%b%s%b\n' '$(COLOR_WARN)' '⚠️  This release APK is unsigned; configure local release signing before distribution.' '$(COLOR_RESET)' >&2
	fi
	if ! gh release create "$$tag" "$$apk#Blink-Sentinel-$$version.apk" \
		--repo "$$repo" \
		--target "$$(git rev-parse HEAD)" \
		--title "Blink Sentinel $$version" \
		--generate-notes; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' "❌ ERROR: GitHub CLI could not create release '$$tag' or upload '$$apk'." '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Check gh auth status, repository release permissions, network access, and whether the tag already exists; then retry make release with the same VERSION.' '$(COLOR_RESET)' >&2
		exit 1
	fi
	printf '%b%s%b\n' '$(COLOR_SUCCESS)' "✅ Created the APK release: https://github.com/$$repo/releases/tag/$$tag" '$(COLOR_RESET)'
