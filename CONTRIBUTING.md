# Contributing

## Adding a profile for your TV brand

Profiles live in `profiles/<brand>.txt`, where `<brand>` is the lowercased value of `ro.product.manufacturer` (shown by `make scan`).

1. Run `make scan TV=<ip>` and look at "System packages not from Google/AOSP".
2. Create `profiles/<brand>.txt` with one package per line and a short description after it:
   ```
   com.vendor.adsservice      Promoted content on the home screen
   ```
3. Only list packages you have **disabled on a real TV** and confirmed that these still work afterwards:
   - booting and the first-run screen
   - the remote (including Bluetooth / voice remotes)
   - HDMI inputs, and the tuner if the TV has one
   - Settings, Wi-Fi, picture and sound settings
   - your streaming apps
4. Add a `NEVER disable` comment listing vendor packages you found to be essential. See [`profiles/tcl.txt`](profiles/tcl.txt).
5. Mention the exact model and Android version you tested on, in the file header and in the PR.

If the stock launcher has a package name we don't know yet, add it to `STOCK_LAUNCHERS` in `scripts/common.sh`.

## Code

- Kotlin, no dependencies beyond the stdlib. Every new library or permission needs a strong reason.
- Put logic in pure functions (see `AppArranger.kt`, `Sorting.kt`) and cover them with JVM unit tests.
- Everything must work with a D-pad: no touch-only or pointer-only UI.
- Check cold start before and after a UI change:
  ```sh
  adb logcat -c && adb shell am force-stop dev.rishikeshsuvarna.zyft && adb shell input keyevent KEYCODE_HOME
  adb logcat -d | grep "Displayed dev.rishikeshsuvarna.zyft"
  ```
- Run `make check` before opening a PR.

## Commits

[Conventional commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `chore:`, `refactor:`, `test:`, `docs:`, `ci:`. Keep PRs small and focused.
