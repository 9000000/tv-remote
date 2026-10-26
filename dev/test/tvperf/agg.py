#!/usr/bin/env python3
# agg.py <report_sample.txt> [thread-prefix] [filter] [n]: inclusive ms per frame (one sample = 1 ms), each frame counted once a sample
import sys, re, collections
path = sys.argv[1]; thr = sys.argv[2] if len(sys.argv) > 2 else ''; flt = sys.argv[3] if len(sys.argv) > 3 else 'tvremote'; n = int(sys.argv[4]) if len(sys.argv) > 4 else 45
inc = collections.Counter(); threads = collections.Counter(); total = 0
cur = None; frames = set()
def flush():
    global total
    if cur is None: return
    threads[cur] += 1
    if cur.startswith(thr):
        total += 1
        for f in frames: inc[f] += 1
for line in open(path, errors='replace'):
    if not line.startswith('\t') and line.strip():
        flush(); cur = line.split('\t')[0]; frames = set()
    elif line.startswith('\t'):
        m = re.match(r'\s+[0-9a-f]+ (.*?) \(', line)
        if m:
            s = re.sub(r'\$\$ExternalSyntheticLambda\d+', '$L', m.group(1))
            if re.search(flt, s): frames.add(s[:150])
flush()
print('threads:', ', '.join(f'{k} {v}' for k, v in threads.most_common(8)))
print(f'{thr or "all"}: {total} ms')
for f, c in inc.most_common(n): print(f'{c:6d} {f}')
