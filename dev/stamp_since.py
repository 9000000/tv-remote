#!/usr/bin/env python3
"""release.sh: docs/data/since.json "next" -> the version being released, and that version gets its date.
  dev/stamp_since.py <version> <yyyy-mm-dd>        (run again: nothing changes)
The file keeps one line per topic, so a merge of two new topics does not conflict."""
import json, os, sys

PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "docs", "data", "since.json")

def dump(j, path=PATH):
    with open(path, "w", encoding="utf-8") as f:
        f.write('{\n "dates": ' + json.dumps(j["dates"], ensure_ascii=False) + ',\n "topics": {\n')
        f.write(",\n".join("  " + json.dumps(k) + ": " + json.dumps(v, ensure_ascii=False) for k, v in j["topics"].items()))
        f.write("\n }\n}\n")

if __name__ == "__main__":
    ver, day = sys.argv[1:3]
    j = json.load(open(PATH, encoding="utf-8"))
    used = False
    for e in j["topics"].values():
        for k, v in e.items():
            if v == "next": e[k] = ver; used = True
    if used: j["dates"].setdefault(ver, day)
    dump(j)
