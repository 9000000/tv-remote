"""Scenes, TV Setup's timers and limits, and the TV's sound for the computer's page. The same rules as the apps' Kotlin
(app/shared .../Scenes.kt, RulesClient.kt, AudioOut.kt Downmix); tests in app/web/tests/test_scenes.py.

A scene is one button that does several things on the TV:
  {"id": "s1", "name": "Xem phim", "icon": "🎬", "steps": [{"do": "wake"}, {"do": "app", "pkg": "com.google.android.youtube.tv"}]}
Steps: wake, sleep, key {key}, app {pkg}, link {url}, volume {level}, wait {s}, timer {min}. The computer keeps its list in tvs.json.
"""
import array, asyncio, base64, json, re, shlex, sys, time

from adbtools import AdbError, PKG_RE

KEYS = {"UP": 19, "DOWN": 20, "LEFT": 21, "RIGHT": 22, "OK": 23, "BACK": 4, "HOME": 3, "MENU": 82, "POWER": 26, "WAKE": 224, "SLEEP": 223,
        "VOL_UP": 24, "VOL_DOWN": 25, "MUTE": 164, "PLAY_PAUSE": 85, "NEXT": 87, "PREV": 88, "SETTINGS": 176, "INPUT": 178, "DEL": 67, "ENTER": 66,
        "ASSIST": 219, "CH_UP": 166, "CH_DOWN": 167, "INFO": 165, "GUIDE": 172, "LIVE": 170, "SUBTITLE": 175, "REWIND": 89, "STOP": 86, "FFWD": 90,
        **{str(d): 7 + d for d in range(10)}}
STEPS = {"wake", "sleep", "key", "app", "link", "volume", "wait", "timer"}
LINK_RE = re.compile(r"^[a-zA-Z][a-zA-Z0-9+.-]*:\S+$")
SETUP = "dev.tvremote.setup"


def _num(v, lo, hi, default=0, cast=int):
    try:
        return max(lo, min(hi, cast(v)))
    except (TypeError, ValueError):
        return default


def clean(scenes):
    """Only what can be run: at most 24 scenes of 30 known steps with sane values, and a name."""
    out = []
    for sc in (scenes if isinstance(scenes, list) else [])[:24]:
        if not isinstance(sc, dict):
            continue
        steps = []
        for s in (sc.get("steps") if isinstance(sc.get("steps"), list) else [])[:30]:
            what = s.get("do") if isinstance(s, dict) else None
            if what not in STEPS:
                continue
            c = {"do": what}
            if what == "key":
                k = str(s.get("key", "")).upper()
                if k not in KEYS:
                    continue
                c["key"] = k
            elif what == "app":
                if not PKG_RE.match(str(s.get("pkg", ""))):
                    continue
                c["pkg"] = s["pkg"]
            elif what == "link":
                u = str(s.get("url", "")).strip()
                if not LINK_RE.match(u) or len(u) > 2000:
                    continue
                c["url"] = u
            elif what == "volume":
                c["level"] = _num(s.get("level"), 0, 100)
            elif what == "wait":
                c["s"] = _num(s.get("s", 1), 0, 30, 1, float)
            elif what == "timer":
                c["min"] = _num(s.get("min"), 0, 720)
            steps.append(c)
        name = str(sc.get("name", "")).strip()[:40]
        if not name or not steps:
            continue
        sid = str(sc.get("id", ""))
        out.append({"id": sid if re.fullmatch(r"[A-Za-z0-9_-]{1,24}", sid) else "s%x" % time.time_ns(), "name": name,
                    "icon": str(sc.get("icon", ""))[:8], "steps": steps})
    return out


async def rules(adb, change=None):
    """TV Setup's timers and limits over ADB (a broadcast only the shell may send). {"setup": "missing" | "old"} without them."""
    arg = "" if change is None else " --es set " + base64.b64encode(json.dumps(change).encode()).decode()
    out = await adb.shell(f"am broadcast --include-stopped-packages -n {SETUP}/.RulesReceiver{arg} 2>&1", timeout=15)
    m = re.search(r'data="([A-Za-z0-9+/=]+)"', out)
    if not m:
        return {"setup": "old" if "package:" in await adb.shell(f"pm path {SETUP} 2>/dev/null") else "missing"}
    state = json.loads(base64.b64decode(m.group(1)))
    if state.get("wanted") and not state.get("running"):              # a receiver may not start the service from the background; the shell may
        await adb.shell(f"am start-foreground-service -n {SETUP}/.RemoteService 2>&1")
    return state


async def launch(adb, pkg):
    if not PKG_RE.match(pkg or ""):
        raise AdbError("bad package name")
    for cat in ("LEANBACK_LAUNCHER", "LAUNCHER"):                       # a TV app's own screen first, then a phone-style one
        lines = (await adb.shell(f"cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.{cat} {pkg} 2>/dev/null")).strip().splitlines()
        comp = lines[-1].strip() if lines else ""
        if re.fullmatch(r"[\w.]+/[\w.$]+", comp) and comp.startswith(pkg + "/") and "Error" not in await adb.shell(f"am start -n {shlex.quote(comp)} 2>&1"):
            return
    raise AdbError("this app cannot be opened (no launcher screen)")


def backup(scenes):
    """The scenes as a file to keep ("Export"); the page reads the same shape back ("Import"), also a bare list."""
    return {"app": "tv-remote", "kind": "scenes", "version": 1, "scenes": scenes}


async def volume(adb, level):
    level = int(level)
    bad = lambda out: any(w in out for w in ("Exception", "Error", "Unknown", "not found"))
    if bad(await adb.shell(f"cmd media_session volume --stream 3 --set {level} 2>&1")) and bad(await adb.shell(f"media volume --stream 3 --set {level} 2>&1")):
        raise AdbError("volume command failed")
    # some TVs take the command and keep their volume (HDMI-CEC, the Google TV emulator): then the keys, one step each
    m = re.search(r"volume is (\d+)", await adb.shell("cmd media_session volume --stream 3 --get 2>&1") or "")
    if m and int(m.group(1)) != level:
        code = 24 if level > int(m.group(1)) else 25
        await adb.shell("; ".join([f"input keyevent {code}"] * min(abs(level - int(m.group(1))), 30)))


async def run(adb, steps):
    """Runs the steps in order; a step that fails is skipped and named. None when all went through."""
    failed = []
    for i, s in enumerate(steps[:30], 1):
        try:
            what = s["do"]
            if what in ("wake", "sleep", "key"):
                await adb.shell(f"input keyevent {224 if what == 'wake' else 223 if what == 'sleep' else KEYS[s['key']]}")
            elif what == "app":
                await launch(adb, s["pkg"])
            elif what == "link":
                out = await adb.shell(f"am start -a android.intent.action.VIEW -d {shlex.quote(s['url'])} 2>&1")
                if "Error" in out or "unable to resolve" in out.lower():
                    raise AdbError("the TV has no app that can open this")
            elif what == "volume":
                await volume(adb, s["level"])
            elif what == "wait":
                await asyncio.sleep(s["s"])
            elif what == "timer":
                r = await rules(adb, {"do": "sleep", "minutes": s["min"]})
                if "setup" in r or "error" in r:
                    raise AdbError("the sleep timer needs TV Setup 2.13.0 or newer on the TV")
        except (AdbError, KeyError) as e:
            failed.append(f"{i}: {e}")
    return "; ".join(failed) or None


def downmix(data, carry=b""):
    """48 kHz stereo 16-bit PCM -> 24 kHz mono (each sample the mean of two frames' four), for a browser without an Opus decoder.
    Returns (pcm, leftover bytes to put in front of the next piece)."""
    data = carry + data
    n = len(data) // 8 * 8
    s = array.array("h", data[:n])
    if sys.byteorder == "big":
        s.byteswap()
    out = array.array("h", ((s[i] + s[i + 1] + s[i + 2] + s[i + 3]) // 4 for i in range(0, len(s), 4)))
    if sys.byteorder == "big":
        out.byteswap()
    return out.tobytes(), data[n:]


def packet(kind, data=b""):
    """The framing the page reads (listen.js): 1 byte kind, 4 bytes length, the data."""
    return bytes([kind]) + len(data).to_bytes(4, "big") + data
