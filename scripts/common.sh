# shellcheck shell=bash
# Shared helpers; sourced by the other scripts.
# shellcheck disable=SC2034  # constants below are used by the scripts that source this file
set -euo pipefail

readonly ADB_PORT=5555
readonly CONNECT_TIMEOUT_S=30
readonly SELF_PKG="dev.rishikeshsuvarna.zyft"
# Earlier package names of this launcher. A different package is a different app to Android,
# so an old one would linger as a second home app after upgrading.
readonly LEGACY_PKGS=(
  dev.rishi.tvlauncher   # "Lite Launcher", v1.0.0
)
readonly SELF_HOME="$SELF_PKG/.MainActivity"
readonly APK="${APK:-app/build/outputs/apk/release/app-release.apk}"
PROFILES_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../profiles" && pwd)"
readonly PROFILES_DIR

# Stock home apps that grab the Home button. Disabling (not uninstalling) is reversible.
readonly STOCK_LAUNCHERS=(
  com.google.android.apps.tv.launcherx      # Google TV home
  com.google.android.tungsten.setupwraith   # Google TV fallback home, re-takes Home if left enabled
  com.google.android.tvlauncher             # Android TV home
  com.google.android.leanbacklauncher       # Legacy Android TV home
  com.mitv.tvhome                           # Xiaomi PatchWall
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
  # First connection pops an "Allow USB debugging?" prompt on the TV; give the user time to accept it.
  echo "Waiting for $ANDROID_SERIAL (accept the debugging prompt on the TV if shown)..."
  local state="" waited=0
  while (( waited < CONNECT_TIMEOUT_S )); do
    state="$(adb get-state 2>/dev/null || true)"
    [[ "$state" == "device" ]] && return 0
    sleep 1; waited=$((waited + 1))
  done
  case "$state" in
    unauthorized) die "TV is not authorised: accept the debugging prompt on screen and retry" ;;
    *) die "can't reach $ANDROID_SERIAL: is the TV on, on the same network, with USB debugging enabled?" ;;
  esac
}

# Lowercased brand, e.g. "tcl", "sony", "xiaomi"; picks profiles/<vendor>.txt.
detect_vendor() { adb shell getprop ro.product.manufacturer | tr -d '\r' | tr '[:upper:]' '[:lower:]'; }

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
