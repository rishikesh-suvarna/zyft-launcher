# Usage: make install TV=192.168.1.50
#        make install TV=192.168.1.50 APK=~/Downloads/lite-launcher-1.0.0.apk   (no build needed)
TV ?=
APK ?=

.PHONY: build test check ci-local install debloat scan restore emulator

build:
	./gradlew assembleRelease

test:
	./gradlew testDebugUnitTest

# What CI runs.
check:
	shellcheck -x -e SC1091 scripts/*.sh
	./gradlew testDebugUnitTest lintRelease assembleRelease

ACT_IMAGE := catthehacker/ubuntu:act-latest
ACT_PLATFORM := linux/amd64

# Runs the GitHub Actions build job in Docker via `act` (brew install act).
# amd64 because Google ships no Linux arm64 Android build tools; slow on Apple Silicon.
# The image only puts node on PATH for login shells, and act drops it once a step
# (setup-java) edits PATH, so pass the image's login PATH through explicitly.
ci-local:
	@image_path="$$(docker run --rm --platform $(ACT_PLATFORM) $(ACT_IMAGE) bash -lc 'echo $$PATH')"; \
	act pull_request -j build --container-architecture $(ACT_PLATFORM) \
		-P ubuntu-latest=$(ACT_IMAGE) --env PATH="$$image_path"

install:
	@if [ -z "$(APK)" ]; then ./gradlew assembleRelease; fi
	APK="$(APK)" scripts/install.sh $(TV)

debloat:
	scripts/debloat.sh $(TV)

scan:
	scripts/scan.sh $(TV)

restore:
	scripts/restore.sh $(TV)

# Boots the Google TV emulator (AVD "tcl_tv"), then: make install TV=emulator-5554
emulator:
	$(HOME)/Library/Android/sdk/emulator/emulator -avd tcl_tv -no-snapshot-save &
