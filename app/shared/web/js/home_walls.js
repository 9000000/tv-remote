// 🏞️ TV Home's drawn backgrounds, as the page's small previews: the SAME recipe the TV paints (app/home WallArt.kt), written once
// as data (HomeConfig's WallSpec.kt holds a copy: tests/home_walls.test.js fails when the two differ) and drawn by two small
// painters that do the same maths. Every size is a share of the picture: x, r of its width, y of its height.
//   bg     the sky: colour stops [hex, at] along a straight line at `ang` degrees (0 = top to bottom, 90 = left to right)
//   layers drawn in order:
//     glow  a soft light: centre x y, radius r (of the width) squashed to `sy` of itself, colour c fading out from alpha a
//     disc  a sun or a bubble: the same with a soft edge
//     hill  a silhouette across the picture down to the bottom: edge y, swinging up and down by `amp` (of the height) along the waves
//           w = [[turns, phase, weight]...]; `ridge` 1 = sharp peaks; filled top → bot (ta / ba alpha)
//   shade  how much of TV Home's dark top-and-bottom veil goes over it (the words stay readable), 1 = what a photo gets
const HOME_ART = {
  "dawn": {
    "bg": [["#1a2147", 0], ["#51376d", 0.42], ["#b8506a", 0.7], ["#ee9a5c", 1]], "ang": 0, "shade": 0.55,
    "layers": [
      {"k": "glow", "x": 0.64, "y": 0.7, "r": 0.62, "sy": 0.55, "c": "#ffb070", "a": 0.8},
      {"k": "disc", "x": 0.64, "y": 0.68, "r": 0.055, "c": "#ffe0a8", "a": 1},
      {"k": "hill", "y": 0.76, "amp": 0.05, "w": [[1.3, 0.4, 1], [2.7, 2.1, 0.5]], "top": "#6a3f5e", "bot": "#3c2344"},
      {"k": "hill", "y": 0.84, "amp": 0.05, "w": [[1.1, 1.9, 1], [3.1, 0.3, 0.45]], "top": "#40254a", "bot": "#25152f"},
      {"k": "hill", "y": 0.92, "amp": 0.035, "w": [[1.6, 0.9, 1], [3.7, 2.4, 0.4]], "top": "#1f1229", "bot": "#120a1a"}
    ]
  },
  "peaks": {
    "bg": [["#1b2a45", 0], ["#385676", 0.5], ["#7d9bb0", 0.8]], "ang": 0, "shade": 0.6,
    "layers": [
      {"k": "glow", "x": 0.3, "y": 0.22, "r": 0.5, "sy": 0.6, "c": "#cfe3f2", "a": 0.28},
      {"k": "hill", "y": 0.5, "amp": 0.13, "ridge": 1, "w": [[1.7, 0.2, 1], [3.9, 1.1, 0.8], [7.3, 0.6, 0.5]], "top": "#7391aa", "bot": "#4e6b86"},
      {"k": "hill", "y": 0.62, "amp": 0.13, "ridge": 1, "w": [[1.3, 1.4, 1], [3.1, 0.3, 0.8], [6.2, 1.7, 0.5]], "top": "#4c6985", "bot": "#314c66"},
      {"k": "hill", "y": 0.75, "amp": 0.11, "ridge": 1, "w": [[1.9, 0.8, 1], [4.3, 1.9, 0.8], [8.1, 0.2, 0.5]], "top": "#2f4860", "bot": "#1e3347"},
      {"k": "hill", "y": 0.89, "amp": 0.07, "ridge": 1, "w": [[1.4, 0.1, 1], [3.3, 1.2, 0.5]], "top": "#1a2d3e", "bot": "#0e1b27"}
    ]
  },
  "dunes": {
    "bg": [["#2a1d33", 0], ["#7b4a48", 0.5], ["#d38c55", 0.82]], "ang": 0, "shade": 0.65,
    "layers": [
      {"k": "glow", "x": 0.8, "y": 0.5, "r": 0.5, "sy": 0.7, "c": "#ffc084", "a": 0.55},
      {"k": "disc", "x": 0.8, "y": 0.5, "r": 0.04, "c": "#ffe6bd", "a": 0.95},
      {"k": "hill", "y": 0.66, "amp": 0.055, "w": [[1.0, 0.3, 1], [2.3, 1.0, 0.35]], "top": "#a8613c", "bot": "#7e4129"},
      {"k": "hill", "y": 0.76, "amp": 0.06, "w": [[1.2, 2.1, 1], [2.9, 0.4, 0.4]], "top": "#7f4429", "bot": "#58301f"},
      {"k": "hill", "y": 0.86, "amp": 0.05, "w": [[0.9, 0.6, 1], [2.2, 1.5, 0.4]], "top": "#552b1c", "bot": "#35190f"},
      {"k": "hill", "y": 0.94, "amp": 0.03, "w": [[1.5, 1.2, 1], [3.4, 0.2, 0.3]], "top": "#26120b", "bot": "#190b07"}
    ]
  },
  "waves": {
    "bg": [["#06222c", 0], ["#0e5160", 0.55], ["#0a3a49", 1]], "ang": 0, "shade": 0.6,
    "layers": [
      {"k": "glow", "x": 0.75, "y": 0.04, "r": 0.55, "sy": 0.8, "c": "#7fe3d2", "a": 0.38},
      {"k": "hill", "y": 0.5, "amp": 0.03, "w": [[2.6, 0, 1], [5.1, 1.2, 0.5]], "top": "#2c8d97", "bot": "#0f4d5a", "ta": 0.55, "ba": 0.7},
      {"k": "hill", "y": 0.62, "amp": 0.035, "w": [[2.1, 1.7, 1], [4.6, 0.4, 0.5]], "top": "#1f7783", "bot": "#0b4452", "ta": 0.7, "ba": 0.85},
      {"k": "hill", "y": 0.75, "amp": 0.04, "w": [[1.8, 0.9, 1], [4.1, 2.0, 0.45]], "top": "#155f6c", "bot": "#08353f", "ta": 0.85, "ba": 0.95},
      {"k": "hill", "y": 0.88, "amp": 0.04, "w": [[1.5, 2.4, 1], [3.6, 1.1, 0.4]], "top": "#0d4854", "bot": "#052630"}
    ]
  },
  "candy": {
    "bg": [["#2b2152", 0], ["#3b2860", 1]], "ang": 135, "shade": 0.85,
    "layers": [
      {"k": "glow", "x": 0.18, "y": 0.22, "r": 0.6, "sy": 0.85, "c": "#ff7eb3", "a": 0.6},
      {"k": "glow", "x": 0.82, "y": 0.2, "r": 0.55, "sy": 0.85, "c": "#7c83ff", "a": 0.65},
      {"k": "glow", "x": 0.55, "y": 0.9, "r": 0.65, "sy": 0.7, "c": "#ffb38a", "a": 0.5},
      {"k": "glow", "x": 0.92, "y": 0.85, "r": 0.4, "sy": 0.9, "c": "#b07cff", "a": 0.5}
    ]
  },
  "peach": {
    "bg": [["#3a1322", 0], ["#4a1a2a", 1]], "ang": 135, "shade": 0.85,
    "layers": [
      {"k": "glow", "x": 0.14, "y": 0.3, "r": 0.62, "sy": 0.9, "c": "#ff7a59", "a": 0.6},
      {"k": "glow", "x": 0.7, "y": 0.12, "r": 0.5, "sy": 0.8, "c": "#ffb347", "a": 0.45},
      {"k": "glow", "x": 0.86, "y": 0.82, "r": 0.55, "sy": 0.85, "c": "#e8416c", "a": 0.6},
      {"k": "glow", "x": 0.4, "y": 0.95, "r": 0.5, "sy": 0.6, "c": "#ff9a76", "a": 0.4}
    ]
  },
  "meadow": {
    "bg": [["#14303a", 0], ["#3f6b5e", 0.45], ["#c9c985", 0.78]], "ang": 0, "shade": 0.7,
    "layers": [
      {"k": "glow", "x": 0.26, "y": 0.6, "r": 0.5, "sy": 0.6, "c": "#fff0a8", "a": 0.6},
      {"k": "disc", "x": 0.26, "y": 0.6, "r": 0.04, "c": "#fff6cc", "a": 0.95},
      {"k": "hill", "y": 0.66, "amp": 0.06, "w": [[1.2, 0.5, 1], [2.8, 1.6, 0.4]], "top": "#6e9448", "bot": "#43703a"},
      {"k": "hill", "y": 0.76, "amp": 0.06, "w": [[1.0, 2.0, 1], [2.5, 0.3, 0.45]], "top": "#437a3c", "bot": "#295429"},
      {"k": "hill", "y": 0.86, "amp": 0.05, "w": [[1.4, 0.7, 1], [3.2, 1.8, 0.4]], "top": "#2a5a2c", "bot": "#173a1d"},
      {"k": "hill", "y": 0.94, "amp": 0.03, "w": [[1.7, 1.1, 1], [3.9, 0.5, 0.3]], "top": "#153219", "bot": "#0d2212"}
    ]
  },
  "mist": {
    "bg": [["#27393a", 0], ["#47605f", 0.5], ["#95a8a3", 0.8]], "ang": 0, "shade": 0.65,
    "layers": [
      {"k": "hill", "y": 0.52, "amp": 0.1, "ridge": 1, "w": [[5.3, 0.3, 1], [11, 1.0, 0.6], [2.2, 0.5, 0.9]], "top": "#6c8480", "bot": "#4d6764"},
      {"k": "glow", "x": 0.5, "y": 0.64, "r": 0.9, "sy": 0.22, "c": "#cfe0dc", "a": 0.4},
      {"k": "hill", "y": 0.64, "amp": 0.1, "ridge": 1, "w": [[4.7, 1.2, 1], [9.8, 0.4, 0.6], [1.9, 1.5, 0.9]], "top": "#486260", "bot": "#33494a"},
      {"k": "glow", "x": 0.5, "y": 0.76, "r": 0.9, "sy": 0.2, "c": "#b9ccc8", "a": 0.3},
      {"k": "hill", "y": 0.77, "amp": 0.09, "ridge": 1, "w": [[6.1, 0.7, 1], [12.4, 1.9, 0.5], [2.6, 0.2, 0.8]], "top": "#2c4343", "bot": "#1d2f2f"},
      {"k": "hill", "y": 0.89, "amp": 0.07, "ridge": 1, "w": [[5.4, 1.6, 1], [10.3, 0.1, 0.5], [2.1, 1.1, 0.8]], "top": "#1a2a2a", "bot": "#0f1919"}
    ]
  },
  "bokeh": {
    "bg": [["#06222a", 0], ["#0d4650", 0.6], ["#092a39", 1]], "ang": 160, "shade": 0.7,
    "layers": [
      {"k": "disc", "x": 0.12, "y": 0.22, "r": 0.09, "c": "#7fe3d0", "a": 0.16},
      {"k": "disc", "x": 0.3, "y": 0.7, "r": 0.12, "c": "#9ad0ff", "a": 0.12},
      {"k": "disc", "x": 0.46, "y": 0.18, "r": 0.06, "c": "#ffe29a", "a": 0.2},
      {"k": "disc", "x": 0.58, "y": 0.52, "r": 0.1, "c": "#7fe3d0", "a": 0.14},
      {"k": "disc", "x": 0.72, "y": 0.8, "r": 0.075, "c": "#ffe29a", "a": 0.18},
      {"k": "disc", "x": 0.82, "y": 0.28, "r": 0.13, "c": "#9ad0ff", "a": 0.13},
      {"k": "disc", "x": 0.93, "y": 0.62, "r": 0.05, "c": "#7fe3d0", "a": 0.22},
      {"k": "disc", "x": 0.04, "y": 0.86, "r": 0.06, "c": "#ffe29a", "a": 0.15},
      {"k": "disc", "x": 0.66, "y": 0.1, "r": 0.035, "c": "#ffffff", "a": 0.18},
      {"k": "disc", "x": 0.2, "y": 0.5, "r": 0.04, "c": "#ffffff", "a": 0.12}
    ]
  },
  "canyon": {
    "bg": [["#2a1522", 0], ["#86403a", 0.5], ["#de885a", 0.76]], "ang": 0, "shade": 0.65,
    "layers": [
      {"k": "glow", "x": 0.5, "y": 0.62, "r": 0.7, "sy": 0.4, "c": "#ffb080", "a": 0.5},
      {"k": "hill", "y": 0.6, "amp": 0.08, "w": [[1.2, 0.2, 1], [2.9, 1.7, 0.5], [6.3, 0.9, 0.2]], "top": "#b0533b", "bot": "#872f26"},
      {"k": "hill", "y": 0.71, "amp": 0.08, "ridge": 1, "w": [[1.6, 1.1, 1], [3.7, 0.4, 0.45]], "top": "#8c382c", "bot": "#65231f"},
      {"k": "hill", "y": 0.82, "amp": 0.07, "ridge": 1, "w": [[1.1, 0.3, 1], [2.8, 1.5, 0.5]], "top": "#68261f", "bot": "#451815"},
      {"k": "hill", "y": 0.92, "amp": 0.04, "w": [[1.5, 0.8, 1], [3.5, 2.0, 0.35]], "top": "#34130f", "bot": "#200b09"}
    ]
  }
};
const HOME_ART_IDS = Object.keys(HOME_ART);

const homeHex = (h, a) => { const n = parseInt(h.slice(1), 16); return `rgba(${n >> 16 & 255},${n >> 8 & 255},${n & 255},${a})`; };
// the edge of a hill at `u` (0..1 across): the same sum WallArt.kt takes
const homeHillY = (l, u) => {
  let s = 0, t = 0;
  for (const [f, p, wt] of l.w) { s += wt * (l.ridge ? Math.abs(Math.sin(Math.PI * f * u + p)) : Math.sin(2 * Math.PI * f * u + p)); t += wt; }
  return l.y + l.amp * (l.ridge ? 2 * s / t - 1 : s / t);
};
// Draws wall `id` on a canvas context of w x h px (the page's small previews call it; resolution does not matter)
function homeArtPaint(ctx, w, h, id) {
  const a = HOME_ART[id];
  if (!a) return;
  const rad = a.ang * Math.PI / 180, dx = Math.sin(rad), dy = Math.cos(rad);
  const half = (Math.abs(dx) * w + Math.abs(dy) * h) / 2, cx = w / 2, cy = h / 2;      // the line runs corner to corner
  const g = ctx.createLinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half);
  for (const [c, at] of a.bg) g.addColorStop(at, c);
  ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
  for (const l of a.layers) {
    if (l.k === 'hill') {
      const top = (l.y - l.amp) * h, ga = ctx.createLinearGradient(0, top, 0, h);
      ga.addColorStop(0, homeHex(l.top, l.ta === undefined ? 1 : l.ta)); ga.addColorStop(1, homeHex(l.bot, l.ba === undefined ? 1 : l.ba));
      ctx.beginPath(); ctx.moveTo(0, h);
      for (let i = 0; i <= 96; i++) ctx.lineTo(w * i / 96, homeHillY(l, i / 96) * h);
      ctx.lineTo(w, h); ctx.closePath(); ctx.fillStyle = ga; ctx.fill();
    } else {
      const r = l.r * w, sy = l.sy === undefined ? 1 : l.sy;
      ctx.save(); ctx.translate(l.x * w, l.y * h); ctx.scale(1, sy);
      const rg = ctx.createRadialGradient(0, 0, 0, 0, 0, r);
      if (l.k === 'disc') { rg.addColorStop(0, homeHex(l.c, l.a)); rg.addColorStop(0.82, homeHex(l.c, l.a)); rg.addColorStop(1, homeHex(l.c, 0)); }
      else { rg.addColorStop(0, homeHex(l.c, l.a)); rg.addColorStop(0.5, homeHex(l.c, l.a * 0.35)); rg.addColorStop(1, homeHex(l.c, 0)); }
      ctx.fillStyle = rg; ctx.beginPath(); ctx.arc(0, 0, r, 0, 2 * Math.PI); ctx.fill();
      ctx.restore();
    }
  }
  // the veil TV Home puts over it (Backdrop.SHADE: black at 69 % / 25 % / 56 % from top to bottom), `shade` of its strength
  const s = ctx.createLinearGradient(0, 0, 0, h), k = a.shade;
  s.addColorStop(0, `rgba(0,0,0,${0.69 * k})`); s.addColorStop(0.5, `rgba(0,0,0,${0.25 * k})`); s.addColorStop(1, `rgba(0,0,0,${0.56 * k})`);
  ctx.fillStyle = s; ctx.fillRect(0, 0, w, h);
}
