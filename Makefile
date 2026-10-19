# Usage: make install TV=192.168.1.50
#        make install TV=192.168.1.50 APK=~/Downloads/zyft-launcher-1.0.0.apk   (no build needed)
TV ?=
APK ?=

.PHONY: build test check ci-local install debloat scan restore backup restore-backup emulator fonts

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

# Rebuilds the Google Sans subsets and the wordmark banner (pip install fonttools).
# SRC is GoogleSans[GRAD,opsz,wght].ttf and LICENSE its OFL.txt, from google/fonts ofl/googlesans.
fonts:
	python3 scripts/fonts/build_fonts.py "$(SRC)" "$(LICENSE)"

install:
	@if [ -z "$(APK)" ]; then ./gradlew assembleRelease; fi
	APK="$(APK)" scripts/install.sh $(TV)

debloat:
	scripts/debloat.sh $(TV)

scan:
	scripts/scan.sh $(TV)

restore:
	scripts/restore.sh $(TV)

# Launcher settings to a file and back, e.g. for a new TV: make restore-backup TV=... FILE=backups/....zyft
FILE ?=
backup:
	scripts/settings.sh backup $(TV) $(FILE)

restore-backup:
	scripts/settings.sh restore $(TV) "$(FILE)"

# Boots the Google TV emulator (AVD "tcl_tv"), then: make install TV=emulator-5554
emulator:
	$(HOME)/Library/Android/sdk/emulator/emulator -avd tcl_tv -no-snapshot-save &
