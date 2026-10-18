# Shared helpers; sourced by the other scripts.
set -euo pipefail

readonly ADB_PORT=5555
readonly SELF_PKG="dev.rishi.tvlauncher"
readonly SELF_HOME="$SELF_PKG/.MainActivity"
readonly APK="app/build/outputs/apk/release/app-release.apk"

# Stock home apps that grab the Home button. Disabling (not uninstalling) is reversible.
readonly STOCK_LAUNCHERS=(
  com.google.android.apps.tv.launcherx      # Google TV home
  com.google.android.tungsten.setupwraith   # Google TV fallback home, re-takes Home if left enabled
  com.google.android.tvlauncher             # Android TV home
  com.google.android.leanbacklauncher       # Legacy Android TV home
)

die() { echo "error: $*" >&2; exit 1; }

# `adb shell` swallows stdin, which would eat the answers meant for confirm().
adb() { command adb "$@" </dev/null; }

connect_tv() {
  local ip="${1:-}"
  [[ -n "$ip" ]] || die "usage: $0 <tv-ip>   (TV: Settings > System > About > Network)"
  if [[ "$ip" == emulator-* ]]; then
    export ANDROID_SERIAL="$ip"
  else
    export ANDROID_SERIAL="$ip:$ADB_PORT"
    adb connect "$ANDROID_SERIAL" >/dev/null
  fi
  # First connection pops an "Allow USB debugging?" prompt on the TV.
  echo "Waiting for TV (accept the debugging prompt on screen if shown)..."
  adb wait-for-device
  [[ "$(adb get-state)" == "device" ]] || die "TV not authorised; accept the prompt and retry"
}

is_installed() { adb shell pm path "$1" 2>/dev/null | grep -q '^package:'; }

confirm() {
  read -r -p "$1 [y/N] " reply
  [[ "$reply" =~ ^[Yy]$ ]]
}

is_enabled() { adb shell pm list packages -e "$1" 2>/dev/null | tr -d '\r' | grep -qx "package:$1"; }

# Everything we disable is logged per device, so restore.sh undoes only our changes
# and leaves packages that were already disabled at the factory alone.
state_file() { echo "$(dirname "$0")/../.tv-state/${ANDROID_SERIAL}.disabled"; }

disable_pkg() {
  local pkg="$1" file
  file="$(state_file)"
  mkdir -p "$(dirname "$file")"
  adb shell pm disable-user --user 0 "$pkg" >/dev/null || { echo "skip $pkg (protected)"; return; }
  grep -qx "$pkg" "$file" 2>/dev/null || echo "$pkg" >> "$file"
  echo "disabled $pkg"
}
