#!/usr/bin/env bash
# Lists vendor/system packages so you can pick bloat for bloat.txt.
source "$(dirname "$0")/common.sh"

connect_tv "${1:-}"
echo "== Vendor (TCL/MediaTek/etc) packages =="
adb shell pm list packages -s | sed 's/^package://' | grep -Ei 'tcl|tvos|mstar|mediatek|mtk|cyber|gaia' | sort
echo
echo "== Biggest RAM users right now (PSS) =="
adb shell dumpsys meminfo | sed -n '/Total PSS by process/,/^$/p' | head -25
