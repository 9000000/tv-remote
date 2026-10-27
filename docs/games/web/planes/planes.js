'use strict';
// Paper Planes: 1-4 paper planes fly on into the screen, side by side; fly through rings (gold ones count 3), keep clear of the storm clouds
// (a hit spins you out and costs 2). 75 seconds, the most points wins. The phone is the plane: turned like a steering wheel it banks and
// turns, tilted back it climbs (tvpad 'motion': the quat relative to how it was held at the start or at Recenter), and the plane on the TV
// copies its roll. A phone that sends no motion (plain-http Chrome before Tilt, Brave) flies by dragging the touchpad; a stick, the D-pad
// or the arrow keys work too. B rolls the plane over: half a second nothing can touch it, then it recharges.
// Mario Kart's boxes: a rainbow "?" gives one thing, the further behind the better the odds; A uses it (with nothing held, A rolls, so
// the TV remote's one OK button plays it all). The planes fly side by side, so what is fired forward hits the course, not a plane:
// paper balls pop storms (+1) and take rings ahead; the rocket and the storm call are what reach the others.
// All 3D is drawn by hand on Kit's 2D canvas (a few dozen fills a frame, no WebGL): it runs on the weakest TV box TV Games runs on.
(function () {
  const K = window.Kit, pad = window.tvpad, W = 1280, H = 720;
  const F = 700, CYH = 416, CAMY = 7.5, BACK = 10, TH = 0.22;               // focal length, screen centre, camera height, camera behind the planes,
  const CT = Math.cos(TH), ST = Math.sin(TH), HZ = CYH - F * Math.tan(TH);   // its tilt down (so the wings show their top, not an edge); the horizon
  const XR = 8, YLO = 0.8, YHI = 8, CLO = 3, TIME = 75, FAR = 170, RR = 1.7, GR = 1.15, CR = 1.45;
  const M = {}, PT = {};                                                     // per phone: motion {q, q0, at}, touchpad target {x, y, at}
  let flyers = [], flash = [];
  let ps, phase, ptimer, left, over, Z, speed, camX, objs, scenery, nextRing, nextCloud, nextBox, pops, shots, sky, hills;

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
  const BOX = 0.62, BOXF = [[0, 1, 3, 2], [4, 5, 7, 6], [0, 2, 6, 4], [1, 3, 7, 5], [0, 1, 5, 4], [2, 3, 7, 6]];   // half its size; faces by corner (bit0 x, bit1 y, bit2 z)
  const isRing = (o) => o.kind === 'ring' || o.kind === 'gold';
  function spawn(kind) {
    if (kind === 'box') {                                                      // a row of 2-3 across the course, like Mario Kart's
      const n = K.pick([2, 3]), y = K.rnd(YLO + 1.5, YHI - 1.5), c = K.rnd(-3, 3);
      for (let i = 0; i < n; i++) objs.push({ kind, z: Z + FAR, x: K.clamp(c + (i - (n - 1) / 2) * 4.5, -XR, XR), y, got: {} });
      return;
    }
    const z = Z + FAR, last = objs[objs.length - 1];
    if (kind === 'cloud') {                                                    // often right next to the ring before it: a choice, not a wall
      const near = last && isRing(last) && Math.random() < 0.55;
      objs.push({ kind, z, x: near ? K.clamp(last.x + K.pick([-1, 1]) * K.rnd(2.2, 3.2), -XR, XR) : K.rnd(-XR, XR), y: K.clamp(near ? last.y + K.rnd(-1, 1) : K.rnd(CLO, YHI), CLO, YHI - 0.4), got: {} });
    } else {
      const prev = objs.filter(isRing).pop(), gold = Math.random() < 0.18;
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
    Z = 0; vz = NaN; ph = [K.rnd(0, 6.3), K.rnd(0, 6.3), K.rnd(0, 6.3), K.rnd(0, 6.3)]; speed = 24; camX = 0; objs = []; pops = []; flyers = []; flash = []; over = false; phase = 'ready'; ptimer = 2.6; left = TIME; nextRing = 0.3; nextCloud = 2.5; nextBox = 3.5; shots = [];
    const n = K.slots.length;
    ps = K.slots.map((s, i) => ({ s, i, x: (i - (n - 1) / 2) * 3, y: 4, vx: 0, vy: 0, bank: 0, pitch: 0, stun: 0, roll: 0, cool: 0, err: 0, errT: 0, item: null, n: 0, spin: 0, shield: 0, mag: 0, useT: 0 }));
    K.slots.forEach((s) => { s.score = 0; if (s.human) recenter(s.pad); });
    scenery = [];
    for (let k = 0; k < 24; k++) scenery.push(place({ z: K.rnd(0, FAR) }));
    for (let k = 0; k < 7; k++) scenery.push({ kind: 'puff', x: K.rnd(-40, 40), y: K.rnd(13, 20), z: K.rnd(30, FAR), r: K.rnd(3, 6) });
  }

  function cpu(p, dt) {                                                      // [sx, sy]: to the next ring worth having, away from a cloud in the way
    let goal = null;
    if (p.item && (p.useT -= dt) <= 0) { p.wantUse = true; p.useT = K.rnd(0.4, 1.4); }
    const want = (o) => isRing(o) || (o.kind === 'box' && !p.item && !p.spin);
    for (const o of objs) if (want(o) && o.z > Z + 2 && !o.got[p.i] && (!goal || o.z < goal.z - (o.kind === 'gold' ? 25 : 0))) goal = o;
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
    if (q.hit.A) { if (p.item) p.wantUse = true; else p.wantRoll = true; }
    if (q.hit.B) p.wantRoll = true;
    if (t) {
      const dz = (v, z) => (Math.abs(v) < z ? 0 : v - Math.sign(v) * z);
      return [K.clamp(-dz(t[0], 0.06) / 0.5, -1, 1), K.clamp(dz(t[1], 0.06) / 0.4, -1, 1), K.clamp(-t[0], -1.4, 1.4)];
    }
    const pt = PT[n];
    if (pt && performance.now() - pt.at < 4000 && !q.x && !q.y) return [K.clamp((pt.x - p.x) / 2, -1, 1), K.clamp((pt.y + 4 - p.y) / 2, -1, 1), null];
    return [q.x, -q.y, null];
  }

  // A point won flies from where it was won (the ring itself, lit up) to that player's score at the top, and counts when it lands: who
  // scored and where they stand in one look, no "+1" scattered over the course. A loss shows on the spot, in red.
  function score(p, v, color, o) {
    const a = o ? proj(o.x, o.y, o.z) : proj(p.x, p.y, Z);
    if (v < 0 || !a) { p.s.score = Math.max(0, p.s.score + v); if (a && v < 0) pops.push({ x: a[0], y: a[1] - 40, t: 0, txt: String(v), color }); return; }
    flyers.push({ p, v, gold: v > 1, x: a[0], y: a[1], r: K.clamp((o && isRing(o) ? (o.kind === 'gold' ? GR : RR) : 0.7) * a[2], 10, 70), t: 0 });
  }
  function hudAt(i) {                                                         // where Kit's score bar shows slot i's number
    const n = K.slots.length, cw = Math.min(290, (W - 60) / n - 14), gx = W / 2 - (n * cw + (n - 1) * 14) / 2;
    return [gx + i * (cw + 14) + cw - 40, 42];
  }
  function land(f) { f.p.s.score += f.v; flash[f.p.i] = 0.45; }
  function drawFlyers(c) {
    for (const f of flyers) {
      const h = hudAt(f.p.i), u = f.t * f.t * (3 - 2 * f.t), x = f.x + (h[0] - f.x) * u, y = f.y + (h[1] - f.y) * u - Math.sin(Math.PI * f.t) * 70;
      const r = f.t < 0.15 ? f.r * (1 + f.t * 2) : f.r * 1.3 * (1 - u) + 13 * u;  // a flash where it was taken, then it shrinks on its way up
      K.light(c, x, y, f.gold ? '#ffd23f' : f.p.s.color, r * 1.8, 0.5);
      c.lineWidth = Math.max(3, r * 0.22); c.strokeStyle = f.p.s.color; c.beginPath(); c.arc(x, y, r, 0, 6.29); c.stroke();
      c.lineWidth = Math.max(1.5, r * 0.1); c.strokeStyle = f.gold ? '#ffd23f' : '#fff'; c.beginPath(); c.arc(x, y, r, 0, 6.29); c.stroke();
    }
    K.slots.forEach((s, i) => {                                               // the score that just went up glows
      if (!(flash[i] > 0)) return;
      const h = hudAt(i), n = K.slots.length, cw = Math.min(290, (W - 60) / n - 14);
      c.globalAlpha = flash[i] / 0.45; c.strokeStyle = s.color; c.lineWidth = 3; K.rr(c, h[0] + 40 - cw, 14, cw, 54, 14); c.stroke();
      K.light(c, h[0], h[1], s.color, 60, 0.5); c.globalAlpha = 1;
    });
  }

  function hurt(p) {                                                          // a storm or a rocket: the shield takes it, else a spin and -2
    const a = proj(p.x, p.y, Z);
    if (p.shield > 0) { p.shield = 0; K.sfx.bounce(); if (a) K.burst(a[0], a[1], '#7dd3fc', 16, 320, 0.5, 6); return; }
    p.stun = 1.2; score(p, -2, '#f87171'); K.sfx.boom(); K.shake(8); if (a) K.burst(a[0], a[1], '#cbd5e1', 14, 300, 0.5, 6);
  }

  // ---- the boxes' things
  const ITEMS = ['ball', 'rocket', 'shield', 'magnet', 'storm'];
  function pickItem(p) {                                                      // Mario Kart's rule: the further behind, the stronger the odds
    const solo = ps.length < 2, rank = ps.filter((q) => q.s.score > p.s.score).length / Math.max(1, ps.length - 1);   // 0 leading .. 1 last
    const w = [3 - rank * 1.5, solo ? 0 : 0.5 + rank * 3, 2.5 - rank, 1 + rank * 1.5, solo ? 0 : 0.3 + rank * 2.2];
    let r = Math.random() * w.reduce((a, b) => a + b, 0);
    for (let i = 0; i < w.length; i++) { r -= w[i]; if (r < 0) return ITEMS[i]; }
    return 'ball';
  }
  function use(p) {
    const it = p.item;
    if (it === 'ball') {                                                       // three shots straight ahead, the way the plane drifts
      shots.push({ kind: 'ball', p, x: p.x, y: p.y, z: Z + 1, vx: p.vx * 0.6, vy: p.vy * 0.6 }); K.sfx.shoot();
      if (--p.n <= 0) p.item = null; return;
    }
    p.item = null;
    if (it === 'rocket') {                                                     // at the leader (the next one when it is you): out ahead, then down on them
      const t = ps.filter((q) => q !== p).sort((a, b) => b.s.score - a.s.score)[0]; if (!t) return;
      shots.push({ kind: 'rocket', p, t, x0: p.x, y0: p.y, x: p.x, y: p.y, z: Z, u: 0, tr: [] }); K.sfx.shoot();
    } else if (it === 'shield') { p.shield = 10; K.sfx.ok(); }
    else if (it === 'magnet') { p.mag = 7; K.sfx.ok(); }
    else {                                                                     // a storm right in front of everyone else: a second to swerve or roll
      for (const q of ps) if (q !== p) objs.push({ kind: 'cloud', z: Z + 34, x: q.x, y: Math.max(CLO, q.y), got: {} });   // storms stay in the sky: low down is a way out
      K.sfx.boom();
    }
  }
  function moveShots(dt) {
    for (const s of shots) {
      if (s.kind === 'ball') {
        const z0 = s.z; s.z += (speed + 70) * dt; s.x += s.vx * dt; s.y += s.vy * dt;
        for (const o of objs) {
          if (o.dead || o.z <= z0 || o.z > s.z || s.done) continue;
          const d = Math.hypot(o.x - s.x, o.y - s.y), a = proj(o.x, o.y, o.z);
          if (o.kind === 'cloud' && d < CR + 0.5) { o.dead = true; s.done = true; score(s.p, 1, '#fff', o); K.sfx.hit(); if (a) K.burst(a[0], a[1], '#cbd5e1', 12, 220, 0.4, 5); }
          else if (isRing(o) && !o.got[s.p.i] && d < (o.kind === 'gold' ? GR : RR)) {
            o.got[s.p.i] = true; score(s.p, o.kind === 'gold' ? 3 : 1, o.kind === 'gold' ? '#ffd23f' : '#fff', o); K.sfx.score();
          }
        }
        if (s.z > Z + 110) s.done = true;
      } else {
        const t = s.t, u = Math.min(1, s.u += dt / 1.1), m = 1 - u;            // a curve: from the shooter, up and out ahead, onto where the target is now
        s.x = m * m * s.x0 + 2 * m * u * (s.x0 + t.x) / 2 + u * u * t.x;
        s.y = m * m * s.y0 + 2 * m * u * (Math.max(s.y0, t.y) + 4) + u * u * t.y; s.z = Z + 2 * m * u * 16;
        s.tr.push([s.x, s.y, s.z]); if (s.tr.length > 9) s.tr.shift();
        if (u >= 1) {
          s.done = true;
          if (t.roll > 0 || t.stun > 0) { const a = proj(t.x, t.y + 1.2, Z); if (a) pops.push({ x: a[0], y: a[1], t: 0, txt: K.tr({ vi: 'Né!', en: 'Dodged!' }), color: '#7dd3fc' }); }
          else hurt(t);
        }
      }
    }
    shots = shots.filter((s) => !s.done);
  }

  function update(dt) {
    K.updateParts(dt);
    if (over) return;
    for (const pp of pops) pp.t += dt; pops = pops.filter((pp) => pp.t < 1);
    for (const f of flyers) if ((f.t += dt / 0.65) >= 1) land(f); flyers = flyers.filter((f) => f.t < 1);
    for (let i = 0; i < flash.length; i++) if (flash[i] > 0) flash[i] -= dt;
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
      nextBox -= dt; if (nextBox <= 0) { spawn('box'); nextBox = K.rnd(3.5, 4.5); }
    }
    for (const sc of scenery) if (sc.z < Z - BACK + 1) { sc.z += FAR + BACK; if (sc.kind === 'puff') sc.x = K.rnd(-40, 40); else place(sc); }

    for (const p of ps) {
      p.cool = Math.max(0, p.cool - dt);
      let inp = input(p, dt);
      if (p.wantRoll) { p.wantRoll = false; if (p.cool <= 0 && !p.stun && phase === 'play') { p.roll = 0.6; p.cool = 2.4; K.sfx.dash(); } }
      if (p.wantUse) { p.wantUse = false; if (p.item && !p.stun && phase === 'play') use(p); }
      if (p.spin > 0 && (p.spin -= dt) <= 0) { p.item = p.next; p.n = p.item === 'ball' ? 3 : 1; p.useT = K.rnd(0.6, 2.5); K.sfx.ok(); }
      p.shield = Math.max(0, p.shield - dt);
      if (p.mag > 0) {                                                         // the magnet: rings close ahead drift onto the plane (away from the others)
        p.mag -= dt; const k2 = Math.min(1, dt * 4);
        for (const o of objs) if (isRing(o) && !o.got[p.i] && o.z > Z && o.z < Z + 24 && Math.hypot(o.x - p.x, o.y - p.y) < 5) { o.x += (p.x - o.x) * k2; o.y += (p.y - o.y) * k2; }
      }
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
    moveShots(dt);
    const xs = ps.map((p) => p.x), want = K.clamp(xs.reduce((t, x) => t + x, 0) / xs.length * 0.35, Math.max.apply(null, xs) - 8.4, Math.min.apply(null, xs) + 8.4);
    camX += (want - camX) * Math.min(1, dt * 2);                             // leans with the pack, never so far a plane at the other edge leaves the screen

    for (const o of objs) {                                                   // each plane meets each thing once, as it crosses the planes' z
      if (o.passed || o.dead || o.z > Z) continue;
      o.passed = true;
      for (const p of ps) {
        const d = Math.hypot(p.x - o.x, p.y - o.y);
        if (o.kind === 'cloud') {
          if (d < CR + 0.4 && !p.roll && !p.stun) hurt(p);
        } else if (o.kind === 'box') {
          if (d < 1.4 && !o.got[p.i]) {
            o.got[p.i] = true; const a = proj(o.x, o.y, Z); if (a) K.burst(a[0], a[1], 'hsl(' + Math.floor(Math.random() * 360) + ',85%,60%)', 10, 240, 0.4, 5);
            if (!p.item && !p.spin) { p.spin = 0.9; p.next = pickItem(p); K.sfx.pickup(); }
          }
        } else if (d < (o.kind === 'gold' ? GR : RR)) {
          o.got[p.i] = true; const v = o.kind === 'gold' ? 3 : 1; score(p, v, '#fff', o);
          if (v > 1) K.sfx.goal(); else K.sfx.score();
          const a = proj(o.x, o.y, Z); if (a) K.burst(a[0], a[1], o.kind === 'gold' ? '#ffd23f' : p.s.color, 10, 260, 0.45, 5);
        }
      }
    }
    objs = objs.filter((o) => o.z > Z - 4 && !o.dead);
    if (phase === 'play' && left <= 0) {
      flyers.forEach(land); flyers = [];                                       // points still in the air count
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
    } else if (o.kind === 'box') {                                            // a turning cube in shifting colours, faces drawn far to near
      if (Object.keys(o.got).length >= ps.length) c.globalAlpha = f * 0.3;
      const an = K.t * 1.6 + o.x, ca = Math.cos(an), sa = Math.sin(an), hue = Math.floor(K.t * 60 + o.x * 30) % 360, y = o.y + 0.2 * Math.sin(K.t * 3 + o.x);
      const g = proj(o.x, 0, o.z);                                             // its shadow on the ground
      if (g) { c.fillStyle = 'rgba(0,0,0,.2)'; c.beginPath(); c.ellipse(g[0], g[1], BOX * 1.3 * g[2], BOX * 0.3 * g[2], 0, 0, 6.29); c.fill(); }
      const P = [];
      for (let k = 0; k < 8; k++) { const u = (k & 1 ? 1 : -1) * BOX, v = k & 2 ? 1 : -1, w = (k & 4 ? 1 : -1) * BOX; P.push(proj(o.x + u * ca - w * sa, y + v * BOX, o.z + u * sa + w * ca)); }
      const NRM = [[sa, 0, -ca], [-sa, 0, ca], [-ca, 0, -sa], [ca, 0, sa], [0, -1, 0], [0, 1, 0]];   // each face's outward normal, turned with the cube
      if (P.some((q) => !q)) { c.globalAlpha = 1; return; }
      const faces = BOXF.map((fc, n) => ({ fc, n, d: (P[fc[0]][3] + P[fc[1]][3] + P[fc[2]][3] + P[fc[3]][3]) / 4 })).sort((u, v) => v.d - u.d);
      c.lineJoin = 'round'; c.lineWidth = Math.max(1.2, 0.06 * s); c.strokeStyle = 'rgba(255,255,255,.9)';
      for (const fa of faces) {
        const q = fa.fc, nn = NRM[fa.n], lit = Math.max(0, -0.45 * nn[0] + 0.75 * nn[1] - 0.5 * nn[2]);   // the sun up, to the left, behind the camera
        c.fillStyle = 'hsl(' + hue + ',80%,' + Math.round(36 + 34 * lit) + '%)';
        c.beginPath(); c.moveTo(P[q[0]][0], P[q[0]][1]); for (let k = 1; k < 4; k++) c.lineTo(P[q[k]][0], P[q[k]][1]); c.closePath(); c.fill(); c.stroke();
      }
      const n = faces[5].fc, cx = (P[n[0]][0] + P[n[2]][0]) / 2, cy = (P[n[0]][1] + P[n[2]][1]) / 2;   // the "?" on the nearest face
      if (s > 9 && faces[5].n !== 4) K.text(c, '?', cx, cy + 1, BOX * 1.5 * s, '#fff', 'center', 900);
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
    const m = proj(p.x, p.y, Z); if (!m) return;
    if (p.shield > 0 && (p.shield > 2 || Math.floor(K.t * 8) % 2)) {         // a bubble, blinking as it runs out
      c.fillStyle = 'rgba(125,211,252,.16)'; c.strokeStyle = 'rgba(186,230,253,.85)'; c.lineWidth = 3;
      c.beginPath(); c.arc(m[0], m[1], 1.45 * m[2], 0, 6.29); c.fill(); c.stroke();
    }
    if (p.mag > 0) {                                                           // the magnet: rings of pull spreading out
      c.strokeStyle = '#f87171'; c.lineWidth = 2;
      for (let k = 0; k < 2; k++) { const u = (K.t * 1.5 + k / 2) % 1; c.globalAlpha = 0.45 * (1 - u); c.beginPath(); c.arc(m[0], m[1], (1.2 + u * 1.1) * m[2], 0, 6.29); c.stroke(); }
      c.globalAlpha = 1;
    }
  }
  function drawShot(c, s) {
    const a = proj(s.x, s.y, s.z); if (!a) return;
    c.globalAlpha = fog(a[3]);
    if (s.kind === 'ball') icon(c, 'ball', a[0], a[1], Math.max(4, 0.5 * a[2]));
    else {
      c.fillStyle = '#fb923c';
      s.tr.forEach((q, i) => { const b = proj(q[0], q[1], q[2]); if (b) { c.globalAlpha = 0.5 * i / s.tr.length; c.beginPath(); c.arc(b[0], b[1], 0.4 * b[2] * i / s.tr.length, 0, 6.29); c.fill(); } });
      const b = s.tr.length > 1 ? proj(s.tr[0][0], s.tr[0][1], s.tr[0][2]) : null;
      c.globalAlpha = 1; icon(c, 'rocket', a[0], a[1], Math.max(12, 0.95 * a[2]), b ? Math.atan2(a[1] - b[1], a[0] - b[0]) + 1.5708 : 0);
    }
    c.globalAlpha = 1;
  }

  function icon(c, kind, x, y, r, ang) {                                     // the boxes' five things, drawn (not font glyphs: they differ per TV)
    c.save(); c.translate(x, y); c.lineJoin = 'round'; c.lineCap = 'round';
    if (kind === 'ball') {
      c.fillStyle = '#f8fafc'; c.beginPath(); c.arc(0, 0, r * 0.8, 0, 6.29); c.fill();
      c.strokeStyle = '#94a3b8'; c.lineWidth = Math.max(1, r * 0.1); c.beginPath();
      c.moveTo(-r * 0.5, -r * 0.2); c.lineTo(-r * 0.1, r * 0.1); c.lineTo(r * 0.2, -r * 0.35); c.moveTo(-r * 0.1, r * 0.1); c.lineTo(r * 0.05, r * 0.55); c.moveTo(r * 0.2, -r * 0.35); c.lineTo(r * 0.55, r * 0.1); c.stroke();
    } else if (kind === 'rocket') {
      c.rotate(ang == null ? 0.785 : ang);
      c.fillStyle = '#fb923c'; c.beginPath(); c.moveTo(-r * 0.22, r * 0.5); c.lineTo(0, r * 0.95); c.lineTo(r * 0.22, r * 0.5); c.fill();
      c.fillStyle = '#b91c1c'; c.beginPath(); c.moveTo(-r * 0.28, r * 0.1); c.lineTo(-r * 0.55, r * 0.6); c.lineTo(r * 0.55, r * 0.6); c.lineTo(r * 0.28, r * 0.1); c.fill();
      c.fillStyle = '#ef4444'; c.beginPath(); c.moveTo(0, -r * 0.9); c.quadraticCurveTo(r * 0.42, -r * 0.4, r * 0.28, r * 0.55); c.lineTo(-r * 0.28, r * 0.55); c.quadraticCurveTo(-r * 0.42, -r * 0.4, 0, -r * 0.9); c.fill();
      c.fillStyle = '#e0f2fe'; c.beginPath(); c.arc(0, -r * 0.2, r * 0.16, 0, 6.29); c.fill();
    } else if (kind === 'shield') {
      c.fillStyle = 'rgba(125,211,252,.5)'; c.strokeStyle = '#7dd3fc'; c.lineWidth = r * 0.12; c.beginPath(); c.arc(0, 0, r * 0.78, 0, 6.29); c.fill(); c.stroke();
      c.fillStyle = 'rgba(255,255,255,.9)'; c.beginPath(); c.ellipse(-r * 0.3, -r * 0.35, r * 0.22, r * 0.12, -0.6, 0, 6.29); c.fill();
    } else if (kind === 'magnet') {
      c.lineWidth = r * 0.36; c.strokeStyle = '#ef4444'; c.beginPath(); c.moveTo(-r * 0.5, -r * 0.35); c.lineTo(-r * 0.5, 0); c.arc(0, 0, r * 0.5, Math.PI, 0, true); c.lineTo(r * 0.5, -r * 0.35); c.stroke();
      c.lineCap = 'butt'; c.strokeStyle = '#e5e7eb'; c.beginPath(); c.moveTo(-r * 0.5, -r * 0.35); c.lineTo(-r * 0.5, -r * 0.75); c.moveTo(r * 0.5, -r * 0.35); c.lineTo(r * 0.5, -r * 0.75); c.stroke();
    } else {
      c.fillStyle = '#4b5568'; blob(c, [0, -r * 0.15, r * 0.45, -r * 0.4, 0, r * 0.32, r * 0.4, 0, r * 0.35]);
      c.strokeStyle = '#ffd23f'; c.lineWidth = Math.max(1.5, r * 0.13); c.beginPath(); c.moveTo(r * 0.05, r * 0.25); c.lineTo(-r * 0.15, r * 0.6); c.lineTo(r * 0.1, r * 0.55); c.lineTo(-r * 0.05, r * 0.9); c.stroke();
    }
    c.restore();
  }
  function badge(c, p, x, y) {                                                // what a plane holds, over it; the box's roulette while it spins
    const sp = p.spin > 0, kind = sp ? ITEMS[Math.floor(K.t * 12) % ITEMS.length] : p.item;
    c.fillStyle = 'rgba(8,12,28,.72)'; K.rr(c, x - 25, y - 25, 50, 50, 13); c.fill();
    c.strokeStyle = p.s.color; c.lineWidth = 3; K.rr(c, x - 25, y - 25, 50, 50, 13); c.stroke();
    c.globalAlpha = sp ? 0.75 : 1; icon(c, kind, x, y, 19); c.globalAlpha = 1;
    if (!sp && p.item === 'ball' && p.n > 1) K.text(c, '×' + p.n, x + 22, y + 20, 20, '#fff', 'center', 900);
  }

  function draw(c) {
    c.save(); K.shakeApply(c);
    backdrop(c);
    for (const sc of scenery) if (sc.kind === 'puff') drawPuff(c, sc);
    const z0 = Math.floor((Z - BACK) / STEP) * STEP, all = [];               // one far-to-near order for ground bands (by their far edge), things and the planes
    for (let z = z0 + FAR; z >= z0; z -= STEP) all.push({ kind: 'band', z, k: z + STEP });
    for (const t of scenery) if (t.kind !== 'puff') all.push({ o: t, k: t.z });
    for (const o of objs) all.push({ o, k: o.z });
    for (const s of shots) all.push({ s, k: s.z });
    all.push({ kind: 'planes', k: Z - 0.01 });
    all.sort((a, b) => b.k - a.k);
    for (const it of all) {
      if (it.kind === 'band') drawBand(c, it.z);
      else if (it.kind === 'planes') ps.slice().sort((a, b) => b.y - a.y).forEach((p) => drawPlane(c, p));
      else if (it.s) drawShot(c, it.s);
      else if (it.o.got) drawObj(c, it.o); else drawTree(c, it.o);
    }
    K.drawParts(c);
    for (const pp of pops) { c.globalAlpha = 1 - pp.t; K.text(c, pp.txt, pp.x, pp.y - pp.t * 60, 40, pp.color, 'center', 900); } c.globalAlpha = 1;
    c.restore();
    if (phase === 'play') {
      for (const p of ps) if (p.item || p.spin > 0) { const a = proj(p.x, p.y + 1.3, Z); if (a) badge(c, p, a[0], Math.max(160, a[1] - 34)); }
      for (const s of shots) if (s.kind === 'rocket' && Math.floor(K.t * 6) % 2) {   // the target sees it coming
        const a = proj(s.t.x, s.t.y, Z); if (a) { c.fillStyle = '#ef4444'; c.beginPath(); c.arc(a[0], a[1] + 1.1 * a[2], 16, 0, 6.29); c.fill(); K.text(c, '!', a[0], a[1] + 1.1 * a[2] + 1, 24, '#fff', 'center', 900); }
      }
    }
    K.hud(c, K.slots);
    drawFlyers(c);
    if (phase === 'ready') {
      ps.forEach((p) => { const a = proj(p.x, p.y + 1.1, Z); if (a) K.nameTag(c, p.s, a[0], a[1]); });
      K.banner(c, Math.ceil(ptimer) > 2 ? K.tr({ vi: 'Cầm điện thoại thẳng', en: 'Hold your phone level' }) : String(Math.ceil(ptimer)),
        K.tr({ vi: 'Xoay như vô lăng để rẽ · ngả về sau để bay lên · A: dùng đồ · B: lộn vòng', en: 'Turn it like a wheel to steer · tilt back to climb · A: use · B: roll' }));
    } else {
      c.fillStyle = 'rgba(8,12,28,.6)'; K.rr(c, W / 2 - 60, 80, 120, 48, 14); c.fill();
      K.text(c, String(Math.max(0, Math.ceil(left))), W / 2, 105, 34, left < 10 ? '#fca5a5' : '#fff', 'center', 900);
    }
  }

  K.layout = () => {                                                         // in flight: the touchpad (a fallback, and the Tilt switch to motion), use and roll
    if (!pad || !K.G) return;
    try { pad.layout('all', K.mode === 'play' ? { pointer: true, motion: true, buttons: ['A', 'B'] } : { buttons: ['A', 'START'] }); } catch (e) { }
  };
  K.run({
    id: 'planes', title: { vi: 'Máy bay giấy', en: 'Paper Planes' },
    tag: { vi: 'Lái bằng cách xoay điện thoại — bay qua vòng, nhặt hộp quà, bắn đối thủ', en: 'Steer by turning your phone — fly through rings, grab boxes, hit the others' },
    help: { vi: ['Xoay điện thoại như vô lăng: rẽ · ngả về sau / chúi tới: lên / xuống', 'Không xoay được? Kéo trên bàn di, hoặc cần analog / D-pad', 'Hộp ?: đạn giấy, tên lửa, khiên, nam châm, gọi giông · A: dùng', 'B: lộn vòng (né mây, né tên lửa)', 'Vòng: +1 · vòng vàng: +3 · trúng mây / tên lửa: −2'],
      en: ['Turn your phone like a wheel: steer · tilt back / forward: up / down', 'No motion? Drag the touchpad, or use a stick / D-pad', '? box: paper balls, rocket, shield, magnet, storm call · A: use', 'B: barrel roll (dodges storms and rockets)', 'Ring: +1 · gold ring: +3 · storm / rocket: −2'] },
    total: [1, 4], buttons: ['A', 'B'],
    start, update, draw,
  });
})();
