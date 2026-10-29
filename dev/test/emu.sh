#!/usr/bin/env bash
# The two emulators this project is tested on, made, started and loaded with the apps in one go (Mac, Linux; on Windows use
# Git Bash with the SDK's tools on PATH, or run the steps by hand). See dev/test/AGENTS.md.
#
#   dev/test/emu.sh            start + install
#   dev/test/emu.sh create     the Google TV and phone AVDs (Android 14), system images downloaded when missing
#   dev/test/emu.sh start      the TV first on port 5554, then the phone on 5556: the phone reaches the TV's ADB as 10.0.2.2:5555
#   dev/test/emu.sh install    builds both APKs, installs TV Setup on the TV and TV Remote on the phone (each found by what it is)
#   dev/test/emu.sh reset      the phone app's saved TVs back to the TV emulator only (a snapshot can bring back real TVs)
#   dev/test/emu.sh stop       both emulators off
#   dev/test/emu.sh status     every emulator running on this computer (hidden ones too): CPU, uptime, what is in front
#
# Settings (environment): TV_AVD, PHONE_AVD (names), TV_IMG, PHONE_IMG (system images), TV_PORT, PHONE_PORT (5554, 5556),
# API (34), SECURE=0 to start the TV without the real "Allow USB debugging?" prompt, HEADLESS=1 for no windows (CI, a
# server), EMU_TTL (minutes before a HEADLESS one stops itself, 120; 0 never), ANDROID_HOME (the SDK).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TV_AVD="${TV_AVD:-tvremote_gtv}"
PHONE_AVD="${PHONE_AVD:-tvremote_test}"
API="${API:-34}"
TV_PORT="${TV_PORT:-5554}"                                         # 5554: its ADB is 5555, 10.0.2.2:5555 from the phone
PHONE_PORT="${PHONE_PORT:-5556}"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
  for d in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "${LOCALAPPDATA:-/none}/Android/Sdk"; do [ -d "$d" ] && { SDK="$d"; break; }; done
fi
[ -n "$SDK" ] && [ -d "$SDK" ] || { echo "Android SDK not found: set ANDROID_HOME (Android Studio → Settings → Android SDK shows where)"; exit 1; }
ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator"
BIN="$SDK/cmdline-tools/latest/bin"

case "$(uname -m)" in arm64|aarch64) ABI=arm64-v8a ;; *) ABI=x86_64 ;; esac   # an image of another chip runs far too slowly
# an older TV: TV_IMG="system-images;android-31;android-tv;arm64-v8a" (Android 12; it cannot save "Always allow":
# adb_keys is not writable there, so every new connection asks again; test the questions on the Android 14 image)
TV_IMG="${TV_IMG:-system-images;android-$API;google-tv;$ABI}"
PHONE_IMG="${PHONE_IMG:-system-images;android-$API;default;$ABI}"

need() { [ -x "$1" ] || { echo "Missing $1 — install \"$2\" in Android Studio → SDK Manager → SDK Tools"; exit 1; }; }

avd_exists() { "$EMU" -list-avds 2>/dev/null | grep -qx "$1"; }

# serial of the running emulator on this port, or nothing
running() { "$ADB" devices | awk -v s="emulator-$1" '$1==s && $2=="device" {print $1}'; }

is_tv() { "$ADB" -s "$1" shell pm list features 2>/dev/null | grep -q android.software.leanback; }

make_avd() {   # name image device
  local name="$1" img="$2" device="$3"
  if avd_exists "$name"; then echo "✓ $name already exists"; return; fi
  need "$BIN/sdkmanager" "Android SDK Command-line Tools"
  [ -d "$SDK/$(echo "$img" | tr ';' '/')" ] || { echo "… downloading $img (a few GB the first time)"; yes | "$BIN/sdkmanager" --install "$img" >/dev/null; }
  local cfg="$HOME/.android/avd/$name.avd/config.ini" kv out
  rm -rf "$HOME/.android/avd/$name.avd"                              # left by an AVD deleted by hand: avdmanager fails on it
  out="$(echo no | "$BIN/avdmanager" create avd -n "$name" -k "$img" -d "$device" 2>&1)" || true
  [ -f "$cfg" ] || { echo "$out" | grep -v "SDK XML" | tail -3; echo "Could not create $name"; exit 1; }
  for kv in hw.ramSize=1536M hw.keyboard=yes "sdcard.size=512 MB"; do   # the computer's keyboard types into the emulator
    grep -v "^${kv%%=*} *=" "$cfg" > "$cfg.tmp" || true                 # avdmanager writes "key = value", with spaces
    echo "$kv" >> "$cfg.tmp"; mv "$cfg.tmp" "$cfg"
  done
  echo "✓ $name created ($img)"
}

create() {
  need "$EMU" "Android Emulator"
  make_avd "$TV_AVD" "$TV_IMG" tv_1080p
  make_avd "$PHONE_AVD" "$PHONE_IMG" pixel_5
}

boot() {   # avd port extra-args...
  local avd="$1" port="$2"; shift 2
  local s="emulator-$port"
  if [ -n "$(running "$port")" ]; then echo "✓ $s already running"; return; fi
  avd_exists "$avd" || { echo "No AVD $avd: run dev/test/emu.sh create first"; exit 1; }
  # each emulator takes ~3 GB and 4 cores of the computer: a third one (another agent's) froze a 24 GB Mac with builds beside it
  local n; n=$("$ADB" devices | grep -c '^emulator-' || true)
  if [ "$n" -ge "${EMU_MAX:-2}" ]; then echo "$n emulators already running (EMU_MAX=${EMU_MAX:-2}): use one of them, or dev/test/emu.sh stop"; exit 1; fi
  mkdir -p "$ROOT/.cache"
  local win=(); [ "${HEADLESS:-0}" = 1 ] && win=(-no-window)
  echo "… starting $avd as $s"
  nohup "$EMU" -avd "$avd" -port "$port" -no-boot-anim -no-audio -no-snapshot-load ${win[@]+"${win[@]}"} "$@" > "$ROOT/.cache/emu-$avd.log" 2>&1 &
  local t=0
  until [ "$("$ADB" -s "$s" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
    sleep 3; t=$((t + 3))
    [ $t -lt 360 ] || { echo "$avd did not boot within 6 minutes: see .cache/emu-$avd.log"; exit 1; }
  done
  echo "✓ $s booted"
  # a hidden one is forgotten easily: a headless phone left streaming the TV ran at ~650% CPU for 12 h (2026-10-25)
  if [ ${#win[@]} -gt 0 ] && [ "${EMU_TTL:-120}" -gt 0 ]; then
    nohup bash -c "sleep $((${EMU_TTL:-120} * 60)); '$ADB' -s $s emu kill" >/dev/null 2>&1 &
    echo "  no window: it stops itself in ${EMU_TTL:-120} min (EMU_TTL=0 never; dev/test/emu.sh status)"
  fi
}

start() {
  need "$EMU" "Android Emulator"
  local busy; busy="$(running $TV_PORT)"
  if [ -n "$busy" ] && ! is_tv "$busy"; then echo "emulator-$TV_PORT is not a TV: stop it (dev/test/emu.sh stop) so the TV gets that port"; exit 1; fi
  local secure=(); [ "${SECURE:-1}" = 1 ] && secure=(-prop ro.adb.secure=1)   # the real "Allow USB debugging?" for the phone's key
  # 2 GB like most Google TVs: with 1.5 the TV swaps itself stiff once TV Setup's Manage screen (a WebView) is open
  boot "$TV_AVD" $TV_PORT -memory 2048 ${secure[@]+"${secure[@]}"}
  boot "$PHONE_AVD" $PHONE_PORT
}

# the running emulators, TV or phone, by what they are: the port alone can be wrong when they were started by hand
find_devices() {
  TV=""; PHONE=""
  for s in $("$ADB" devices | awk '$1 ~ /^emulator-/ && $2=="device" {print $1}'); do
    if is_tv "$s"; then [ -n "$TV" ] || TV="$s"; else [ -n "$PHONE" ] || PHONE="$s"; fi
  done
}

install() {
  find_devices
  [ -n "$TV$PHONE" ] || { echo "No emulator running: dev/test/emu.sh start"; exit 1; }
  if [ -n "$TV" ]; then
    "$ROOT/app/tv/build.sh" >/dev/null
    "$ADB" -s "$TV" install -r "$ROOT/app/tv/build/tv-setup.apk" | tail -1 | sed "s/^/TV Setup → $TV: /"
    "$ROOT/app/home/build.sh" >/dev/null                             # TV Home, the launcher: installed, not made the home (the gallery does that)
    "$ADB" -s "$TV" install -r "$ROOT/app/home/build/tv-home.apk" | tail -1 | sed "s/^/TV Home → $TV: /"
    "$ROOT/app/channels/build.sh" >/dev/null                         # TV Channels, the iptv-org player
    "$ADB" -s "$TV" install -r "$ROOT/app/channels/build/tv-channels.apk" | tail -1 | sed "s/^/TV Channels → $TV: /"
    "$ROOT/app/games/build.sh" >/dev/null                            # TV Games, retro + web games with phones as pads
    "$ADB" -s "$TV" install -r "$ROOT/app/games/build/tv-games.apk" | tail -1 | sed "s/^/TV Games → $TV: /"
  fi
  if [ -n "$PHONE" ]; then
    "$ROOT/app/android/build.sh" >/dev/null
    "$ADB" -s "$PHONE" install -r "$ROOT/app/android/build/tv-remote.apk" | tail -1 | sed "s/^/TV Remote → $PHONE: /"
    "$ADB" -s "$PHONE" shell am start -n dev.tvremote.phone/.MainActivity >/dev/null
  fi
}

reset() {
  find_devices
  [ -n "$PHONE" ] || { echo "No phone emulator running"; exit 1; }
  "$ADB" -s "$PHONE" root >/dev/null; sleep 2; "$ADB" -s "$PHONE" wait-for-device
  local f=/data/data/dev.tvremote.phone/shared_prefs/tvs.xml
  "$ADB" -s "$PHONE" shell am force-stop dev.tvremote.phone
  if "$ADB" -s "$PHONE" shell "[ -f $f ]"; then
    "$ADB" -s "$PHONE" shell "sed -i 's|<string name=\"ips\">[^<]*</string>|<string name=\"ips\">10.0.2.2</string>|' $f"
    # a test of another brand (a fake Roku on the host) leaves 10.0.2.2 saved as that brand: every probe then asks its ports, not ADB
    "$ADB" -s "$PHONE" shell "sed -E -i '/name=\"(kind|name):10\.0\.2\.2\"/d' $f"
  fi
  "$ADB" -s "$PHONE" shell am start -n dev.tvremote.phone/.MainActivity >/dev/null
  echo "✓ the phone app's TVs: 10.0.2.2 (the TV emulator) only"
}

# every emulator on this computer, this project's or not, with or without a window: what it is, how long, how hot, what is in front
status() {
  local rows; rows="$(ps -wwAo pid=,etime=,args= | grep '[q]emu-system.*-avd ' || true)"
  [ -n "$rows" ] || { echo "No emulator running"; return; }
  local pids; pids="$(echo "$rows" | awk '{printf "-pid %s ", $1}')"
  local cpu; cpu="$(top -l 2 -s 1 $pids -stats pid,cpu | awk 'NF==2 && $1 ~ /^[0-9]+$/ {c[$1]=$2} END {for (p in c) print p, c[p]}')"   # the second sample: now, not since start
  echo "$rows" | while read -r pid etime args; do
    local avd port win front
    avd="$(echo "$args" | sed -n 's/.*-avd \([^ ]*\).*/\1/p')"; port="$(echo "$args" | sed -n 's/.*-port \([0-9]*\).*/\1/p')"
    case "$args" in *-headless*|*-no-window*) win="NO WINDOW" ;; *) win="window" ;; esac
    front=""; [ -n "$port" ] && front="$("$ADB" -s "emulator-$port" shell "dumpsys window displays | grep -m1 mCurrentFocus" </dev/null 2>/dev/null | sed 's/.* \([^ ]*\)}.*/\1/' | tr -d '\r')"
    printf '%-7s %-16s %-10s %-11s up %-12s %6s%% CPU  %s\n' "$pid" "${avd:-?}" "emulator-${port:-?}" "$win" "$etime" "$(echo "$cpu" | awk -v p="$pid" '$1==p {print $2}')" "$front"
  done
}

stop() {
  local p t
  for p in $PHONE_PORT $TV_PORT; do
    [ -n "$(running $p)" ] || continue
    "$ADB" -s "emulator-$p" emu kill >/dev/null
    t=0; while [ -n "$(running $p)" ] && [ $t -lt 30 ]; do sleep 1; t=$((t + 1)); done   # gone, so a start right after gets the port
    echo "✓ emulator-$p off"
  done
  true
}

case "${1:-}" in
  create) create ;;
  start) start ;;
  install) install ;;
  reset) reset ;;
  stop) stop ;;
  status) status ;;
  "") start; install ;;
  *) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 1 ;;
esac
