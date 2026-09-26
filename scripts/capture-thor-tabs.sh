#!/usr/bin/env bash
set -Eeuo pipefail

serial="${1:?usage: capture-thor-tabs.sh <adb-serial> [output-dir]}"
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
out="${2:-$root/captures/thor}"
settle="${SETTLE_SECONDS:-1.5}"
adb="$root/.toolchains/android-sdk/platform-tools/adb.exe"
[[ -x "$adb" ]] || adb=adb

top_display=4630946441858561667
bottom_display=4630946482288158084
bottom_logical=4
tab_bar_y=115

mkdir -p "$out"
tabs=(messages:152 friends:385 home:620 activities:853 settings:1085)
for entry in "${tabs[@]}"; do
  name="${entry%%:*}"
  x="${entry##*:}"
  "$adb" -s "$serial" shell input -d "$bottom_logical" tap "$x" "$tab_bar_y"
  "$adb" -s "$serial" shell sleep "$settle"
  "$adb" -s "$serial" exec-out screencap -p -d "$top_display" > "$out/$name-top.png"
  "$adb" -s "$serial" exec-out screencap -p -d "$bottom_display" > "$out/$name-bottom.png"
  printf 'captured %s\n' "$name"
done
"$adb" -s "$serial" shell input -d "$bottom_logical" tap 620 "$tab_bar_y"
