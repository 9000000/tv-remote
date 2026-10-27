'use strict';
// Paper Planes: 1-4 paper planes fly on into the screen, side by side; fly through rings (gold ones count 3), keep clear of the storm clouds
// (a hit spins you out and costs 2). 75 seconds, the most points wins. The phone is the plane: turned like a steering wheel it banks and
// turns, tilted back it climbs (tvpad 'motion': the quat relative to how it was held at the start or at Recenter), and the plane on the TV
// copies its roll. A phone that sends no motion (plain-http Chrome before Tilt, Brave) flies by dragging the touchpad; a stick, the D-pad
// or the arrow keys work too. The button rolls the plane over: half a second no cloud can touch it, then it recharges.
// All 3D is drawn by hand on Kit's 2D canvas (a few dozen fills a frame, no WebGL): it runs on the weakest TV box TV Games runs on.
(function () {
  const K = window.Kit, pad = window.tvpad, W = 1280, H = 720;
  const F = 700, CYH = 416, CAMY = 7.5, BACK = 10, TH = 0.22;               // focal length, screen centre, camera height, camera behind the planes,
  const CT = Math.cos(TH), ST = Math.sin(TH), HZ = CYH - F * Math.tan(TH);   // its tilt down (so the wings show their top, not an edge); the horizon
  const XR = 8, YLO = 0.8, YHI = 8, TIME = 75, FAR = 170, RR = 1.7, GR = 1.15, CR = 1.45;
  const M = {}, PT = {};                                                     // per phone: motion {q, q0, at}, touchpad target {x, y, at}
  let ps, phase, ptimer, left, over, Z, speed, camX, objs, scenery, nextRing, nextCloud, pops, sky, hills;

  // ---- quaternions [w, x, y, z]: the phone's attitude relative to the one it had when calibrated
  const qmul = (a, b) => [a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3], a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
    a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1], a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0]];
  const rot = (q, v) => {                                                    // v turned by q
    const x = q[1], y = q[2], z = q[3], w = q[0];
    const tx = 2 * (y * v[2] - z * v[1]), ty = 2 * (z * v[0] - x * v[2]), tz = 2 * (x * v[1] - y * v[0]);
    return [v[0] + w * tx + y * tz - z * ty, v[1] + w * ty + z * tx - x * tz, v[2] + w * tz + x * ty - y * tx];
  };
  function tilt(n) {                                                         // [roll, pitch] in radians, or null when this phone sends no motion
    const m = M[n]; if (!m || performance.now() - m.at > 700) return null;
    if (!m.q0) m.q0 = m.q;
    const r = qmul([m.q0[0], -m.q0[1], -m.q0[2], -m.q0[3]], m.q), xa = rot(r, [1, 0, 0]), ya = rot(r, [0, 1, 0]);
    return [Math.atan2(xa[1], xa[0]), Math.atan2(ya[2], ya[1])];             // turned anticlockwise +; top toward the player (pulled back) +
  }
  const recenter = (n) => { if (M[n]) M[n].q0 = M[n].q; PT[n] = { x: 0, y: 0, at: 0 }; };
  if (pad) {
    pad.on('motion', (e) => { if (!e.quat) return; const m = M[e.player] || (M[e.player] = {}); m.q = e.quat; m.at = performance.now(); });
    pad.on('pointer', (e) => {
      const p = PT[e.player] || (PT[e.player] = { x: 0, y: 0, at: 0 });
      if (e.center) { recenter(e.player); return; }
      p.x = K.clamp(p.x + e.dx * 0.03, -XR, XR); p.y = K.clamp(p.y - e.dy * 0.03, YLO - 4, YHI - 4); p.at = performance.now();
    });
  }

  // ---- the world: x across, y up, z into the screen; the camera rides BACK behind the planes, which all sit at z = Z.
  // Everything (planes, rings, trees, the ground) is placed relative to a course that bends and rolls: course(z) is its centre line,
  // two sines each way, new phases each match, straight for the first stretch. The camera looks along the course where it is (its
  // slope is taken off, OutRun's trick: a shear, not a turn), so what lies ahead swings aside and rises over hills or drops behind them.
  let ph = [0, 0, 0, 0], vz = NaN, cwx = 0, cwy = 0, slx = 0, sly = 0, hz = HZ;
  const course = (z) => {
    const k = K.clamp((z - 60) / 140, 0, 1);
    return [k * (15 * Math.sin(z / 95 + ph[0]) + 6 * Math.sin(z / 41 + ph[1])), k * (6 * Math.sin(z / 80 + ph[2]) + 2.4 * Math.sin(z / 33 + ph[3]))];
  };
  function view() {                                                          // the camera's place and slope, once per position
    if (vz === Z) return; vz = Z;
    const cz = Z - BACK, a = course(cz - 1), b = course(cz + 1), m = course(cz);
    cwx = m[0]; cwy = m[1]; slx = (b[0] - a[0]) / 2; sly = (b[1] - a[1]) / 2;
    hz = CYH - F * (ST - sly * CT) / (CT + sly * ST);                        // where the flat far away meets the sky, from this slope
  }
  function proj(x, y, z) {
    view();
    const dz = z - (Z - BACK), c = course(z), dy = y + c[1] - cwy - sly * dz - CAMY, d = dz * CT - dy * ST; if (d < 0.6) return null;
    const s = F / d; return [W / 2 + (x + c[0] - cwx - slx * dz - camX) * s, CYH - (dy * CT + dz * ST) * s, s, d];
  }
  const fog = (d) => K.clamp(1.15 - d / FAR, 0, 1);
  function spawn(kind) {
    const z = Z + FAR, last = objs[objs.length - 1];
    if (kind === 'cloud') {                                                    // often right next to the ring before it: a choice, not a wall
      const near = last && last.kind !== 'cloud' && Math.random() < 0.55;
      objs.push({ kind, z, x: near ? K.clamp(last.x + K.pick([-1, 1]) * K.rnd(2.2, 3.2), -XR, XR) : K.rnd(-XR, XR), y: near ? last.y + K.rnd(-1, 1) : K.rnd(YLO + 0.6, YHI - 0.6), got: {} });
    } else {
      const prev = objs.filter((o) => o.kind !== 'cloud').pop(), gold = Math.random() < 0.18;
      const x = prev ? K.clamp(prev.x + K.rnd(-4.5, 4.5), -XR + 1, XR - 1) : 0, y = prev ? K.clamp(prev.y + K.rnd(-2.5, 2.5), YLO + 1, YHI - 1) : 4;
      objs.push({ kind: gold ? 'gold' : 'ring', z, x, y, got: {} });
    }
  }

  // what stands by the river, now and then: mostly trees, a farmhouse, a windmill, a patch of flowers. Few, so the rings stay easy to see
  const KINDS = ['pine', 'pine', 'pine', 'oak', 'oak', 'oak', 'flowers', 'flowers', 'house', 'mill'];
  function place(o) {
    o.kind = K.pick(KINDS); o.x = K.pick([-1, 1]) * (o.kind === 'house' || o.kind === 'mill' ? K.rnd(14, 24) : K.rnd(10.5, 26));
    o.h = K.rnd(1.6, 3.2); o.c = K.pick(['#f472b6', '#facc15', '#f8fafc', '#c084fc']); o.t = K.rnd(0, 6); return o;
  }

  function start() {
    Z = 0; vz = NaN; ph = [K.rnd(0, 6.3), K.rnd(0, 6.3), K.rnd(0, 6.3), K.rnd(0, 6.3)]; speed = 24; camX = 0; objs = []; pops = []; over = false; phase = 'ready'; ptimer = 2.6; left = TIME; nextRing = 0.3; nextCloud = 2.5;
    const n = K.slots.length;
    ps = K.slots.map((s, i) => ({ s, i, x: (i - (n - 1) / 2) * 3, y: 4, vx: 0, vy: 0, bank: 0, pitch: 0, stun: 0, roll: 0, cool: 0, err: 0, errT: 0 }));
    K.slots.forEach((s) => { s.score = 0; if (s.human) recenter(s.pad); });
    scenery = [];
    for (let k = 0; k < 24; k++) scenery.push(place({ z: K.rnd(0, FAR) }));
    for (let k = 0; k < 7; k++) scenery.push({ kind: 'puff', x: K.rnd(-40, 40), y: K.rnd(13, 20), z: K.rnd(30, FAR), r: K.rnd(3, 6) });
  }

  function cpu(p, dt) {                                                      // [sx, sy]: to the next ring worth having, away from a cloud in the way
    let goal = null;
    for (const o of objs) if (o.kind !== 'cloud' && o.z > Z + 2 && !o.got[p.i] && (!goal || o.z < goal.z - (o.kind === 'gold' ? 25 : 0))) goal = o;
    p.errT -= dt; if (p.errT <= 0) { p.errT = K.rnd(0.6, 1.4); p.err = K.rnd(-1.1, 1.1); }
    let tx = goal ? goal.x + p.err * 0.6 : 0, ty = goal ? goal.y + p.err * 0.4 : 4;
    for (const o of objs) if (o.kind === 'cloud' && o.z > Z && o.z < Z + 30 && Math.abs(o.x - p.x) < 2.6 && Math.abs(o.y - p.y) < 2.4) {
      tx = p.x + (p.x >= o.x ? 4 : -4); if (o.z < Z + 10 && p.cool <= 0 && Math.random() < 0.02) p.wantRoll = true;
    }
    return [K.clamp((tx - p.x) / 3, -1, 1) * 0.8, K.clamp((ty - p.y) / 2.5, -1, 1) * 0.75];
  }

  function input(p, dt) {                                                    // [sx, sy, bank to show or null]
    const s = p.s;
    if (!s.human) return cpu(p, dt).concat([null]);
    const n = s.pad, q = K.pad(n), t = tilt(n);
    if (q.pulse) p.wantRoll = true;
    if (t) {
      const dz = (v, z) => (Math.abs(v) < z ? 0 : v - Math.sign(v) * z);
      return [K.clamp(-dz(t[0], 0.06) / 0.5, -1, 1), K.clamp(dz(t[1], 0.06) / 0.4, -1, 1), K.clamp(-t[0], -1.4, 1.4)];
    }
    const pt = PT[n];
    if (pt && performance.now() - pt.at < 4000 && !q.x && !q.y) return [K.clamp((pt.x - p.x) / 2, -1, 1), K.clamp((pt.y + 4 - p.y) / 2, -1, 1), null];
    return [q.x, -q.y, null];
  }

  function score(p, v, color) {
    p.s.score = Math.max(0, p.s.score + v);
    const a = proj(p.x, p.y + 1.2, Z); if (a) pops.push({ x: a[0], y: a[1], t: 0, txt: (v > 0 ? '+' : '') + v, color });
  }

  function update(dt) {
    K.updateParts(dt);
    if (over) return;
    for (const pp of pops) pp.t += dt; pops = pops.filter((pp) => pp.t < 1);
    if (phase === 'ready') {
      ptimer -= dt; K.slots.forEach((s) => { if (s.human) recenter(s.pad); });        // whatever way the phone is held at GO is level
      if (ptimer <= 0) { phase = 'play'; K.sfx.go(); }
    } else {
      left -= dt; K.timeTick(left); speed = 24 + (TIME - left) * 0.17;
    }
    Z += speed * dt * (phase === 'ready' ? 0.5 : 1);
    if (phase === 'play') {
      nextRing -= dt; if (nextRing <= 0) { spawn('ring'); nextRing = K.rnd(0.75, 1.05); }
      nextCloud -= dt; if (nextCloud <= 0) { spawn('cloud'); nextCloud = K.rnd(0.9, 1.6) * (left > 40 ? 1.3 : 0.85); }
    }
    for (const sc of scenery) if (sc.z < Z - BACK + 1) { sc.z += FAR + BACK; if (sc.kind === 'puff') sc.x = K.rnd(-40, 40); else place(sc); }

    for (const p of ps) {
      p.cool = Math.max(0, p.cool - dt);
      let inp = input(p, dt);
      if (p.wantRoll) { p.wantRoll = false; if (p.cool <= 0 && !p.stun && phase === 'play') { p.roll = 0.6; p.cool = 2.4; K.sfx.dash(); } }
      if (p.stun > 0) { p.stun -= dt; inp = [0, -0.4, null]; }
      if (p.roll > 0) p.roll = Math.max(0, p.roll - dt);
      const k = Math.min(1, dt * 5);
      p.vx += (inp[0] * 10 - p.vx) * k; p.vy += (inp[1] * 7 - p.vy) * k;
      p.x = K.clamp(p.x + p.vx * dt, -XR, XR); p.y = K.clamp(p.y + p.vy * dt, YLO, YHI);
      const bank = inp[2] != null ? inp[2] : inp[0] * 0.7;
      p.bank += (bank - p.bank) * Math.min(1, dt * 10); p.pitch += (inp[1] * 0.4 - p.pitch) * k;
    }
    for (let a = 0; a < ps.length; a++) for (let b = a + 1; b < ps.length; b++) {   // wing to wing: nudged apart, never through each other
      const A = ps[a], B = ps[b], dx = B.x - A.x, dy = B.y - A.y, d = Math.hypot(dx, dy);
      if (d < 1.5 && d > 0.001) { const o = (1.5 - d) / 2; A.x -= dx / d * o; A.y -= dy / d * o; B.x += dx / d * o; B.y += dy / d * o; }
    }
    camX += (ps.reduce((t, p) => t + p.x, 0) / ps.length * 0.35 - camX) * Math.min(1, dt * 2);

    for (const o of objs) {                                                   // each plane meets each thing once, as it crosses the planes' z
      if (o.passed || o.z > Z) continue;
      o.passed = true;
      for (const p of ps) {
        const d = Math.hypot(p.x - o.x, p.y - o.y);
        if (o.kind === 'cloud') {
          if (d < CR + 0.4 && !p.roll && !p.stun) { p.stun = 1.2; score(p, -2, '#f87171'); K.sfx.boom(); K.shake(8); const a = proj(p.x, p.y, Z); if (a) K.burst(a[0], a[1], '#cbd5e1', 14, 300, 0.5, 6); }
        } else if (d < (o.kind === 'gold' ? GR : RR)) {
          o.got[p.i] = true; const v = o.kind === 'gold' ? 3 : 1; score(p, v, o.kind === 'gold' ? '#ffd23f' : '#fff');
          if (v > 1) K.sfx.goal(); else K.sfx.score();
          const a = proj(o.x, o.y, Z); if (a) K.burst(a[0], a[1], o.kind === 'gold' ? '#ffd23f' : p.s.color, 10, 260, 0.45, 5);
        }
      }
    }
    objs = objs.filter((o) => o.z > Z - 4);
    if (phase === 'play' && left <= 0) {
      over = true; const top = Math.max.apply(null, K.slots.map((s) => s.score)), ws = K.slots.filter((s) => s.score === top);
      K.over(ws.length === 1 ? ws[0].i : -1, K.slots.map((s) => s.name + ': ' + s.score));
    }
  }

  // ---- drawing
  function backdrop(c) {
    if (!sky) {
      sky = c.createLinearGradient(0, 0, 0, HZ); sky.addColorStop(0, '#2b6fd6'); sky.addColorStop(0.7, '#8ec5f2'); sky.addColorStop(1, '#ffe2b8');
    }
    view(); const h = K.clamp(hz, 60, H);
    c.fillStyle = sky; c.fillRect(0, 0, W, h + 1);
    c.fillStyle = '#4f9a4c'; c.fillRect(0, h, W, H - h);                      // the far fields; the bands paint over it
    if (!hills) hills = ridges();
    const sx = (((-slx * 900 - camX * 6) % W) + W) % W;                      // far away: slides with each bend, never comes nearer
    K.light(c, W * 0.72 - slx * 500, h - 150, '#fff3c4', 120, 0.9);   // the sun, low over the hills
    c.drawImage(hills, sx - W, h - 150); c.drawImage(hills, sx, h - 150);
    c.globalAlpha = 0.5; c.fillStyle = '#ffe2b8'; c.fillRect(0, h - 1, W, 22); c.globalAlpha = 1;     // haze on the horizon
  }
  function ridges() {                                                        // two rows of far hills, drawn once, W wide and seamless
    const g = document.createElement('canvas'); g.width = W; g.height = 152; const c = g.getContext('2d');
    [['#a9bfdc', 90, 3, 0.4], ['#86ae95', 45, 5, 2.1]].forEach((r) => {
      c.fillStyle = r[0]; c.beginPath(); c.moveTo(0, 152);
      for (let x = 0; x <= W; x += 16) { const a = x / W * 6.2832; c.lineTo(x, 150 - r[1] * (0.55 + 0.3 * Math.sin(a * r[2] + r[3]) + 0.15 * Math.sin(a * r[2] * 3 + 1))); }
      c.lineTo(W, 152); c.fill();
    });
    return g;
  }
  const STEP = 8;
  function drawBand(c, z) {                                                   // the ground from z to z + STEP, drawn far to near (a hill hides what is behind it)
    const a = proj(0, 0, z), b = proj(0, 0, z + STEP); if (!a || !b || a[1] <= b[1]) return;   // its far edge above its near one, or it faces away
    c.globalAlpha = Math.max(0.35, fog(b[3]));
    c.fillStyle = ((z / STEP) & 1) ? '#5fae5a' : '#56a352'; c.fillRect(0, b[1], W, a[1] - b[1] + 1);
    c.fillStyle = ((z / STEP) & 1) ? '#c9b48a' : '#c2ad82';                    // a river of sand down the middle: shows each bend and hill
    const w0 = 3 * a[2], w1 = 3 * b[2];
    c.beginPath(); c.moveTo(a[0] - w0, a[1] + 1); c.lineTo(a[0] + w0, a[1] + 1); c.lineTo(b[0] + w1, b[1]); c.lineTo(b[0] - w1, b[1]); c.fill();
    c.globalAlpha = 1;
  }

  function drawTree(c, t) {                                                  // anything by the river
    const a = proj(t.x, 0, t.z); if (!a || a[3] > FAR) return;
    const s = a[2], x = a[0], y = a[1]; c.globalAlpha = fog(a[3]);
    if (t.kind === 'pine') {
      c.fillStyle = '#7a5230'; c.fillRect(x - 0.12 * s, y - 0.8 * s, 0.24 * s, 0.8 * s);
      c.fillStyle = '#2f7a3a'; c.beginPath(); c.moveTo(x, y - t.h * s); c.lineTo(x + 0.9 * s, y - 0.6 * s); c.lineTo(x - 0.9 * s, y - 0.6 * s); c.fill();
    } else if (t.kind === 'oak') {
      c.fillStyle = '#7a5230'; c.fillRect(x - 0.14 * s, y - 1.1 * s, 0.28 * s, 1.1 * s);
      c.fillStyle = '#3f8f3a'; blob(c, [x, y - 1.7 * s, 0.85 * s, x - 0.55 * s, y - 1.3 * s, 0.6 * s, x + 0.6 * s, y - 1.35 * s, 0.6 * s]);
      c.fillStyle = '#5aa84e'; blob(c, [x - 0.2 * s, y - 1.95 * s, 0.4 * s]);
    } else if (t.kind === 'flowers') {
      c.fillStyle = '#4a9a45'; c.beginPath(); c.ellipse(x, y, 1.4 * s, 0.3 * s, 0, 0, 6.29); c.fill();
      c.fillStyle = t.c; for (let k = 0; k < 7; k++) { const u = Math.sin(t.t + k * 2.4), v = Math.cos(t.t * 1.7 + k * 1.3); c.fillRect(x + u * 1.2 * s - 0.08 * s, y + v * 0.2 * s - 0.12 * s, 0.16 * s, 0.16 * s); }
    } else if (t.kind === 'house') {
      const w = 1.6 * s, hh = 1.1 * s;
      c.fillStyle = '#f3e9d2'; c.fillRect(x - w / 2, y - hh, w, hh);
      c.fillStyle = '#c2410c'; c.beginPath(); c.moveTo(x - w * 0.62, y - hh); c.lineTo(x, y - hh - 0.8 * s); c.lineTo(x + w * 0.62, y - hh); c.fill();
      c.fillStyle = '#7c4a1e'; c.fillRect(x - 0.17 * s, y - 0.6 * s, 0.34 * s, 0.6 * s);
      c.fillStyle = '#93c5fd'; c.fillRect(x + 0.35 * s, y - 0.8 * s, 0.3 * s, 0.28 * s); c.fillRect(x - 0.65 * s, y - 0.8 * s, 0.3 * s, 0.28 * s);
    } else {                                                                 // a windmill, its sails turning slowly
      c.fillStyle = '#ece4d4'; c.beginPath(); c.moveTo(x - 0.5 * s, y); c.lineTo(x + 0.5 * s, y); c.lineTo(x + 0.28 * s, y - 2.6 * s); c.lineTo(x - 0.28 * s, y - 2.6 * s); c.fill();
      c.fillStyle = '#9a3412'; c.beginPath(); c.arc(x, y - 2.6 * s, 0.32 * s, Math.PI, 0); c.fill();
      c.strokeStyle = '#5b4636'; c.lineWidth = Math.max(1, 0.12 * s); c.beginPath();
      const r = K.t * 0.8 + t.t;
      for (let k = 0; k < 4; k++) { const an = r + k * 1.5708; c.moveTo(x, y - 2.5 * s); c.lineTo(x + Math.cos(an) * 1.5 * s, y - 2.5 * s + Math.sin(an) * 1.5 * s); }
      c.stroke();
      c.fillStyle = '#5b4636'; c.beginPath(); c.arc(x, y - 2.5 * s, 0.1 * s, 0, 6.29); c.fill();
    }
    c.globalAlpha = 1;
  }
  function blob(c, k) {                                                       // circles [x, y, r, ...] as one shape (each its own sub-path: no seams)
    c.beginPath(); for (let i = 0; i < k.length; i += 3) { c.moveTo(k[i] + k[i + 2], k[i + 1]); c.arc(k[i], k[i + 1], k[i + 2], 0, 6.29); } c.fill();
  }
  function drawPuff(c, o) {
    const a = proj(o.x, o.y, o.z); if (!a) return;
    const s = a[2]; c.globalAlpha = 0.85 * fog(a[3]); c.fillStyle = '#fff';
    blob(c, [a[0], a[1], o.r * s, a[0] - o.r * 0.9 * s, a[1] + o.r * 0.25 * s, o.r * 0.7 * s, a[0] + o.r * s, a[1] + o.r * 0.3 * s, o.r * 0.6 * s]);
    c.globalAlpha = 1;
  }
  function drawObj(c, o) {
    const a = proj(o.x, o.y, o.z); if (!a) return;
    const s = a[2], f = o.z < Z ? K.clamp((o.z - Z + 4) / 4, 0, 1) * 0.45 : fog(a[3]);
    c.globalAlpha = f;
    if (o.kind === 'cloud') {
      const r = CR * s;
      c.fillStyle = '#4b5568'; blob(c, [a[0], a[1], r, a[0] - r * 0.85, a[1] + r * 0.25, r * 0.7, a[0] + r * 0.85, a[1] + r * 0.2, r * 0.75]);
      c.fillStyle = '#6b7488'; c.beginPath(); c.arc(a[0] - r * 0.25, a[1] - r * 0.3, r * 0.55, 0, 6.29); c.fill();
      c.strokeStyle = '#ffd23f'; c.lineWidth = Math.max(2, 0.14 * s); c.lineJoin = 'round';
      c.beginPath(); c.moveTo(a[0] + r * 0.1, a[1] + r * 0.5); c.lineTo(a[0] - r * 0.2, a[1] + r * 1.05); c.lineTo(a[0] + r * 0.15, a[1] + r * 1.0); c.lineTo(a[0] - r * 0.1, a[1] + r * 1.55); c.stroke();
    } else {
      const gold = o.kind === 'gold', r = (gold ? GR : RR) * s, taken = Object.keys(o.got).length > 0;
      c.lineWidth = Math.max(2, (gold ? 0.32 : 0.26) * s);
      c.strokeStyle = 'rgba(0,0,0,.25)'; c.beginPath(); c.arc(a[0], a[1] + c.lineWidth * 0.35, r, 0, 6.29); c.stroke();
      c.strokeStyle = gold ? '#ffd23f' : taken ? '#bfe8ff' : '#ffffff'; c.beginPath(); c.arc(a[0], a[1], r, 0, 6.29); c.stroke();
      if (gold && a[3] < 80) K.light(c, a[0], a[1], '#ffd23f', r * 1.6, 0.25 * f);
      const g = proj(o.x, 0, o.z);                                             // its spot on the ground: where it is, in depth
      if (g) { c.globalAlpha = 0.18 * f; c.fillStyle = '#000'; c.beginPath(); c.ellipse(g[0], g[1], r * 0.8, r * 0.18, 0, 0, 6.29); c.fill(); }
    }
    c.globalAlpha = 1;
  }

  const MODEL = [[0, 0, 1.25], [-0.95, 0.06, -0.55], [0.95, 0.06, -0.55], [0, 0, -0.6], [0, -0.38, -0.45]];   // nose, wing tips, tail, keel
  const TRIS = [[0, 1, 3, 1], [0, 3, 2, 1], [0, 3, 4, 0.7], [0, 4, 3, 0.55]];                                  // corners + shade
  function lighter(hex, k) {
    const v = parseInt(hex.slice(1), 16), f = (x) => Math.round(x + (255 - x) * k);
    return 'rgb(' + f(v >> 16) + ',' + f((v >> 8) & 255) + ',' + f(v & 255) + ')';
  }
  function drawPlane(c, p) {
    const g = proj(p.x, 0, Z); if (g) { c.globalAlpha = 0.22; c.fillStyle = '#000'; c.beginPath(); c.ellipse(g[0], g[1], 0.9 * g[2], 0.2 * g[2], 0, 0, 6.29); c.fill(); c.globalAlpha = 1; }
    const b = p.bank + (p.roll > 0 ? (1 - p.roll / 0.6) * 6.283 : 0) + (p.stun > 0 ? p.stun * 9 : 0), pi = p.pitch, h = p.vx * 0.018;
    const cb = Math.cos(b), sb = Math.sin(b), cp = Math.cos(pi), sp = Math.sin(pi), ch = Math.cos(h), sh = Math.sin(h);
    const pts = MODEL.map((v) => {
      let x = v[0] * cb + v[1] * sb, y = -v[0] * sb + v[1] * cb, z = v[2];          // bank: right wing down for +b
      const y2 = y * cp + z * sp; z = -y * sp + z * cp; y = y2;                     // pitch: nose up for +p
      const x2 = x * ch + z * sh; z = -x * sh + z * ch; x = x2;                     // heading: nose right for +h
      return proj(p.x + x, p.y + y, Z + z);
    });
    if (pts.some((q) => !q)) return;
    const tris = TRIS.map((t) => ({ t, d: (pts[t[0]][3] + pts[t[1]][3] + pts[t[2]][3]) / 3 })).sort((u, v) => v.d - u.d);
    c.lineJoin = 'round'; c.lineWidth = 2; c.strokeStyle = 'rgba(20,24,40,.7)';
    for (const o of tris) {
      const t = o.t, A = pts[t[0]], B = pts[t[1]], C = pts[t[2]];
      const facing = (B[0] - A[0]) * (C[1] - A[1]) - (B[1] - A[1]) * (C[0] - A[0]);   // which side of the paper we see: the underside a shade darker
      c.fillStyle = t[3] === 1 ? lighter(p.s.color, facing < 0 ? 0.55 : 0.2) : lighter(p.s.color, t[3] - 0.5);
      c.beginPath(); c.moveTo(A[0], A[1]); c.lineTo(B[0], B[1]); c.lineTo(C[0], C[1]); c.closePath(); c.fill(); c.stroke();
    }
    if (p.roll > 0) K.light(c, pts[0][0], pts[0][1], '#ffffff', 60, 0.35);
  }

  function draw(c) {
    c.save(); K.shakeApply(c);
    backdrop(c);
    for (const sc of scenery) if (sc.kind === 'puff') drawPuff(c, sc);
    const z0 = Math.floor((Z - BACK) / STEP) * STEP, all = [];               // one far-to-near order for ground bands (by their far edge), things and the planes
    for (let z = z0 + FAR; z >= z0; z -= STEP) all.push({ kind: 'band', z, k: z + STEP });
    for (const t of scenery) if (t.kind !== 'puff') all.push({ o: t, k: t.z });
    for (const o of objs) all.push({ o, k: o.z });
    all.push({ kind: 'planes', k: Z - 0.01 });
    all.sort((a, b) => b.k - a.k);
    for (const it of all) {
      if (it.kind === 'band') drawBand(c, it.z);
      else if (it.kind === 'planes') ps.slice().sort((a, b) => b.y - a.y).forEach((p) => drawPlane(c, p));
      else if (it.o.got) drawObj(c, it.o); else drawTree(c, it.o);
    }
    K.drawParts(c);
    for (const pp of pops) { c.globalAlpha = 1 - pp.t; K.text(c, pp.txt, pp.x, pp.y - pp.t * 60, 40, pp.color, 'center', 900); } c.globalAlpha = 1;
    c.restore();
    K.hud(c, K.slots);
    if (phase === 'ready') {
      ps.forEach((p) => { const a = proj(p.x, p.y + 1.1, Z); if (a) K.nameTag(c, p.s, a[0], a[1]); });
      K.banner(c, Math.ceil(ptimer) > 2 ? K.tr({ vi: 'Cầm điện thoại thẳng', en: 'Hold your phone level' }) : String(Math.ceil(ptimer)),
        K.tr({ vi: 'Xoay như vô lăng để rẽ · ngả về sau để bay lên · nút: lộn vòng', en: 'Turn it like a wheel to steer · tilt back to climb · button: roll' }));
    } else {
      c.fillStyle = 'rgba(8,12,28,.6)'; K.rr(c, W / 2 - 60, 80, 120, 48, 14); c.fill();
      K.text(c, String(Math.max(0, Math.ceil(left))), W / 2, 105, 34, left < 10 ? '#fca5a5' : '#fff', 'center', 900);
    }
  }

  K.layout = () => {                                                         // in flight: the touchpad (a fallback, and the Tilt switch to motion) + the roll button
    if (!pad || !K.G) return;
    try { pad.layout('all', K.mode === 'play' ? { pointer: true, motion: true, buttons: ['A'] } : { buttons: ['A', 'START'] }); } catch (e) { }
  };
  K.run({
    id: 'planes', title: { vi: 'Máy bay giấy', en: 'Paper Planes' },
    tag: { vi: 'Lái bằng cách xoay điện thoại — bay qua vòng, né mây giông', en: 'Steer by turning your phone — fly through rings, dodge the storms' },
    help: { vi: ['Xoay điện thoại như vô lăng: rẽ · ngả về sau / chúi tới: lên / xuống', 'Không xoay được? Kéo trên bàn di, hoặc cần analog / D-pad', 'Nút: lộn vòng (né mây giông)', 'Vòng: +1 · vòng vàng: +3 · trúng mây: −2'],
      en: ['Turn your phone like a wheel: steer · tilt back / forward: up / down', 'No motion? Drag the touchpad, or use a stick / D-pad', 'Button: barrel roll (dodges a storm)', 'Ring: +1 · gold ring: +3 · storm: −2'] },
    total: [1, 4], buttons: ['A'],
    start, update, draw,
  });
})();
