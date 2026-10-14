"""Fetches adb and ffmpeg into ./tools when this computer has neither, so nobody has to install them by hand (Homebrew,
winget). Every launcher runs it before the server; with both tools there it does nothing. A failed download is not fatal:
the remote still works, the screen view (ffmpeg) or the ADB features say what is missing.

adb: Google's own platform-tools zip. ffmpeg: the static builds of the ffmpeg-static project (npm), pinned to one release
and checked against the SHA-256 below, so a changed file is refused.
"""
import gzip, hashlib, io, os, platform, subprocess, sys, urllib.request, zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tvtools import ADB, EXE, FFMPEG, ROOT, utf8_console

TOOLS = ROOT / "tools"
ADB_URL = "https://dl.google.com/android/repository/platform-tools-latest-{}.zip"
FFMPEG_URL = "https://github.com/eugeneware/ffmpeg-static/releases/download/b6.0/ffmpeg-{}.gz"
FFMPEG_SHA = {
    "darwin-arm64": "6be74d6f449889c2e87a75873894f8520cad56c08ac76f2a628d85b0519daaca",
    "darwin-x64": "a12354fce7eb62361473bbe10d53a1893695babd35869ec8e92e5dfea8d0440b",
    "win32-x64": "450d66226c79405c724e821f291cab0911e934bfa9fa2231adcab587f3e07b50",
    "linux-x64": "17c1ae10b52ac499180679fe6ba77e17642390c4eedb0f1e3b0ac045da55128f",
}


def have(path):
    return os.path.isabs(path) and os.path.exists(path)                # find() returns the bare name when nothing was found


def target():
    arch = {"x86_64": "x64", "amd64": "x64", "arm64": "arm64", "aarch64": "arm64"}.get(platform.machine().lower(), "")
    osname = {"darwin": "darwin", "win32": "win32", "linux": "linux"}.get(sys.platform, "")
    return osname, arch


def fetch(url, what):
    print(f"Đang tải {what} / Downloading {what}...", flush=True)
    with urllib.request.urlopen(url, timeout=60) as r:
        return r.read()


def get_adb(osname):
    if osname not in ("darwin", "linux", "win32"):
        return "no build for this system"
    data = fetch(ADB_URL.format({"win32": "windows"}.get(osname, osname)), "adb (Google platform-tools)")
    zipfile.ZipFile(io.BytesIO(data)).extractall(TOOLS)
    for f in (TOOLS / "platform-tools").iterdir():                     # zipfile drops the exec bit
        if f.is_file() and not f.suffix:
            f.chmod(0o755)


def get_ffmpeg(osname, arch):
    key = f"{osname}-{arch}"
    if not FFMPEG_SHA.get(key):
        return "no build for this system"
    gz = fetch(FFMPEG_URL.format(key), "ffmpeg")
    if hashlib.sha256(gz).hexdigest() != FFMPEG_SHA[key]:
        return "the download does not match its checksum"
    data = gzip.decompress(gz)
    out = TOOLS / "ffmpeg" / "bin" / f"ffmpeg{EXE}"
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_suffix(".part")
    tmp.write_bytes(data)
    tmp.chmod(0o755)
    if osname == "darwin":                                             # Apple silicon kills unsigned binaries: sign it for this Mac
        subprocess.run(["codesign", "-s", "-", "-f", str(tmp)], capture_output=True)
    tmp.replace(out)


def main():
    utf8_console()
    osname, arch = target()
    TOOLS.mkdir(exist_ok=True)
    for name, path, get, hint in (
        ("adb", ADB, lambda: get_adb(osname), "brew install android-platform-tools / winget install Google.PlatformTools / apt install adb"),
        ("ffmpeg", FFMPEG, lambda: get_ffmpeg(osname, arch), "brew install ffmpeg / winget install Gyan.FFmpeg / apt install ffmpeg"),
    ):
        if have(path):
            continue
        try:
            why = get()
        except Exception as e:
            why = str(e)
        if why:
            print(f"Chưa tải được {name} ({why}). Có thể tự cài: {hint}\n"
                  f"Could not fetch {name} ({why}). Install it yourself: {hint}", flush=True)


if __name__ == "__main__":
    main()
