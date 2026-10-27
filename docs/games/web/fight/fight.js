'use strict';
// Dojo Duel: a 2D fighting game for 2-4, everyone on one dojo floor, no scrolling. Stick / D-pad walks, up jumps, down crouches;
// A punches, B kicks, X throws a fireball (one bar of the meter; a full meter of three makes it the super), Y held blocks
// (crouch to block a low hit). Chains on a hit: A A A (jab, jab, uppercut), A A B and B B (roundhouse), down + B sweeps,
// in the air A punches down and B dives. The TV remote's OK is A, so a remote player still has the punch chain.
// Hits stop the two fighters a moment (hitstop), launch into juggles with damage scaled down per hit, knock down; two rounds win.
// CPUs fill the empty seats. Fighters are jointed figures posed from angles (POSES), eased toward the move's pose each frame.
(function () {
  const K = window.Kit, W = 1280, GROUND = 640, GRAV = 2300, WALK = 270, JUMP = 980, HP = 100, BAR = 100, WIN = 2, TIME = 60;
  const XL = 70, XR = 1210, BODY = 46;
  // one pose: [torso, head, front arm up, front arm low, back arm up, back arm low, front thigh, front shin, back thigh, back shin, hip drop]
  // in degrees from straight down, positive toward where the fighter faces (torso and head: from straight up)
  const POSES = {
    idle: [6, 0, 55, 150, 30, 140, 18, 4, -18, -6, 0],
    crouch: [18, 4, 55, 150, 30, 140, 75, 0, -65, 5, 22],
    jump: [4, 0, 70, 160, 40, 150, 70, -30, 10, -60, 0],
    jabW: [10, 0, 60, 160, 35, 145, 24, 5, -22, -7, 2],
    jab: [16, 4, 90, 90, 40, 150, 28, 6, -26, -8, 4],
    upperW: [20, 6, 30, 160, 40, 150, 26, 6, -24, -8, 12],
    upper: [-6, -8, 165, 175, 30, 120, 10, 0, -25, -10, -8],
    kickW: [-8, 0, 60, 150, 40, 150, 70, -40, -8, -2, 0],
    kick: [-22, -6, 60, 150, 40, 150, 100, 95, -6, -2, 0],
    roundW: [-20, -10, 80, 160, -20, 90, 60, -60, -6, -2, 0],
    round: [-35, -12, 70, 150, -30, 80, 118, 112, -8, -4, -4],
    sweep: [32, 10, 40, 100, 20, 90, 88, 92, -62, 20, 32],
    cjab: [22, 6, 90, 90, 30, 140, 75, 0, -65, 5, 22],
    airA: [22, 10, 70, 70, 40, 150, 60, -30, 10, -60, 0],
    airB: [-10, -6, 20, 120, -40, -20, 45, 45, -20, -100, 0],
    fireW: [-6, 0, 30, 120, 20, 120, 30, 6, -30, -8, 8],
    fire: [12, 4, 92, 92, 88, 88, 32, 6, -32, -8, 8],
    block: [2, 6, 80, 165, 70, 160, 20, 4, -20, -6, 4],
    cblock: [16, 8, 80, 165, 70, 160, 75, 0, -65, 5, 22],
    hurt: [-25, -20, -30, -10, -50, -30, 10, 0, -25, -10, 4],
    down: [0, 6, -20, -30, -40, -20, 10, 6, -8, 0, 0],                       // straight: the whole figure is turned onto the floor
    win: [2, -8, 170, 180, 35, 140, 18, 4, -18, -6, 0],
  };
  // a move: startup / active / recovery seconds, damage, push (kx, ky), hit stun; box [x0, x1, y0, y1] from the feet, forward +;
  // low = only a crouching block stops it; kd = knocks down; next = what a press during its recovery chains into, once it touched someone
  const MOVES = {
    jab: { s: 0.06, a: 0.06, r: 0.12, dmg: 5, kx: 90, ky: 0, stun: 0.3, box: [28, 92, -132, -104], next: { A: 'jab2', B: 'kick' } },
    jab2: { s: 0.05, a: 0.06, r: 0.14, dmg: 5, kx: 100, ky: 0, stun: 0.32, box: [28, 92, -132, -104], next: { A: 'upper', B: 'round' } },
    upper: { s: 0.07, a: 0.09, r: 0.3, dmg: 9, kx: 170, ky: -640, stun: 0.5, box: [6, 84, -186, -100], kd: true, heavy: true, wind: 'upperW', lunge: 260 },
    kick: { s: 0.1, a: 0.08, r: 0.2, dmg: 8, kx: 300, ky: 0, stun: 0.34, box: [30, 108, -96, -62], next: { B: 'round', A: 'jab' }, wind: 'kickW' },
    round: { s: 0.13, a: 0.1, r: 0.32, dmg: 12, kx: 580, ky: -320, stun: 0.5, box: [24, 118, -128, -80], kd: true, heavy: true, wind: 'roundW', lunge: 160 },
    sweep: { s: 0.08, a: 0.1, r: 0.32, dmg: 7, kx: 120, ky: -220, stun: 0.5, box: [24, 112, -28, 0], low: true, kd: true, crouch: true },
    cjab: { s: 0.05, a: 0.06, r: 0.12, dmg: 3, kx: 100, ky: 0, stun: 0.24, box: [26, 88, -78, -54], low: true, crouch: true, next: { A: 'cjab', B: 'sweep' } },
    airA: { s: 0.05, a: 0.14, r: 0.1, dmg: 6, kx: 160, ky: -120, stun: 0.32, box: [14, 70, -104, -50], air: true },
    airB: { s: 0.08, a: 9, r: 0.14, dmg: 9, kx: 320, ky: -160, stun: 0.4, box: [8, 74, -54, 4], air: true, dive: true },
    fire: { s: 0.2, a: 0.02, r: 0.3, cost: BAR, shot: 1, wind: 'fireW' },
    super: { s: 0.42, a: 0.02, r: 0.5, cost: 3 * BAR, shot: 2, wind: 'fireW' },
  };
  const TALK = { ko: 'K.O.', time: { vi: 'Hết giờ', en: 'Time' }, fight: { vi: 'Đánh!', en: 'Fight!' }, round: { vi: 'Ván ', en: 'Round ' },
    hit: { vi: ' đòn', en: ' hits' }, draw: { vi: 'Hòa', en: 'Draw' }, takes: { vi: 'thắng ván này', en: 'takes the round' } };
  let ps, shots, fx, words, phase, ptimer, round, over, left, slow, roundWin, bg;

  const deg = Math.PI / 180, lerp = (a, b, k) => a + (b - a) * k;
  function shade(hex, k) {                                                     // k < 0 darker, > 0 lighter
    const n = parseInt(hex.slice(1), 16), f = (v) => Math.round(k < 0 ? v * (1 + k) : v + (255 - v) * k);
    return 'rgb(' + f(n >> 16) + ',' + f((n >> 8) & 255) + ',' + f(n & 255) + ')';
  }

  function newRound() {
    round++; phase = 'ready'; ptimer = 1.8; left = TIME; slow = 0; shots = []; fx = []; words = [];
    const n = K.slots.length, gap = n === 2 ? 420 : 900 / (n - 1);
    ps = K.slots.map((s, i) => {
      const x = n === 2 ? W / 2 + (i ? 1 : -1) * gap / 2 : W / 2 - 450 + i * gap;
      return { s, i, x, y: GROUND, vx: 0, vy: 0, f: x < W / 2 ? 1 : -1, hp: HP, hpShow: HP, bar: round === 1 ? 0 : (ps && ps[i] ? ps[i].bar : 0),
        st: 'idle', mv: null, t: 0, stop: 0, hitSet: null, touched: false, shot: false, stun: 0, kd: false, ko: false, ground: true, airUsed: false,
        combo: 0, flash: 0, pose: POSES.idle.slice(), buf: null, bufT: 0, upHeld: false, walkT: 0, inv: 0,
        ai: { t: 0, q: [], cool: K.rnd(0.3, 0.8), block: 0, seen: null, jump: 0 } };
    });
  }

  // ---- input: one shape for a person and a CPU
  function humanIn(p) {
    const q = K.pad(p.s.pad), up = q.y < -0.55, o = { mx: Math.abs(q.x) > 0.3 ? K.clamp(q.x * 1.3, -1, 1) : 0, down: q.y > 0.55, up: up && !p.upHeld,
      A: !!q.hit.A, B: !!q.hit.B, X: !!q.hit.X, block: !!(q.held.Y || q.kb.Y || q.gp.Y) };
    p.upHeld = up; return o;
  }
  function nearest(p) {
    let b = null, bd = 1e9;
    for (const o of ps) if (o !== p && !o.ko) { const d = Math.abs(o.x - p.x) + Math.abs(o.y - p.y) * 0.5; if (d < bd) { bd = d; b = o; } }
    return b;
  }
  const NONE = { mx: 0, down: false, up: false, A: false, B: false, X: false, block: false };
  const PLANS = [['A', 'A', 'A'], ['A', 'A', 'B'], ['B', 'B'], ['dB'], ['A', 'B', 'B'], ['dA', 'dA', 'dB']];
  function cpuIn(p, dt) {
    const o = { mx: 0, down: false, up: false, A: false, B: false, X: false, block: false }, ai = p.ai, t = nearest(p);
    if (!t) return o;
    const dx = t.x - p.x, d = Math.abs(dx), dir = dx > 0 ? 1 : -1;
    ai.t -= dt; ai.cool -= dt; ai.block -= dt; ai.jump -= dt;
    if (ai.q.length && ai.t <= 0) {                                             // the next press of a chain, spaced like a person's
      const k = ai.q.shift(); if (k[0] === 'd') { o.down = true; o[k[1]] = true; } else o[k] = true;
      ai.t = K.rnd(0.11, 0.16); return o;
    }
    for (const q of ps) if (q !== p && q.st === 'attack' && q.mv && !q.mv.shot && Math.abs(q.x - p.x) < 170 && ai.seen !== q.t0) {   // a swing coming: block it, sometimes
      ai.seen = q.t0; if (Math.random() < 0.3 + (1 - p.hp / HP) * 0.3) { ai.block = K.rnd(0.3, 0.5); ai.low = q.mv.low && Math.random() < 0.6; }
    }
    for (const s of shots) if (s.o !== p && Math.sign(s.vx) === Math.sign(p.x - s.x) && Math.abs(s.x - p.x) < 300 && ai.seen !== s) {
      ai.seen = s; const r = Math.random(); if (r < 0.45) o.up = true; else if (r < 0.75) ai.block = 0.5;
    }
    if (ai.block > 0) { o.block = true; o.down = !!ai.low; return o; }
    if (p.bar >= 3 * BAR && d < 700 && Math.random() < 0.02) { o.X = true; return o; }
    if (d > 300 && p.bar >= BAR && Math.random() < 0.012) { o.X = true; return o; }
    if (d > 150 && d < 270 && ai.jump <= 0 && Math.random() < 0.012) { o.up = true; o.mx = dir; ai.jump = 2; ai.q = ['B']; ai.t = 0.28; return o; }
    if (!p.ground && p.vy > -200 && d < 120 && Math.random() < 0.08) { o.A = true; return o; }
    if (d > 96) o.mx = dir * (d > 220 ? 1 : 0.7);
    else if (ai.cool <= 0) { ai.q = K.pick(PLANS).slice(); ai.t = 0; ai.cool = K.rnd(0.6, 1.3); }
    else if (d < 60) o.mx = -dir * 0.6;                                        // too close to swing well: a step back
    return o;
  }

  // ---- moves
  function start(p, name) {
    const m = MOVES[name];
    if (m.cost) { if (p.bar < m.cost) return false; p.bar -= m.cost; }
    p.st = 'attack'; p.mv = m; p.mvName = name; p.t = 0; p.t0 = Math.random(); p.hitSet = []; p.touched = false; p.shot = false;
    if (m.air) p.airUsed = true;
    if (m.lunge && p.ground) p.vx = p.f * m.lunge;                             // the chain's finisher steps in, so it reaches who the jabs pushed back
    if (m.heavy || m === MOVES.kick) K.sfx.dash();
    if (m.shot === 2) { K.sfx.boom(); K.shake(6); }
    return true;
  }
  function boxOf(p, m) {
    const b = m.box, x0 = p.x + (p.f > 0 ? b[0] : -b[1]), x1 = p.x + (p.f > 0 ? b[1] : -b[0]);
    return [x0, x1, p.y + b[2], p.y + b[3]];
  }
  function hurtOf(p) {
    const low = p.st === 'crouch' || (p.st === 'block' && p.cb) || (p.st === 'attack' && p.mv.crouch);
    return [p.x - 24, p.x + 24, p.y - (low ? 96 : 160), p.y];
  }
  const meet = (a, b) => a[0] < b[1] && b[0] < a[1] && a[2] < b[3] && b[2] < a[3];
  function hit(v, a, m, x, y, fromShot) {
    if (v.inv > 0 || v.ko || v.st === 'down') return false;
    const front = Math.sign(a.x - v.x) === v.f || Math.abs(a.x - v.x) < 6 || fromShot;
    if (v.st === 'block' && v.ground && (front || fromShot) && (!m.low || v.cb)) {
      v.bstun = 0.16; v.vx = (x < v.x ? 1 : -1) * 240; if (!fromShot) a.vx = -a.f * 90;
      v.bar = Math.min(3 * BAR, v.bar + 3); if (fromShot) v.hp = Math.max(1, v.hp - 1);
      K.sfx.bounce(); K.burst(x, y, '#93c5fd', 7, 200, 0.25, 5); fx.push({ k: 'block', x, y, t: 0 }); hold(v, fromShot ? null : a, 0.025); return true;
    }
    v.combo = v.st === 'hurt' || !v.ground ? v.combo + 1 : 1;
    const dmg = m.dmg * Math.max(0.45, 1 - 0.12 * (v.combo - 1));
    v.hp = Math.max(0, v.hp - dmg); v.st = 'hurt'; v.mv = null; v.stun = m.stun; v.flash = 0.1; v.ground = v.ground && !m.ky;
    const dir = fromShot ? Math.sign(a.vx || 1) : a.f;
    v.vx = dir * m.kx; if (m.ky) v.vy = m.ky; else if (!v.ground || v.y < GROUND) v.vy = -260;   // a hit in the air keeps it up: juggles
    if (m.kd || v.hp <= 0) v.kd = true;
    const owner = fromShot ? a.o : a;
    owner.bar = Math.min(3 * BAR, owner.bar + 9); v.bar = Math.min(3 * BAR, v.bar + 5);
    const big = m.heavy || m.kd || dmg >= 9;
    hold(v, fromShot ? null : a, big ? 0.07 : 0.035); K.shake(big ? 7 : 3); K.sfx.hit(big ? 1 : 0.35);
    K.burst(x, y, '#fff', big ? 14 : 7, big ? 420 : 280, 0.3, 6); K.burst(x, y, owner.s.color, big ? 10 : 4, 300, 0.4, 5);
    fx.push({ k: 'hit', x, y, t: 0, big });
    if (v.combo >= 2) { const w = words.find((q) => q.p === owner && q.t < 1); if (w) { w.n = v.combo; w.t = 0; } else words.push({ p: owner, n: v.combo, t: 0 }); }
    if (v.hp <= 0) { v.ko = true; slow = 1; K.sfx.boom(); K.shake(14); }
    return true;
  }
  function spawnShot(p, big) {
    shots.push({ o: p, x: p.x + p.f * 60, y: p.y - 112, vx: p.f * (big ? 560 : 640), r: big ? 46 : 22, big, s: { dmg: big ? 30 : 10, kx: big ? 520 : 300, ky: big ? -420 : 0, stun: 0.45, kd: big, heavy: big }, life: 2.4, hits: [] });
    K.sfx.shoot();
  }

  function press(p, inp) { if (inp.A || inp.B || inp.X) { p.buf = inp.A ? 'A' : inp.B ? 'B' : 'X'; p.bufDown = inp.down; p.bufT = 0.2; } }
  function step(p, inp, dt) {
    p.flash -= dt; p.inv -= dt; p.bufT -= dt; if (p.bstun > 0) p.bstun -= dt;
    p.hpShow = p.hpShow > p.hp ? Math.max(p.hp, p.hpShow - dt * (p.st === 'hurt' ? 4 : 40)) : p.hp;
    press(p, inp);
    if (p.ko) { if (p.ground && p.st !== 'down') p.st = 'down'; }
    else if (p.st === 'hurt') {
      p.stun -= dt;
      if (p.ground && p.stun <= 0) { if (p.kd) { p.st = 'down'; p.t = 0; p.kd = false; K.sfx.fall(); } else { p.st = 'idle'; p.combo = 0; } }
    } else if (p.st === 'down') {
      p.t += dt; if (p.t > 0.75) { p.st = 'idle'; p.inv = 0.45; p.combo = 0; }   // up again, a moment untouchable
    } else if (p.st === 'attack') {
      const m = p.mv; p.t += dt;
      if (m.dive && p.t >= m.s && !p.ground) { p.vx = p.f * 520; p.vy = 760; }
      if (m.shot && !p.shot && p.t >= m.s) { p.shot = true; spawnShot(p, m.shot === 2); }
      if (m.box && p.t >= m.s && p.t < m.s + m.a) {
        const hb = boxOf(p, m);
        for (const v of ps) if (v !== p && p.hitSet.indexOf(v) < 0 && meet(hb, hurtOf(v))) {
          p.hitSet.push(v); if (hit(v, p, m, (Math.max(hb[0], v.x - 24) + Math.min(hb[1], v.x + 24)) / 2, (hb[2] + hb[3]) / 2, false)) p.touched = true;
        }
      }
      const end = m.dive ? (p.ground ? p.t : 1e9) : m.s + m.a + m.r;
      if (m.dive && p.ground && !p.diveEnd) { p.diveEnd = p.t; }
      const done = m.dive ? (p.diveEnd != null && p.t - p.diveEnd > m.r) : p.t >= end;
      const chain = m.next && p.touched && p.t >= m.s + m.a && p.bufT > 0 && m.next[p.buf];
      if (chain) { const nx = m.next[p.buf]; p.bufT = 0; p.diveEnd = null; start(p, nx); }
      else if (done || (m.air && p.ground && !m.dive)) { p.st = 'idle'; p.mv = null; p.diveEnd = null; }
    }
    if (p.st === 'idle' || p.st === 'walk' || p.st === 'crouch' || p.st === 'jump' || p.st === 'block') {
      const t = nearest(p);
      if (p.ground) {
        if (t && !(p.bstun > 0)) p.f = t.x >= p.x ? 1 : -1;
        p.cb = inp.down;
        if (p.bstun > 0) p.st = 'block';
        else if (p.bufT > 0) {
          const b = p.buf, low = p.bufDown || inp.down; p.bufT = 0;
          if (b === 'X') start(p, p.bar >= 3 * BAR ? 'super' : 'fire');
          else start(p, b === 'A' ? (low ? 'cjab' : 'jab') : (low ? 'sweep' : 'kick'));
        } else if (inp.block) { p.st = 'block'; p.vx *= 0.5; }
        else if (inp.up) { p.vy = -JUMP; p.vx = inp.mx * 330; p.ground = false; p.st = 'jump'; p.airUsed = false; K.sfx.bounce(); }
        else if (inp.down) p.st = 'crouch';
        else { p.vx = inp.mx * WALK; p.st = inp.mx ? 'walk' : 'idle'; }
      } else {
        p.st = 'jump';
        if (p.bufT > 0 && !p.airUsed && p.buf !== 'X') { const b = p.buf; p.bufT = 0; start(p, b === 'A' ? 'airA' : 'airB'); }
      }
    }
    // the body
    if (!p.ground) p.vy += GRAV * dt;
    p.x += p.vx * dt; p.y += p.vy * dt;
    if (p.y >= GROUND) {
      if (!p.ground) { p.ground = true; if (p.st === 'jump') p.st = 'idle'; if (p.vy > 600) K.burst(p.x, GROUND, '#d6b98c', 6, 160, 0.3, 4); }
      p.y = GROUND; p.vy = 0;
    }
    if (p.ground && p.st !== 'walk') p.vx *= Math.max(0, 1 - 9 * dt);
    if (p.x < XL) { p.x = XL; if (p.st === 'hurt') p.vx = Math.abs(p.vx) * 0.3; }
    if (p.x > XR) { p.x = XR; if (p.st === 'hurt') p.vx = -Math.abs(p.vx) * 0.3; }
    if (p.st === 'walk') p.walkT += dt * Math.abs(p.vx) / 40;
    // the pose it eases toward
    let want = POSES[p.st === 'walk' ? 'idle' : p.st === 'attack' ? (p.t < p.mv.s ? p.mv.wind || 'jabW' : p.mvName === 'jab2' ? 'jab' : p.mvName === 'super' ? 'fire' : p.mvName) : p.st === 'block' && p.cb ? 'cblock' : p.st] || POSES.idle;
    if (p.ko && p.ground) want = POSES.down;
    if (phase === 'end' && !p.ko && roundWin === p.i) want = POSES.win;
    const k = Math.min(1, dt * (p.st === 'attack' ? 34 : 16));
    for (let i = 0; i < 11; i++) p.pose[i] = lerp(p.pose[i], want[i], k);
  }

  // the blow holds the two it joins, not the screen (with four fighting, a whole-screen stop on every hit read as lag), and briefly:
  // 60 / 110 ms (3-5 frames on a 50 Hz TV) felt like the game catching up, on a phone pad most of all
  function hold(v, a, t) { v.stop = Math.max(v.stop, t); if (a) a.stop = Math.max(a.stop, t); }
  function update(dt) {
    K.updateParts(dt);
    if (over) return;
    for (const w of words) w.t += dt; words = words.filter((w) => w.t < 1.1);
    for (const e of fx) e.t += dt; fx = fx.filter((e) => e.t < 0.25);
    if (phase === 'ready') { ptimer -= dt; for (const p of ps) step(p, NONE, dt); if (ptimer <= 0) { phase = 'play'; K.sfx.go(); } return; }
    if (slow > 0) { slow -= dt; dt *= 0.3; }                                   // the last blow in slow motion
    if (phase === 'play') { left -= dt; K.timeTick(left); }
    for (const p of ps) {
      if (p.stop > 0) { p.stop -= dt; if (phase === 'play' && p.s.human && !p.ko) press(p, humanIn(p)); continue; }   // hitstop: held on the blow, a press in it still counts
      step(p, phase === 'play' && !p.ko ? (p.s.human ? humanIn(p) : cpuIn(p, dt)) : NONE, dt);
    }
    for (let i = 0; i < ps.length; i++) for (let j = i + 1; j < ps.length; j++) {   // bodies do not pass through each other on the floor
      const a = ps[i], b = ps[j];
      if (a.ko || b.ko || a.st === 'down' || b.st === 'down' || Math.abs(a.y - b.y) > 90) continue;
      const d = b.x - a.x, ov = BODY * 2 - Math.abs(d); if (ov > 0) { const s = d >= 0 ? 1 : -1; a.x -= s * ov / 2; b.x += s * ov / 2; }
    }
    for (const s of shots) {
      s.x += s.vx * dt; s.life -= dt;
      for (const v of ps) if (v !== s.o && s.hits.indexOf(v) < 0 && meet([s.x - s.r, s.x + s.r, s.y - s.r, s.y + s.r], hurtOf(v))) {
        s.hits.push(v); if (hit(v, s, s.s, s.x, s.y, true) && !s.big) s.life = 0;
      }
      for (const o of shots) if (o !== s && o.o !== s.o && o.life > 0 && Math.abs(o.x - s.x) < o.r + s.r && Math.abs(o.y - s.y) < 40) {   // two fireballs meet
        if (!o.big || s.big) o.life = 0; if (!s.big || o.big) s.life = 0; K.burst((o.x + s.x) / 2, s.y, '#fff', 12, 300, 0.35, 6); K.sfx.hit(0.6);
      }
      if (s.x < -60 || s.x > W + 60) s.life = 0;
    }
    shots = shots.filter((s) => s.life > 0);
    if (phase === 'play') {
      const up = ps.filter((p) => !p.ko);
      if (up.length <= 1 || left <= 0) {
        phase = 'end'; ptimer = up.length <= 1 ? 3 : 2.4; roundWin = -1;
        const best = up.slice().sort((a, b) => b.hp - a.hp);
        if (best.length && (best.length < 2 || best[0].hp > best[1].hp)) roundWin = best[0].i;
        if (roundWin >= 0) K.slots[roundWin].score++;
        for (const p of ps) if (!p.ko && p.i !== roundWin) p.st = 'idle';
        endWord = up.length <= 1 ? TALK.ko : K.tr(TALK.time);
      }
    } else if (phase === 'end') {
      ptimer -= dt;
      if (ptimer <= 0) {
        const top = K.slots.reduce((m, s) => (s.score > m.score ? s : m), K.slots[0]);
        if (top.score >= WIN) { over = true; K.over(top.i, K.slots.map((s) => s.name + ': ' + s.score)); } else newRound();
      }
    }
  }
  let endWord = '';

  // ---- drawing
  function drawBg() {                                                          // the dojo, drawn once
    const cv = document.createElement('canvas'); cv.width = W; cv.height = 720; const c = cv.getContext('2d');
    let g = c.createLinearGradient(0, 0, 0, 520); g.addColorStop(0, '#2a1a12'); g.addColorStop(1, '#4a2e1c'); c.fillStyle = g; c.fillRect(0, 0, W, 520);
    for (let x = 40; x < W; x += 240) {                                        // paper screens between dark posts
      c.fillStyle = '#f3e3c3'; c.fillRect(x, 150, 200, 300);
      g = c.createLinearGradient(0, 150, 0, 450); g.addColorStop(0, 'rgba(255,200,120,.35)'); g.addColorStop(1, 'rgba(120,60,20,.25)'); c.fillStyle = g; c.fillRect(x, 150, 200, 300);
      c.strokeStyle = '#5a3a22'; c.lineWidth = 4;
      for (let i = 1; i < 4; i++) { c.beginPath(); c.moveTo(x + i * 50, 150); c.lineTo(x + i * 50, 450); c.stroke(); }
      for (let j = 1; j < 6; j++) { c.beginPath(); c.moveTo(x, 150 + j * 50); c.lineTo(x + 200, 150 + j * 50); c.stroke(); }
      c.strokeStyle = '#3a2214'; c.lineWidth = 10; c.strokeRect(x, 150, 200, 300);
    }
    c.fillStyle = '#24140c'; for (let x = 0; x <= W; x += 240) c.fillRect(x + 22, 110, 26, 410);   // posts
    c.fillStyle = '#1c0f08'; c.fillRect(0, 96, W, 30); c.fillRect(0, 500, W, 24);                   // beams
    c.fillStyle = '#f1e2c4'; c.fillRect(W / 2 - 70, 170, 140, 230);           // the hanging banner with a red sun
    c.fillStyle = '#7a1d1d'; c.fillRect(W / 2 - 70, 170, 140, 12); c.fillRect(W / 2 - 70, 388, 140, 12);
    c.fillStyle = '#b91c1c'; c.beginPath(); c.arc(W / 2, 285, 46, 0, 6.283); c.fill();
    g = c.createLinearGradient(0, 520, 0, 720); g.addColorStop(0, '#8a5a32'); g.addColorStop(1, '#5e3a1e'); c.fillStyle = g; c.fillRect(0, 520, W, 200);
    c.strokeStyle = 'rgba(40,20,8,.45)'; c.lineWidth = 2;                      // floor boards, toward a point far behind the wall
    for (let i = -12; i <= 12; i++) { c.beginPath(); c.moveTo(W / 2 + i * 40, 520); c.lineTo(W / 2 + i * 150, 720); c.stroke(); }
    for (const y of [548, 590, 650]) { c.beginPath(); c.moveTo(0, y); c.lineTo(W, y); c.stroke(); }
    c.fillStyle = 'rgba(255,240,210,.08)'; c.beginPath(); c.ellipse(W / 2, 640, 520, 60, 0, 0, 6.283); c.fill();
    for (const x of [160, W - 160]) {                                          // lanterns
      g = c.createRadialGradient(x, 300, 10, x, 300, 180); g.addColorStop(0, 'rgba(255,190,90,.45)'); g.addColorStop(1, 'rgba(255,190,90,0)'); c.fillStyle = g; c.fillRect(x - 180, 120, 360, 360);
      c.fillStyle = '#e8562a'; c.beginPath(); c.ellipse(x, 300, 30, 40, 0, 0, 6.283); c.fill();
      c.fillStyle = '#2a1408'; c.fillRect(x - 18, 256, 36, 8); c.fillRect(x - 18, 336, 36, 8); c.fillRect(x - 2, 126, 4, 130);
    }
    return cv;
  }
  const OL = '#16100c';
  function limb(c, ax, ay, bx, by, w, col) {
    c.lineCap = 'round'; c.strokeStyle = OL; c.lineWidth = w + 6; c.beginPath(); c.moveTo(ax, ay); c.lineTo(bx, by); c.stroke();
    c.strokeStyle = col; c.lineWidth = w; c.beginPath(); c.moveTo(ax, ay); c.lineTo(bx, by); c.stroke();
  }
  function drawFighter(c, p) {
    const P = p.pose, f = p.f, white = p.flash > 0, col = white ? '#fff' : p.s.color, dark = white ? '#e5e7eb' : shade(p.s.color, -0.38);
    const blink = p.inv > 0 && ((K.t * 20) | 0) % 2;
    c.fillStyle = 'rgba(0,0,0,.32)'; c.beginPath(); c.ellipse(p.x, GROUND + 4, 46 * (1 - Math.min(0.5, (GROUND - p.y) / 500)), 10, 0, 0, 6.283); c.fill();
    if (blink) c.globalAlpha = 0.5;
    const lying = p.st === 'down' || (p.ko && p.ground);
    c.save(); c.translate(p.x, p.y);
    if (lying) { c.translate(f * 70, -14); c.rotate(-f * 86 * deg); }          // on its back along the floor, head away from the hitter
    else if (p.st === 'hurt' && !p.ground) c.rotate(-f * Math.max(-40, Math.min(60, -p.vy / 14)) * deg);
    const hip = [0, -66 + P[10]], dir = (a, l) => [Math.sin(a * deg) * f * l, Math.cos(a * deg) * l];
    const up = (a, l) => [Math.sin(a * deg) * f * l, -Math.cos(a * deg) * l];
    const add = (p0, d) => [p0[0] + d[0], p0[1] + d[1]];
    const chest = add(hip, up(P[0], 52)), sh = add(hip, up(P[0], 46)), head = add(chest, up(P[0] + P[1], 22));
    const leg = (a1, a2, x0) => { const h = add(hip, [x0 * f, 0]), k = add(h, dir(a1, 34)); return [h, k, add(k, dir(a2, 34))]; };
    const arm = (a1, a2) => { const e = add(sh, dir(a1, 28)); return [sh, e, add(e, dir(a2, 26))]; };
    const bl = leg(P[8], P[9], -6), fl = leg(P[6], P[7], 6), ba = arm(P[4], P[5]), fa = arm(P[2], P[3]);
    limb(c, bl[0][0], bl[0][1], bl[1][0], bl[1][1], 15, dark); limb(c, bl[1][0], bl[1][1], bl[2][0], bl[2][1], 13, dark);
    limb(c, ba[0][0], ba[0][1], ba[1][0], ba[1][1], 12, dark); limb(c, ba[1][0], ba[1][1], ba[2][0], ba[2][1], 11, dark);
    c.fillStyle = OL; c.beginPath(); c.arc(ba[2][0], ba[2][1], 10, 0, 6.283); c.fill(); c.fillStyle = white ? '#fff' : '#e8e0d0'; c.beginPath(); c.arc(ba[2][0], ba[2][1], 7, 0, 6.283); c.fill();
    limb(c, hip[0], hip[1], chest[0], chest[1], 30, col);                      // the torso and its belt
    const bx = hip[0] + (chest[0] - hip[0]) * 0.18, by = hip[1] + (chest[1] - hip[1]) * 0.18;
    c.strokeStyle = white ? '#fff' : '#1f2937'; c.lineWidth = 8; c.beginPath(); c.moveTo(bx - 15, by + 1); c.lineTo(bx + 15, by - 1); c.stroke();
    limb(c, fl[0][0], fl[0][1], fl[1][0], fl[1][1], 16, col); limb(c, fl[1][0], fl[1][1], fl[2][0], fl[2][1], 14, col);
    c.fillStyle = OL; c.beginPath(); c.arc(head[0], head[1], 21, 0, 6.283); c.fill();   // the head: a mask in the seat colour, a white band
    c.fillStyle = col; c.beginPath(); c.arc(head[0], head[1], 18, 0, 6.283); c.fill();
    const tilt = (P[0] + P[1]) * deg * f;
    c.save(); c.translate(head[0], head[1]); c.rotate(tilt);
    c.fillStyle = white ? '#fff' : '#f8fafc'; c.fillRect(-18, -9, 36, 7);
    const wave = Math.sin(K.t * 9 + p.i) * 4;
    c.strokeStyle = '#f8fafc'; c.lineWidth = 4; c.beginPath(); c.moveTo(-f * 16, -6); c.quadraticCurveTo(-f * 30, -10 + wave, -f * 40, -2 - wave); c.stroke();
    c.beginPath(); c.moveTo(-f * 16, -5); c.quadraticCurveTo(-f * 28, 0 + wave, -f * 36, 6); c.stroke();
    c.fillStyle = '#0f172a'; c.fillRect(f > 0 ? 3 : -15, 2, 12, 5);           // the eye slit
    c.fillStyle = '#fff'; c.fillRect(f > 0 ? 9 : -12, 3, 4, 3);
    c.restore();
    limb(c, fa[0][0], fa[0][1], fa[1][0], fa[1][1], 13, col); limb(c, fa[1][0], fa[1][1], fa[2][0], fa[2][1], 12, col);
    c.fillStyle = OL; c.beginPath(); c.arc(fa[2][0], fa[2][1], 11, 0, 6.283); c.fill(); c.fillStyle = white ? '#fff' : '#f1ece2'; c.beginPath(); c.arc(fa[2][0], fa[2][1], 8, 0, 6.283); c.fill();
    if (p.st === 'attack' && p.mv && p.mv.box && p.t >= p.mv.s && p.t < p.mv.s + Math.min(p.mv.a, 0.12)) {   // a swish behind the striking hand or foot
      const tip = p.mvName.indexOf('A') > 0 || /jab|upper/.test(p.mvName) ? fa[2] : fl[2];
      c.globalAlpha = 0.5; c.strokeStyle = '#fff'; c.lineWidth = 5; c.beginPath(); c.arc(tip[0] - f * 18, tip[1], 22, f > 0 ? -0.9 : 2.2, f > 0 ? 0.9 : 4.0); c.stroke(); c.globalAlpha = blink ? 0.5 : 1;
    }
    if (p.st === 'block') { c.globalAlpha = 0.35; c.strokeStyle = '#93c5fd'; c.lineWidth = 5; c.beginPath(); c.arc(f * 34, p.cb ? -50 : -100, p.cb ? 44 : 62, -1.2, 1.2); if (f < 0) { c.beginPath(); c.arc(f * 34, p.cb ? -50 : -100, p.cb ? 44 : 62, Math.PI - 1.2, Math.PI + 1.2); } c.stroke(); }
    c.restore(); c.globalAlpha = 1;
    if (p.st === 'attack' && p.mv && p.mv.shot && p.t < p.mv.s) K.light(c, p.x + f * 60, p.y - 112, p.mv.shot === 2 ? '#fde047' : p.s.color, 40 + p.t * 220, 0.8);   // charging
  }
  function drawShot(c, s) {
    const col = s.big ? '#fde047' : s.o.s.color;
    for (let i = 1; i <= 4; i++) { c.globalAlpha = 0.18 * (5 - i) / 4; c.fillStyle = col; c.beginPath(); c.arc(s.x - Math.sign(s.vx) * i * s.r * 0.6, s.y, s.r * (1 - i * 0.12), 0, 6.283); c.fill(); }
    c.globalAlpha = 1; K.light(c, s.x, s.y, col, s.r * 3, 0.9);
    c.fillStyle = col; c.beginPath(); c.arc(s.x, s.y, s.r, 0, 6.283); c.fill();
    c.fillStyle = '#fff'; c.beginPath(); c.arc(s.x + Math.sin(K.t * 40) * 2, s.y, s.r * 0.55, 0, 6.283); c.fill();
  }
  function hudAt(i) {
    const n = K.slots.length, cw = Math.min(290, (W - 60) / n - 14), gx = W / 2 - (n * cw + (n - 1) * 14) / 2;
    return [gx + i * (cw + 14), cw];
  }
  function drawBars(c) {                                                       // health under each name, the meter's three bars under that
    for (const p of ps) {
      const h = hudAt(p.i), x = h[0], w = h[1], y = 74;
      c.fillStyle = 'rgba(8,12,28,.8)'; K.rr(c, x, y, w, 18, 6); c.fill();
      c.fillStyle = '#fef3c7'; K.rr(c, x + 3, y + 3, (w - 6) * p.hpShow / HP, 12, 4); c.fill();
      const lowHp = p.hp < 30;
      c.fillStyle = lowHp && ((K.t * 4) | 0) % 2 ? '#f87171' : p.s.color; if (p.hp > 0) { K.rr(c, x + 3, y + 3, (w - 6) * p.hp / HP, 12, 4); c.fill(); }
      const sw = (w - 12) / 3;
      for (let k = 0; k < 3; k++) {
        const fill = K.clamp((p.bar - k * BAR) / BAR, 0, 1);
        c.fillStyle = 'rgba(8,12,28,.8)'; K.rr(c, x + k * (sw + 6), y + 22, sw, 8, 3); c.fill();
        if (fill > 0) { c.fillStyle = fill >= 1 ? (p.bar >= 3 * BAR && ((K.t * 6) | 0) % 2 ? '#fff' : '#fbbf24') : '#a16207'; K.rr(c, x + k * (sw + 6), y + 22, sw * fill, 8, 3); c.fill(); }
      }
    }
  }
  function draw(c) {
    if (!bg) bg = drawBg();
    c.save(); K.shakeApply(c);
    c.drawImage(bg, 0, 0);
    for (const s of shots) drawShot(c, s);
    const order = ps.slice().sort((a, b) => (a.ko ? -1 : 0) - (b.ko ? -1 : 0) || (a.st === 'attack' ? 1 : 0) - (b.st === 'attack' ? 1 : 0));
    for (const p of order) drawFighter(c, p);
    for (const e of fx) {                                                      // the impact star
      const u = e.t / 0.25, r = (e.big ? 50 : 30) * (0.6 + u), n = e.k === 'block' ? 0 : 8;
      c.globalAlpha = 1 - u; c.strokeStyle = e.k === 'block' ? '#93c5fd' : '#fff'; c.lineWidth = e.big ? 6 : 4;
      if (n) { c.beginPath(); for (let i = 0; i < n; i++) { const a = i / n * 6.283 + e.x; c.moveTo(e.x + Math.cos(a) * r * 0.4, e.y + Math.sin(a) * r * 0.4); c.lineTo(e.x + Math.cos(a) * r, e.y + Math.sin(a) * r); } c.stroke(); }
      else { c.beginPath(); c.arc(e.x, e.y, r, 0, 6.283); c.stroke(); }
      c.globalAlpha = 1;
    }
    K.drawParts(c);
    c.restore();
    for (const w of words) {                                                   // "3 hits" over the one dealing them
      const a = w.t < 0.8 ? 1 : 1 - (w.t - 0.8) / 0.3, s = 1 + Math.max(0, 0.25 - w.t) * 2;
      c.globalAlpha = Math.max(0, a); K.text(c, w.n + K.tr(TALK.hit), w.p.x, w.p.y - 230 - w.t * 20, 40 * s, w.p.s.color, 'center', 900); c.globalAlpha = 1;
    }
    K.hud(c, K.slots);
    drawBars(c);
    c.fillStyle = 'rgba(8,12,28,.8)'; c.beginPath(); c.arc(W / 2, 140, 30, 0, 6.283); c.fill();
    K.text(c, String(Math.max(0, Math.ceil(left))), W / 2, 141, 30, left < 10 ? '#fca5a5' : '#fff', 'center', 900);
    if (phase === 'ready') {
      K.banner(c, ptimer > 0.6 ? K.tr(TALK.round) + round : K.tr(TALK.fight), null, '#fff');
      for (const p of ps) K.nameTag(c, p.s, p.x, p.y - 210);
    } else if (phase === 'end' && !over && ptimer < (endWord === TALK.ko ? 2.2 : 2.4)) {
      if (ptimer > 1.4) K.banner(c, endWord, null, endWord === TALK.ko ? '#f87171' : '#fff');
      else K.banner(c, roundWin >= 0 ? K.slots[roundWin].name : K.tr(TALK.draw), roundWin >= 0 ? K.tr(TALK.takes) : null, roundWin >= 0 ? K.slots[roundWin].color : '#fff');
    }
  }

  const G = {
    id: 'fight', title: { vi: 'Võ đài', en: 'Dojo Duel' },
    tag: { vi: 'Đấm, đá, chưởng, nối combo — thắng 2 ván là thắng trận', en: 'Punch, kick, fireballs, chain combos — first to 2 rounds' },
    help: {
      vi: ['Cần / D-pad: đi · lên: nhảy · xuống: ngồi', 'A đấm · B đá · X chưởng (1 vạch nộ) · giữ Y: đỡ', 'Combo: A A A · A A B · B B · xuống + B quét chân', 'Trên không: A đấm chúi · B đá bay', 'Đủ 3 vạch nộ + X: tuyệt chiêu'],
      en: ['Stick / D-pad: walk · up: jump · down: crouch', 'A punch · B kick · X fireball (1 bar) · hold Y: block', 'Combos: A A A · A A B · B B · down + B sweeps', 'In the air: A punch · B dive kick', 'Full meter + X: super'],
    },
    total: [2, 4], buttons: ['A', 'B', 'X', 'Y'],
    start() { round = 0; over = false; ps = null; newRound(); },
    update, draw,
  };
  K.run(G);
})();
