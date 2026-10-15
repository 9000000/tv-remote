"""📺📺 Several saved TVs at once, for a home with many TVs, a homestay or a hotel: one table that compares them, and one APK
installed (or one app removed, or a restart) on each TV picked, each with its own progress.

The computer does it from its list of TVs (tvs.json), the phone app from its own (Fleet.kt has the same reading and actions).
TV Setup has no list: it manages only the TV it runs on.
"""
import asyncio, re, time, uuid

import tvshell
from adbtools import Adb, AdbError, PKG_RE, PROTECTED, background

# one round trip per TV: what the table shows, the parts between "#~#" lines
INFO = ("getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk; "
        "printf '\\043~\\043\\n'; df -k /data | tail -1; printf '\\043~\\043\\n'; grep MemTotal /proc/meminfo; printf '\\043~\\043\\n'; "
        "pm list packages -3; printf '\\043~\\043\\n'; dumpsys package dev.tvremote.setup | grep -m1 versionName")
PARALLEL = 3                                                          # installs at once: an APK goes over the home Wi-Fi to each TV


def parse(out):
    """What INFO printed; Fleet.parse in Fleet.kt reads it the same way (tests/fleet_cases.json)."""
    p = out.replace("\r", "").split("#~#\n") + [""] * 5
    head = p[0].split("\n") + [""] * 4
    o = {"model": f"{head[0].strip()} {head[1].strip()}".strip(), "android": head[2].strip(),
         "sdk": int(head[3].strip()) if head[3].strip().isdigit() else 0, "disk_total": 0, "disk_free": 0}
    df = p[1].split()
    if len(df) >= 4 and df[1].isdigit() and df[3].isdigit():
        o["disk_total"], o["disk_free"] = int(df[1]) * 1024, int(df[3]) * 1024
    m = re.search(r"MemTotal:\s+(\d+)", p[2])
    o["ram"] = int(m[1]) * 1024 if m else 0
    o["apps"] = sorted(line[8:].strip() for line in p[3].split("\n") if line.startswith("package:"))
    m = re.search(r"versionName=(\S+)", p[4])
    o["setup"] = m[1] if m else ""
    return o


class Fleet:
    def __init__(self):
        self.adbs, self.jobs = {}, {}

    def adb(self, host, cur):
        """The current TV's own connection, or one kept per other TV (so the next action does not connect again)."""
        if cur is not None and cur.host == host:
            return cur.adb
        if host not in self.adbs:
            self.adbs[host] = Adb(f"{host}:5555")
        return self.adbs[host]

    async def info(self, host, adb):
        o = {"host": host, "state": "closed"}
        try:
            o["state"] = (await asyncio.wait_for(adb.status(), 15))["state"]
            if o["state"] != "ready":
                return o
            o.update(parse(await adb.shell(INFO, timeout=15)))
        except (AdbError, asyncio.TimeoutError) as e:
            o["state"], o["error"] = "error", str(e) or "timed out"
        return o

    def run(self, title, targets, work):
        """One job for many TVs: work(adb, part) runs for each (host, adb), a few at a time; each part keeps its own progress."""
        jid = uuid.uuid4().hex[:8]
        j = {"id": jid, "title": title, "status": "running", "t": time.time(),
             "tvs": {h: {"status": "waiting", "msg": "waiting", "pct": 0} for h, _ in targets}}
        self.jobs[jid] = j
        for old in sorted(self.jobs.values(), key=lambda x: x["t"])[:-10]:
            self.jobs.pop(old["id"], None)
        gate = asyncio.Semaphore(PARALLEL)

        async def one(host, adb):
            part = j["tvs"][host]
            async with gate:
                part.update(status="running", msg="connecting")
                try:
                    st = (await asyncio.wait_for(adb.status(force=True), 20))["state"]
                    if st != "ready":
                        raise AdbError("adb:" + st)
                    part["msg"] = await work(adb, part) or "done"
                    part.update(status="done", pct=100)
                except Exception as e:
                    part.update(status="error", msg=str(e) or e.__class__.__name__)

        async def all_of_them():
            await asyncio.gather(*(one(h, a) for h, a in targets))
            j["status"] = "done"
        background(all_of_them())
        return j


async def install(adb, part, local):
    part["pct"] = 10
    await adb.install(local, part)
    return "installed"


def action(name, pkg):
    """The work for one of the bulk actions that are not an install, refused here before any TV is touched."""
    if name == "uninstall":
        if not PKG_RE.fullmatch(pkg or ""):
            raise ValueError("bad package name")
        if PROTECTED.fullmatch(pkg):
            raise AdbError("protected: the TV needs this app")

        async def go(adb, part):
            await adb.uninstall(pkg)
            return "uninstalled"
        return go
    if name == "reboot":
        async def go(adb, part):
            await adb.shell(tvshell.REBOOT_PREP, timeout=15)
            try:
                await adb.run("reboot", timeout=10)                   # the link drops as the TV goes down: not an error
            except AdbError:
                pass
            adb._st_t = 0
            return "restarting"
        return go
    raise ValueError("unknown action")
