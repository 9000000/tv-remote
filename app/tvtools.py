"""Where the external tools live. Works on macOS, Linux and Windows.

adb / ffmpeg are taken from PATH, else from ./tools (where install-windows.cmd puts platform-tools), else Homebrew.
"""
import os, shutil, sys
from pathlib import Path

APP = Path(__file__).resolve().parent                                 # source of the web app
ROOT = APP.parent                                                     # repo root: cert/key, tvs.json, vendor/, tools/, .cache/
SCRIPTS = ROOT / "scripts"
EXE = ".exe" if os.name == "nt" else ""


def find(name, *extra):
    for cand in (shutil.which(name), *extra, f"/opt/homebrew/bin/{name}", f"/usr/local/bin/{name}"):
        if cand and Path(cand).exists():
            return str(cand)
    return name                                                       # let the OS report "not found" when it is used


ADB = find("adb", ROOT / "tools" / "platform-tools" / f"adb{EXE}")
FFMPEG = find("ffmpeg", *sorted((ROOT / "tools").glob(f"ffmpeg*/bin/ffmpeg{EXE}")))
CERT, KEY = str(ROOT / "cert.pem"), str(ROOT / "key.pem")             # this computer's identity towards the TVs it is paired with


def utf8_console():
    """Vietnamese messages must not crash on a Windows console that is not UTF-8."""
    for s in (sys.stdout, sys.stderr):
        try:
            s.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass
