#!/usr/bin/env bash
# Store screenshots for every listing language, on one connected phone or emulator
# (Android 13 or later keeps the language switch flicker-free). Installs the debug
# build, uses its Demo hook (app/src/debug) to seed data and pick screens, and writes
# fastlane/metadata/android/<locale>/images/phoneScreenshots/*.png for Play and F-Droid.
set -euo pipefail
cd "$(dirname "$0")/.."

PKG=com.tappony.android.debug
ACT=com.tappony.android.MainActivity
OUT=fastlane/metadata/android

./gradlew -q :app:installDebug

demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
adb shell settings put global sysui_demo_allowed 1
demo enter
demo clock -e hhmm 0941
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile show -e level 4 -e datatype none
demo notifications -e visible false

for pair in en-US:en de-DE:de es-ES:es fr-FR:fr pt-BR:pt-BR ru-RU:ru zh-CN:zh-Hans; do
  dir=${pair%%:*}
  lang=${pair##*:}
  shots="$OUT/$dir/images/phoneScreenshots"
  mkdir -p "$shots"
  rm -f "$shots"/*.png
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/$ACT" --es demo_lang "$lang" --es demo_screen scan >/dev/null
  sleep 5
  i=1
  for screen in scan profile rules tags history; do
    adb shell am start -W -n "$PKG/$ACT" --es demo_screen "$screen" >/dev/null
    sleep 2
    adb exec-out screencap -p > "$shots/${i}_${screen}.png"
    i=$((i + 1))
  done
  echo "$dir done"
done

demo exit
