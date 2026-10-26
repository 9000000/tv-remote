#!/bin/bash
# prof.sh <serial> <name> <keys...>: simpleperf on the release TV Home while the keys are pressed, names from the R8 map; prints ms per thread and the main thread's own code
D=$1; N=$2; shift 2
R=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
S=${OUT:-/tmp/tvperf}; mkdir -p $S                      # perf.data and the report stay here
H=$(dirname "$0")
SP=$(ls -d ~/Library/Android/sdk/ndk/*/simpleperf | head -1)
A="adb -s $D shell"
$A input keyevent HOME; sleep 3
$A rm -f /data/local/tmp/perf.data
$A simpleperf record --app dev.tvremote.home -e cpu-clock -f 1000 -g --duration $(( $# / 2 + 5 )) -o /data/local/tmp/perf.data > /dev/null 2>&1 &
sleep 1.5
for x in "$@"; do $A input keyevent $x; sleep 0.45; done
wait
adb -s $D pull /data/local/tmp/perf.data $S/$N.data > /dev/null
python3 $SP/report_sample.py -i $S/$N.data --proguard-mapping-file $R/app/home/build/outputs/mapping/release/mapping.txt > $S/$N.txt 2>/dev/null
python3 $H/agg.py $S/$N.txt dev.tvremote.home 'tvremote' ${TOP:-25}
$A input keyevent HOME
