#!/usr/bin/env bash
# Installs Lite Launcher on the TV and makes it the only home app.
source "$(dirname "$0")/common.sh"

connect_tv "${1:-}"
[[ -f "$APK" ]] || die "$APK not found; run 'make build' first"

echo "Installing $APK..."
adb install -r "$APK"

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
