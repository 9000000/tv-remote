# The intro page's demo (docs/index.html, .demo at the top of #features): docs/demo.css from dev/demo.src.css, where
# times are written as @chapter:second, plus the keyframes worked out here (fingers, captions, the four TVs). Run from anywhere:
#   python3 dev/demo_css.py
# The captions' text lives in index.html (.dcap); keep CAPS below in the same order. The other stories (How it works,
# the install tabs) are not generated: docs/stories.js (GSAP timelines) and docs/stories.css.
import re, os
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
T = 40.0
def pc(sec): return f"{max(0, min(100, sec / T * 100)):.3f}".rstrip('0').rstrip('.') + "%"
def at(k, s): return pc((k - 1) * 10 + s)

def win(name, a, b, f=.3, on="opacity:1", off="opacity:0"):
    """visible from a to b (absolute seconds)"""
    return f"@keyframes {name}{{0%,{pc(a)}{{{off}}}{pc(a+f)},{pc(b-f)}{{{on}}}{pc(b)},100%{{{off}}}}}\n"

out = []
A = out.append

# ---------- generated parts ----------
# ① suggested tiles drop out one by one
for i in range(6):
    s = 2.9 + i * .25
    A(f".dblt .sg i:nth-child({i+1}){{animation-name:dsg{i}}}\n")
    A(f"@keyframes dsg{i}{{0%,{pc(s)}{{opacity:1;transform:none;filter:none}}{pc(s+.35)},97%{{opacity:0;transform:scale(.6);filter:grayscale(1)}}98%,100%{{opacity:1;transform:none;filter:none}}}}\n")
# ① checklist rows: (start, done)
for i, (s, d) in enumerate([(1.8, 2.8), (2.8, 4.5), (4.5, 5.8)]):
    A(f".dp1 .c{i+1}{{animation-name:dcl{i}}}.dp1 .c{i+1} u{{animation-name:dclu{i}}}.dp1 .c{i+1} i::after{{animation-name:dclk{i}}}\n")
    A(f"@keyframes dcl{i}{{0%,{pc(s)}{{opacity:0;transform:translateY(3cqw)}}{pc(s+.3)},97%{{opacity:1;transform:none}}98%,100%{{opacity:0}}}}\n")
    A(f"@keyframes dclu{i}{{0%,{pc(d-.05)}{{opacity:1}}{pc(d)},100%{{opacity:0}}}}\n")
    A(f"@keyframes dclk{i}{{0%,{pc(d)}{{opacity:0;transform:scale(.4)}}{pc(d+.2)}{{opacity:1;transform:scale(1.2)}}{pc(d+.4)},97%{{opacity:1;transform:none}}98%,100%{{opacity:0}}}}\n")

# ④ four TVs: install done at, back from restart at (chapter seconds)
DONE = [4.6, 5.6, 4.0, 5.2]; READY = [9.0, 9.5, 8.7, 9.2]
for i, (d, r) in enumerate(zip(DONE, READY)):
    d += 30; r += 30; s = 32.3; b = 37.5
    n = i + 1
    A(f".mtv:nth-child({n}) .pg{{animation-name:dpg{n}}}.mtv:nth-child({n}) .pg::after{{animation-name:dpf{n}}}"
      f".mtv:nth-child({n}) .ok{{animation-name:dok{n}}}.mtv:nth-child({n}) .t i:last-child{{animation-name:dnw{n}}}.mtv:nth-child({n}) .bo{{animation-name:dbo{n}}}\n")
    A(f"@keyframes dpg{n}{{0%,{pc(s)}{{opacity:0}}{pc(s+.2)},{pc(d)}{{opacity:1}}{pc(d+.2)},100%{{opacity:0}}}}\n")
    A(f"@keyframes dpf{n}{{0%,{pc(s+.2)}{{transform:scaleX(0)}}{pc(d)},100%{{transform:scaleX(1)}}}}\n")
    A(f"@keyframes dok{n}{{0%,{pc(d)}{{opacity:0;transform:scale(.3)}}{pc(d+.2)}{{opacity:1;transform:scale(1.15)}}{pc(d+.4)},{pc(d+1.3)}{{opacity:1;transform:none}}{pc(d+1.6)},100%{{opacity:0;transform:none}}}}\n")
    A(f"@keyframes dnw{n}{{0%,{pc(d+.2)}{{opacity:0;transform:scale(.3)}}{pc(d+.5)},100%{{opacity:1;transform:none}}}}\n")
    A(f"@keyframes dbo{n}{{0%,{pc(b)}{{opacity:0}}{pc(b+.2)},{pc(r)}{{opacity:1}}{pc(r+.3)},100%{{opacity:0}}}}\n")
    # the phone's row for it
    c = 30 + 1.05 + i * .1
    A(f".dp4 .f{n} .cb{{animation-name:dcb{n}}}.dp4 .f{n} em{{animation-name:dpg{n}}}.dp4 .f{n} em::after{{animation-name:dpf{n}}}"
      f".dp4 .f{n} .sa{{animation-name:dsa{n}}}.dp4 .f{n} .sb{{animation-name:dsb{n}}}.dp4 .f{n} .sc{{animation-name:dsc{n}}}.dp4 .f{n} .se{{animation-name:dse{n}}}.dp4 .f{n} .sf{{animation-name:dsf{n}}}\n")
    A(f"@keyframes dcb{n}{{0%,{pc(c)}{{background:transparent;border-color:#4a5470;color:transparent}}{pc(c+.15)},99%{{background:#5b8cff;border-color:#5b8cff;color:#06112b}}100%{{background:transparent;border-color:#4a5470;color:transparent}}}}\n")
    for nm, a2, b2 in [("sa", 0, s), ("sb", s, d), ("sc", d, b), ("se", b, r), ("sf", r, 40)]:
        a2 = max(a2, 30) if nm != "sa" else 30
        A(f"@keyframes d{nm}{n}{{0%,{pc(a2)}{{opacity:0}}{pc(a2+.05)},{pc(b2-.05)}{{opacity:1}}{pc(b2)},100%{{opacity:0}}}}\n")

# captions: chapter, from, to (the text is in index.html, in this order)
CAPS = [
 (1, 0, 2.0, "🐢 TV chậm, đầy quảng cáo.", "🐢 A slow TV, full of ads."),
 (1, 2.0, 6.0, "⚡ Một nút: tắt app rác, đổi màn hình chính.", "⚡ One button: junk apps off, a light home screen."),
 (1, 6.0, 10, "✨ Nhẹ hẳn. Không xóa gì, Hoàn tác được.", "✨ Much lighter. Nothing deleted, one tap to undo."),
 (2, 0, 2.2, "📍 TV nhà bà ở Huế, cách bạn 900 km.", "📍 Grandma's TV, 900 km away."),
 (2, 2.2, 5.6, "✏️ Khoanh chỗ cần bấm: nét vẽ hiện trên TV của bà.", "✏️ Circle what to press: it shows up on her TV."),
 (2, 5.6, 10, "👵 Bà bấm theo. Xong.", "👵 She presses it. Done."),
 (3, 0, 1.3, "📴 Mẹ cài một lần, rồi tắt điện thoại.", "📴 Mom sets it once, then turns her phone off."),
 (3, 1.3, 5.6, "⏳ TV tự đếm: đủ 60 phút là đóng YouTube.", "⏳ The TV keeps count: 60 minutes, then YouTube closes."),
 (3, 5.6, 10, "🌙 Tới giờ ngủ, TV tự tắt.", "🌙 At bedtime, the TV turns itself off."),
 (4, 0, 2.0, "🏠 Phòng khách, phòng ngủ, quán: 4 TV.", "🏠 Living room, bedroom, café: 4 TVs."),
 (4, 2.0, 7.1, "📦 Chọn hết, cài app cho cả 4 cùng lúc.", "📦 Select all, install on all 4 at once."),
 (4, 7.1, 10, "🔄 Khởi động lại cả loạt, cũng một nút.", "🔄 Restart them all, one button too."),
]
for i, (k, a, b, vi, en) in enumerate(CAPS):
    a2 = (k - 1) * 10 + a; b2 = (k - 1) * 10 + b
    A(f".dcap>:nth-child({i+1}){{animation-name:dcp{i}}}\n")
    if a2 == 0:   # the loop's first caption is also showing when it wraps
        A(f"@keyframes dcp{i}{{0%,{pc(b2-.3)}{{opacity:1;transform:none}}{pc(b2)},99.4%{{opacity:0;transform:translateY(-6px)}}100%{{opacity:1;transform:none}}}}\n")
    else:
        A(f"@keyframes dcp{i}{{0%,{pc(a2)}{{opacity:0;transform:translateY(8px)}}{pc(a2+.3)},{pc(b2-.3)}{{opacity:1;transform:none}}{pc(b2)},100%{{opacity:0;transform:translateY(-6px)}}}}\n")

# the beam: when the phone is sending to the TV
BEAM = [(1.5, 5.8), (11.3, 15.1), (32.0, 36.0), (37.2, 39.3)]
parts = []
prev = 0
for a, b in BEAM:
    parts.append(f"{pc(prev)},{pc(a)}{{opacity:0}}{pc(a+.15)},{pc(b)}{{opacity:1}}")
    prev = b + .15
parts.append(f"{pc(prev)},100%{{opacity:0}}")
A("@keyframes dbeam{" + "".join(parts) + "}\n")

# fingers: taps (sec, x%, y%) and strokes
def finger(name, events):
    """events: list of (sec, x, y, kind) kind in hide/show/press"""
    ks = {}
    for t, x, y, kind in events:
        o = 0 if kind == "hide" else 1
        sc = ".75" if kind == "press" else "1"
        ks[pc(t)] = f"left:{x:.2f}%;top:{y:.2f}%;opacity:{o};transform:scale({sc})"
    first = events[0]
    body = f"0%{{left:{first[1]:.2f}%;top:{first[2]+20:.2f}%;opacity:0;transform:none}}"
    body += "".join(f"{k}{{{v}}}" for k, v in ks.items())
    last = events[-1]
    body += f"100%{{left:{last[1]:.2f}%;top:{last[2]:.2f}%;opacity:0;transform:none}}"
    A(f"@keyframes {name}{{{body}}}\n")

def tap(t, x, y):
    return [(t - .6, x + 6, y + 8, "hide"), (t - .3, x, y, "show"), (t, x, y, "press"), (t + .2, x, y, "show"), (t + .55, x + 5, y + 6, "hide")]

finger("dfg1", tap(1.5, 50, 30.7))
finger("dfg4", tap(31.05, 86, 7.6) + tap(32.0, 26.75, 48) + tap(37.2, 73.25, 48))

# ② the circle and the arrow, drawn on the picture: TV coordinates (0-100 wide) to the phone's screen
def bez(p0, p1, p2, p3, n):
    pts = []
    for j in range(1, n + 1):
        t = j / n; u = 1 - t
        pts.append((u**3*p0[0] + 3*u*u*t*p1[0] + 3*u*t*t*p2[0] + t**3*p3[0], u**3*p0[1] + 3*u*u*t*p1[1] + 3*u*t*t*p2[1] + t**3*p3[1]))
    return pts
C = [((21.5, 25.6), (15, 23.6), (4.5, 24.6), (3.2, 31)), ((3.2, 31), (2, 37), (8, 40.6), (14.5, 40.4)),
     ((14.5, 40.4), (22, 40.2), (25.6, 36.4), (25, 31.6)), ((25, 31.6), (24.6, 28.6), (22.6, 26.6), (19.4, 25.9))]
circle = [C[0][0]] + [p for seg in C for p in bez(*seg, 4)]
arrow = [(40, 14.5), (33.5, 17.5), (26.6, 23.6)]
def ph(x, y): return 4 + .92 * x, (32 + .92 * y) / (19.5 / 9)
ev = tap(11.25, 14, 42.7)
ev.append((11.9 + 0, *ph(*circle[0]), "hide"))
ev.append((12.25, *ph(*circle[0]), "show"))
for j, p in enumerate(circle):
    ev.append((12.4 + 1.8 * j / (len(circle) - 1), *ph(*p), "press"))
ev.append((14.25, *ph(*arrow[0]), "show"))
for j, p in enumerate(arrow):
    ev.append((14.4 + .6 * j / (len(arrow) - 1), *ph(*p), "press"))
ev.append((15.25, *ph(30, 22), "hide"))
finger("dfg2", ev)

css = re.sub(r'@([1-4]):([0-9.]+)', lambda m: at(int(m.group(1)), float(m.group(2))), open(f"{ROOT}/dev/demo.src.css").read())
BANNER = ("/* GENERATED by dev/demo_css.py from dev/demo.src.css: do not edit this file. Change that, then run\n"
          "   python3 dev/demo_css.py */\n")
css += "".join(out)
open(f"{ROOT}/docs/demo.css", "w").write(BANNER + css)
