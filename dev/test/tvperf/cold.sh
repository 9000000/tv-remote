#!/bin/bash
# cold.sh <serial> [runs=3]: TV Home's cold start (am start -W, process killed first) and its idle PSS 15 s after the start.
# Read-only on settings: only restarts Home. Use the RELEASE build (see ../AGENTS.md).
# Not yet trusted: on the FPT box (2026-10-28) am start -W gave TotalTime 0 (Home treated as already started), so only WaitTime
# and PSS were usable; check TotalTime is not 0 before comparing. Prints per run: TotalTime / WaitTime in ms, then PSS (MB) and the process count; the median is what to compare between builds.
D=$1; N=${2:-3}
A="adb -s $D shell"
for i in $(seq "$N"); do
  $A input keyevent HOME; sleep 2                  # -S below = force-stop + start (a plain force-stop makes the system relaunch Home warm)
  w=$($A am start -W -S -a android.intent.action.MAIN -c android.intent.category.HOME -n dev.tvremote.home/.HomeActivity | tr -d '\r' | awk '/TotalTime|WaitTime/ {printf "%s %s  ", $1, $2}')
  sleep 15
  pss=$($A dumpsys meminfo dev.tvremote.home | tr -d '\r' | awk '/TOTAL PSS:/ {print $3; exit} /^ *TOTAL / {print $2; exit}')
  echo "run $i: $w PSS_KB ${pss:-?}"
done
