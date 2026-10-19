# Roadmap

Guiding rule: every feature must keep the launcher small, offline (no internet permission) and fast on a 1.5 GB RAM TV. Anything that needs a background service, network access or large bitmaps is out unless it is opt-in.

## Phase 1: Usable by anyone (done)

- [x] Per-brand debloat profiles with auto-detection (`profiles/`)
- [x] Android TV 7.0+ support (`minSdk` 24)
- [x] CI: tests, lint and shellcheck on every push; signed APKs on `v*` tags
- [x] Install from a prebuilt APK without a JDK or SDK
- [x] README, CONTRIBUTING, LICENSE

## Phase 2: Remote-first UX (done)

- [x] **Number keys:** keys 1-9 on the remote open favorites 1-9 directly
- [x] **Move mode:** reorder favorites by pressing OK, then left/right, then OK, instead of one step per menu
- [x] **Status header:** date next to the clock
- [x] **No-network warning** in the header (uses the install-time `ACCESS_NETWORK_STATE` permission only)

## Phase 2.5: Zyft UI v1 (done)

Implemented from `design/` (Claude Design export): tokens, ZyftTile focus treatment, side sheets,
move mode with cancel, row-snapped scrolling with a sticky header, first-run card, brand icon and banner.

- [x] Outline the "zyft" wordmark (Google Sans Medium) to vector paths for the TV banner
- [x] Google Sans as the UI font (Latin subset, 115 KB for two weights)

## Phase 3: Nice to have (opt-in)

- [ ] **Photo background:** pick an image, downscaled to screen size so it costs at most about 4 MB
- [ ] **App lock:** PIN on selected apps (kids)
- [x] **Backup / restore launcher settings** via adb, for moving to a new TV (`make backup`, `make restore-backup`)
- [x] **Auto-launch on boot:** open a chosen app at startup (app options > Open at startup)
- [ ] **Auto-switch to an input on boot** (for example a set-top box on HDMI)
- [ ] **Voice search tile,** when an assistant is installed
- [ ] More brand profiles: Sony, Philips, Xiaomi, Hisense (Android), Nvidia Shield, Chromecast with Google TV

## Not planned

- **"Continue watching" row:** reading the system Watch Next provider needs a privileged permission that sideloaded apps can't get.
- **Recommendations / content rows:** they are what makes stock launchers slow and ad-filled.
- **Fire TV:** the home screen can't be replaced without root.
