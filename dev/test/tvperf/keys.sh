#!/bin/bash
# keys.sh <serial> "<open keys>" "<measured keys>": frames, jank, worst per measured key set, after opening
D=$1; A="adb -s $D shell"
$A input keyevent HOME; sleep 3
for x in $2; do $A input keyevent $x; sleep 0.6; done; sleep 1.5
$A dumpsys gfxinfo dev.tvremote.home reset >/dev/null
n=0; for x in $3; do $A input keyevent $x; sleep 0.6; n=$((n+1)); done; sleep 1
g=$($A dumpsys gfxinfo dev.tvremote.home)
echo "keys=$n $(echo "$g" | grep -m1 'Total frames' | tr -s ' ') | $(echo "$g" | grep -m1 'Janky frames:' | tr -s ' ') | p90 $(echo "$g" | grep -m1 '90th percentile' | awk -F: '{print $2}') | worst $(echo "$g" | grep -m1 '^HISTOGRAM' | tr ' ' '\n' | grep '=[1-9]' | tail -1)"
$A input keyevent BACK; $A input keyevent BACK; $A input keyevent HOME
