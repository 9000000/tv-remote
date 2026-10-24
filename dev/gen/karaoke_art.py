#!/usr/bin/env python3
"""TV Karaoke's launcher pictures: app/karaoke/src/main/res/drawable/icon.png (192) and banner.png (320x180).
A microphone on an amber-to-rose tile, drawn at 4x and scaled down, in the style of dev/gen/channels_art.py. Needs Pillow."""
import pathlib
from PIL import Image, ImageDraw, ImageFont

OUT = pathlib.Path(__file__).resolve().parents[2] / "app/karaoke/src/main/res/drawable"
A, B = (245, 158, 11), (225, 29, 72)                                  # amber -> rose, top-left to bottom-right


def tile(w, h, r):
    g = Image.new("RGB", (w, h))
    px = g.load()
    for y in range(h):
        for x in range(w):
            t = (x / w + y / h) / 2
            px[x, y] = tuple(int(A[i] + (B[i] - A[i]) * t) for i in range(3))
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), r, fill=255)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.paste(g, (0, 0), m)
    return out


def glyph(d, cx, cy, s, bg):
    """A microphone (white head and stand) centred at (cx, cy), s = its height."""
    w = s * 0.34
    x0, y0 = cx - w / 2, cy - s * 0.5
    d.rounded_rectangle((x0, y0, x0 + w, y0 + s * 0.52), w / 2, fill="white")           # the head
    for k in (0.16, 0.28, 0.40):                                                         # the grille
        d.rectangle((x0 + w * 0.18, y0 + s * k, x0 + w * 0.82, y0 + s * k + s * 0.025), fill=bg)
    t = s * 0.05
    d.arc((cx - w * 0.82, y0 + s * 0.18, cx + w * 0.82, y0 + s * 0.74), 0, 180, fill="white", width=int(t))   # the holder
    d.rectangle((cx - t / 2, y0 + s * 0.74, cx + t / 2, y0 + s * 0.92), fill="white")
    d.rounded_rectangle((cx - w * 0.5, y0 + s * 0.90, cx + w * 0.5, y0 + s * 0.90 + t), t / 2, fill="white")


K = 4
MID = tuple((A[i] + B[i]) // 2 for i in range(3))                    # the holes in the pad show the tile's middle hue
icon = tile(192 * K, 192 * K, 44 * K)
glyph(ImageDraw.Draw(icon), 96 * K, 96 * K, 120 * K, MID)
icon.resize((192, 192), Image.LANCZOS).save(OUT / "icon.png")

ban = tile(320 * K, 180 * K, 0).convert("RGB")
d = ImageDraw.Draw(ban)
glyph(d, 66 * K, 90 * K, 96 * K, MID)
font = None
for f in ("/System/Library/Fonts/SFNSRounded.ttf", "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
          "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"):
    try:
        font = ImageFont.truetype(f, 28 * K); break
    except OSError:
        pass
d.text((132 * K, 90 * K), "TV\nKaraoke", fill="white", font=font, anchor="lm", spacing=2 * K)
ban.resize((320, 180), Image.LANCZOS).save(OUT / "banner.png")
print("icon.png, banner.png written")
