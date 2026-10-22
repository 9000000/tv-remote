'use strict';
// Bi-a (8-ball pool) physics, rules and bot: pure, loadable by node (app/games/tests/bida.test.js). Units: the table is 800 x 400, a ball's radius 10.
// Physics is a fixed step of 1/240 s on typed arrays (nothing allocated per step); friction slows a ball by a constant plus a part of its speed.
// A game `g` = BD.create(); the shooter (g.turn) places the cue ball when g.hand, then BD.shoot(g, angle, power 0..1), calls BD.step(g) until it
// returns false (or BD.run), then BD.settle(g) judges the shot: fouls (scratch, no ball hit, wrong ball first, nothing reached a rail), groups
// (solids 1-7 / stripes 9-15, given by the first ball legally potted after the break), who plays next (a foul gives the other player the cue
// ball in hand anywhere), and the 8-ball (potted on its turn without a foul = win; early, or with a foul = loss; on the break it is put back).
(function (root) {
  const W = 800, H = 400, R = 10, D2 = 4 * R * R;
  const VMAX = 1700, FA = 75, FK = 0.35, STOP = 5, HS = 1 / 240, CUSH = 0.75, BALLE = 0.95;
  const PK = new Float64Array([0, 0, 24, W / 2, -4, 21, W, 0, 24, 0, H, 24, W / 2, H + 4, 21, W, H, 24]);   // x, y, capture radius
  const HEAD = W * 0.25;                                                       // the head string: the break is from behind it
  const grp = (id) => (id === 8 ? 2 : id >= 9 ? 1 : id >= 1 ? 0 : -1);          // 0 solids, 1 stripes, 2 the eight

  function create(rng) {
    const g = {
      x: new Float64Array(16), y: new Float64Array(16), vx: new Float64Array(16), vy: new Float64Array(16), pot: new Uint8Array(16),
      turn: 0, grp: [-1, -1], open: true, hand: false, zone: 'head', breaking: true, rolling: false, winner: -1, why: '', acc: 0, shots: 0,
      shot: { first: -1, rail: false, n: 0, pots: new Int8Array(16), live: false, onEight: false },
      ev: { n: 0, k: new Uint8Array(32), s: new Float32Array(32) },             // collisions of the last frame, for the sound: kind 0 ball 1 rail 2 pocket
      rng: rng || Math.random,
    };
    rack(g); return g;
  }
  function rack(g) {
    const rnd = g.rng, fx = W * 0.72, dx = R * Math.sqrt(3) + 0.2, dy = 2 * R + 0.2, slots = [];
    for (let r = 0; r < 5; r++) for (let c = 0; c <= r; c++) slots.push({ x: fx + r * dx, y: H / 2 + (c - r / 2) * dy, r, c });
    const ids = [1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15];
    for (let i = ids.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); const t = ids[i]; ids[i] = ids[j]; ids[j] = t; }
    const at = new Array(15); let k = 0;
    for (let i = 0; i < 15; i++) at[i] = (slots[i].r === 2 && slots[i].c === 1) ? 8 : ids[k++];
    const c1 = 10, c2 = 14;                                                     // the two back corners of the triangle: one solid, one stripe
    if (grp(at[c1]) === grp(at[c2])) for (let i = 0; i < 15; i++) if (i !== c1 && i !== c2 && at[i] !== 8 && grp(at[i]) !== grp(at[c1])) { const t = at[i]; at[i] = at[c2]; at[c2] = t; break; }
    for (let i = 0; i < 15; i++) { g.x[at[i]] = slots[i].x; g.y[at[i]] = slots[i].y; }
    g.x[0] = HEAD; g.y[0] = H / 2;
    g.pot.fill(0); g.vx.fill(0); g.vy.fill(0);
  }

  const ev = (g, k, s) => { const e = g.ev; if (e.n < 32) { e.k[e.n] = k; e.s[e.n] = s; e.n++; } };

  /** One fixed step; true while any ball still moves. */
  function step(g) {
    const x = g.x, y = g.y, vx = g.vx, vy = g.vy, pot = g.pot, s = g.shot;
    for (let i = 0; i < 16; i++) {
      if (pot[i]) continue;
      const ax = vx[i], ay = vy[i];
      if (ax !== 0 || ay !== 0) {
        x[i] += ax * HS; y[i] += ay * HS;
        const v = Math.sqrt(ax * ax + ay * ay), nv = v - (FA + FK * v) * HS;
        if (nv < STOP) { vx[i] = 0; vy[i] = 0; } else { const f = nv / v; vx[i] = ax * f; vy[i] = ay * f; }
      }
      let inPocket = false;
      for (let p = 0; p < 18; p += 3) {
        const dx = x[i] - PK[p], dy = y[i] - PK[p + 1], r = PK[p + 2];
        if (dx * dx + dy * dy < r * r) { inPocket = true; break; }
      }
      if (inPocket) { pot[i] = 1; vx[i] = 0; vy[i] = 0; s.pots[s.n++] = i; ev(g, 2, 40); continue; }
      if (x[i] < R) { x[i] = R; if (vx[i] < 0) { ev(g, 1, -vx[i]); vx[i] = -vx[i] * CUSH; if (s.first >= 0) s.rail = true; } }
      else if (x[i] > W - R) { x[i] = W - R; if (vx[i] > 0) { ev(g, 1, vx[i]); vx[i] = -vx[i] * CUSH; if (s.first >= 0) s.rail = true; } }
      if (y[i] < R) { y[i] = R; if (vy[i] < 0) { ev(g, 1, -vy[i]); vy[i] = -vy[i] * CUSH; if (s.first >= 0) s.rail = true; } }
      else if (y[i] > H - R) { y[i] = H - R; if (vy[i] > 0) { ev(g, 1, vy[i]); vy[i] = -vy[i] * CUSH; if (s.first >= 0) s.rail = true; } }
    }
    for (let i = 0; i < 15; i++) {
      if (pot[i]) continue;
      for (let j = i + 1; j < 16; j++) {
        if (pot[j]) continue;
        let dx = x[j] - x[i], dy = y[j] - y[i];
        const d2 = dx * dx + dy * dy;
        if (d2 >= D2) continue;
        let d = Math.sqrt(d2);
        if (d < 1e-6) { dx = 1; dy = 0; d = 1; }
        const nx = dx / d, ny = dy / d, o = (2 * R - d) / 2;
        x[i] -= nx * o; y[i] -= ny * o; x[j] += nx * o; y[j] += ny * o;
        const rel = (vx[i] - vx[j]) * nx + (vy[i] - vy[j]) * ny;
        if (rel > 0) {
          const im = rel * (1 + BALLE) / 2;
          vx[i] -= im * nx; vy[i] -= im * ny; vx[j] += im * nx; vy[j] += im * ny;
          if (s.first < 0 && s.live && i === 0) s.first = j;
          ev(g, 0, rel);
        }
      }
    }
    let mov = false;
    for (let i = 0; i < 16; i++) if (!pot[i] && (vx[i] !== 0 || vy[i] !== 0)) { mov = true; break; }
    g.rolling = mov;
    return mov;
  }

  function cleared(g, gr) { for (let i = 1; i < 16; i++) if (grp(i) === gr && !g.pot[i]) return false; return true; }

  /** The shooter hits the cue ball at [ang] with [pw] 0..1. */
  function shoot(g, ang, pw) {
    const s = g.shot, me = g.turn;
    s.first = -1; s.rail = false; s.n = 0; s.live = true; s.onEight = g.grp[me] >= 0 && cleared(g, g.grp[me]);
    pw = pw < 0.02 ? 0.02 : pw > 1 ? 1 : pw;
    g.vx[0] = Math.cos(ang) * VMAX * pw; g.vy[0] = Math.sin(ang) * VMAX * pw;
    g.hand = false; g.rolling = true; g.acc = 0; g.shots++; g.ev.n = 0;
  }
  function canPlace(g, x, y) {
    if (!(x >= R && x <= W - R && y >= R && y <= H - R)) return false;
    if (g.zone === 'head' && x > HEAD) return false;
    for (let p = 0; p < 18; p += 3) { const dx = x - PK[p], dy = y - PK[p + 1], r = PK[p + 2] + 4; if (dx * dx + dy * dy < r * r) return false; }
    for (let i = 1; i < 16; i++) { if (g.pot[i]) continue; const dx = g.x[i] - x, dy = g.y[i] - y; if (dx * dx + dy * dy < D2) return false; }
    return true;
  }
  function place(g, x, y) { if (!g.hand || !canPlace(g, x, y)) return false; g.x[0] = x; g.y[0] = y; g.vx[0] = g.vy[0] = 0; g.pot[0] = 0; return true; }
  // the nearest free spot to (x, y) for a ball put back on the table
  function freeSpot(g, x, y, zone) {
    const z = g.zone; g.zone = zone || null; let bx = -1, by = -1, bd = 1e9;
    for (let gx = R; gx <= W - R; gx += 5) for (let gy = R; gy <= H - R; gy += 5) {
      if (!canPlace(g, gx, gy)) continue;
      const d = (gx - x) * (gx - x) + (gy - y) * (gy - y);
      if (d < bd) { bd = d; bx = gx; by = gy; }
    }
    g.zone = z; return [bx, by];
  }

  /** Judge the shot that has just stopped. Returns {foul, pots, over, winner, why, turn, cont}. */
  function settle(g) {
    const s = g.shot, me = g.turn, op = 1 - me, pots = [];
    s.live = false;
    for (let k = 0; k < s.n; k++) pots.push(s.pots[k]);
    const scratch = pots.indexOf(0) >= 0, eight = pots.indexOf(8) >= 0, breaking = g.breaking;
    let foul = null;
    if (scratch) foul = 'scratch';
    else if (s.first < 0) foul = 'nohit';
    else {
      const fg = grp(s.first), mine = g.grp[me];
      const ok = g.open ? fg !== 2 : (fg === mine || (fg === 2 && s.onEight));
      if (!ok) foul = 'wrong';
    }
    const objects = pots.filter((b) => b !== 0 && b !== 8);
    if (!foul && !breaking && !s.rail && !pots.length) foul = 'rail';
    g.breaking = false;
    const res = { foul, pots, over: false, winner: -1, why: '', turn: me, cont: false, assigned: false };
    if (eight) {
      if (breaking) {                                                           // the eight on the break is spotted again
        const p = freeSpot(g, W * 0.72 + 2 * (R * Math.sqrt(3) + 0.2), H / 2, null);
        g.pot[8] = 0; g.x[8] = p[0]; g.y[8] = p[1]; g.vx[8] = g.vy[8] = 0;
        pots.splice(pots.indexOf(8), 1);
      } else {
        const win = !foul && s.onEight;
        g.winner = win ? me : op; g.why = win ? 'clear' : (foul ? 'foul' : 'early');
        res.over = true; res.winner = g.winner; res.why = g.why; return res;
      }
    }
    if (g.open && !foul && !breaking && objects.length) {
      const gr = grp(objects[0]); g.grp[me] = gr; g.grp[op] = 1 - gr; g.open = false; res.assigned = true;
    }
    const cont = !foul && objects.length > 0 && (g.open || objects.some((b) => grp(b) === g.grp[me]));
    res.cont = cont;
    if (scratch) {                                                              // back on the table at the head spot, the next player moves it
      g.pot[0] = 0; const p = freeSpot(g, HEAD, H / 2, null); g.x[0] = p[0]; g.y[0] = p[1]; g.vx[0] = g.vy[0] = 0;
    }
    if (foul) { g.turn = op; g.hand = true; g.zone = null; }
    else if (!cont) g.turn = op;
    res.turn = g.turn;
    return res;
  }

  /** Whole shot at once (tests, bots): step until everything stops, then judge. */
  function run(g, maxSteps) { let n = maxSteps || 240 * 60; while (n-- > 0 && step(g)); return settle(g); }
  /** The page's frame: advance by [dt] seconds; the verdict when the balls have stopped, else null. */
  function update(g, dt) {
    g.acc += dt > 0.05 ? 0.05 : dt;
    while (g.acc >= HS) { g.acc -= HS; if (!step(g)) { g.acc = 0; return settle(g); } }
    return null;
  }

  // ---- aim: where the cue ball would hit first (for the aim line), no allocation
  const hit = { t: 0, ball: -1, gx: 0, gy: 0 };
  function ray(g, ang) {
    const px = g.x[0], py = g.y[0], dx = Math.cos(ang), dy = Math.sin(ang);
    let bt = 1e9, bb = -1;
    for (let k = 1; k < 16; k++) {
      if (g.pot[k]) continue;
      const cx = g.x[k] - px, cy = g.y[k] - py, b = cx * dx + cy * dy;
      if (b <= 0) continue;
      const disc = b * b - (cx * cx + cy * cy - D2);
      if (disc < 0) continue;
      const t = b - Math.sqrt(disc);
      if (t >= 0 && t < bt) { bt = t; bb = k; }
    }
    const tx = dx > 1e-9 ? (W - R - px) / dx : dx < -1e-9 ? (R - px) / dx : 1e9, ty = dy > 1e-9 ? (H - R - py) / dy : dy < -1e-9 ? (R - py) / dy : 1e9;
    const wt = Math.min(tx, ty);
    if (wt < bt) { bt = wt; bb = -1; }
    hit.t = bt; hit.ball = bb; hit.gx = px + dx * bt; hit.gy = py + dy * bt;
    return hit;
  }

  // ---- the bot: ghost-ball method. Behind the ghost ball (2R from the target, on the line pocket -> target) the cue ball hits the target toward the pocket.
  const dist = (v) => v / FK - FA / (FK * FK) * Math.log(1 + FK * v / FA);     // how far a ball goes from speed v
  function speedFor(d) { let lo = 0, hi = VMAX; for (let i = 0; i < 28; i++) { const m = (lo + hi) / 2; if (dist(m) < d) lo = m; else hi = m; } return hi; }
  function segD2(px, py, ax, ay, bx, by) {
    const vx = bx - ax, vy = by - ay, l = vx * vx + vy * vy;
    let t = l > 0 ? ((px - ax) * vx + (py - ay) * vy) / l : 0; t = t < 0 ? 0 : t > 1 ? 1 : t;
    const dx = ax + vx * t - px, dy = ay + vy * t - py; return dx * dx + dy * dy;
  }
  function blocked(g, ax, ay, bx, by, s1, s2) {
    for (let k = 1; k < 16; k++) if (!g.pot[k] && k !== s1 && k !== s2 && segD2(g.x[k], g.y[k], ax, ay, bx, by) < D2 * 0.96) return true;
    return false;
  }
  const PD = [[-1, -1], [0, -1], [1, -1], [-1, 1], [0, 1], [1, 1]];              // which way is out of each pocket
  const best = { score: -9, ang: 0, pw: 0.5, id: -1 };
  function targets(g, me) {
    const out = [];
    if (g.open) { for (let i = 1; i < 16; i++) if (i !== 8 && !g.pot[i]) out.push(i); return out; }
    const gr = g.grp[me];
    for (let i = 1; i < 16; i++) if (grp(i) === gr && !g.pot[i]) out.push(i);
    if (!out.length && !g.pot[8]) out.push(8);
    return out;
  }
  /** The best straight-in shot from (cx, cy) at the [tg] balls: fills `best` (score -9 = none). */
  function evaluate(g, cx, cy, tg) {
    best.score = -9; best.id = -1;
    for (let a = 0; a < tg.length; a++) {
      const t = tg[a], tx = g.x[t], ty = g.y[t];
      for (let p = 0; p < 6; p++) {
        const ux0 = PK[p * 3] - tx, uy0 = PK[p * 3 + 1] - ty, dtp = Math.sqrt(ux0 * ux0 + uy0 * uy0), ux = ux0 / dtp, uy = uy0 / dtp;
        const o = PD[p];
        if (p === 1 || p === 4 ? Math.abs(uy) < 0.55 : ux * o[0] + uy * o[1] < 0.42) continue;
        const gx = tx - ux * 2 * R, gy = ty - uy * 2 * R;
        if (gx < R || gx > W - R || gy < R || gy > H - R) continue;
        const vx = gx - cx, vy = gy - cy, dcg = Math.sqrt(vx * vx + vy * vy);
        if (dcg < 1) continue;
        const cs = (vx * ux + vy * uy) / dcg;
        if (cs < 0.25) continue;
        if (blocked(g, cx, cy, gx, gy, t, 0) || blocked(g, tx, ty, PK[p * 3], PK[p * 3 + 1], t, 0)) continue;
        const sc = cs - (dcg + dtp) / 1100 - (p === 1 || p === 4 ? 0.04 : 0);
        if (sc > best.score) {
          best.score = sc; best.id = t; best.ang = Math.atan2(vy, vx);
          const vt = speedFor(dtp * 1.1 + 45), v1 = vt / Math.max(0.3, cs);
          best.pw = Math.min(1, Math.max(0.1, speedFor(dcg + dist(v1)) / VMAX));
        }
      }
    }
    return best.score > -9;
  }
  const gauss = (r) => (r() + r() + r() + r() - 2) * 1.7;
  const SIGMA = [0.05, 0.022, 0.008];
  /** The bot's whole move: {x, y} (the cue ball's spot when it has it in hand, else null), {ang, pw}. [level] 0 easy .. 2 hard. */
  function botMove(g, level) {
    const me = g.turn, r = g.rng, sg = SIGMA[level == null ? 1 : level], tg = targets(g, me), mv = { x: null, y: null, ang: 0, pw: 0.5, pot: false };
    if (g.breaking) {                                                           // the break: hard into the head of the rack
      mv.ang = Math.atan2(H / 2 - g.y[0], W * 0.72 - g.x[0]) + gauss(r) * 0.01; mv.pw = 0.88 + r() * 0.12; return mv;
    }
    let cx = g.x[0], cy = g.y[0];
    if (g.hand) {
      let bs = -99, bx = -1, by = -1;
      for (let gx = R + 4; gx < W - R; gx += 40) for (let gy = R + 8; gy < H - R; gy += 36) {
        if (!canPlace(g, gx, gy)) continue;
        const sc = evaluate(g, gx, gy, tg) ? best.score : -5 - Math.abs(gx - W * 0.4) / 800;
        if (sc > bs) { bs = sc; bx = gx; by = gy; }
      }
      if (bx < 0) { const p = freeSpot(g, HEAD, H / 2, g.zone); bx = p[0]; by = p[1]; }
      cx = bx; cy = by; mv.x = bx; mv.y = by;
    }
    if (evaluate(g, cx, cy, tg)) { mv.ang = best.ang + gauss(r) * sg; mv.pw = best.pw * (1 + gauss(r) * 0.06); mv.pot = true; }
    else {                                                                      // nothing straight in: touch the nearest ball of its own with some power
      let bd = 1e9, bi = -1;
      for (let a = 0; a < tg.length; a++) {
        const t = tg[a], d = (g.x[t] - cx) * (g.x[t] - cx) + (g.y[t] - cy) * (g.y[t] - cy);
        const clear = !blocked(g, cx, cy, g.x[t], g.y[t], t, 0);
        const sc = d + (clear ? 0 : 1e6);
        if (sc < bd) { bd = sc; bi = t; }
      }
      if (bi < 0) bi = 1;
      mv.ang = Math.atan2(g.y[bi] - cy, g.x[bi] - cx) + gauss(r) * sg;
      mv.pw = Math.min(0.7, 0.3 + Math.sqrt(bd) / 1500);
    }
    mv.pw = Math.min(1, Math.max(0.05, mv.pw));
    return mv;
  }

  const BD = { W, H, R, HEAD, VMAX, PK, grp, create, rack, step, shoot, settle, run, update, canPlace, place, freeSpot, cleared, ray, hit, botMove, targets, evaluate, speedFor, dist };
  if (typeof module === 'object' && module.exports) module.exports = BD; else root.BD = BD;
})(this);
