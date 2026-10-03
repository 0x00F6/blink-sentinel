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
export PUBLISH_GIT_REPO = $(if $(GIT_REPO),$(GIT_REPO),$(if $(GIT_MAIN_REPO),$(GIT_MAIN_REPO),$(git_repo)))
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

.PHONY: help fmt test test-one lint check debug build release-apk install clean tasks push release

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
	printf '%b  🧹 %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make fmt' '$(COLOR_RESET)' 'Format Kotlin and Kotlin DSL source files'
	printf '%b  🧪 %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make test' '$(COLOR_RESET)' 'Run JVM unit tests'
	printf '%b  🎯 %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make test-one TEST=package.TestName' '$(COLOR_RESET)' 'Run one JVM test class'
	printf '%b  🔎 %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make lint' '$(COLOR_RESET)' 'Run Android lint'
	printf '%b  ✅ %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make check' '$(COLOR_RESET)' 'Format code, run tests, and lint'
	printf '%b  📋 %-39s%b %s\n\n' '$(COLOR_SUCCESS)' 'make tasks' '$(COLOR_RESET)' 'List Gradle tasks'
	printf '%b%s%b\n' '$(COLOR_INFO)' '📦 Build and install' '$(COLOR_RESET)'
	printf '%b  🐞 %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make debug' '$(COLOR_RESET)' 'Build the debug APK'
	printf '%b  🏗️  %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make build' '$(COLOR_RESET)' 'Run tests, lint, and build the debug APK'
	printf '%b  📦 %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make release-apk' '$(COLOR_RESET)' 'Build a local release APK without publishing'
	printf '%b  📲 %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make install' '$(COLOR_RESET)' 'Install the debug APK on a connected device'
	printf '%b  🧹 %-39s%b %s\n\n' '$(COLOR_SUCCESS)' 'make clean' '$(COLOR_RESET)' 'Remove Gradle build outputs'
	printf '%b%s%b\n' '$(COLOR_INFO)' '🚀 GitHub publishing' '$(COLOR_RESET)'
	printf '%b  ⬆️  %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make push' '$(COLOR_RESET)' 'Commit changes and push the current branch'
	printf '%b  🏷️  %-39s%b %s\n' '$(COLOR_SUCCESS)' 'make release VERSION=0.1.0 JKS_FILE=/path/key.jks' '$(COLOR_RESET)' 'Format, test, sign, and publish a release APK'
	printf '%b  ❔ %-39s%b %s\n\n' '$(COLOR_SUCCESS)' 'make help' '$(COLOR_RESET)' 'Show this help'
	printf '%b%s%b\n' '$(COLOR_INFO)' '💡 Examples' '$(COLOR_RESET)'
	printf '  ⬆️  Push the current branch to the default repository:\n    make push\n\n'
	printf '  📂 Push to another repository (main must be checked out):\n    make push %bGIT_REPO%b=OWNER/REPOSITORY %bGIT_BRANCH%b=main %bGIT_REMOTE%b=origin\n\n' '$(COLOR_WARN)' '$(COLOR_RESET)' '$(COLOR_WARN)' '$(COLOR_RESET)' '$(COLOR_WARN)' '$(COLOR_RESET)'
	printf '  🏷️  Sign and publish a release:\n    make release %bVERSION%b=0.1.0 %bJKS_FILE%b=/secure/path/release.jks %bJKS_ALIAS%b=release\n\n' '$(COLOR_WARN)' '$(COLOR_RESET)' '$(COLOR_WARN)' '$(COLOR_RESET)' '$(COLOR_WARN)' '$(COLOR_RESET)'
	printf '  🧪 Run one test class:\n    make test-one %bTEST%b=dev.homesentinel.PresenceMachineTest\n\n' '$(COLOR_WARN)' '$(COLOR_RESET)'
	printf '%b%s%b\n' '$(COLOR_INFO)' '⚙️  Publishing variables' '$(COLOR_RESET)'
	printf '  %b%-20s%b %s\n' '$(COLOR_WARN)' 'GIT_MAIN_REPO' '$(COLOR_RESET)' 'Default push/release repository: https://github.com/0x00F6/blink-sentinel.git'
	printf '  %b%-20s%b %s\n' '$(COLOR_WARN)' 'GIT_REPO' '$(COLOR_RESET)' 'Override the repository for push; release uses GIT_MAIN_REPO.'
	printf '  %b%-20s%b %s\n' '$(COLOR_WARN)' 'GIT_BRANCH' '$(COLOR_RESET)' 'Optional; must match the checked-out branch.'
	printf '  %b%-20s%b %s\n' '$(COLOR_WARN)' 'GIT_REMOTE' '$(COLOR_RESET)' 'Remote name (default: origin); URL must match the destination.'
	printf '  %b%-20s%b %s\n' '$(COLOR_WARN)' 'GIT_COMMIT_MESSAGE' '$(COLOR_RESET)' 'Optional automatic commit message.'
	printf '  %b%-20s%b %s\n' '$(COLOR_WARN)' 'VERSION' '$(COLOR_RESET)' 'Required for release; use MAJOR.MINOR.PATCH.'
	printf '  %b%-20s%b %s\n' '$(COLOR_WARN)' 'JKS_FILE' '$(COLOR_RESET)' 'Required release keystore path; may be exported in your shell.'
	printf '  %b%-20s%b %s\n\n' '$(COLOR_WARN)' 'JKS_ALIAS' '$(COLOR_RESET)' 'Optional; otherwise prompted. Passwords are always prompted securely.'
	printf '%b%s%b\n' '$(COLOR_INFO)' '🔧 Before publishing' '$(COLOR_RESET)'
	printf '  1. Create the GitHub repository and run gh auth login.\n'
	printf '  2. Check the remote URL and Git identity (see README.md).\n'
	printf '  3. For release, create a signing keystore (see README.md).\n\n'
	printf '  🎨 Disable colors: %bNO_COLOR%b= make help\n' '$(COLOR_WARN)' '$(COLOR_RESET)'

test:
	@set -euo pipefail
	if ! ./gradlew testDebugUnitTest; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ JVM unit tests failed.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 Fix the failing test or setup issue above, then retry with: ./gradlew testDebugUnitTest --stacktrace' '$(COLOR_RESET)' >&2
		exit 1
	fi

fmt:
	@set -euo pipefail
	if ! ./gradlew ktlintFormat; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ Kotlin formatting failed.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 Check Gradle/plugin resolution and formatter output above, then retry make fmt.' '$(COLOR_RESET)' >&2
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

check: fmt test lint

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
		author_name="$$(git config user.name || true)"
		author_email="$$(git config user.email || true)"
		if [[ "$$author_name" != "0x00F6" || "$$author_email" != "0x951475369@protonmail.com" ]]; then
			error "Git identity must be 0x00F6 <0x951475369@protonmail.com>; configured identity is '$$author_name <$$author_email>'." 'Set the repository identity with git config user.name "0x00F6" and git config user.email "0x951475369@protonmail.com", then retry.'
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
	if [[ -z "$${JKS_FILE:-}" ]]; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ ERROR: JKS_FILE is required to sign an installable GitHub release APK.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Run make release VERSION=0.1.0 JKS_FILE=/secure/path/blink-sentinel-release.jks; the keystore password will be prompted securely.' '$(COLOR_RESET)' >&2
		exit 2
	fi
	jks_file="$$JKS_FILE"
	if [[ ! -f "$$jks_file" ]]; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' "❌ ERROR: Keystore file '$$jks_file' does not exist or is not a regular file." '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Check JKS_FILE and pass the path to your existing .jks or .keystore file.' '$(COLOR_RESET)' >&2
		exit 2
	fi
	keytool_bin="$$(command -v keytool || true)"
	if [[ -z "$$keytool_bin" && -n "$${JAVA_HOME:-}" && -x "$$JAVA_HOME/bin/keytool" ]]; then
		keytool_bin="$$JAVA_HOME/bin/keytool"
	fi
	if [[ -z "$$keytool_bin" ]]; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ ERROR: keytool is unavailable; the JDK is required to validate the signing keystore.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Install/configure the project JDK and ensure its bin directory is on PATH.' '$(COLOR_RESET)' >&2
		exit 1
	fi
	printf '%b%s%b\n' '$(COLOR_INFO)' '🧹 Running Kotlin formatting, all JVM unit tests, and Android lint before release...' '$(COLOR_RESET)'
	if ! $(MAKE) --no-print-directory check; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ ERROR: Release preflight failed during formatting, tests, or lint.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Fix the reported issue, run make check successfully, then retry make release.' '$(COLOR_RESET)' >&2
		exit 1
	fi
	if ! IFS= read -r -s -p 'Keystore password: ' BLINK_SENTINEL_JKS_STORE_PASSWORD < /dev/tty; then
		printf '\n%b%s%b\n' '$(COLOR_ERROR)' '❌ ERROR: Could not read the keystore password from the terminal.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Run make release from an interactive terminal.' '$(COLOR_RESET)' >&2
		exit 1
	fi
	printf '\n'
	export BLINK_SENTINEL_JKS_STORE_PASSWORD
	key_alias="$${JKS_ALIAS:-}"
	if [[ -z "$$key_alias" ]]; then
		if ! IFS= read -r -p 'Keystore key alias: ' key_alias < /dev/tty || [[ -z "$$key_alias" ]]; then
			printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ ERROR: No signing key alias was provided.' '$(COLOR_RESET)' >&2
			printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Set JKS_ALIAS=your-key-alias or enter the alias when prompted.' '$(COLOR_RESET)' >&2
			exit 1
		fi
	fi
	export BLINK_SENTINEL_JKS_KEY_ALIAS="$$key_alias"
	if ! "$$keytool_bin" -list -keystore "$$jks_file" -storepass:env BLINK_SENTINEL_JKS_STORE_PASSWORD -alias "$$key_alias" >/dev/null 2>&1; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' "❌ ERROR: The keystore password is incorrect or alias '$$key_alias' is missing." '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Check the keystore password and key alias, then retry.' '$(COLOR_RESET)' >&2
		exit 1
	fi
	if ! IFS= read -r -s -p 'Private key password (press Enter to reuse keystore password): ' BLINK_SENTINEL_JKS_KEY_PASSWORD < /dev/tty; then
		printf '\n%b%s%b\n' '$(COLOR_ERROR)' '❌ ERROR: Could not read the private key password from the terminal.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Run make release from an interactive terminal.' '$(COLOR_RESET)' >&2
		exit 1
	fi
	printf '\n'
	if [[ -z "$$BLINK_SENTINEL_JKS_KEY_PASSWORD" ]]; then
		BLINK_SENTINEL_JKS_KEY_PASSWORD="$$BLINK_SENTINEL_JKS_STORE_PASSWORD"
	fi
	export BLINK_SENTINEL_JKS_KEY_PASSWORD
	export BLINK_SENTINEL_JKS_FILE="$$jks_file"
	clear_signing_secrets() {
		unset BLINK_SENTINEL_JKS_STORE_PASSWORD BLINK_SENTINEL_JKS_KEY_PASSWORD BLINK_SENTINEL_JKS_KEY_ALIAS BLINK_SENTINEL_JKS_FILE
	}
	trap clear_signing_secrets EXIT
	gradle_file="app/build.gradle.kts"
	version_name_count="$$(grep -Ec '^[[:space:]]*versionName = "[^"]+"[[:space:]]*$$' "$$gradle_file" || true)"
	if [[ "$$version_name_count" != 1 ]]; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' "❌ ERROR: Expected exactly one literal versionName in $$gradle_file; found $$version_name_count." '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Restore one line such as versionName = "1.0.0" in defaultConfig, then retry the release.' '$(COLOR_RESET)' >&2
		exit 1
	fi
	current_version="$$(sed -nE 's/^[[:space:]]*versionName = "([^"]+)"[[:space:]]*$$/\1/p' "$$gradle_file")"
	if [[ "$$current_version" != "$$version" ]]; then
		if ! sed -i.bak -E "s|^([[:space:]]*versionName = \")[^\"]+(\"[[:space:]]*)$$|\\1$$version\\2|" "$$gradle_file"; then
			printf '%b%s%b\n' '$(COLOR_ERROR)' "❌ ERROR: Could not update the versionName in $$gradle_file." '$(COLOR_RESET)' >&2
			printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Check write permissions for app/build.gradle.kts and retry make release.' '$(COLOR_RESET)' >&2
			exit 1
		fi
		rm -f "$$gradle_file.bak"
		printf '%b%s%b\n' '$(COLOR_INFO)' "📝 Updated Android versionName in $$gradle_file: $$current_version → $$version." '$(COLOR_RESET)'
	else
		printf '%b%s%b\n' '$(COLOR_INFO)' "ℹ️  Android versionName in $$gradle_file is already $$version; no file change needed." '$(COLOR_RESET)'
	fi
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
	if ! ./gradlew --no-daemon assembleRelease; then
		clear_signing_secrets
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ ERROR: Gradle failed to assemble the release APK.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Check the JDK/SDK setup, keystore access, alias and passwords; then retry with the same JKS_FILE.' '$(COLOR_RESET)' >&2
		exit 1
	fi
	clear_signing_secrets
	trap - EXIT
	apk="app/build/outputs/apk/release/app-release.apk"
	if [[ ! -f "$$apk" ]]; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ ERROR: Gradle did not produce the signed release APK app/build/outputs/apk/release/app-release.apk.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Confirm the release signing configuration in app/build.gradle.kts and inspect the Gradle output above.' '$(COLOR_RESET)' >&2
		exit 1
	fi
	apksigner="$(command -v apksigner || true)"
	if [[ -z "$$apksigner" ]]; then
		sdk_path="$${ANDROID_HOME:-$${ANDROID_SDK_ROOT:-}}"
		if [[ -z "$$sdk_path" && -f local.properties ]]; then
			sdk_path="$$(sed -nE 's/^sdk\.dir=(.*)$$/\1/p' local.properties | head -n 1)"
		fi
		if [[ -d "$$sdk_path/build-tools" ]]; then
			for candidate in "$$sdk_path"/build-tools/*/apksigner; do
				if [[ -x "$$candidate" ]]; then apksigner="$$candidate"; fi
			done
		fi
	fi
	if [[ -z "$$apksigner" ]] || ! "$$apksigner" verify --verbose "$$apk" >/dev/null 2>&1; then
		printf '%b%s%b\n' '$(COLOR_ERROR)' '❌ ERROR: The release APK failed signature verification.' '$(COLOR_RESET)' >&2
		printf '%b%s%b\n' '$(COLOR_ERROR)' '💡 SOLUTION: Check JKS_FILE, alias, and both passwords; install Android SDK Build-Tools so apksigner is available, then retry.' '$(COLOR_RESET)' >&2
		exit 1
	fi
	$(MAKE) --no-print-directory push GIT_REPO="$$main_repo" git_branch="$$PUBLISH_GIT_BRANCH" git_remote="$$PUBLISH_GIT_REMOTE" git_commit_message="$$PUBLISH_COMMIT_MESSAGE"
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
