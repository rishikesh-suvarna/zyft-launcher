#!/usr/bin/env bash
# Installs Zyft Launcher on the TV and makes it the only home app.
source "$(dirname "$0")/common.sh"

connect_tv "${1:-}"
[[ -f "$APK" ]] || die "$APK not found; run 'make build' first"

echo "Installing $APK..."
# A self-built APK (debug key) and a release APK (release key) can't update each other.
if ! output="$(adb install -r "$APK" 2>&1)"; then
  echo "$output"
  [[ "$output" == *INSTALL_FAILED_UPDATE_INCOMPATIBLE* ]] || die "install failed"
  echo "The installed Zyft Launcher is signed with a different key, so it can't be updated in place."
  confirm "Uninstall it and install this one? Launcher settings (favorites, hidden apps, sort) will reset." \
    || die "aborted; nothing changed"
  adb uninstall "$SELF_PKG" >/dev/null
  adb install "$APK"
else
  echo "$output"
fi

for legacy in "${LEGACY_PKGS[@]}"; do
  is_installed "$legacy" || continue
  echo "Found the old build of this launcher ($legacy); it would compete for the Home button."
  if confirm "Uninstall $legacy? Its settings don't carry over to the new package."; then
    adb uninstall "$legacy" >/dev/null && echo "removed $legacy"
  fi
done

echo "Current home candidates:"
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep / || true

to_disable=()
for pkg in "${STOCK_LAUNCHERS[@]}"; do
  is_enabled "$pkg" && to_disable+=("$pkg")
done

if (( ${#to_disable[@]} )); then
  printf '  %s\n' "${to_disable[@]}"
  if confirm "Disable these stock launchers? (undo with 'make restore')"; then
    for pkg in "${to_disable[@]}"; do disable_pkg "$pkg"; done
  fi
fi

# Android TV has no "choose default home" UI, so set it explicitly where supported.
adb shell cmd package set-home-activity "$SELF_HOME" >/dev/null 2>&1 \
  || adb shell cmd role add-role-holder android.app.role.HOME "$SELF_PKG" >/dev/null 2>&1 \
  || echo "note: could not set default home explicitly; disabling the stock launcher is enough"

adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
echo "Done. Press Home on the remote to verify."
