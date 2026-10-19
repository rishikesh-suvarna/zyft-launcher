#!/usr/bin/env bash
# Copies Zyft's settings (favorites and their order, hidden apps, sort, background, usage)
# off a TV or onto one, e.g. when moving to a new TV.
#   scripts/settings.sh backup  <tv-ip> [file]   default file: backups/<tv-ip>-<timestamp>.zyft
#   scripts/settings.sh restore <tv-ip> <file>
source "$(dirname "$0")/common.sh"

readonly RECEIVER="$SELF_PKG/.SettingsBridge"
readonly ACTION_PREFIX="$SELF_PKG"
readonly RESULT_OK=-1  # Activity.RESULT_OK
BACKUP_DIR="$(cd "$(dirname "$0")/.." && pwd)/backups"
readonly BACKUP_DIR

# Sends an ordered broadcast to the launcher and prints its result data; fails on an error result.
# `am broadcast` reports: Broadcast completed: result=-1, data="..."
call_launcher() {
  local out code data
  out="$(adb shell am broadcast -n "$RECEIVER" -a "$ACTION_PREFIX.$1" "${@:2}" | tr -d '\r')"
  code="$(sed -n 's/.*Broadcast completed: result=\(-\{0,1\}[0-9]*\).*/\1/p' <<<"$out")"
  data="$(sed -n 's/.*, data="\(.*\)".*/\1/p' <<<"$out")"
  [[ "$code" == "$RESULT_OK" ]] || die "launcher refused: ${data:-$out}"
  printf '%s' "$data"
}

usage() { die "usage: $0 backup <tv-ip> [file] | restore <tv-ip> <file>"; }

cmd="${1:-}"; tv="${2:-}"; file="${3:-}"
[[ -n "$tv" ]] || usage
case "$cmd" in
  backup)
    connect_tv "$tv"
    is_installed "$SELF_PKG" || die "Zyft Launcher is not installed on $ANDROID_SERIAL"
    if [[ -z "$file" ]]; then
      mkdir -p "$BACKUP_DIR"
      file="$BACKUP_DIR/${ANDROID_SERIAL//:/_}-$(date +%Y%m%d-%H%M%S).zyft"
    fi
    call_launcher EXPORT_SETTINGS | base64 -d > "$file"
    echo "Saved $(($(wc -l < "$file") - 1)) settings to $file"
    ;;
  restore)
    [[ -f "$file" ]] || usage
    head -n 1 "$file" | grep -qx 'zyft-settings-v1' || die "$file is not a Zyft settings backup"
    connect_tv "$tv"
    is_installed "$SELF_PKG" || die "install Zyft Launcher first (make install TV=$tv)"
    call_launcher IMPORT_SETTINGS --es data "$(base64 < "$file" | tr -d '\n')"
    echo
    # Restart so every screen picks the new settings up, not just the ones read live.
    adb shell am force-stop "$SELF_PKG"
    adb shell am start -n "$SELF_HOME" >/dev/null 2>&1
    echo "Launcher restarted"
    ;;
  *) usage ;;
esac
