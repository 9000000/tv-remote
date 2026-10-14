"""Local web remote for Android TV / Google TV.  Run: ./venv/bin/python app/server.py  ->  http://127.0.0.1:8765

Binds to 127.0.0.1 only. Mutating endpoints require a JSON body (forces a CORS preflight) and a same-origin
Host/Origin, so other websites open in your browser cannot drive the TV.
"""
import argparse, asyncio, ipaddress, json, mimetypes, os, re, shlex, signal, sys, tempfile, time
from pathlib import Path
from aiohttp import web
from androidtvremote2 import CannotConnect, ConnectionClosed, InvalidAuth
from androidtvremote2.remotemessage_pb2 import RemoteKeyCode
import tvconfig
import scenes as scn
from adbtools import AdbError, PKG_RE, FP_RE, PROTECTED, my_fingerprint, memory_by_app
from tvtools import ADB, ROOT, SCRIPTS
from screen import scrcpy_launch, live_session, RESYNC
from session import Session, Live
from discover import port_open, discover

STARTED = time.time()                                                  # the page is read from disk on every load, this code only now

KEYS = {
    "UP": "DPAD_UP", "DOWN": "DPAD_DOWN", "LEFT": "DPAD_LEFT", "RIGHT": "DPAD_RIGHT", "OK": "DPAD_CENTER",
    "BACK": "BACK", "HOME": "HOME", "MENU": "MENU", "POWER": "POWER", "MUTE": "MUTE", "ASSIST": "SEARCH",   # over the remote protocol the search key opens the Assistant
    "VOL_UP": "VOLUME_UP", "VOL_DOWN": "VOLUME_DOWN", "PLAY_PAUSE": "MEDIA_PLAY_PAUSE",
    "NEXT": "MEDIA_NEXT", "PREV": "MEDIA_PREVIOUS", "CH_UP": "CHANNEL_UP", "CH_DOWN": "CHANNEL_DOWN",
    "SETTINGS": "SETTINGS", "INPUT": "TV_INPUT", "SEARCH": "SEARCH", "DEL": "DEL",
    "INFO": "INFO", "GUIDE": "GUIDE", "LIVE": "TV", "SUBTITLE": "CAPTIONS", "REWIND": "MEDIA_REWIND", "STOP": "MEDIA_STOP", "FFWD": "MEDIA_FAST_FORWARD",
    "SLEEP": "SLEEP", "WAKE": "WAKEUP",                               # "tắt TV" / "bật TV" spoken: never POWER, which would turn a TV that is off on
}
# The "More" keys go through ADB when it is open: this TV ignores some of them over the remote protocol (the input key, for one).
ADB_KEYS = {"INPUT": 178, "INFO": 165, "GUIDE": 172, "LIVE": 170, "SUBTITLE": 175, "CH_UP": 166, "CH_DOWN": 167, "REWIND": 89, "STOP": 86, "FFWD": 90}
OPENERS = {"MENU", "SETTINGS", "INPUT", "GUIDE", "LIVE"}             # keys that should bring up a screen of their own
APPS = {"settings": "android-app://com.android.tv.settings"}          # remote-protocol fallback when ADB is closed

WEB = ROOT / "app" / "shared" / "web"                             # the one page all three remotes serve (the two Android apps carry it as an asset)
TS_PKG = "com.tailscale.ipn"


@web.middleware
async def guard(request, handler):
    host = request.headers.get("Host", "").split(":")[0]
    if host not in ("127.0.0.1", "localhost"):
        raise web.HTTPForbidden(text="bad host")                      # DNS-rebinding guard
    if request.method == "POST":
        origin = request.headers.get("Origin")
        if origin and origin.split("//", 1)[-1] != request.headers.get("Host"):
            raise web.HTTPForbidden(text="cross-origin")
        if request.content_type not in ("application/json", "application/octet-stream"):
            raise web.HTTPUnsupportedMediaType(text="json only")
    return await handler(request)


async def body(request):
    try:
        return await request.json()
    except Exception:
        raise web.HTTPBadRequest(text="bad json")


def build(S):
    """S.cur is the current Session, S.cfg the saved TVs (tvconfig)."""
    routes = web.RouteTableDef()
    tv, adb, screen, meta, control = (Live(lambda n=n: getattr(S.cur, n)) for n in ("tv", "adb", "screen", "meta", "control"))

    async def switch(host):
        old, S.cur = S.cur, Session(host)
        await S.cur.start()
        await old.close()

    @routes.get("/")
    async def index(_):
        # The page names its scripts and its stylesheet with a stamp of their newest change, so a browser never pairs a new
        # page with scripts it kept from an older version (which showed the new layout unstyled, run by the old code).
        stamp = str(int(max(f.stat().st_mtime for f in WEB.rglob("*") if f.is_file())))
        html = re.sub(r'((?:src|href)="(?:style\.css|stats\.js|js/[^"?]+))"', r'\1?v=' + stamp + '"', (WEB / "index.html").read_text(encoding="utf-8"))
        html = html.replace("<html ", '<html data-core="mac" ', 1)        # which core serves the page decides what it shows (see core.js)
        return web.Response(text=html, content_type="text/html", headers={"Cache-Control": "no-store"})

    @routes.get("/api/state")
    async def state(_):
        entry = tvconfig.find(S.cfg, tv.host) or {}
        st = await adb.status()
        if st["state"] in ("ready", "unauthorized"):
            asyncio.create_task(keep_trusted(st["state"]))
        return web.json_response({**tv.state(), "adb": st, "name": entry.get("name", ""), "saved": bool(entry)})

    @routes.post("/api/adb/connect")
    async def adb_connect(request):
        await body(request)
        await adb.ensure()
        return web.json_response(await adb.status(force=True))

    @routes.post("/api/key")
    async def key(request):
        d = await body(request)
        k = str(d.get("key", "")).upper()
        name = KEYS.get(k) or (k if "KEYCODE_" + k in RemoteKeyCode.keys() else None)   # friendly alias or any raw key name
        if not name:
            raise web.HTTPBadRequest(text="unknown key")
        direction = str(d.get("dir") or "SHORT").upper()
        if direction not in ("SHORT", "START_LONG", "END_LONG"):
            raise web.HTTPBadRequest(text="bad dir")
        if (k in OPENERS or k in ADB_KEYS) and direction == "SHORT":
            try:
                front = lambda: adb.shell("dumpsys window | grep -m1 mCurrentFocus", timeout=5)
                before = (await front()).strip() if k in OPENERS else ""   # openers: say so when the screen ignores them
                if name == "SETTINGS":
                    await adb.shell("am start -a android.settings.SETTINGS")
                elif k in ADB_KEYS:
                    await adb.shell(f"input keyevent {ADB_KEYS[k]}")
                else:
                    tv.need().send_key_command(name, "SHORT")
                if k not in OPENERS:
                    return web.json_response({"ok": True})
                await asyncio.sleep(0.9)
                return web.json_response({"ok": True, "noop": bool(before) and (await front()).strip() == before})
            except web.HTTPException:
                raise
            except Exception:
                pass                                                  # ADB closed: just press the key
        if name == "SETTINGS" and direction != "END_LONG":            # the key itself does nothing on many TVs; the Settings app always opens
            try:
                await adb.shell("am start -a android.settings.SETTINGS")
                return web.json_response({"ok": True})
            except Exception:
                pass                                                  # ADB closed: fall back to the key
        if not tv.available and direction != "END_LONG" and (await adb.status())["state"] == "ready":
            # the remote protocol is down (e.g. Google's remote service crashed while the sound was captured): keys over ADB
            await adb.shell(("input keyevent --longpress " if direction == "START_LONG" else "input keyevent ") + "KEYCODE_" + name)
            return web.json_response({"ok": True, "via": "adb"})
        r, held = tv.need(), S.cur.held
        if direction != "SHORT" and name in held:
            held.pop(name).cancel()
        if direction == "START_LONG":                                 # safety net: never leave a key held if the page vanishes
            held[name] = asyncio.get_running_loop().call_later(8, lambda: (held.pop(name, None), tv.available and r.send_key_command(name, "END_LONG")))
        r.send_key_command(name, direction)
        return web.json_response({"ok": True})

    @routes.post("/api/app")
    async def app(request):
        d = await body(request)
        link = str(d.get("link", "")).strip()
        if not link:
            raise web.HTTPBadRequest(text="link required")
        if (await adb.status())["state"] == "ready":                  # ADB reports whether the TV really opened it
            try:
                await (adb.open_settings() if link == "settings" else adb.launch(link) if PKG_RE.match(link) else adb.open_link(link))
            except AdbError as e:
                return web.json_response({"error": str(e)}, status=400)
            return web.json_response({"ok": True, "via": "adb"})
        tv.need().send_launch_app_command(APPS.get(link, link))        # best effort: this TV often ignores it
        return web.json_response({"ok": True, "via": "remote"})

    @routes.post("/api/volume")
    async def volume(request):
        want = (await body(request)).get("level")
        info = tv.remote.volume_info or {}
        if not isinstance(want, int) or not 0 <= want <= int(info.get("max") or 100):
            raise web.HTTPBadRequest(text="bad level")
        if (await adb.status())["state"] == "ready":
            try:
                if await adb.set_volume_fast(want):
                    for _ in range(10):                               # the TV reports the new level over the remote link
                        if (tv.remote.volume_info or {}).get("level") == want:
                            return web.json_response({"ok": True, "via": "adb-fast"})
                        await asyncio.sleep(0.1)
                await adb.set_volume(want)
                return web.json_response({"ok": True, "via": "adb"})
            except AdbError:
                pass
        r = tv.need()
        for _ in range(60):                                           # no ADB: step with the volume keys
            cur = (r.volume_info or {}).get("level")
            if cur is None or cur == want:
                break
            r.send_key_command("VOLUME_UP" if cur < want else "VOLUME_DOWN")
            await asyncio.sleep(0.2)
        return web.json_response({"ok": True, "via": "keys", "level": (r.volume_info or {}).get("level")})

    @routes.post("/api/text")
    async def text(request):
        d = await body(request)
        tv.need().send_text(str(d.get("text", ""))[:500])
        return web.json_response({"ok": True})

    @routes.get("/api/screen")
    async def screen_(_):
        try:
            return web.Response(body=await screen.grab(), content_type="image/jpeg", headers={"Cache-Control": "no-store"})
        except Exception as e:
            return web.json_response({"error": f"screen: {e}"}, status=503)

    @routes.get("/api/stream")
    async def stream(request):
        resp = web.StreamResponse(headers={"Content-Type": "multipart/x-mixed-replace;boundary=ffmpeg", "Cache-Control": "no-store"})
        await resp.prepare(request)
        try:
            while True:
                t = time.time()
                sent = await live_session(S.cur.adb, resp.write, lambda: request.transport is not None and not request.transport.is_closing(),
                                          eco=request.query.get("q") != "hd", size=int(request.query.get("size", "960") or 960) if request.query.get("size", "960").isdigit() else 960)
                if request.transport is None or request.transport.is_closing():
                    break
                if sent == RESYNC:
                    continue
                if not sent or time.time() - t < 5:                   # recorder is not working: end so the page falls back
                    break
        except (ConnectionError, asyncio.CancelledError):
            pass
        return resp

    def adb_errors(fn):
        async def wrapped(request):
            try:
                return await fn(request)
            except AdbError as e:
                return web.json_response({"error": str(e)}, status=400)
            except web.HTTPException:
                raise
            except Exception as e:
                return web.json_response({"error": f"{e.__class__.__name__}: {e}"}, status=500)
        return wrapped

    # ---- the TV's "Allow USB debugging?" question: answered for one named key only (see AdbPrompt.kt) ----
    @routes.get("/api/adb/prompt")
    @adb_errors
    async def adb_prompt(_):
        p = await adb.prompt_read() if (await adb.status())["state"] == "ready" else None
        return web.json_response({"fingerprint": p[0] if p else ""})

    @routes.post("/api/adb/allow")
    @adb_errors
    async def adb_allow(request):
        d = await body(request)
        fp = str(d.get("fingerprint", "")).upper()
        if not FP_RE.fullmatch(fp):
            return web.json_response({"error": "bad fingerprint"}, status=400)
        async with prompt_lock:
            return web.json_response({"result": await adb.prompt_allow(fp, min(max(int(d.get("wait", 0) or 0), 0), 40), bool(d.get("others")))})

    @routes.post("/api/adb/deny")
    @adb_errors
    async def adb_deny(request):
        async with prompt_lock:
            return web.json_response({"ok": await adb.prompt_deny(str((await body(request)).get("fingerprint", "")))})

    prompt_lock = asyncio.Lock()
    trust = {"asked": 0, "fetched": set()}

    async def keep_trusted(st):
        """While ADB works, fetch a TV Setup pass for this TV once; when the TV asks about this computer, have TV Setup answer."""
        entry = tvconfig.find(S.cfg, tv.host)
        if not entry:
            return
        host = tv.host
        try:
            if st == "ready" and not entry.get("setup_pass") and host not in trust["fetched"]:
                trust["fetched"].add(host)
                token = await adb.setup_pass()
                if token:
                    entry["setup_pass"] = token; tvconfig.save(S.cfg)
            elif st == "unauthorized" and entry.get("setup_pass") and time.time() - trust["asked"] > 45:
                trust["asked"] = time.time()
                fp = my_fingerprint()
                if not fp:
                    return
                import aiohttp
                async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=70)) as s:
                    async with s.post(f"http://{host}:8765/api/adb/allow", json={"fingerprint": fp, "wait": 30, "others": True},
                                      headers={"Cookie": "tvr=" + entry["setup_pass"]}) as r:
                        if r.status in (401, 403):
                            entry.pop("setup_pass", None); tvconfig.save(S.cfg)   # "Unpair all" was pressed on the TV
                        elif r.status == 200 and (await r.json()).get("result") == "allowed":
                            print(f"TV Setup allowed this computer on {host}", flush=True)
                            await adb.ensure()
        except Exception:
            pass

    @routes.get("/api/apps")
    @adb_errors
    async def apps(request):
        pkgs = await adb.apps(request.query.get("system") == "1")
        off = await adb.disabled()
        return web.json_response({"apps": [{"pkg": p, "off": p in off, "keep": bool(PROTECTED.fullmatch(p)), "meta": meta.cached(p, adb.apk_paths[p])} for p in pkgs]})

    @routes.get("/api/apps/report")
    @adb_errors
    async def apps_report(_):
        """What an AI (or a person) needs to say which apps can go; the page turns it into a prompt to copy."""
        lst = lambda out: sorted(l[8:].strip() for l in out.splitlines() if l.startswith("package:"))
        # all at once: meminfo alone takes seconds on a slow TV
        head, user, system, off, mem, _ = await asyncio.gather(
            adb.shell("getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; grep MemTotal /proc/meminfo"),
            adb.shell("pm list packages -3 -e"), adb.shell("pm list packages -s -e"), adb.shell("pm list packages -d"), adb.shell("dumpsys meminfo"),
            adb.apps(False))                                          # the apk paths the names come from
        h = head.splitlines() + ["", "", ""]
        m = re.search(r"MemTotal:\s+(\d+)", head)
        user = lst(user)
        return web.json_response({
            "device": (h[0].strip() + " " + h[1].strip()).strip(), "android": h[2].strip(), "ram_mb": int(m.group(1)) // 1024 if m else 0,
            "user": user, "system": lst(system), "disabled": lst(off),
            "memory": [{"pkg": p, "mb": mb} for p, mb in memory_by_app(mem)],
            "labels": {p: (meta.cached(p, adb.apk_paths[p]) or {}).get("label") for p in user if p in adb.apk_paths and (meta.cached(p, adb.apk_paths[p]) or {}).get("label")},
        })

    @routes.get("/api/app/meta")
    @adb_errors
    async def app_meta(request):
        pkg = request.query.get("pkg", "")
        if pkg not in adb.apk_paths:
            raise web.HTTPNotFound()
        return web.json_response(await meta.get(pkg, adb.apk_paths[pkg]))

    @routes.get("/api/app/icon")
    async def app_icon(request):
        pkg = request.query.get("pkg", "")
        f = meta.icon_file(pkg)
        if not PKG_RE.match(pkg) or not f.exists():
            raise web.HTTPNotFound()
        data = f.read_bytes()
        kind = "image/png" if data[:4] == b"\x89PNG" else "image/webp" if data[:4] == b"RIFF" else "image/jpeg"
        return web.Response(body=data, content_type=kind, headers={"Cache-Control": "max-age=86400", "X-Content-Type-Options": "nosniff"})

    @routes.post("/api/app/{action}")
    @adb_errors
    async def app_action(request):
        action, pkg = request.match_info["action"], str((await body(request)).get("pkg", ""))
        if action not in ("launch", "stop", "uninstall", "disable", "enable"):
            raise web.HTTPNotFound()
        res = await getattr(adb, action)(pkg)
        return web.json_response({"ok": True, "result": res})

    @routes.get("/api/files")
    @adb_errors
    async def files(request):
        path, items = await adb.ls(request.query.get("path", ""))
        return web.json_response({"path": path, "items": items, "space": adb.space, "volumes": adb.volumes})

    @routes.get("/api/file")
    @adb_errors
    async def file_get(request):
        path = request.query.get("path", "")
        proc = await adb.open_pull(path)
        kind = mimetypes.guess_type(path)[0] or ""
        inline = request.query.get("inline") == "1" and kind in ("image/jpeg", "image/png", "image/webp", "image/gif")   # preview: bitmaps only
        resp = web.StreamResponse(headers={"Content-Type": kind if inline else "application/octet-stream", "X-Content-Type-Options": "nosniff",
                                           "Content-Disposition": '%s; filename="%s"' % ("inline" if inline else "attachment", path.rsplit("/", 1)[-1].replace('"', "_"))})
        await resp.prepare(request)
        while chunk := await proc.stdout.read(1 << 20):
            await resp.write(chunk)
        await proc.wait()
        return resp

    @routes.post("/api/file/delete")
    @adb_errors
    async def file_delete(request):
        await adb.delete(str((await body(request)).get("path", "")))
        return web.json_response({"ok": True})

    @routes.post("/api/file/{action}")
    @adb_errors
    async def file_action(request):
        action, d = request.match_info["action"], await body(request)
        path = str(d.get("path", ""))
        if action == "mkdir":
            await adb.mkdir(path)
        elif action == "rename":
            await adb.rename(path, str(d.get("name", "")))
        elif action == "open":
            await adb.open_on_tv(path)
        elif action == "install":
            return web.json_response(adb.job_install_path(path))
        else:
            raise web.HTTPNotFound()
        return web.json_response({"ok": True})

    @routes.post("/api/fetch")
    @adb_errors
    async def fetch(request):
        d = await body(request)
        return web.json_response(adb.job_fetch(str(d.get("url", "")).strip(), d.get("dest") or ""))

    @routes.post("/api/install")
    @adb_errors
    async def install(request):
        return web.json_response(adb.job_install_url(str((await body(request)).get("url", "")).strip()))

    @routes.post("/api/upload")
    @adb_errors
    async def upload(request):
        kind = request.query.get("kind", "file")                          # file -> push to dest, install -> adb install
        if kind not in ("file", "install"):
            raise web.HTTPBadRequest(text="bad kind")
        tmp = tempfile.TemporaryDirectory(prefix="tvup-")
        try:
            local = await adb.save_upload(request, tmp.name, request.query.get("name", "upload.bin"))
            return web.json_response(adb.job_from_local(local, tmp, kind, request.query.get("dest", "")))
        except BaseException:
            tmp.cleanup()
            raise

    @routes.post("/api/touch")
    @adb_errors
    async def touch(request):
        d = await body(request)
        x, y, a = float(d.get("x", 0)), float(d.get("y", 0)), d.get("a")
        if a == "tap":
            await control.send(control.touch(0, x, y), control.touch(1, x, y))
        elif a in ("down", "move", "up"):
            await control.send(control.touch({"down": 0, "up": 1, "move": 2}[a], x, y))
        elif a == "scroll":
            await control.send(control.scroll(x, y, float(d.get("dy", 0))))
        else:
            raise web.HTTPBadRequest(text="bad action")
        return web.json_response({"ok": True})

    @routes.post("/api/type")
    @adb_errors
    async def type_(request):
        d = await body(request)
        n, t = min(int(d.get("del") or 0), 500), str(d.get("text") or "")[:500]
        msgs = [control.key(67)] * n                                  # KEYCODE_DEL
        msgs += [control.key(112)] * min(int(d.get("fdel") or 0), 500)  # KEYCODE_FORWARD_DEL
        if t:
            msgs.append(control.text(t))
        if d.get("enter"):
            msgs.append(control.key(66))                              # KEYCODE_ENTER
        if msgs:
            await control.send(*msgs)
        return web.json_response({"ok": True})

    @routes.post("/api/setup")
    async def setup(request):
        d = await body(request)
        mode = d.get("mode")
        if mode not in ("apply", "undo"):
            raise web.HTTPBadRequest(text="bad mode")

        async def go(j):                                              # same script as the command line: deploy/tv_setup.py
            choice = [] if mode == "undo" else [*([] if d.get("anim", True) else ["--keep-anim"]), *([] if d.get("home", True) else ["--keep-home"]),
                      *(["--disable=" + ",".join(x for x in d["disable"] if PKG_RE.match(str(x)))] if isinstance(d.get("disable"), list) else [])]
            p = await asyncio.create_subprocess_exec(sys.executable, str(SCRIPTS / "tv_setup.py"), mode, adb.target, *(["--vi"] if d.get("lang") != "en" else []), *choice,
                                                     stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT)
            async for line in p.stdout:
                text = line.decode(errors="replace")
                j["log"] += text; j["msg"] = text.strip()[:140] or j["msg"]; j["pct"] = min(95, j["pct"] + 4)
            if await p.wait():
                raise AdbError(j["msg"] or "setup failed")
            adb.apk_paths.clear()
            return "xong"
        j = adb.job(go, f"setup {mode}")
        j["log"] = ""
        return web.json_response(j)

    @routes.get("/api/setup/plan")
    @adb_errors
    async def setup_plan(_):
        p = await asyncio.create_subprocess_exec(sys.executable, str(SCRIPTS / "tv_setup.py"), "plan", adb.target, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT)
        out, _ = await asyncio.wait_for(p.communicate(), 90)
        try:
            plan = json.loads(out.decode(errors="replace").strip().splitlines()[-1])
        except Exception:
            raise AdbError(out.decode(errors="replace").strip()[-200:] or "could not read the TV")
        await adb.apps(True)                                          # labels the apps tab already knows
        for a in plan["suggest"] + plan["apps"]:
            a["label"] = (meta.cached(a["pkg"], adb.apk_paths.get(a["pkg"], "")) or {}).get("label") or a["label"] or ".".join(a["pkg"].split(".")[-2 if len(a["pkg"].split(".")[-1]) <= 3 else -1:])
        plan["apps"].sort(key=lambda a: a["label"].lower())
        return web.json_response(plan)

    @routes.get("/api/job")
    async def job(request):
        j = adb.jobs.get(request.query.get("id", ""))
        return web.json_response(j) if j else web.json_response({"error": "unknown job"}, status=404)

    @routes.post("/api/wake")
    async def wake(request):
        await body(request)
        e = tvconfig.find(S.cfg, tv.host) or {}
        p = await asyncio.create_subprocess_exec(
            sys.executable, str(SCRIPTS / "wake.py"), "--wait", "60",
            env={**os.environ, "TV_HOST": tv.host, "TV_MAC": e.get("mac", ""), "TV_LAN": e.get("lan", ""), "RELAY_USER": e.get("relay_user", ""),
                 "RELAY_HOST": e.get("relay_host", ""), "RELAY_CMD": e.get("relay_cmd", "")},
            stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT)
        out, _ = await p.communicate()
        if p.returncode == 0 and not tv.remote.is_on:
            await asyncio.sleep(2)
            if tv.available:
                tv.remote.send_key_command("POWER")                   # screen-off but network alive
        return web.json_response({"ok": p.returncode == 0, "log": out.decode(errors="replace")[-400:]})

    # ---- TVs: saved list, discovery, first-time setup ----
    @routes.get("/api/tvs")
    async def tvs(_):
        return web.json_response({"current": tv.host, "tvs": S.cfg["tvs"]})

    @routes.get("/api/discover")
    async def discover_(_):
        return web.json_response(await discover())

    @routes.post("/api/tv/select")
    async def tv_select(request):
        host = str((await body(request)).get("host", "")).strip()
        try:
            ipaddress.ip_address(host)
        except ValueError:
            raise web.HTTPBadRequest(text="not an IP address")
        if tvconfig.find(S.cfg, host):
            S.cfg["current"] = host; tvconfig.save(S.cfg)
        await switch(host)
        return web.json_response({"ok": True})

    async def tv_info():
        """What the first-time setup needs to know about the current TV (needs ADB)."""
        out = await adb.shell("getprop ro.product.manufacturer; echo '#'; getprop ro.product.model; echo '#'; pm path " + TS_PKG
                              + "; echo '#'; pm path me.efesser.flauncher; echo '#'; ip addr show")
        part = [x.strip() for x in out.split("#", 4)] + [""] * 5
        ts = lan = mac = ""
        for block in re.split(r"^\d+: ", part[4], flags=re.M):         # one block per network interface
            ip = re.search(r"inet ([0-9.]+)/", block)
            ether = re.search(r"link/ether ([0-9a-f:]{17})", block)
            if not ip:
                continue
            if ip[1].startswith("100."):
                ts = ip[1]
            elif not lan and ether and ipaddress.ip_address(ip[1]).is_private and not ip[1].startswith("127."):
                lan, mac = ip[1], ether[1].upper()                    # the interface that is actually on the home network
        return {"model": f"{part[0]} {part[1]}".strip(), "tailscale_installed": "package:" in part[2], "tailscale_ip": ts, "lan_ip": lan,
                "mac": mac, "flauncher": "package:" in part[3]}

    @routes.get("/api/setup/status")
    async def setup_status(_):
        st = await adb.status(force=True)
        info = {}
        if st["state"] == "ready":
            try:
                info = await tv_info()
            except AdbError:
                pass
        return web.json_response({"host": tv.host, "adb": st, "remote": tv.state(), "saved": tvconfig.find(S.cfg, tv.host), **info})

    HEALTH = ("getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk; "
              "getprop ro.build.version.security_patch; getprop ro.board.platform; getprop ro.product.cpu.abi; cat /sys/devices/system/cpu/present; "
              "printf '\\043~\\043\\n'; head -1 /proc/stat; sleep 0.6; head -1 /proc/stat; printf '\\043~\\043\\n'; grep -E '^(MemTotal|MemAvailable):' /proc/meminfo; printf '\\043~\\043\\n'; df -k /data | tail -1; "
              "printf '\\043~\\043\\n'; cat /proc/uptime; printf '\\043~\\043\\n'; wm size; wm density; dumpsys display | grep -m1 -o 'fps=[0-9.]*'; printf '\\043~\\043\\n'; dumpsys wifi | grep -m1 mWifiInfo; "
              "printf '\\043~\\043\\n'; ip -o -4 addr show; printf '\\043~\\043\\n'; top -n 1 -b -m 7 -o %CPU,%MEM,ARGS -s 1 | tail -7")

    STATS = "/sdcard/Android/data/dev.tvremote.setup/files/stats"

    @routes.get("/api/stats")
    @adb_errors
    async def stats(request):
        """What the TV Setup app on the TV has recorded (app/tv .../Stats.kt): time per app per day, and memory/CPU/storage samples."""
        days = max(1, min(90, int(request.query.get("days", "7") or 7)))
        since = time.time() - days * 86400
        try:
            usage = json.loads(await adb.shell(f"cat {STATS}/usage.json 2>/dev/null"))
        except ValueError:
            usage = None
        samples = []
        for line in (await adb.shell(f"tail -n {days * 300} {STATS}/samples.jsonl 2>/dev/null")).splitlines():
            try:
                row = json.loads(line)
                if row[0] >= since:
                    samples.append(row)
            except (ValueError, IndexError, TypeError):
                pass
        try:                                                          # every package's display name, system apps included
            known = await asyncio.get_running_loop().run_in_executor(None, meta._names)
        except Exception:
            known = {}
        names = {pkg: known[pkg] for per_day in (usage or {}).values() for pkg in per_day if known.get(pkg)}
        return web.json_response({"days": days, "collector": usage is not None or bool(samples), "usage": usage or {}, "samples": samples, "labels": names})

    @routes.post("/api/free")
    @adb_errors
    async def free(_):
        """Closes the apps that are only waiting in the background (what is on screen stays); says how much memory came back, in MB."""
        async def avail():
            m = re.search(r"MemAvailable:\s+(\d+)", await adb.shell("cat /proc/meminfo"))
            return int(m.group(1)) // 1024 if m else 0
        before = await avail()
        await adb.shell("am kill-all")
        await asyncio.sleep(1.5)
        after = await avail()
        return web.json_response({"before": before, "after": after, "freed": max(0, after - before)})

    @routes.get("/api/health")
    @adb_errors
    async def health(_):
        """A snapshot of the TV for the Health tab: hardware, load, memory, storage, Wi-Fi, and how long ADB takes to answer."""
        t0 = time.monotonic()
        await adb.shell(":")
        ping = round((time.monotonic() - t0) * 1000)
        part = [x.strip() for x in (await adb.shell(HEALTH, timeout=20)).split("#~#")] + [""] * 8
        props = (part[0].splitlines() + [""] * 8)[:8]
        cpu = None
        stat = [[int(n) for n in line.split()[1:]] for line in part[1].splitlines() if line.startswith("cpu")]
        if len(stat) == 2:
            total, idle = sum(stat[1]) - sum(stat[0]), (stat[1][3] + stat[1][4]) - (stat[0][3] + stat[0][4])
            cpu = round(100 * (total - idle) / total) if total > 0 else None
        mem = {m[1]: int(m[2]) * 1024 for m in re.finditer(r"(?m)^(\w+):\s+(\d+) kB", part[2])}
        df = part[3].split()
        wifi = {k.strip(): v.strip() for k, v in (kv.split(":", 1) for kv in part[6].replace("mWifiInfo", "").split(",") if ":" in kv)}
        num = lambda s: int(m[0]) if (m := re.search(r"-?\d+", s or "")) else None
        top = []
        for line in part[8].splitlines():
            m = re.match(r"\s*([\d.]+)\s+([\d.]+)\s+(.+)", line)
            if m and not m[3].startswith("top -n"):
                name = re.sub(r"@[\d.]+-service.*$", "", re.sub(r"^(android|vendor\.\w+)\.hardware\.", "", m[3].split()[0].strip("[]")))   # system services: keep the part that says what it is
                top.append({"cpu": float(m[1]), "mem": float(m[2]), "name": name})
        cores = props[7].split("-")
        return web.json_response({
            "ping_ms": ping, "maker": props[0], "model": props[1], "android": props[2], "sdk": props[3], "patch": props[4], "chip": props[5], "abi": props[6],
            "cores": int(cores[-1]) + 1 if cores[-1].isdigit() else None, "cpu": cpu,
            "mem_total": mem.get("MemTotal"), "mem_free": mem.get("MemAvailable"),
            "disk_total": int(df[1]) * 1024 if len(df) > 3 and df[1].isdigit() else None, "disk_free": int(df[3]) * 1024 if len(df) > 3 and df[3].isdigit() else None,
            "uptime": float(part[4].split()[0]) if part[4].split() else None,
            "screen": (re.search(r"(\d+x\d+)", part[5]) or [""])[0], "density": num((re.search(r"density: (\d+)", part[5]) or ["", ""])[1]),
            "fps": round(float(m[1])) if (m := re.search(r"fps=([\d.]+)", part[5])) else None,
            "wifi": {"ssid": wifi.get("SSID", ""), "rssi": num(wifi.get("RSSI")), "speed": num(wifi.get("Link speed")), "freq": num(wifi.get("Frequency"))} if wifi.get("SSID") else None,
            "ips": [{"if": m[1], "ip": m[2]} for m in re.finditer(r"(?m)^\d+: (\S+)\s+inet ([\d.]+)", part[7]) if m[1] != "lo"],
            "top": top[:5], "remote": tv.state()})

    @routes.post("/api/search")
    @adb_errors
    async def search(request):
        """Hands a sentence to the TV's own search / Assistant, as if it had been spoken to the TV's remote."""
        q = str((await body(request)).get("query", "")).strip()
        if not q:
            raise web.HTTPBadRequest(text="empty query")
        out = await adb.shell("am start -a android.search.action.GLOBAL_SEARCH --es query " + shlex.quote(q) + " 2>&1")
        if "Error" in out:
            return web.json_response({"error": "TV này không có tìm kiếm / Assistant của Google"}, status=400)
        return web.json_response({"ok": True})

    # ---- automation: TV Setup's timers and limits (they run on the TV, see scenes.rules), and scenes ----
    @routes.get("/api/rules")
    @adb_errors
    async def rules_get(_):
        return web.json_response(await rules_with_names(None))

    @routes.post("/api/rules")
    @adb_errors
    async def rules_set(request):
        return web.json_response(await rules_with_names(await body(request)))

    async def rules_with_names(change):
        r = await scn.rules(adb, change)
        for l in r.get("limits", []):                                 # the names the Apps tab shows
            l["label"] = (meta.cached(l["pkg"], adb.apk_paths.get(l["pkg"], "")) or {}).get("label") or l.get("label") or l["pkg"]
        return r

    @routes.get("/api/scenes")
    async def scenes_get(request):
        if "download" in request.query:                               # "Export": a file to keep
            name = time.strftime("tv-remote-scenes-%Y-%m-%d.json")
            return web.Response(text=json.dumps(scn.backup(S.cfg.get("scenes", [])), ensure_ascii=False, indent=2), content_type="application/json",
                                headers={"Content-Disposition": f'attachment; filename="{name}"', "Cache-Control": "no-store"})
        return web.json_response({"scenes": S.cfg.get("scenes", [])})

    @routes.post("/api/scenes")
    async def scenes_set(request):
        S.cfg["scenes"] = scn.clean((await body(request)).get("scenes"))
        tvconfig.save(S.cfg)
        return web.json_response({"scenes": S.cfg["scenes"]})

    @routes.post("/api/scene/run")
    @adb_errors
    async def scene_run(request):
        d = await body(request)
        steps = d.get("steps") or next((x["steps"] for x in S.cfg.get("scenes", []) if x["id"] == d.get("id")), None)
        if steps is None:
            return web.json_response({"error": "no such scene"}, status=404)
        cleaned = scn.clean([{"name": "x", "steps": steps}])
        failed = await scn.run(adb, cleaned[0]["steps"] if cleaned else [])
        return web.json_response({"ok": not failed, "failed": failed or ""})

    # ---- 🎧 the TV's sound in this browser (see listen.js): Opus straight from scrcpy, or PCM made 24 kHz mono here ----
    @routes.get("/api/audio")
    async def audio(request):
        resp = web.StreamResponse(headers={"Content-Type": "application/octet-stream", "Cache-Control": "no-store"})
        await resp.prepare(request)
        opus = request.query.get("codec") == "opus"
        share = request.query.get("share") == "1"                     # the TV keeps playing too (playback capture, Android 13+)
        hq = request.query.get("hq") == "1"                           # high quality: Opus 160 kbit/s, or PCM as captured (48 kHz stereo)
        proc = port = writer = None
        try:
            if (await adb.status())["state"] != "ready":
                await resp.write(scn.packet(5, b"adb: not ready")); return resp
            sdk = int((await adb.shell("getprop ro.build.version.sdk")).strip() or 0)
            if sdk < 30:                                              # scrcpy captures sound from Android 11
                await resp.write(scn.packet(5, b"unsupported")); return resp
            if share and sdk < 33:                                    # too old to keep playing: the private way, and say so
                share = False
                await resp.write(scn.packet(3, b"noshare"))
            proc, port = await scrcpy_launch(S.cur.adb, "video=false control=false send_dummy_byte=true send_device_meta=false audio=true "
                                             + (f"audio_codec=opus audio_bit_rate={160000 if hq else 64000}" if opus else "audio_codec=raw")
                                             + (" audio_source=playback audio_dup=true" if share else ""))
            reader = None
            for _ in range(100):                                      # until the server listens, adb closes the socket at once
                reader, writer = await asyncio.open_connection("127.0.0.1", port)
                try:
                    first = await asyncio.wait_for(reader.readexactly(1), 4)
                except (asyncio.TimeoutError, asyncio.IncompleteReadError):
                    first = b""
                if first == b"\x00":
                    break
                writer.close(); writer = None
                if proc.returncode is not None:
                    break
                await asyncio.sleep(0.25)
            if not writer:
                raise AdbError("scrcpy-server did not start")
            if int.from_bytes(await reader.readexactly(4), "big") in (0, 1):   # the TV could not capture sound
                await resp.write(scn.packet(5, b"unsupported")); return resp
            whole = opus or hq                                        # 48 kHz stereo; else made 24 kHz mono here
            await resp.write(scn.packet(0, bytes([1 if opus else 2]) + (48000 if whole else 24000).to_bytes(4, "big") + bytes([2 if whole else 1])))
            carry = b""
            while True:
                head = await reader.readexactly(12)
                flags, size = int.from_bytes(head[:8], "big"), int.from_bytes(head[8:], "big")
                data = await reader.readexactly(size)
                if flags & (1 << 62):                                 # the codec header (OpusHead)
                    if opus:
                        await resp.write(scn.packet(1, data))
                    continue
                if not whole:
                    data, carry = scn.downmix(data, carry)
                await resp.write(scn.packet(2, data))
        except (ConnectionError, asyncio.CancelledError, asyncio.IncompleteReadError):
            pass
        except AdbError as e:
            try:
                await resp.write(scn.packet(5, str(e).encode()))
            except ConnectionError:
                pass
        finally:
            if proc:                                                  # on its own: the page has gone and this task may be cancelled
                asyncio.create_task(scn.end_capture(adb, proc, writer, port))
        return resp

    @routes.post("/api/shell")
    @adb_errors
    async def shell(request):
        """Terminal tab: one command as the TV's shell user."""
        cmd = str((await body(request)).get("cmd", "")).strip()
        if not cmd:
            raise web.HTTPBadRequest(text="empty command")
        out = await adb.shell(cmd + " 2>&1", timeout=30)
        return web.json_response({"out": out.replace("\r\n", "\n")[-200000:]})

    @routes.post("/api/tv/save")
    @adb_errors
    async def tv_save(request):
        """Remember the current TV. Once it has a Tailscale IP that becomes its address, so it works from anywhere."""
        d = await body(request)
        info = await tv_info() if (await adb.status())["state"] == "ready" else {}
        old = tv.host
        host = info.get("tailscale_ip") or old
        if not host:
            raise web.HTTPBadRequest(text="no TV selected")
        entry = tvconfig.upsert(S.cfg, {"name": str(d.get("name") or "").strip()[:60] or info.get("model") or host, "host": host,
                                        "lan": info.get("lan_ip", ""), "mac": info.get("mac", "")}, replace_host=old)
        tvconfig.save(S.cfg)
        if host != old:
            await switch(host)
        return web.json_response({"ok": True, "tv": entry, "moved": host != old})

    @routes.post("/api/tv/remove")
    async def tv_remove(request):
        host = str((await body(request)).get("host", ""))
        tvconfig.remove(S.cfg, host); tvconfig.save(S.cfg)
        if host == tv.host:
            await switch(S.cfg["current"])
        return web.json_response({"ok": True})

    @routes.get("/api/tailscale")
    @adb_errors
    async def tailscale_state(request):
        return web.json_response({"installed": "package:" in await adb.shell("pm path " + TS_PKG + "; echo ."), "ip": await adb.tailscale_ip()})

    @routes.post("/api/tailscale/start")
    @adb_errors
    async def tailscale_start(request):
        await body(request)
        return web.json_response(adb.job_tailscale_start())

    # ---- turning ADB on with the remote buttons alone (port 6466), as the phone app does ----
    SETUP_APK = "https://github.com/hoangtran0410/tv-remote/releases/latest/download/tv-setup.apk"
    DOWNLOADER, SETUP_PKG = "com.esaba.downloader", "dev.tvremote.setup"
    SETUP_CODE = "downloader://2397346"                               # Downloader's own link for the AFTVnews code of SETUP_APK: opens Downloader alone, no "Open with"
    assist = {"status": "idle", "msg": "", "stop": False, "installed": False, "task": None}

    def front():
        return (tv.remote.current_app or "") if tv.available else ""

    async def until_front(want, seconds, stop=lambda: False):
        for _ in range(int(seconds * 5)):
            if stop():
                return False
            if want(front()):
                return True
            await asyncio.sleep(0.2)
        return False

    async def send_link(link):
        for _ in range(30):                                           # the TV may have dropped us after a link it disliked
            if tv.available:
                break
            await asyncio.sleep(0.2)
        tv.need().send_launch_app_command(link)

    async def press_ok():
        tv.need().send_key_command("DPAD_CENTER")

    async def open_on_tv(pkg, stop=lambda: False):
        """Bring an app to the front without ADB: ask for it, else open its Play Store page and press its main button."""
        if front() == pkg:
            return True
        await send_link(f"market://launch?id={pkg}")
        if await until_front(lambda a: a == pkg, 4, stop):
            return True
        await send_link(f"https://play.google.com/store/apps/details?id={pkg}")
        if not await until_front(lambda a: a == "com.android.vending", 10, stop):
            return False
        await asyncio.sleep(3)                                        # the page draws, with its main button in focus
        await press_ok()
        return await until_front(lambda a: a == pkg, 15, stop)

    async def answer_chooser(stop):
        for _ in range(3):                                            # "Open with": OK takes what is offered
            await asyncio.sleep(1.5)
            if front() != "android" or stop():
                break
            await press_ok()

    async def install_setup():
        """Get the TV Setup app onto the TV: hand the TV the download link; a TV without an app for links gets Downloader first."""
        stop = lambda: assist["stop"]
        say = lambda m: assist.update(msg=m)
        try:
            if not front():
                assist.update(status="blind", msg="TV này không cho biết đang mở gì nên không tự làm được. Làm theo cách “Tự làm” bên dưới.")
                return
            say("Đang gửi đường dẫn tải cho TV...")
            await send_link(SETUP_APK)
            await until_front(lambda a: a in (DOWNLOADER, "android"), 6, stop)
            if front() == "android":
                say("TV đang hỏi “Mở bằng”: đang chọn giúp...")
                await answer_chooser(stop); await asyncio.sleep(1.5)
            a = front()
            direct = not stop() and (a == DOWNLOADER or "chrome" in a or "browser" in a)
            there = direct or await open_on_tv(DOWNLOADER, stop)
            while not there and not stop():                          # its Play Store page was told to install it
                say("TV chưa có app Downloader: đang cài từ CH Play. Nếu TV không tự cài, chọn “Cài đặt” trên màn hình TV. Khi TV báo cài xong, bấm “Đã cài xong”.")
                for _ in range(600):
                    if stop() or assist["installed"] or front() == DOWNLOADER:
                        break
                    await asyncio.sleep(0.2)
                if stop():
                    break
                assist["checking"] = True                             # one check at a time: more presses of "Installed" change nothing
                say("Đang mở Downloader trên TV để kiểm tra...")
                there = front() == DOWNLOADER or await open_on_tv(DOWNLOADER, stop)
                assist.update(installed=False, checking=False)
            if stop():
                assist.update(status="idle", msg=""); return
            if not direct:
                say("Đang đưa đường dẫn tải cho Downloader...")
                await asyncio.sleep(3)
                await send_link(SETUP_CODE)
                await answer_chooser(stop); await asyncio.sleep(2.5)
            was = front()
            say("TV đang tải TV Setup...")
            await until_front(lambda x: x != was, 120, stop)          # until the TV's installer (or its permission screen) comes up
            assist.update(status="idle" if stop() else "look", msg="")
        except (web.HTTPException, ConnectionClosed, CannotConnect, OSError) as e:
            assist.update(status="failed", msg=f"Mất kết nối với TV giữa chừng, thử lại: {getattr(e, 'text', '') or e}")
        except asyncio.CancelledError:
            assist.update(status="idle", msg="")
        finally:
            assist["checking"] = False

    @routes.get("/api/assist")
    async def assist_state(_):
        return web.json_response({"status": assist["status"], "msg": assist["msg"], "front": front(), "connected": tv.available,
                                  "checking": bool(assist.get("checking") or assist["installed"])})

    @routes.post("/api/assist")
    async def assist_do(request):
        d = await body(request)
        do = str(d.get("do", ""))
        if do == "stop":
            assist["stop"] = True
            return web.json_response({"ok": True})
        if do == "installed":
            if not assist.get("checking"):
                assist["installed"] = True
            return web.json_response({"ok": True})
        tv.need()
        if do in ("open", "settings") and (assist["status"] == "running" or assist.get("opening")):   # one job on the TV at a time
            return web.json_response({"error": "TV đang làm việc vừa yêu cầu, chờ vài giây rồi bấm lại"}, status=409)
        if do == "setup":
            old = assist.get("task")
            if assist["status"] == "running" and assist["stop"] and old and not old.done():   # stopped a moment ago, still winding down
                old.cancel()
                try:
                    await old
                except (asyncio.CancelledError, Exception):
                    pass
            if assist["status"] != "running":
                assist.update(status="running", msg="", stop=False, installed=False, checking=False)
                assist["task"] = asyncio.create_task(install_setup())
            return web.json_response({"ok": True})
        if do == "open":                                              # an app by package name, or any link
            what = str(d.get("what", "")).strip()
            if not what:
                raise web.HTTPBadRequest(text="what required")
            if ":" in what:
                await send_link(what)
                return web.json_response({"ok": True, "front": front()})
            if not PKG_RE.match(what):
                raise web.HTTPBadRequest(text="not a package name")
            assist["opening"] = True
            try:
                return web.json_response({"ok": await open_on_tv(what), "front": front()})
            finally:
                assist["opening"] = False
        if do == "settings":                                          # every way a TV may open its Settings when asked
            is_settings = lambda a: "settings" in a.lower()
            tries = [lambda: tv.need().send_key_command("SETTINGS"), lambda: send_link("android-app://com.android.tv.settings"),
                     lambda: send_link("intent:#Intent;action=android.settings.SETTINGS;end")]
            ok = is_settings(front())
            for attempt in tries:
                if ok:
                    break
                try:
                    r = attempt()
                    if asyncio.iscoroutine(r):
                        await r
                except (web.HTTPException, ConnectionClosed, OSError, ValueError):
                    continue
                ok = await until_front(is_settings, 2.4)
            return web.json_response({"ok": ok, "blind": not front()})
        raise web.HTTPBadRequest(text="unknown action")

    @routes.post("/api/pair/start")
    async def pair_start(request):
        await body(request)
        try:
            await tv.pair_start()
        except (CannotConnect, ConnectionClosed, OSError) as e:
            return web.json_response({"error": f"không tới được dịch vụ remote của TV (cổng 6467): {e}"}, status=400)
        return web.json_response({"ok": True})

    @routes.post("/api/pair/finish")
    async def pair_finish(request):
        code = str((await body(request)).get("code", "")).strip()
        try:
            await tv.pair_finish(code)
        except InvalidAuth:
            return web.json_response({"error": "sai mã, thử lại"}, status=400)
        except (CannotConnect, ConnectionClosed, OSError, ValueError) as e:
            return web.json_response({"error": f"ghép thất bại, bấm Ghép lại: {e}"}, status=400)
        for _ in range(30):
            if tv.available:
                break
            await asyncio.sleep(0.2)
        return web.json_response({"ok": True, "connected": tv.available})

    @routes.get("/api/about")
    async def about(request):
        try:
            version = (ROOT / "VERSION").read_text().strip()
        except OSError:
            version = ""
        released = ""                                                # the day of that version, from its heading in the changelog
        try:
            m = re.search(r"^## " + re.escape(version) + r" - (\d{4})-(\d{2})-(\d{2})", (ROOT / "CHANGELOG.md").read_text(), re.M)
            if m and version:
                released = f"{m.group(3)}/{m.group(2)}/{m.group(1)}"
        except OSError:
            pass
        tv_web = f"http://{tv.host}:8765" if tv.host and tv.host not in ("127.0.0.1", "localhost") and await port_open(tv.host, 8765) else ""   # the TV Setup app's page
        stale = any(f.stat().st_mtime > STARTED for f in Path(__file__).parent.glob("*.py"))   # updated since start: only a restart loads it
        return web.json_response({"version": version, "released": released, "tv_web": tv_web, "stale": stale})

    @routes.post("/api/shutdown")
    async def shutdown(request):
        await body(request)
        asyncio.get_running_loop().call_later(0.3, S.stop.set)        # after this reply has gone out
        return web.json_response({"ok": True})

    app_ = web.Application(middlewares=[guard])
    app_.add_routes(routes)
    for name in ("style.css", "stats.js", "manifest.json", "icon-180.png", "icon-192.png", "icon-512.png", "icon-maskable.png"):
        app_.router.add_get("/" + name, lambda _, f=WEB / name: web.FileResponse(f, headers={"Cache-Control": "no-store"}))
    app_.router.add_static("/js", WEB / "js")
    return app_


class State:
    pass


ADB_OWNED = [False]                                                    # whether this run started the adb server (port 5037)


def release_adb(cfg):
    """Stopping leaves nothing behind: the adb server this run started goes too (with its links to the TVs). An adb that was
    already running belongs to someone else (Android Studio...): only this program's TVs are disconnected from it."""
    import subprocess
    try:
        if ADB_OWNED[0]:
            subprocess.run([ADB, "kill-server"], capture_output=True, timeout=5)
        else:
            for host in {t.get("host") for t in cfg["tvs"]} | {cfg["current"]}:
                if host:
                    subprocess.run([ADB, "disconnect", f"{host}:5555"], capture_output=True, timeout=5)
    except (OSError, subprocess.SubprocessError):
        pass


async def main(a):
    S = State()
    S.cfg = tvconfig.load()
    S.stop = asyncio.Event()
    ADB_OWNED[0] = not await port_open("127.0.0.1", 5037)
    for sig in ("SIGINT", "SIGTERM", "SIGHUP"):                        # Ctrl+C, the Mac app's Quit, closing the Terminal window
        try:
            asyncio.get_running_loop().add_signal_handler(getattr(signal, sig), S.stop.set)
        except (AttributeError, NotImplementedError, ValueError):
            pass                                                      # Windows: Ctrl+C still ends below as KeyboardInterrupt
    S.cur = Session(a.tv or S.cfg["current"])
    await S.cur.start()
    runner = web.AppRunner(build(S))
    await runner.setup()
    await web.TCPSite(runner, "127.0.0.1", a.port).start()
    print(f"Remote: http://127.0.0.1:{a.port}  (TV {S.cur.host or 'not chosen yet'})", flush=True)
    await S.stop.wait()                                               # the page's "Tắt" button, or Ctrl+C
    print("Stopping.", flush=True)
    await S.cur.close()
    try:
        await asyncio.wait_for(runner.cleanup(), 4)
    except asyncio.TimeoutError:
        pass                                                          # open live views are cancelled when the loop ends
    release_adb(S.cfg)


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--tv", default=os.environ.get("TV_HOST", ""), help="TV IP for this run (default: the one last picked in the web UI)")
    p.add_argument("--port", type=int, default=8765)
    try:
        asyncio.run(main(p.parse_args()))
    except KeyboardInterrupt:
        release_adb(tvconfig.load())
