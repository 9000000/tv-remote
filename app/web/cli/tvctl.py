"""Control the current TV from the command line.  Usage:
  tvctl.py info
  tvctl.py key HOME|BACK|DPAD_UP|DPAD_DOWN|DPAD_LEFT|DPAD_RIGHT|DPAD_CENTER|VOLUME_UP|VOLUME_DOWN|MUTE|POWER|MEDIA_PLAY_PAUSE|CHANNEL_UP ...
  tvctl.py app <url-or-package>   e.g. youtube://  https://www.youtube.com/watch?v=...  com.netflix.ninja
"""
import asyncio, json, sys
from pathlib import Path
from androidtvremote2 import AndroidTVRemote

import os
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from tvtools import CERT, KEY
import tvconfig
HOST = os.environ.get("TV_HOST") or tvconfig.load()["current"]        # the TV picked in the web UI unless TV_HOST says otherwise

def via_server(argv):
    """If server.py is running it owns the TV session (a second session's keys are ignored), so go through it."""
    import urllib.request
    def post(path, data):
        req = urllib.request.Request("http://127.0.0.1:8765" + path, json.dumps(data).encode(), {"Content-Type": "application/json"})
        return json.load(urllib.request.urlopen(req, timeout=10))
    try:
        urllib.request.urlopen("http://127.0.0.1:8765/api/state", timeout=2)
    except OSError:
        return None                                    # server not running -> talk to the TV directly
    cmd, args = argv[0], argv[1:]
    if cmd == "info":
        print(json.dumps(json.load(urllib.request.urlopen("http://127.0.0.1:8765/api/state")), indent=1)); return 0
    if cmd == "key":
        import time
        for k in args:
            post("/api/key", {"key": k}); time.sleep(0.3)
        print("sent (via server)", args); return 0
    if cmd == "app":
        print(post("/api/app", {"link": args[0]})); return 0
    return None


async def main(argv):
    if not argv:
        print(__doc__); return 2
    r = via_server(argv)
    if r is not None:
        return r
    remote = AndroidTVRemote("Claude Remote", CERT, KEY, HOST)
    await remote.async_generate_cert_if_missing()
    await remote.async_connect()
    try:
        cmd, args = argv[0], argv[1:]
        if cmd == "info":
            print(json.dumps({"device_info": remote.device_info, "is_on": remote.is_on,
                              "current_app": remote.current_app, "volume": remote.volume_info}, default=str, indent=1))
        elif cmd == "key":
            for k in args:
                remote.send_key_command(k.upper()); await asyncio.sleep(0.3)
            print("sent", args)
        elif cmd == "app":
            remote.send_launch_app_command(args[0]); print("launched", args[0])
        else:
            print(__doc__); return 2
        await asyncio.sleep(0.5)
    finally:
        remote.disconnect()
    return 0

sys.exit(asyncio.run(main(sys.argv[1:])))
