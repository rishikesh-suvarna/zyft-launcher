#!/usr/bin/env bash
# Disables (reversibly) the packages listed in bloat.txt that exist on this TV.
source "$(dirname "$0")/common.sh"

connect_tv "${1:-}"

targets=()
while read -r pkg _; do
  [[ -z "$pkg" || "$pkg" == \#* ]] && continue
  is_enabled "$pkg" && targets+=("$pkg")
done < "$(dirname "$0")/bloat.txt"

(( ${#targets[@]} )) || { echo "Nothing from bloat.txt is installed and enabled."; exit 0; }
printf '  %s\n' "${targets[@]}"
confirm "Disable these ${#targets[@]} packages?" || exit 0

for pkg in "${targets[@]}"; do
  disable_pkg "$pkg"
done

if confirm "Also halve system animation durations (snappier UI)?"; then
  for key in window_animation_scale transition_animation_scale animator_duration_scale; do
    adb shell settings put global "$key" 0.5
  done
  touch "$(state_file).anim"
fi
echo "Reboot the TV for the full effect: adb -s $ANDROID_SERIAL reboot"
