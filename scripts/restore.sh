#!/usr/bin/env bash
# Re-enables exactly what install.sh / debloat.sh disabled, then removes Zyft Launcher.
source "$(dirname "$0")/common.sh"

connect_tv "${1:-}"
file="$(state_file)"

if [[ -f "$file" ]]; then
  while read -r pkg; do
    [[ -n "$pkg" ]] && adb shell pm enable "$pkg" >/dev/null && echo "enabled $pkg"
  done < "$file"
  rm "$file"
else
  # No log (e.g. state lost): bring back the stock launchers so the TV always has a home screen.
  echo "No record of changes for $ANDROID_SERIAL; re-enabling stock launchers only."
  for pkg in "${STOCK_LAUNCHERS[@]}"; do
    is_installed "$pkg" && adb shell pm enable "$pkg" >/dev/null && echo "enabled $pkg"
  done
fi

if [[ -f "$file.anim" ]]; then
  for key in window_animation_scale transition_animation_scale animator_duration_scale; do
    adb shell settings put global "$key" 1.0
  done
  rm "$file.anim"
  echo "animation speed reset"
fi

if confirm "Uninstall Zyft Launcher too?"; then
  for pkg in "$SELF_PKG" "${LEGACY_PKGS[@]}"; do
    is_installed "$pkg" && adb uninstall "$pkg" >/dev/null && echo "uninstalled $pkg"
  done
fi
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
