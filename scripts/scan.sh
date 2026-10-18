#!/usr/bin/env bash
# Read-only: prints device info and vendor packages, to help write profiles/<vendor>.txt.
source "$(dirname "$0")/common.sh"

connect_tv "${1:-}"
echo "== Device =="
for prop in ro.product.manufacturer ro.product.model ro.build.version.release ro.board.platform; do
  echo "$prop=$(adb shell getprop "$prop" | tr -d '\r')"
done
echo "profile: profiles/$(detect_vendor).txt"
echo
echo "== Home apps =="
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep / || true
echo
echo "== System packages not from Google/AOSP (vendor candidates) =="
adb shell pm list packages -s | sed 's/^package://' | tr -d '\r' \
  | grep -vE '^(android$|com\.android\.|com\.google\.)' | sort
echo
echo "== Biggest RAM users right now (PSS) =="
adb shell dumpsys meminfo | sed -n '/Total PSS by process/,/^$/p' | head -25
