#!/usr/bin/env python3
"""TV Games' launcher pictures: app/games/src/main/res/drawable/icon.png (192) and banner.png (320x180).
A game pad on a violet-to-pink tile, drawn at 4x and scaled down, in the style of dev/gen/channels_art.py. Needs Pillow."""
import pathlib
from PIL import Image, ImageDraw, ImageFont

OUT = pathlib.Path(__file__).resolve().parents[2] / "app/games/src/main/res/drawable"
A, B = (124, 58, 237), (219, 39, 119)                                # violet -> pink, top-left to bottom-right


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
    """A pad (white body, a cross on the left, two buttons on the right) centred at (cx, cy), s = its width."""
    w, h = s, s * 0.56
    x0, y0 = cx - w / 2, cy - h / 2
    d.rounded_rectangle((x0, y0, x0 + w, y0 + h), h * 0.5, fill="white")
    a, t = s * 0.22, s * 0.042                                        # the cross: arm length, half thickness
    px, py = x0 + w * 0.29, cy
    d.rectangle((px - a / 2, py - t, px + a / 2, py + t), fill=bg)
    d.rectangle((px - t, py - a / 2, px + t, py + a / 2), fill=bg)
    r = s * 0.06
    for bx, by in ((x0 + w * 0.66, cy + s * 0.06), (x0 + w * 0.78, cy - s * 0.06)):
        d.ellipse((bx - r, by - r, bx + r, by + r), fill=bg)


K = 4
MID = tuple((A[i] + B[i]) // 2 for i in range(3))                    # the holes in the pad show the tile's middle hue
icon = tile(192 * K, 192 * K, 44 * K)
glyph(ImageDraw.Draw(icon), 96 * K, 96 * K, 128 * K, MID)
icon.resize((192, 192), Image.LANCZOS).save(OUT / "icon.png")

ban = tile(320 * K, 180 * K, 0).convert("RGB")
d = ImageDraw.Draw(ban)
glyph(d, 70 * K, 90 * K, 84 * K, MID)
font = None
for f in ("/System/Library/Fonts/SFNSRounded.ttf", "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
          "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"):
    try:
        font = ImageFont.truetype(f, 34 * K); break
    except OSError:
        pass
d.text((132 * K, 90 * K), "TV\nGames", fill="white", font=font, anchor="lm", spacing=2 * K)
ban.resize((320, 180), Image.LANCZOS).save(OUT / "banner.png")
print("icon.png, banner.png written")
