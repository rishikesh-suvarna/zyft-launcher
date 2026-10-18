# Usage: make install TV=192.168.1.50
#        make install TV=192.168.1.50 APK=~/Downloads/lite-launcher-1.0.0.apk   (no build needed)
TV ?=
APK ?=

.PHONY: build test check install debloat scan restore emulator

build:
	./gradlew assembleRelease

test:
	./gradlew testDebugUnitTest

# What CI runs.
check:
	shellcheck -x -e SC1091 scripts/*.sh
	./gradlew testDebugUnitTest lintRelease assembleRelease

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
