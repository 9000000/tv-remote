#!/bin/bash
# Runs a heavy command (ffmpeg, a Gradle build, a node audit) only when the computer has room for it, at low priority.
#   dev/test/light.sh <command> [args...]
# Waits while the load is above the number of cores or memory is short, then runs it under `nice` with one lock, so
# heavy steps of several agents queue up instead of piling on. Why: on 2026-10-24 six ffmpeg encodes started side by side
# (x264 opens ~1.5 threads per core each) beside a TV emulator transcoding in software: load 84 on 12 cores, the emulator
# crashed and the Mac froze (dev/DECISIONS.md "Tests that froze the computer").
# Env: LIGHT_WAIT (s, default 600: then it runs anyway, said so), LIGHT_FREE (% memory free needed, default 15).
set -u
[ $# -gt 0 ] || { echo "usage: dev/test/light.sh <command> [args...]"; exit 2; }
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
LOCK="$ROOT/.cache/heavy.lock"
mkdir -p "$ROOT/.cache"
cores=$( (sysctl -n hw.ncpu || nproc) 2>/dev/null )
need_free="${LIGHT_FREE:-15}"
load1() { (sysctl -n vm.loadavg 2>/dev/null | awk '{print int($2)}') || awk '{print int($1)}' /proc/loadavg; }
free_pct() {
  if command -v memory_pressure >/dev/null; then memory_pressure 2>/dev/null | awk -F': ' '/free percentage/ {print int($2)}'
  else awk '/MemTotal/ {t=$2} /MemAvailable/ {a=$2} END {print int(a*100/t)}' /proc/meminfo; fi
}
waited=0; limit="${LIGHT_WAIT:-600}"
while :; do
  l=$(load1); f=$(free_pct); f=${f:-100}
  busy=""
  if [ -f "$LOCK" ] && kill -0 "$(cat "$LOCK" 2>/dev/null)" 2>/dev/null; then busy="another heavy step (pid $(cat "$LOCK"))"
  elif [ "$l" -gt "$cores" ]; then busy="load $l > $cores cores"
  elif [ "$f" -lt "$need_free" ]; then busy="memory free $f% < $need_free%"; fi
  [ -z "$busy" ] && break
  if [ $waited -ge "$limit" ]; then echo "light.sh: still $busy after ${limit}s, running anyway" >&2; break; fi
  [ $((waited % 30)) -eq 0 ] && echo "light.sh: waiting, $busy" >&2
  sleep 5; waited=$((waited + 5))
done
echo $$ > "$LOCK"
trap 'rm -f "$LOCK"' EXIT
nice -n 10 "$@"
