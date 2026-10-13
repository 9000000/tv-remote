"""App name + icon for the app list.

Read straight out of the APK on the TV: the APK is a zip, so we range-read only its directory, manifest, resource
table and the icon file over adb instead of pulling the whole thing. Apps whose icon is a vector drawable fall back to
the Play Store listing. Results are cached in .cache/apps (keyed by the APK path, which changes on every update).
"""
import asyncio, html, io, json, logging, re, shlex, subprocess, urllib.request, zipfile
from pathlib import Path

from pyaxmlparser import APK
from pyaxmlparser.axmlprinter import AXMLPrinter

logging.getLogger("pyaxmlparser").setLevel(logging.CRITICAL)
CACHE = Path(__file__).resolve().parent.parent.parent / ".cache" / "apps"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
BITMAP = (".png", ".webp", ".jpg", ".jpeg")
CHUNK = 262144


class RemoteFile:
    """Seekable read-only view of a file on the TV, fetched in 256 KB chunks with `dd`."""
    def __init__(self, adb, target, path):
        self.cmd, self.path, self.pos, self.chunks = [adb, "-s", target, "exec-out"], shlex.quote(path), 0, {}
        size = self._x(f"stat -c %s {self.path}").strip()
        if not size.isdigit():
            raise OSError("cannot read APK on TV")
        self.size = int(size)

    def _x(self, sh):
        return subprocess.run(self.cmd + [sh], capture_output=True, timeout=60).stdout

    def seekable(self):
        return True

    def tell(self):
        return self.pos

    def seek(self, off, whence=0):
        self.pos = off if whence == 0 else self.pos + off if whence == 1 else self.size + off
        return self.pos

    def read(self, n=-1):
        end = self.size if n is None or n < 0 else min(self.size, self.pos + n)
        if end <= self.pos:
            return b""
        a, b = self.pos // CHUNK, (end - 1) // CHUNK
        i = a
        while i <= b:
            if i in self.chunks:
                i += 1; continue
            j = i
            while j <= b and j not in self.chunks:
                j += 1
            data = self._x(f"dd if={self.path} bs={CHUNK} skip={i} count={j - i} 2>/dev/null")
            for k in range(i, j):
                self.chunks[k] = data[(k - i) * CHUNK:(k - i + 1) * CHUNK]
            i = j
        out = b"".join(self.chunks[k] for k in range(a, b + 1))[self.pos - a * CHUNK:end - a * CHUNK]
        self.pos = end
        return out


def _res_id(ref):
    return int(ref[1:].split(":")[-1], 16)


def _bitmap(res, ref):
    """Resource reference -> (best bitmap file name | None, [xml file names])."""
    best, best_dpi, xmls = None, -1, []
    for config, name in res.get_resolved_res_configs(_res_id(ref)):
        if not isinstance(name, str):
            continue
        if name.lower().endswith(BITMAP):
            dpi = config.get_density()
            dpi = 1 if dpi > 640 else dpi + 2                        # nodpi/anydpi bitmaps rank below real densities
            if dpi > best_dpi:
                best, best_dpi = name, dpi
        elif name.endswith(".xml"):
            xmls.append(name)
    return best, xmls


def _from_apk(rf):
    z = zipfile.ZipFile(rf)
    names = set(z.namelist())
    mini = io.BytesIO()
    with zipfile.ZipFile(mini, "w") as m:                            # pyaxmlparser only needs these two members
        for n in ("AndroidManifest.xml", "resources.arsc"):
            if n in names:
                m.writestr(n, z.read(n))
    apk = APK(mini.getvalue(), raw=True)
    out = {"label": "", "bg": "", "adaptive": False}
    label = apk.get_app_name() or ""
    out["label"] = "" if label.startswith("@") else label
    res = apk.get_android_resources()
    if not res:
        return out, None

    def color(ref):
        try:
            for _, v in res.get_resolved_res_configs(_res_id(ref)):
                if isinstance(v, str) and re.fullmatch(r"#[0-9a-fA-F]{8}", v):
                    return "#" + v[3:]
        except Exception:
            pass
        return ""

    def resolve(ref, depth=0):
        name, xmls = _bitmap(res, ref)
        if name in names:
            return name
        if depth >= 2:
            return None
        for x in xmls:                                               # adaptive-icon / layer-list: follow its layers
            if x not in names:
                continue
            tree = AXMLPrinter(z.read(x)).get_xml_obj()
            layers = [(e.tag, e.get(ANDROID_NS + "drawable") or e.get(ANDROID_NS + "src") or "") for e in tree.iter()]
            layers.sort(key=lambda l: {"foreground": 0, "background": 2}.get(l[0], 1))
            for tag, v in layers:
                if tag == "background" and tree.tag == "adaptive-icon":
                    out["bg"] = out["bg"] or (color(v) if v.startswith("@") else "#" + v[3:] if re.fullmatch(r"#[0-9a-fA-F]{8}", v) else "")
            for tag, v in layers:
                if v.startswith("@"):
                    got = resolve(v, depth + 1)
                    if got:
                        out["adaptive"] = out["adaptive"] or (tree.tag == "adaptive-icon" and tag == "foreground")
                        return got
        return None

    for attr in ("icon", "banner", "logo"):
        ref = apk.get_attribute_value("application", attr)
        if ref and ref.startswith("@"):
            try:
                name = resolve(ref)
            except Exception:
                name = None
            if name:
                return out, z.read(name)
    return out, None


def _from_play(pkg):
    req = urllib.request.Request(f"https://play.google.com/store/apps/details?id={pkg}&hl=vi", headers={"User-Agent": "Mozilla/5.0"})
    page = urllib.request.urlopen(req, timeout=10).read().decode("utf-8", "replace")
    img = re.search(r'<meta property="og:image" content="([^"]+)"', page)
    title = re.search(r'<meta property="og:title" content="([^"]+)"', page)
    label = re.sub(r"\s+[-–]\s+[^-–]*Google Play$", "", html.unescape(title.group(1))) if title else ""
    icon = urllib.request.urlopen(html.unescape(img.group(1)) + "=s128", timeout=10).read() if img else None
    return label, icon


class AppMeta:
    def __init__(self, adb_bin, target):
        self.adb_bin, self.target = adb_bin, target
        self.sem, self.inflight = asyncio.Semaphore(4), {}

    def cached(self, pkg, apk_path):
        try:
            m = json.loads((CACHE / f"{pkg}.json").read_text())
            return m if m.get("apk") == apk_path else None
        except (OSError, ValueError):
            return None

    def icon_file(self, pkg):
        return CACHE / f"{pkg}.img"

    def _resolve(self, pkg, apk_path):
        meta, icon, failed = {"apk": apk_path, "label": "", "bg": "", "adaptive": False, "icon": False}, None, False
        try:
            got, icon = _from_apk(RemoteFile(self.adb_bin, self.target, apk_path))
            meta.update(got)
        except Exception:
            failed = True                                             # TV unreachable or odd APK: don't cache the miss
        if not icon:
            meta["bg"], meta["adaptive"] = "", False
            try:
                label, icon = _from_play(pkg)
                meta["label"] = meta["label"] or label
            except Exception:
                pass
        meta["icon"] = bool(icon)
        if not failed:
            CACHE.mkdir(parents=True, exist_ok=True)
            if icon:
                self.icon_file(pkg).write_bytes(icon)
            (CACHE / f"{pkg}.json").write_text(json.dumps(meta))
        return meta

    async def get(self, pkg, apk_path):
        m = self.cached(pkg, apk_path)
        if m:
            return m
        if pkg not in self.inflight:
            async def work():
                try:
                    async with self.sem:
                        return await asyncio.get_running_loop().run_in_executor(None, self._resolve, pkg, apk_path)
                finally:
                    self.inflight.pop(pkg, None)
            self.inflight[pkg] = asyncio.create_task(work())
        return await asyncio.shield(self.inflight[pkg])
