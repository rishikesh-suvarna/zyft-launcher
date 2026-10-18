# Roadmap

Guiding rule: every feature must keep the launcher small, permission-free and fast on a 1.5 GB RAM TV. Anything that needs a background service, network access or large bitmaps is out unless it is opt-in.

## Phase 1: Usable by anyone (done)

- [x] Per-brand debloat profiles with auto-detection (`profiles/`)
- [x] Android TV 7.0+ support (`minSdk` 24)
- [x] CI: tests, lint and shellcheck on every push; signed APKs on `v*` tags
- [x] Install from a prebuilt APK without a JDK or SDK
- [x] README, CONTRIBUTING, LICENSE

## Phase 2: Remote-first UX

- [x] **Number keys:** keys 1-9 on the remote open favorites 1-9 directly
- [x] **Move mode:** reorder favorites by pressing OK, then left/right, then OK, instead of one step per menu
- [ ] **Status header:** date next to the clock, plus a no-network warning

## Phase 3: Nice to have (opt-in)

- [ ] **Photo background:** pick an image, downscaled to screen size so it costs at most about 4 MB
- [ ] **App lock:** PIN on selected apps (kids)
- [ ] **Backup / restore launcher settings** via adb, for moving to a new TV
- [ ] **Auto-launch on boot:** open a chosen app at startup (for example a set-top box input)
- [ ] **Voice search tile,** when an assistant is installed
- [ ] More brand profiles: Sony, Philips, Xiaomi, Hisense (Android), Nvidia Shield, Chromecast with Google TV

## Not planned

- **"Continue watching" row:** reading the system Watch Next provider needs a privileged permission that sideloaded apps can't get.
- **Recommendations / content rows:** they are what makes stock launchers slow and ad-filled.
- **Fire TV:** the home screen can't be replaced without root.
