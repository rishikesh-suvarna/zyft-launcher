# Usage: make install TV=192.168.1.50
TV ?=

.PHONY: build test install debloat scan restore emulator

build:
	./gradlew assembleRelease

test:
	./gradlew testDebugUnitTest

install: build
	scripts/install.sh $(TV)

debloat:
	scripts/debloat.sh $(TV)

scan:
	scripts/scan.sh $(TV)

restore:
	scripts/restore.sh $(TV)

# Boots the Google TV emulator (AVD "tcl_tv"), then: make install TV=emulator-5554
emulator:
	$(HOME)/Library/Android/sdk/emulator/emulator -avd tcl_tv -no-snapshot-save &
