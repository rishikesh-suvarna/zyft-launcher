#!/usr/bin/env bash
# Disables (reversibly) the packages from profiles/android-tv.txt plus the TV brand's profile.
source "$(dirname "$0")/common.sh"

connect_tv "${1:-}"

profiles=("$PROFILES_DIR/android-tv.txt")
vendor="$(detect_vendor)"
if [[ -f "$PROFILES_DIR/$vendor.txt" ]]; then
  profiles+=("$PROFILES_DIR/$vendor.txt")
else
  echo "No profile for '$vendor' yet; using the generic Android TV list only (see CONTRIBUTING.md)."
fi

targets=()
for profile in "${profiles[@]}"; do
  while read -r pkg _; do
    [[ -z "$pkg" || "$pkg" == \#* ]] && continue
    is_enabled "$pkg" && targets+=("$pkg")
  done < "$profile"
done

(( ${#targets[@]} )) || { echo "Nothing from ${profiles[*]##*/} is installed and enabled."; exit 0; }
echo "Profiles: ${profiles[*]##*/}"
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
