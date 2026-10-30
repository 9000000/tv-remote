#!/usr/bin/env python3
"""Rounds the corners of the app's logo everywhere it is a picture, and builds the Mac / Windows icons from it.

Run from the repo root: python3 dev/gen/round_icons.py  (needs Pillow; iconutil on a Mac for icon.icns).
Safe to run again: a corner already cut stays cut, nothing else changes.

Left square on purpose: icon-180.png (iPhone's home screen rounds it itself and paints see-through corners black) and
icon-maskable.png (Android cuts it to the launcher's own shape); docs/img/icon-180.png is a copy of the first.
"""
import os, shutil, subprocess, sys, tempfile
from PIL import Image, ImageChops, ImageDraw

RADIUS = 0.22                                                         # of the side, close to iOS / macOS app icons
ROUNDED = ["app/shared/web/icon-512.png", "app/shared/web/icon-192.png", "docs/img/icon.png",
           "app/android/src/main/res/drawable/icon.png", "app/tvsetup/src/main/res/drawable/icon.png"]


def rounded(img):
    img = img.convert("RGBA")
    w, h = img.size
    big = Image.new("L", (w * 4, h * 4), 0)                           # drawn 4x larger, then shrunk: smooth edges
    ImageDraw.Draw(big).rounded_rectangle((0, 0, w * 4 - 1, h * 4 - 1), radius=round(min(w, h) * 4 * RADIUS), fill=255)
    img.putalpha(ImageChops.darker(img.getchannel("A"), big.resize((w, h), Image.LANCZOS)))
    return img


def main():
    for p in ROUNDED:
        rounded(Image.open(p)).save(p, optimize=True)
        print("rounded", p)
    shutil.copyfile("app/shared/web/icon-180.png", "docs/img/icon-180.png")
    master = Image.open("app/shared/web/icon-512.png")
    master.save("app/desktop/icon.ico", sizes=[(s, s) for s in (16, 24, 32, 48, 64, 128, 256)])
    print("built app/desktop/icon.ico")
    if sys.platform != "darwin":
        print("icon.icns needs a Mac (iconutil): left as it is")
        return
    with tempfile.TemporaryDirectory() as tmp:
        d = os.path.join(tmp, "icon.iconset")
        os.mkdir(d)
        for s in (16, 32, 128, 256, 512):
            master.resize((s, s), Image.LANCZOS).save(os.path.join(d, f"icon_{s}x{s}.png"))
            if s < 512:
                master.resize((s * 2, s * 2), Image.LANCZOS).save(os.path.join(d, f"icon_{s}x{s}@2x.png"))
        subprocess.run(["iconutil", "-c", "icns", d, "-o", "app/desktop/icon.icns"], check=True)
    print("built app/desktop/icon.icns")


if __name__ == "__main__":
    main()
