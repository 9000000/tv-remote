#!/bin/bash
# panels.sh <serial> <label>: TV Home's panels on the D-pad, each from Home: frames / jank / worst / CPU, and PSS before / open / after
D=$1; L=$2
A="adb -s $D shell"
P=dev.tvremote.home
k() { for x in "$@"; do $A input keyevent $x; sleep 0.45; done; }
pss() { $A dumpsys meminfo $P | awk '/TOTAL PSS:/{print int($3/1024); exit} /^ *TOTAL /{print int($2/1024); exit}'; }
heap() { $A dumpsys meminfo $P | awk '/Native Heap:/{n=int($3/1024)} /Java Heap:/{j=int($3/1024)} /^ *Views:/{v=$2} END{print "java="j" native="n" views="v}'; }
tck=$($A getconf CLK_TCK 2>/dev/null | tr -d '\r'); [ -z "$tck" ] && tck=100
home() { $A input keyevent HOME; sleep 3; }
run() {  # run <name> <open keys...> -- <walk keys...>
  name=$1; shift; o=(); while [ "$1" != "--" ]; do o+=("$1"); shift; done; shift
  home; pid=$($A pidof $P | tr -d '\r')
  p0=$(pss); $A dumpsys gfxinfo $P reset >/dev/null
  c0=$($A cat /proc/$pid/stat | awk '{print $14+$15}')
  k "${o[@]}"; sleep 1.5; p1=$(pss); h1=$(heap)
  k "$@"; k BACK; sleep 0.6; k BACK BACK; sleep 1
  c1=$($A cat /proc/$pid/stat | awk '{print $14+$15}')
  g=$($A dumpsys gfxinfo $P)
  tot=$(echo "$g" | grep -m1 "Total frames rendered" | awk -F: '{print $2}' | tr -d ' \r')
  jank=$(echo "$g" | grep -m1 "Janky frames:" | awk -F: '{print $2}' | tr -d '\r' | xargs)
  p99=$(echo "$g" | grep -m1 "99th percentile" | awk -F: '{print $2}' | tr -d ' \r')
  worst=$(echo "$g" | grep -m1 "^HISTOGRAM" | tr ' ' '\n' | grep "=[1-9]" | tail -1)
  p2=$(pss)
  echo "$L $name: frames=$tot jank=$jank p99=$p99 worst=$worst cpu=$(( (c1-c0)*1000/tck ))ms pss=$p0>$p1>$p2 MB open:$h1"
}
D4="DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN"
# the tile menu (MENU on the first tile), walked
run tilemenu MENU -- DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_UP DPAD_UP DPAD_UP
# Customize from the tile menu: the first level walked, then the Background page and the Look page
run customize MENU $D4 DPAD_CENTER -- DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_UP DPAD_UP DPAD_UP DPAD_UP DPAD_UP
run cust_bg MENU $D4 DPAD_CENTER DPAD_DOWN DPAD_RIGHT -- DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_RIGHT DPAD_RIGHT DPAD_DOWN DPAD_DOWN DPAD_UP DPAD_UP
run cust_look MENU $D4 DPAD_CENTER DPAD_DOWN DPAD_DOWN DPAD_RIGHT -- DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_UP DPAD_UP DPAD_UP
# the Control Center: up to the top bar, right to the gear
run control DPAD_UP DPAD_UP DPAD_UP DPAD_UP DPAD_UP DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_CENTER -- DPAD_DOWN DPAD_RIGHT DPAD_DOWN DPAD_LEFT DPAD_DOWN DPAD_DOWN DPAD_UP DPAD_UP
$A input keyevent HOME
