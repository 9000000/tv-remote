"""One TV at a time: the remote-protocol link (keys without ADB), its ADB, picture and input, and the stand-in the routes hold."""
import asyncio, json, os
from aiohttp import web
from androidtvremote2 import AndroidTVRemote, CannotConnect, ConnectionClosed, InvalidAuth
from adbtools import Adb
from appmeta import AppMeta
from tvtools import ADB, CERT, KEY
from screen import Screen, Control


class Tv:
    """The Android TV Remote protocol connection (remote keys, volume, power state) to one TV."""
    def __init__(self, host):
        self.host = host
        self.remote = AndroidTVRemote("Claude Remote", CERT, KEY, host or "0.0.0.0")
        self.available = False
        self.paired = None                                            # None: not known yet
        self.error = "" if host else "no TV selected"
        self._task = None

    async def start(self):
        if await self.remote.async_generate_cert_if_missing():
            os.chmod(KEY, 0o600)
        self.remote.add_is_available_updated_callback(lambda ok: setattr(self, "available", ok))
        if self.host:
            self._task = asyncio.create_task(self._connect_loop())

    async def stop(self):
        if self._task:
            self._task.cancel()
        try:
            self.remote.disconnect()
        except Exception:
            pass
        self.available = False

    async def _connect_loop(self):
        while True:
            try:
                await self.remote.async_connect()
                self.available, self.paired, self.error = True, True, ""
                self.remote.keep_reconnecting()
                return
            except InvalidAuth:
                self.paired, self.error = False, "not paired"
                return
            except (CannotConnect, ConnectionClosed, OSError) as e:
                self.error = f"cannot connect: {e!r}"
                await asyncio.sleep(5)

    async def pair_start(self):
        """Makes the TV show a 6-character code."""
        if self._task:
            self._task.cancel()
        try:
            self.remote.disconnect()
        except Exception:
            pass
        self.available = False
        await self.remote.async_start_pairing()

        async def abandoned():                                        # nobody typed the code: go back to normal connecting
            await asyncio.sleep(180)
            await self._connect_loop()
        self._task = asyncio.create_task(abandoned())

    async def pair_finish(self, code):
        await self.remote.async_finish_pairing(code)
        if self._task:
            self._task.cancel()
        self.error = ""
        self._task = asyncio.create_task(self._connect_loop())

    def state(self):
        r = self.remote
        return {"host": self.host, "connected": self.available, "paired": self.paired, "error": self.error,
                "is_on": r.is_on, "app": r.current_app, "volume": r.volume_info, "device": r.device_info}

    def need(self):
        if not self.available:
            raise web.HTTPServiceUnavailable(text=json.dumps({"error": self.error or "TV not connected"}),
                                             content_type="application/json")
        return self.remote


class Session:
    """Everything bound to the TV being controlled. Picking another TV swaps the whole thing."""
    def __init__(self, host):
        self.host, target = host, f"{host}:5555" if host else ""
        self.tv, self.adb, self.screen = Tv(host), Adb(target), Screen(target)
        self.meta, self.control, self.held, self.warm = AppMeta(ADB, target), Control(self.adb), {}, None

    async def start(self):
        await self.tv.start()
        if self.host:
            self.warm = asyncio.create_task(self._prewarm())

    async def _prewarm(self):
        """Read app names/icons in the background so the page finds them cached."""
        for _ in range(60):
            if (await self.adb.status())["state"] == "ready":
                break
            await asyncio.sleep(10)
        try:
            await self.adb.shell("pkill screenrecord")                # recorders orphaned by a previous run of this server
        except Exception:
            pass
        try:
            await asyncio.gather(*(self.meta.get(p, self.adb.apk_paths[p]) for p in await self.adb.apps()), return_exceptions=True)
        except Exception:
            pass

    async def close(self):
        if self.warm:
            self.warm.cancel()
        for t in self.held.values():
            t.cancel()
        await self.tv.stop()
        await self.control.close()


class Live:
    """Stands in for one part of the current Session, so the route handlers keep working after a TV switch."""
    def __init__(self, get):
        self.__dict__["_get"] = get

    def __getattr__(self, name):
        return getattr(self._get(), name)
