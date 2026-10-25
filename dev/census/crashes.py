#!/usr/bin/env python3
"""The census worker's crash reports (POST /v1/crash). Needs the admin token: ~/.tv-remote-release/crash-token (or $CRASH_TOKEN).

  crashes.py                    top crashes as a table (most often first)
  crashes.py --v 0.3.1 --app pc only one version / app
  crashes.py SIG                one crash with its stack and device (SIG = the first letters of the sig column)
  crashes.py --delete SIG       remove one (all its versions)
  crashes.py --url http://127.0.0.1:8787   a local `wrangler dev`
"""
import argparse, json, os, sys, urllib.parse, urllib.request

URL = "https://tvr-census.99-hoangtran.workers.dev"


def token():
    t = os.environ.get("CRASH_TOKEN", "").strip()
    if not t:
        try:
            t = open(os.path.expanduser("~/.tv-remote-release/crash-token")).read().strip()
        except OSError:
            sys.exit("no token: ~/.tv-remote-release/crash-token or $CRASH_TOKEN")
    return t


def call(base, path, params, method="GET"):
    q = urllib.parse.urlencode({k: v for k, v in params.items() if v})
    req = urllib.request.Request(f"{base}{path}?{q}", method=method, headers={"Authorization": "Bearer " + token(), "User-Agent": "tvr-crashes"})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        sys.exit(f"{e.code} {e.read().decode()[:200]}")


def table(rows):
    if not rows:
        print("(no crashes)")
        return
    print(f"{'sig':<10} {'n':>4} {'inst':>4} {'app':<5} {'v':<8} {'last':<10} {'where':<6} kind / msg")
    for r in rows:
        print(f"{r['sig'][:8]:<10} {r['n']:>4} {r['installs']:>4} {r['app']:<5} {r['v']:<8} {r['last']:<10} {r['loc']:<6} {r['kind']}: {(r['msg'] or '')[:70]}")


def one(rows):
    for r in rows:
        dev = " ".join(str(r[k]) for k in ("brand", "model", "android", "os", "arch", "java") if r.get(k))
        print(f"== {r['sig']}  {r['app']} {r['v']}  x{r['n']} ({r['installs']} installs)  {r['first']} .. {r['last']}  [{r['loc']}]\n   device: {dev}\n   {r['kind']}: {r['msg']}\n")
        print(r["stack"] or "(no stack)", "\n")


def main():
    a = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    a.add_argument("sig", nargs="?")
    a.add_argument("--v", default="")
    a.add_argument("--app", default="")
    a.add_argument("--url", default=URL)
    a.add_argument("--delete", metavar="SIG")
    o = a.parse_args()
    if o.delete:
        sig = full(o, o.delete)
        print(call(o.url, "/v1/crashes", {"sig": sig}, "DELETE"))
    elif o.sig:
        one(call(o.url, "/v1/crashes", {"sig": full(o, o.sig)})["crashes"])
    else:
        table(call(o.url, "/v1/crashes", {"v": o.v, "app": o.app})["crashes"])


def full(o, prefix):
    """The sig column shows 8 characters: find the whole one."""
    hits = {r["sig"] for r in call(o.url, "/v1/crashes", {})["crashes"] if r["sig"].startswith(prefix)}
    if len(hits) != 1:
        sys.exit(f"{len(hits)} crashes start with {prefix!r}")
    return hits.pop()


if __name__ == "__main__":
    main()
