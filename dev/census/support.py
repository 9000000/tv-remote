#!/usr/bin/env python3
"""Support codes from TV Setup (src/support.js). Needs the admin token: ~/.tv-remote-release/crash-token (or $CRASH_TOKEN).

  support.py                         the last 50 codes: when sent, which TV, whether it lets you look now
  support.py CODE                    what the TV sent (its facts and log)
  support.py CODE /api/state         while the TV lets support look: GET that route on the TV, print the answer
  support.py CODE --all              the usual reads in one go: about, state, diagnose, setup/version, wirelog
  support.py CODE sh "dumpsys package dev.tvremote.setup.keys"   any read-only command (TV Setup newer than 0.26.2: settings, dumpsys,
                                     getprop, pm list/dump, appops get, logcat -d); /api/support/prefs, /api/support/logcat
  support.py --delete CODE           forget it
  support.py --url http://127.0.0.1:8798   a local `wrangler dev`
"""
import argparse, datetime, json, os, sys, time, urllib.error, urllib.parse, urllib.request

URL = "https://tvr-census.99-hoangtran.workers.dev"
ALL = ["/api/about", "/api/state", "/api/diagnose", "/api/setup/version", "/api/wirelog"]


def token():
    t = os.environ.get("CRASH_TOKEN", "").strip()
    if not t:
        try:
            t = open(os.path.expanduser("~/.tv-remote-release/crash-token")).read().strip()
        except OSError:
            sys.exit("no token: ~/.tv-remote-release/crash-token or $CRASH_TOKEN")
    return t


def call(base, path, params=None, body=None, method="GET"):
    q = urllib.parse.urlencode(params or {})
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(f"{base}{path}" + (f"?{q}" if q else ""), data=data, method=method,
                                 headers={"Authorization": "Bearer " + token(), "User-Agent": "tvr-support", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        sys.exit(f"{e.code} {e.read().decode()[:300]}")


def when(ms, now):
    if not ms:
        return "-"
    s = (now - ms) / 1000
    return f"{int(s)} s ago" if abs(s) < 120 else f"{int(s / 60)} min ago" if abs(s) < 7200 else datetime.datetime.fromtimestamp(ms / 1000).strftime("%Y-%m-%d %H:%M")


def ask(base, code, path, wait=40):
    q = call(base, "/v1/support/ask", body={"code": code, "path": path}, method="POST")
    end = time.time() + wait
    while time.time() < end:
        time.sleep(1.5)
        a = call(base, "/v1/support/answer", {"code": code, "id": q["id"]})
        if not a.get("pending"):
            return a
    return {"status": 0, "body": f"no answer in {wait} s: the TV stopped looking, or is asleep"}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("code", nargs="?")
    ap.add_argument("path", nargs="?")
    ap.add_argument("cmd", nargs="?")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--delete", action="store_true")
    ap.add_argument("--url", default=URL)
    a = ap.parse_args()
    base = a.url.rstrip("/")
    if not a.code:
        r = call(base, "/v1/support")
        for s in r["support"]:
            live = "LIVE " + str(int((s["live"] - r["now"]) / 60000) + 1) + " min" if s["live"] > r["now"] else ""
            print(f'{s["code"]}  {when(s["made"], r["now"]):>16}  {s["app"]} {s["v"]:<8} {s["model"]:<28} seen {when(s["seen"], r["now"])}  {live}')
        return
    if a.delete:
        print(call(base, "/v1/support", {"code": a.code}, method="DELETE"))
        return
    if a.path == "sh":                                       # a read-only command on the TV, over TV Setup's own ADB
        a.path = "/api/support/sh?c=" + urllib.parse.quote(a.cmd or "", safe="").replace("~", "%7E")
    if a.path or a.all:
        for p in ([a.path] if a.path else ALL):
            r = ask(base, a.code, p)
            print(f"== {p}  ({r.get('status')})")
            print(r.get("body", ""))
        return
    r = call(base, "/v1/support", {"code": a.code})
    live = f"lets support look for {int((r['live'] - r['now']) / 60000) + 1} more min" if r["live"] > r["now"] else "not open now"
    print(f'{r["code"]}  {r["app"]} {r["v"]}  {r["model"]}  sent {when(r["made"], r["now"])}, seen {when(r["seen"], r["now"])}, {live}\n')
    print(r["report"])


if __name__ == "__main__":
    main()
