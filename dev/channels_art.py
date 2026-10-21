#!/usr/bin/env python3
"""TV Channels' launcher pictures: app/channels/src/main/res/drawable/icon.png (192) and banner.png (320x180).
A screen with an antenna on a teal-to-blue tile, drawn at 4x and scaled down. Needs Pillow."""
import pathlib
from PIL import Image, ImageDraw, ImageFont

OUT = pathlib.Path(__file__).resolve().parent.parent / "app/channels/src/main/res/drawable"
A, B = (20, 184, 166), (37, 99, 235)                                 # teal -> blue, top-left to bottom-right


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


def glyph(d, cx, cy, s):
    """A TV with two antenna ears and a play mark, centred at (cx, cy), s = its width."""
    w, h = s, s * 0.66
    x0, y0 = cx - w / 2, cy - h / 2 + s * 0.08
    lw = max(2, int(s * 0.07))
    j = y0 + lw / 2                                                   # the ears meet inside the screen's top edge: no gap
    for sx in (-1, 1):
        d.line((cx + sx * s * 0.2, y0 - s * 0.22, cx, j), fill="white", width=lw)
        for x, y in ((cx + sx * s * 0.2, y0 - s * 0.22), (cx, j)):    # round ends, as the line icons have
            d.ellipse((x - lw / 2, y - lw / 2, x + lw / 2, y + lw / 2), fill="white")
    d.rounded_rectangle((x0, y0, x0 + w, y0 + h), s * 0.12, outline="white", width=lw)
    t = s * 0.16
    d.polygon([(cx - t * 0.7, cy - t + s * 0.08), (cx - t * 0.7, cy + t + s * 0.08), (cx + t, cy + s * 0.08)], fill="white")


K = 4
icon = tile(192 * K, 192 * K, 44 * K)
glyph(ImageDraw.Draw(icon), 96 * K, 92 * K, 112 * K)
icon.resize((192, 192), Image.LANCZOS).save(OUT / "icon.png")

ban = tile(320 * K, 180 * K, 0).convert("RGB")
d = ImageDraw.Draw(ban)
glyph(d, 70 * K, 86 * K, 76 * K)
font = None
for f in ("/System/Library/Fonts/SFNSRounded.ttf", "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
          "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"):
    try:
        font = ImageFont.truetype(f, 34 * K); break
    except OSError:
        pass
d.text((128 * K, 90 * K), "TV\nChannels", fill="white", font=font, anchor="lm", spacing=2 * K)
ban.resize((320, 180), Image.LANCZOS).save(OUT / "banner.png")
print("icon.png, banner.png written")
