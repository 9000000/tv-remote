# The intro page's other stories, as timelines: How it works (.st-h, four chapters) and the install tabs (.st-b browser,
# .st-a phone app, .st-c computer app, .st-p preparing the TV; one part per step). Their look is dev/stories.src.css,
# their markup index.html; dev/demo_css.py writes all of it into docs/demo.css. Times are seconds from the loop's start.
# Everything is a track: a state held from one moment to the next, so a scene reads as a list of "at t, this changes".

class Story:
    def __init__(self, root, T, parts):
        self.root, self.T, self.L, self.n, self.kf, self.names = root, T, T / parts, 0, [], {}

    def add(self, sels, name, body):
        """one element can follow several tracks: their names are joined into one animation-name"""
        self.names.setdefault(sels, []).append(name)
        self.kf.append(f"@keyframes {name}{{{body}}}\n")

    @property
    def out(self):
        return [f"{k}{{animation-name:{','.join(v)}}}\n" for k, v in self.names.items()] + self.kf

    def pc(self, t):
        return f"{max(0, min(100, t / self.T * 100)):.3f}".rstrip('0').rstrip('.') + "%"

    def at(self, k, s):
        """second s of part k (1-based)"""
        return (k - 1) * self.L + s

    def track(self, sel, init, pts, pseudo=""):
        """pts: (t, css, dur[, timing]) moving to css over dur from t; back to init just before the loop ends"""
        self.n += 1
        name = f"s{self.root[3:]}{self.n}"
        fr, cur = [(0, init, None)], init
        for p in sorted(pts, key=lambda p: p[0]):
            t, css, dur = p[:3]
            fr.append((t, cur, p[3] if len(p) > 3 else None))
            fr.append((t + dur, css, None))
            cur = css
        if cur != init:
            fr += [(self.T - .25, cur, None), (self.T, init, None)]
        else:
            fr.append((self.T, init, None))
        body = "".join(f"{self.pc(t)}{{{css}{';animation-timing-function:' + tf if tf else ''}}}" for t, css, tf in fr)
        self.add(",".join(f".{self.root} {s.strip()}{pseudo}" for s in sel.split(",")), name, body)

    def show(self, sel, *wins, f=.25, on="opacity:1", off="opacity:0"):
        """visible in each (a, b); a <= 0 means already showing when the loop starts"""
        init, pts = off, []
        for a, b in wins:
            if a <= 0:
                init = on
            else:
                pts.append((a - f, on, f))
            if b < self.T:
                pts.append((b, off, f))
        self.track(sel, init, pts)

    def press(self, sel, *ts, on="transform:scale(.88);filter:brightness(1.4)"):
        off = "transform:none;filter:none"
        self.track(sel, off, [p for t in ts for p in ((t, on, .08), (t + .08, off, .22))])

    def fill(self, sel, a, b):
        self.track(sel, "transform:scaleX(0)", [(a, "transform:scaleX(1)", b - a, "linear")], "::after")

    def type(self, sel, a, b, n):
        self.track(sel, "width:0", [(a, f"width:{n}ch", b - a, f"steps({n},end)")])

    def toggle(self, sel, t, on=True):
        a, b = ("background:#3a4258", "background:#3ddc84") if on else ("background:#3ddc84", "background:#3a4258")
        x, y = ("transform:none", "transform:translateX(108%)") if on else ("transform:translateX(108%)", "transform:none")
        self.track(sel, a, [(t, b, .15)])
        self.track(sel, x, [(t, y, .15)], "::after")

    def focus(self, sel, *wins):
        self.show(sel, *wins, f=.12, on="box-shadow:0 0 0 .5cqw #fff;background:#34405a",
                  off="box-shadow:0 0 0 0 transparent;background:rgba(255,255,255,.06)")

    def pointer(self, sel, events):
        """a finger or a cursor, at each (t, x%, y%, hide|show|move|press); it glides between them"""
        self.n += 1
        name = f"s{self.root[3:]}{self.n}"
        events = sorted(events)
        def st(x, y, kind):
            return f"left:{x:.2f}%;top:{y:.2f}%;opacity:{0 if kind == 'hide' else 1};transform:scale({'.75' if kind == 'press' else '1'})"
        x0, y0, xl, yl = events[0][1], events[0][2], events[-1][1], events[-1][2]
        body = f"0%{{left:{x0:.2f}%;top:{y0 + 12:.2f}%;opacity:0;transform:none}}"
        body += "".join(f"{self.pc(t)}{{{st(x, y, k)}}}" for t, x, y, k in events)
        body += f"100%{{left:{xl:.2f}%;top:{yl:.2f}%;opacity:0;transform:none}}"
        self.add(f".{self.root} {sel}", name, body)

    def steps(self, k):
        """the install step k lit while its part plays"""
        a, b = (k - 1) * self.L, k * self.L
        self.show(f".steps li:nth-child({k})", (a, b), f=.2, on="background:rgba(91,140,255,.1);box-shadow:inset 3px 0 var(--blue)",
                  off="background:transparent;box-shadow:inset 0 0 transparent")


def taps(*pts, near=1.4):
    """a finger pressing each (t, x, y) in turn: it glides from one to the next when they are close in time, and lifts
    away and comes back when they are not (separate taps written apart would jump between their spots)"""
    ev, prev = [], None
    for t, x, y in sorted(pts):
        if prev and t - prev[0] < near:
            g = min(.15, (t - prev[0]) / 3)
            ev += [(prev[0] + g, prev[1], prev[2], "show"), (t - g, x, y, "move"), (t, x, y, "press")]
        else:
            if prev:
                ev += [(prev[0] + .15, prev[1], prev[2], "show"), (prev[0] + .5, prev[1] + 5, prev[2] + 6, "hide")]
            ev += [(t - .6, x + 6, y + 8, "hide"), (t - .3, x, y, "show"), (t, x, y, "press")]
        prev = (t, x, y)
    return ev + [(prev[0] + .15, prev[1], prev[2], "show"), (prev[0] + .5, prev[1] + 5, prev[2] + 6, "hide")]


def key(d):
    x, y = KEYS[d]
    return x, y / PY


def click(t, x, y, frm=None):
    """a cursor gliding in and clicking"""
    fx, fy = frm or (x + 14, y + 18)
    return [(t - .6, fx, fy, "show"), (t - .1, x, y, "move"), (t, x, y, "press"), (t + .12, x, y, "show")]


PY = 19.5 / 9                                                      # phone screen: height per width
def P(x, y):
    """phone coordinates (cqw of the phone's width) to % of its screen"""
    return x, y / PY

def mir(x, y):
    """a spot on the TV (cqw) as seen in the phone's picture (left 4, top 34, 92 wide)"""
    return P(4 + .92 * x, 34 + .92 * y)

YT, NF, MV = (13.84, 32.9), (31.92, 32.9), (19.9, 48.3)            # YouTube and Netflix tiles, the first "continue" film
KEYS = {d: (19 + 31 * ((int(d) - 1) % 3), 88 + 19 * ((int(d) - 1) // 3)) for d in "123456789"}
OK, RIGHT, VOLUP = (50, 135), (66, 135), (86, 125)
ROW = (50, 94)


def how():
    s = Story("st-h", 40, 4)
    A = s.at
    # the phone's three pages
    s.show(".hs1", (0, 1.9))
    s.show(".hrm", (1.9, 30), (35, 40))
    s.show(".hsf", (30, 35))
    s.show(".hs1 .hr1", (1.0, 1.9), f=.2)
    s.press(".hs1 .hr1", 1.6)
    s.show(".hsb .nw", (0, 20), (30, 40), f=.1)
    s.show(".hsb .n4", (20, 30), f=.1)
    # chips on the remote: which way the phone is talking
    s.track(".ck.c1", "opacity:1;background:#173826;color:#3ddc84", [
        (A(2, 1.2), "opacity:1;background:#3a1720;color:#ff6b6b", .2), (A(2, 7.0), "opacity:1;background:#173826;color:#3ddc84", .2),
        (A(3, 0), "opacity:.35;background:#173826;color:#3ddc84", .2), (A(4, 0), "opacity:0;background:#173826;color:#3ddc84", .2)])
    s.show(".ck.c2", (A(2, 2.6), A(2, 6.8)), on="opacity:1;background:#13243a;color:#9fd0ff", off="opacity:.35;background:#1f2738;color:#9aa4b8")
    s.show(".ck.c3", (A(3, 3.0), A(4, 0)), on="opacity:1;background:#13243a;color:#9fd0ff", off="opacity:0;background:#1f2738;color:#9aa4b8")
    s.show(".ck.c4", (A(4, 5), 40), on="opacity:1;background:#13243a;color:#9fd0ff", off="opacity:0;background:#1f2738;color:#9aa4b8")
    # what the TV and the phone's picture show
    s.show(".tyt", (A(1, 7.0), A(2, 0)))
    s.show(".myt", (A(1, 7.4), A(2, 0)))
    s.show(".moff", (A(2, 1.4), A(2, 7.4)))
    s.show(".tmv", (A(3, 6.7), A(4, 0)))
    s.show(".mmv", (A(3, 7.1), A(4, 0)))
    s.show(".htv .z1", (A(1, 6.4), A(1, 7.0)), (A(2, 5.0), A(2, 5.3)), (A(4, 6.8), A(4, 8.0)), f=.15)
    s.show(".htv .z2", (A(2, 5.3), A(2, 6.6)), (A(4, 8.0), 39.6), f=.15)
    s.show(".htv .z3", (A(3, 6.2), A(3, 6.7)), f=.15)
    s.show(".htv .side", (A(2, .5), A(2, 2.3)), on="opacity:1;transform:none", off="opacity:0;transform:translateX(30%)")
    s.toggle(".htv .side .tg", A(2, 1.2), on=False)
    s.show(".htv .vbar", (A(2, 3.5), A(2, 5.0)))
    s.track(".htv .vbar i", "transform:scaleX(.4)", [(A(2, 3.7), "transform:scaleX(.5)", .15), (A(2, 4.3), "transform:scaleX(.6)", .15)], "::after")
    s.show(".htv .pair", (A(4, 2.0), A(4, 5.0)))
    s.show(".htv .o1", (A(1, 2.1), A(1, 4.4)))
    s.show(".htv .o2", (A(2, 6.6), A(2, 9.4)))
    s.show(".htv .o3", (A(4, 5.2), A(4, 7.6)))
    # the iPhone: address typed, then the code
    s.type(".hsf .u1", A(4, .3), A(4, 1.6), 17)
    s.show(".hsf .pcode", (A(4, 1.8), 40))
    for i in range(6):
        s.show(f".hsf .bx i:nth-child({i+1})", (A(4, 3.0 + .3 * i), 40), f=.08, on="color:#fff;border-color:#5b8cff", off="color:transparent;border-color:#2f3850")
    # the path between them
    s.show(".hpath .p1", (0, A(2, 2.6)), (A(2, 6.8), A(3, 0)))
    s.show(".hpath .p2", (A(2, 2.6), A(2, 6.8)))
    s.show(".hpath .p3", (A(3, 3.0), A(4, 0)))
    s.show(".hpath .p4", (A(4, 0), 40))
    s.show(".hpath .h1", (0, A(3, 0)), (A(4, 0), 40))
    s.show(".hpath .h3", (A(3, 0), A(4, 0)))
    s.show(".hpath .cloud", (A(3, .6), A(4, 0)))
    s.show(".hpath .cloud .via", (A(3, .8), A(3, 2.6)))
    s.track(".hpath .cloud s", "text-decoration-color:transparent", [(A(3, 2.6), "text-decoration-color:#ff6b6b", .2)])
    s.track(".hpath .cloud>span", "transform:none", [(A(3, 2.6), "transform:translateX(-4px)", .06), (A(3, 2.66), "transform:translateX(4px)", .06),
                                                     (A(3, 2.72), "transform:translateX(-3px)", .06), (A(3, 2.78), "transform:none", .06)])
    s.show(".hpath .ln", (A(3, 3.0), A(4, 0)), on="box-shadow:0 0 0 3px rgba(61,220,132,.35),0 0 18px rgba(61,220,132,.5)", off="box-shadow:0 0 0 0 transparent,0 0 0 transparent")
    s.show(".lane.up", (A(1, 1.6), A(1, 2.0)), (A(1, 6.1), A(1, 6.7)), (A(2, 3.4), A(2, 5.6)), (A(3, 5.9), A(3, 6.5)),
           (A(4, 1.5), A(4, 1.9)), (A(4, 2.9), A(4, 4.8)), (A(4, 6.7), A(4, 8.4)), f=.1)
    s.show(".lane.dn", (A(1, 2.0), A(2, 1.3)), (A(2, 7.2), A(3, 0)), (A(3, 3.2), A(4, 0)), (A(4, 5.2), 40), f=.1)
    # the finger
    s.pointer(".hph .dfg", taps((1.6, *P(*ROW)), (A(1, 6.2), *mir(*YT)),
                                (A(2, 3.6), *P(*VOLUP)), (A(2, 4.3), *P(*VOLUP)), (A(2, 5.2), *P(*RIGHT)),
                                (A(3, 6.0), *mir(*MV)),
                                *[(A(4, 3.0 + .3 * i), *key(d)) for i, d in enumerate("482913")],
                                (A(4, 6.8), *P(*OK)), (A(4, 8.0), *P(*RIGHT))))
    # captions, in the order of .hcap's <p>s
    for i, (k, a, b) in enumerate([(1, 0, 2), (1, 2, 6), (1, 6, 10), (2, 0, 2.6), (2, 2.6, 6.4), (2, 6.4, 10),
                                   (3, 0, 2.4), (3, 2.4, 6), (3, 6, 10), (4, 0, 2), (4, 2, 5), (4, 5, 10)]):
        s.show(f".hcap>:nth-child({i+1})", (A(k, a), A(k, b)), f=.3, on="opacity:1;transform:none", off="opacity:0;transform:translateY(8px)")
    return s.out


def browser():
    s = Story("st-b", 20, 5)
    for k in range(1, 6):
        s.steps(k)
    s.show(".b1", (0, 4)); s.show(".b2", (4, 8)); s.show(".b3", (8, 12)); s.show(".b4", (12, 20))
    s.type(".y1", .3, 1.2, 10)
    s.focus(".q1", (1.2, 1.5)); s.focus(".q2", (1.5, 4))
    s.press(".q2", 1.6, 3.6)
    s.fill(".r1", 1.7, 3.0)
    s.show(".q2 .i1", (0, 3.1), f=.05); s.show(".q2 .i2", (3.1, 4), f=.05)
    s.type(".y2", 4.3, 5.7, 7)
    s.focus(".q3", (5.8, 8)); s.press(".q3", 6.0)
    s.show(".dlr", (6.1, 8)); s.fill(".r2", 6.2, 7.6)
    s.show(".u3", (8.3, 9.6)); s.focus(".q5", (8.4, 9.6)); s.toggle(".g3", 9.0)
    s.focus(".q4", (9.6, 12)); s.press(".q4", 9.9)
    s.fill(".r3", 10.0, 11.2); s.show(".k3", (11.3, 12))
    s.focus(".q6", (12.3, 12.8)); s.press(".q6", 12.6)
    s.show(".u4", (12.8, 14.0)); s.focus(".q7", (12.9, 14)); s.toggle(".g4", 13.4)
    s.show(".b4 .st1", (12, 14.1), f=.05); s.show(".b4 .st2", (14.1, 20), f=.05)
    s.show(".u5", (14.6, 20))
    s.show(".o1", (18.6, 20))
    s.press(".rc .rk", 1.6, 3.6, 6.0, 9.0, 9.9, 12.6, 13.4)
    s.press(".rc .rd", .5, .9, 4.4, 4.8, 5.2, 5.6, 9.4)
    s.show(".rc", (0, 16), on="opacity:1;transform:none", off="opacity:0;transform:translateY(30%)")
    s.show(".sph", (16.1, 20), on="opacity:1;transform:none", off="opacity:0;transform:translateY(20%)")
    s.show(".e1", (16, 17.3)); s.show(".e2", (17.3, 18.5)); s.show(".e3", (18.5, 20))
    for i in range(6):
        s.show(f".e2 .bx i:nth-child({i+1})", (17.5 + .16 * i, 20), f=.06, on="color:#fff;border-color:#5b8cff", off="color:transparent;border-color:#2f3850")
    s.pointer(".sph .dfg", taps((16.9, *P(50, 124)), *[(17.5 + .16 * i, *key(d)) for i, d in enumerate("482913")], (19.4, *P(*OK))))
    return s.out


def phone_app():
    s = Story("st-a", 16, 4)
    for k in range(1, 5):
        s.steps(k)
    s.show(".a1", (0, 4)); s.show(".a2", (4, 8)); s.show(".a3", (8, 12)); s.show(".a4", (12, 16))
    s.fill(".a1 .bar", .2, 1.4)
    s.show(".a1 .sheet", (1.8, 4), on="opacity:1;transform:none", off="opacity:0;transform:translateY(40%)")
    s.fill(".a1 .sheet .bar", 2.5, 3.3); s.show(".a1 .sheet .ok", (3.4, 4))
    s.press(".a2 .scan", 4.5)
    s.show(".a2 .rad", (4.6, 6.0)); s.show(".a2 .prow", (5.6, 8), f=.2)
    s.show(".a3 .w1", (8, 10.5), f=.1); s.show(".a3 .w2", (10.5, 12), f=.1)
    s.show(".stv .usb", (8.2, 10.6))
    s.focus(".usb .q1", (8.8, 9.6)); s.focus(".usb .q2", (9.6, 10.6))
    s.show(".usb .q1 em", (9.2, 16), f=.05)
    s.press(".usb .q2", 10.4)
    s.show(".stv .o1", (12.2, 14.2))
    s.show(".stv .dfoc", (13.8, 14.2), f=.12)
    s.show(".stv .dyt", (14.2, 16))
    s.show(".a4 .myt", (14.6, 16))
    s.pointer(".sph .dfg", taps((1.7, *P(50, 62)), (2.4, *P(72, 196)), (4.5, *P(50, 39)), (6.8, *P(*ROW)), (13.6, *mir(*YT))))
    s.press(".a1 .open", 1.7); s.press(".a1 .sheet .tb2", 2.4); s.press(".a2 .prow", 6.8)
    return s.out


def computer():
    s = Story("st-c", 16, 4)
    for k in range(1, 5):
        s.steps(k)
    s.show(".c1", (0, 4)); s.show(".c2", (4, 8)); s.show(".c3", (8, 12)); s.show(".c4", (12, 16))
    s.fill(".c1 .bar", .3, 1.8); s.show(".c1 .ok", (1.9, 4))
    s.show(".c1 .apic", (2.3, 4), on="opacity:1;transform:none", off="opacity:0;transform:scale(.6)")
    s.press(".c1 .apic", 3.0, 3.18)
    s.track(".c2 .drag", "transform:none;opacity:1", [(4.4, "transform:translateX(45cqw) scale(.85);opacity:1", 1.0), (5.4, "transform:translateX(45cqw) scale(.4);opacity:0", .2)])
    s.press(".c2 .fold", 5.4)
    s.show(".c2 .dlg", (5.8, 8)); s.press(".c2 .go", 7.2)
    s.show(".c3 .more", (9.2, 12), f=.15); s.press(".c3 .lnk", 9.0); s.press(".c3 .more", 10.4)
    s.show(".c4 .sp1", (12, 12.5), f=.1); s.show(".c4 .prow", (12.5, 16), f=.2); s.press(".c4 .prow", 13.0)
    s.show(".c4 .wait", (13.1, 14.5), f=.1); s.show(".c4 .mirw", (14.5, 16))
    s.show(".stv .usb", (13.2, 14.4)); s.focus(".usb .q2", (13.6, 14.4)); s.show(".usb .q1 em", (0, 16)); s.press(".usb .q2", 14.2)
    s.show(".stv .o1", (14.5, 16))
    ev = click(3.0, 50, 66) + [(3.18, 50, 66, "press"), (3.3, 50, 66, "show"), (3.6, 60, 80, "hide")]
    ev += click(4.4, 25, 45) + [(5.4, 70, 45, "move"), (5.6, 70, 45, "show")] + click(7.2, 64, 68, (70, 45)) + [(7.7, 64, 68, "hide")]
    ev += click(9.0, 22, 58) + click(10.4, 57, 86, (22, 58)) + [(10.9, 57, 86, "hide")]
    ev += click(13.0, 50, 42) + [(13.5, 50, 42, "hide")]
    s.pointer(".lap .cur", ev)
    return s.out


def prep():
    s = Story("st-p", 12, 3)
    for k in range(1, 4):
        s.steps(k)
    # 1: TV Setup opens Developer options by itself; ADB on
    s.show(".p0", (0, 1.2)); s.show(".p2", (1.2, 4))
    s.press(".p2 .adb", 2.0); s.show(".p2 .dlg", (2.2, 3.2)); s.press(".p2 .dlg .go", 3.0); s.toggle(".p2 .adb .tg", 3.1)
    s.show(".o1", (3.3, 4))
    # 2: no Developer options yet: Build pressed seven times
    s.show(".p1", (4, 8))
    taps = [4.5 + .3 * i for i in range(7)]
    s.press(".p1 .bld", *taps)
    for i, (a, b) in enumerate([(taps[3], taps[4]), (taps[4], taps[5]), (taps[5], taps[6])]):
        s.show(f".p1 .left>:nth-child({i+1})", (a + .05, b), f=.05)
    s.show(".p1 .dev", (taps[6] + .1, 8), on="opacity:1;transform:none", off="opacity:0;transform:translateY(30%)")
    # 3: an operator's box wants its own password: keys only, through Android TV Remote
    s.show(".p3", (8, 10.6))
    s.press(".p3 .bld", 8.4, 8.7, 9.0)
    s.show(".p3 .pw", (9.2, 10.6))
    s.show(".o3", (10.8, 12)); s.show(".dfoc", (11.0, 12), f=.12)
    s.track(".dfoc", "transform:none", [(11.5, "transform:translateX(18.08cqw)", .2)])
    s.press(".rc .rk", 2.0, 3.0, *taps, 8.4, 8.7, 9.0)
    s.press(".rc .rd", 1.6, 2.6, 4.2, 10.0)
    return s.out


def build():
    return "".join(how() + browser() + phone_app() + computer() + prep())
