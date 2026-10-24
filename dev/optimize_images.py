#!/usr/bin/env python3
"""Shrink every image tracked by git without a visible change. Dry run by default; `--write` applies.

    python3 dev/optimize_images.py [--write] [--min-gain 3] [--ssim 0.985] [paths...]

Per format (a file is only replaced when the result is >= --min-gain % smaller, so a second run changes nothing):
  PNG        lossless: Pillow optimize, and a palette when the picture has <= 256 colours; pixels are compared after.
  JPEG       jpegtran -optimize -progressive -copy none (lossless, strips EXIF) when installed; then, only for files
             that are not "strict", a re-encode at q=88 kept (only when it was saved above that) when SSIM vs the original >= --ssim.
  WebP       lossless files: cwebp -lossless -z 9 (pixels compared after); lossy files: re-encode at q=80, same SSIM gate.
             Animated WebP is left alone.
Never touched (the file name or bytes are pinned elsewhere, so a change breaks something):
  docs/home/*        TV Home wallpapers + thumbnails: SHA-256 in docs/home/wallpapers.json, signed (dev/sign), already
                     1920x1080 progressive q82/90 (dev/gen/wallpapers.py). To shrink them, change the quality THERE and re-sign.
  docs/launchers/*   launcher gallery pictures: SHA-256 in docs/launchers/launchers.json, signed (dev/sign/sign_launchers.py).
Lossless only (no re-encode): app icons / banners (app/**/res, icon-*.png, app/desktop) and anything under a `strict` rule
below, so what Android, the stores or the icon builder read stays the same pixels.
Formats and names never change: code and the site refer to files by name and extension.
SSIM (8x8 blocks of the luma) needs numpy; without it only the lossless steps run.
"""
import argparse, io, os, re, shutil, subprocess, sys, tempfile
from PIL import Image, ImageChops

try:
    import numpy  # noqa: F401  needed only by the lossy re-encodes (their SSIM gate)
    LOSSY = True
except ImportError:
    LOSSY = False

ROOT = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True,
                      cwd=os.path.dirname(os.path.abspath(__file__))).stdout.strip()
EXT = re.compile(r"\.(png|jpe?g|webp)$", re.I)
# Hash-pinned or signed: never touched (see the docstring).
PINNED = [re.compile(p) for p in (r"^docs/home/", r"^docs/launchers/")]
# Lossless only: icons and banners with a strict spec.
STRICT = [re.compile(p) for p in (r"^app/.*/res/", r"/icon[^/]*\.png$", r"^app/desktop/", r"/banner[^/]*\.png$", r"^docs/img/(app-|logo)[^/]*\.png$")]


def tracked():
    out = subprocess.run(["git", "ls-files", "-z"], capture_output=True, cwd=ROOT).stdout.decode().split("\0")
    return [f for f in out if f and EXT.search(f)]


def ssim(a, b):
    """Mean SSIM of the luma over 8x8 blocks (a, b: PIL images of the same size). Needs numpy."""
    a, b = a.convert("L"), b.convert("L")
    if a.size != b.size:
        return 0.0
    c1, c2 = (0.01 * 255) ** 2, (0.03 * 255) ** 2
    import numpy as np
    x, y = np.asarray(a, dtype="float64"), np.asarray(b, dtype="float64")
    h, w = (x.shape[0] // 8) * 8, (x.shape[1] // 8) * 8
    if h == 0 or w == 0:
        return 1.0 if (x == y).all() else 0.0
    x = x[:h, :w].reshape(h // 8, 8, w // 8, 8).transpose(0, 2, 1, 3).reshape(-1, 64)
    y = y[:h, :w].reshape(h // 8, 8, w // 8, 8).transpose(0, 2, 1, 3).reshape(-1, 64)
    mx, my = x.mean(1), y.mean(1)
    cv = ((x - mx[:, None]) * (y - my[:, None])).mean(1)
    s = ((2 * mx * my + c1) * (2 * cv + c2)) / ((mx ** 2 + my ** 2 + c1) * (x.var(1) + y.var(1) + c2))
    return float(s.mean())


def same_pixels(a, b):
    a, b = a.convert("RGBA"), b.convert("RGBA")
    return a.size == b.size and ImageChops.difference(a, b).getbbox() is None


def png(data, strict, args):
    im = Image.open(io.BytesIO(data))
    im.load()
    best = None
    def keep(b):
        nonlocal best
        if same_pixels(im, Image.open(io.BytesIO(b))) and (best is None or len(b) < len(best)):
            best = b
    kw = {"optimize": True}
    if "icc_profile" in im.info:
        kw["icc_profile"] = im.info["icc_profile"]
    b = io.BytesIO(); im.save(b, "PNG", **kw); keep(b.getvalue())
    rgba = im.convert("RGBA")
    if rgba.getcolors(256) is not None:  # <= 256 colours: a palette loses nothing (keep() re-checks the pixels)
        q = rgba.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
        b = io.BytesIO(); q.save(b, "PNG", optimize=True); keep(b.getvalue())
    return best


JPEG_Q, WEBP_Q = 88, 80
STD_LUMA = sum([16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56, 14, 17, 22, 29, 51, 87, 80, 62,
                              18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92, 49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99])


def jpeg_quality(im):
    """The libjpeg quality a JPEG was saved with, from its luma table (101 when unknown, so it counts as 'high')."""
    t = getattr(im, "quantization", None)
    if not t or 0 not in t:
        return 101
    scale = 100.0 * sum(t[0]) / STD_LUMA  # libjpeg: table = std * scale / 100
    return 100 - scale / 2 if scale <= 100 else 5000 / scale


def jpeg(data, strict, args):
    best = data
    if shutil.which("jpegtran"):
        r = subprocess.run(["jpegtran", "-optimize", "-progressive", "-copy", "none"], input=data, capture_output=True)
        if r.returncode == 0 and 0 < len(r.stdout) < len(best):
            best = r.stdout
    if not strict and LOSSY:
        src = Image.open(io.BytesIO(data)); src.load()
        # One target quality, and only for files saved above it: a second run finds q <= JPEG_Q and leaves them (no generation loss).
        if jpeg_quality(src) > JPEG_Q + 1:
            b = io.BytesIO()
            src.save(b, "JPEG", quality=JPEG_Q, optimize=True, progressive=True, subsampling="keep")
            if len(b.getvalue()) < len(best) * (1 - args.min_gain / 100) and ssim(src, Image.open(io.BytesIO(b.getvalue()))) >= args.ssim:
                best = b.getvalue()
    return best


def webp(data, strict, args):
    im = Image.open(io.BytesIO(data))
    if getattr(im, "n_frames", 1) > 1:
        return None
    im.load()
    lossless = data[12:16] == b"VP8L"
    best = data
    if lossless:
        if shutil.which("cwebp"):
            with tempfile.TemporaryDirectory() as d:
                s, o = os.path.join(d, "in.png"), os.path.join(d, "out.webp")
                im.save(s, "PNG")
                subprocess.run(["cwebp", "-quiet", "-lossless", "-z", "9", "-exact", "-metadata", "none", s, "-o", o], check=True)
                b = open(o, "rb").read()
        else:
            t = io.BytesIO(); im.save(t, "WEBP", lossless=True, quality=100, method=6, exact=True); b = t.getvalue()
        if len(b) < len(best) and same_pixels(im, Image.open(io.BytesIO(b))):
            best = b
    elif not strict and LOSSY:
        # One target quality: re-encoding the result at the same q gains < min-gain, so a second run leaves it.
        t = io.BytesIO(); im.save(t, "WEBP", quality=WEBP_Q, method=6)
        if len(t.getvalue()) < len(best) * (1 - args.min_gain / 100) and ssim(im, Image.open(io.BytesIO(t.getvalue()))) >= args.ssim:
            best = t.getvalue()
    return best


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--write", action="store_true", help="apply (default: dry run)")
    ap.add_argument("--min-gain", type=float, default=3.0, help="skip a file unless it gets this many %% smaller")
    ap.add_argument("--ssim", type=float, default=0.99, help="lossy re-encodes must keep SSIM >= this")
    ap.add_argument("paths", nargs="*", help="only these files (relative to the repo root); default: every tracked image")
    args = ap.parse_args()
    files = args.paths or tracked()
    before = after = 0
    changed = 0
    for f in sorted(files):
        if any(p.search(f) for p in PINNED):
            continue
        path = os.path.join(ROOT, f)
        data = open(path, "rb").read()
        strict = any(p.search(f) for p in STRICT)
        kind = f.rsplit(".", 1)[1].lower()
        try:
            new = {"png": png, "jpg": jpeg, "jpeg": jpeg, "webp": webp}[kind](data, strict, args)
        except Exception as e:  # a file Pillow cannot read stays as it is
            print("skip %s: %s" % (f, e))
            continue
        before += len(data)
        if new is None or len(new) > len(data) * (1 - args.min_gain / 100):
            after += len(data)
            continue
        after += len(new)
        changed += 1
        print("%8d -> %8d  %5.1f%%  %s" % (len(data), len(new), 100 * (1 - len(new) / len(data)), f))
        if args.write:
            with open(path, "wb") as o:
                o.write(new)
    print("%d files smaller; total %d -> %d bytes (-%d, %.1f%%)%s" % (
        changed, before, after, before - after, 100 * (before - after) / max(before, 1), "" if args.write else "  [dry run: add --write]"))


if __name__ == "__main__":
    sys.exit(main())
