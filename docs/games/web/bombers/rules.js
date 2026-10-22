'use strict';
// Bombers' rules and bots, no screen (node tests load this file too: app/games/tests/bombers.test.js).
// Cells are indexed r * COLS + c; a player's x / y are in tiles (cell c covers x in [c, c+1)). Everything the hot loop touches is a
// typed array or a pooled object made once, so a match allocates nothing per frame.
(function (root) {
  const COLS = 15, ROWS = 11, N = COLS * ROWS, INF = Infinity, MAXB = 64;
  const FUSE = 2.5, FLAME = 0.55, SD_AT = 75, SD_EVERY = 0.25, DROP = 0.3;
  const H = 0.38, SLIDE = 0.35, EPS = 0.0001;                                    // half the body (a tile is 1), how far into a wall a corner still slides
  const SPEED0 = 3.4, SPEED_UP = 0.5, SPEED_MAX = 5.4, RANGE0 = 2, RANGE_MAX = 9, BOMBS_MAX = 6;
  const DX = [0, 0, -1, 1], DY = [-1, 1, 0, 0];                                  // 0 up, 1 down, 2 left, 3 right
  const CORNERS = [[0, 0], [COLS - 1, ROWS - 1], [COLS - 1, 0], [0, ROWS - 1]];
  const noop = () => { };

  // the sudden-death order: the outer ring first, then inwards
  const SPIRAL = (() => {
    const out = []; let t = 0, b = ROWS - 1, l = 0, r = COLS - 1;
    while (t <= b && l <= r) {
      for (let c = l; c <= r; c++) out.push(t * COLS + c); t++;
      for (let q = t; q <= b; q++) out.push(q * COLS + r); r--;
      if (t <= b) { for (let c = r; c >= l; c--) out.push(b * COLS + c); b--; }
      if (l <= r) { for (let q = b; q >= t; q--) out.push(q * COLS + l); l++; }
    }
    return Int16Array.from(out);
  })();

  // pillars on the odd/odd cells, crates in most of the rest, the four corners kept clear (a cell and two more each way)
  function makeGrid(rand, rate) {
    const g = new Uint8Array(N); rate = rate == null ? 0.6 : rate;
    for (let r = 0; r < ROWS; r++) for (let c = 0; c < COLS; c++) {
      if (c % 2 === 1 && r % 2 === 1) { g[r * COLS + c] = 1; continue; }
      let near = false; for (const [cc, rr] of CORNERS) if (Math.abs(c - cc) + Math.abs(r - rr) <= 2) near = true;
      if (!near && rand() < rate) g[r * COLS + c] = 2;
    }
    return g;
  }

  // the cells a bomb's flame covers: its own, then up to `range` each way, stopping at a pillar and at (but burning) the first crate
  function blast(grid, c, r, range, out) {
    let n = 0; out[n++] = r * COLS + c;
    for (let d = 0; d < 4; d++) for (let k = 1; k <= range; k++) {
      const cc = c + DX[d] * k, rr = r + DY[d] * k; if (cc < 0 || rr < 0 || cc >= COLS || rr >= ROWS) break;
      const i = rr * COLS + cc, v = grid[i]; if (v === 1) break;
      out[n++] = i; if (v === 2) break;
    }
    return n;
  }

  const bm = new Int16Array(N), qd = new Uint8Array(MAXB + 2), seen = new Uint8Array(N), T1 = new Int16Array(N), Q1 = new Int16Array(MAXB + 2);
  // Every bomb whose timer reached 0 goes off, and any bomb in a flame goes off with it (a chain). `res` is filled: bombs[0..nb) the
  // indexes that exploded, cells[0..nc) the cells on fire with cellOwner[] the bomb owner of each, broken[0..nbr) the crates burnt (already 0 in grid).
  function newRes() { return { nb: 0, nc: 0, nbr: 0, bombs: new Int16Array(MAXB), cells: new Int16Array(N), cellOwner: new Int8Array(N), broken: new Int16Array(N) }; }
  function detonate(grid, bombs, flame, res) {
    bm.fill(-1); qd.fill(0); seen.fill(0); res.nb = res.nc = res.nbr = 0;
    let qn = 0, qh = 0;
    for (let k = 0; k < bombs.length; k++) if (bombs[k].on) { bm[bombs[k].r * COLS + bombs[k].c] = k; if (bombs[k].t <= 0) { Q1[qn++] = k; qd[k] = 1; } }
    while (qh < qn) {
      const k = Q1[qh++], b = bombs[k]; b.on = false; res.bombs[res.nb++] = k;
      const n = blast(grid, b.c, b.r, b.range, T1);
      for (let j = 0; j < n; j++) {
        const i = T1[j]; if (seen[i]) continue; seen[i] = 1;
        res.cellOwner[res.nc] = b.owner; res.cells[res.nc++] = i; flame[i] = 1;
        if (grid[i] === 2) res.broken[res.nbr++] = i;
        const m = bm[i]; if (m >= 0 && bombs[m].on && !qd[m]) { qd[m] = 1; Q1[qn++] = m; }
      }
    }
    for (let j = 0; j < res.nbr; j++) grid[res.broken[j]] = 0;                  // crates go after the whole chain, so one still shields in this pass
    return res.nb;
  }

  const L = [], te = new Float32Array(MAXB + 2), lm = new Int16Array(N), T2 = new Int16Array(N);
  // out[cell] = the seconds until fire reaches it (0 = burning now, Infinity = no bomb will touch it). A bomb in another's flame goes off at
  // the earlier time. `extra` is a bomb that is not placed yet ({c, r, t, range}).
  function danger(grid, bombs, flame, out, extra) {
    out.fill(INF); L.length = 0;
    for (let k = 0; k < bombs.length; k++) if (bombs[k].on) L.push(bombs[k]);
    if (extra) L.push(extra);
    lm.fill(-1);
    for (let j = 0; j < L.length; j++) { lm[L[j].r * COLS + L[j].c] = j; te[j] = L[j].t; }
    for (let ch = true, g = 0; ch && g <= L.length; g++) {
      ch = false;
      for (let j = 0; j < L.length; j++) {
        const b = L[j], n = blast(grid, b.c, b.r, b.range, T2);
        for (let q = 0; q < n; q++) { const m = lm[T2[q]]; if (m >= 0 && te[m] > te[j]) { te[m] = te[j]; ch = true; } }
      }
    }
    for (let j = 0; j < L.length; j++) {
      const b = L[j], n = blast(grid, b.c, b.r, b.range, T2);
      for (let q = 0; q < n; q++) if (te[j] < out[T2[q]]) out[T2[q]] = te[j];
    }
    for (let i = 0; i < N; i++) if (flame[i]) out[i] = 0;
  }

  const blk = new Uint8Array(N), dist = new Int16Array(N), first = new Int8Array(N), Q2 = new Int16Array(N);
  let lastDist = 0;
  // The first move (0 up, 1 down, 2 left, 3 right) of the shortest walk from (c, r) to a cell with goal[] set, or -1. Bombs block, pillars and
  // crates block, and a cell is only entered when the fire gets there later than we do (+ margin seconds): margin 99 = never enter any danger.
  function bfs(grid, bombs, dg, c, r, speed, goal, margin, extra) {
    blk.fill(0); dist.fill(-1);
    for (let k = 0; k < bombs.length; k++) if (bombs[k].on) blk[bombs[k].r * COLS + bombs[k].c] = 1;
    if (extra) blk[extra.r * COLS + extra.c] = 1;
    const s = r * COLS + c; dist[s] = 0; first[s] = -1; let qn = 0, qh = 0; Q2[qn++] = s;
    while (qh < qn) {
      const cur = Q2[qh++], cc = cur % COLS, cr = (cur / COLS) | 0;
      for (let d = 0; d < 4; d++) {
        const nc = cc + DX[d], nr = cr + DY[d]; if (nc < 0 || nr < 0 || nc >= COLS || nr >= ROWS) continue;
        const ni = nr * COLS + nc; if (dist[ni] >= 0 || grid[ni] || blk[ni]) continue;
        const steps = dist[cur] + 1; if (dg[ni] <= steps / speed + margin) continue;
        dist[ni] = steps; first[ni] = cur === s ? d : first[cur];
        if (goal[ni]) { lastDist = steps; return first[ni]; }
        Q2[qn++] = ni;
      }
    }
    return -1;
  }
  const XB = { on: true, c: 0, r: 0, t: FUSE, range: 2, owner: 0, pass: 0 }, D2 = new Float32Array(N), G2 = new Uint8Array(N);
  // would a bomb dropped at (c, r) leave a way out before it blows (every other bomb and its chain counted)?
  // With fc / fr: can someone standing there (a foe, at `speed`) get out of that bomb's reach? true too when it is not in reach at all.
  function canEscape(grid, bombs, flame, c, r, range, t, speed, fc, fr) {
    XB.c = c; XB.r = r; XB.range = range; XB.t = t;
    danger(grid, bombs, flame, D2, XB);
    let margin = 0.5;
    if (fc === undefined) { fc = c; fr = r; } else { if (D2[fr * COLS + fc] === INF) return true; margin = 0; }
    for (let i = 0; i < N; i++) G2[i] = D2[i] === INF && grid[i] === 0 ? 1 : 0;
    return bfs(grid, bombs, D2, fc, fr, speed, G2, margin, XB) >= 0;
  }

  // ---------------- the world: players, bombs, fire, power-ups, sudden death
  function createWorld(n, humans, rand) {
    rand = rand || Math.random;
    const w = {
      rand, grid: makeGrid(rand), bombs: [], bombAt: new Int16Array(N).fill(-1), flame: new Uint8Array(N), flameT: new Float32Array(N), flameBy: new Int8Array(N),
      power: new Uint8Array(N), danger: new Float32Array(N), safe: new Uint8Array(N), roomy: new Uint8Array(N), goal: new Uint8Array(N), occ: new Uint8Array(N),
      players: [], T: 0, sdN: 0, sdT: 0, frozen: false, res: newRes(), on: { place: noop, boom: noop, drop: noop, pick: noop, die: noop, sd: noop },
    };
    for (let k = 0; k < MAXB; k++) w.bombs.push({ on: false, c: 0, r: 0, t: 0, range: RANGE0, owner: 0, pass: 0 });
    for (let i = 0; i < n; i++) {
      w.players.push({
        i, human: !!(humans && humans[i]), alive: true, x: CORNERS[i][0] + 0.5, y: CORNERS[i][1] + 0.5, maxB: 1, out: 0, range: RANGE0, speed: SPEED0,
        dx: 0, dy: 0, dx2: 0, dy2: 0, axis: 0, bomb: false, face: 1, walk: 0, think: 0.2 + rand() * 0.3, cool: 0.5 + rand(), bad: new Uint8Array(N), badT: 0, cause: -2,
      });
    }
    return w;
  }

  const solid = (w, p, c, r) => {
    if (c < 0 || r < 0 || c >= COLS || r >= ROWS) return true;
    const i = r * COLS + c; if (w.grid[i]) return true;
    const b = w.bombAt[i]; return b >= 0 && !((w.bombs[b].pass >> p.i) & 1);          // your own fresh bomb is walked off, not through
  };
  // One slice of walking along one axis. Blocked by a wall with only a corner of the body in it: slide sideways round the corner.
  function step(w, p, sx, sy, d, noSlide) {
    if (sx) {
      const nx = p.x + sx * d, col = Math.floor(nx + sx * H), r0 = Math.floor(p.y - H), r1 = Math.floor(p.y + H);
      const b0 = solid(w, p, col, r0), b1 = r1 !== r0 && solid(w, p, col, r1);
      if (!b0 && !b1) { p.x = nx; return true; }
      if (!noSlide && r1 !== r0 && b0 !== b1) {
        const fr = b0 ? r1 : r0, br = b0 ? r0 : r1, depth = br < fr ? Math.max(0, fr - p.y) : Math.max(0, p.y - (fr + 1));
        if (depth <= SLIDE) return step(w, p, 0, fr > br ? 1 : -1, Math.min(d, Math.abs(fr + 0.5 - p.y)), true);
      }
      const lim = sx > 0 ? col - H - EPS : col + 1 + H + EPS;
      if (sx > 0 ? lim > p.x : lim < p.x) { p.x = lim; return true; }
      return false;
    }
    const ny = p.y + sy * d, row = Math.floor(ny + sy * H), c0 = Math.floor(p.x - H), c1 = Math.floor(p.x + H);
    const b0 = solid(w, p, c0, row), b1 = c1 !== c0 && solid(w, p, c1, row);
    if (!b0 && !b1) { p.y = ny; return true; }
    if (!noSlide && c1 !== c0 && b0 !== b1) {
      const fc = b0 ? c1 : c0, bc = b0 ? c0 : c1, depth = bc < fc ? Math.max(0, fc - p.x) : Math.max(0, p.x - (fc + 1));
      if (depth <= SLIDE) return step(w, p, fc > bc ? 1 : -1, 0, Math.min(d, Math.abs(fc + 0.5 - p.x)), true);
    }
    const lim = sy > 0 ? row - H - EPS : row + 1 + H + EPS;
    if (sy > 0 ? lim > p.y : lim < p.y) { p.y = lim; return true; }
    return false;
  }

  // the stick -> one axis (the new one when both are held, so a turn at a junction works), with the other as the fallback if that is blocked
  function axisDir(p, x, y) {
    const TH = 0.45, ax = Math.abs(x) > TH, ay = Math.abs(y) > TH;
    p.dx = p.dy = p.dx2 = p.dy2 = 0;
    if (!ax && !ay) return;
    let horiz = ax;
    if (ax && ay) horiz = Math.abs(x) > 1.3 * Math.abs(y) ? true : Math.abs(y) > 1.3 * Math.abs(x) ? false : p.axis !== 1;
    if (horiz) { p.dx = x > 0 ? 1 : -1; if (ay) p.dy2 = y > 0 ? 1 : -1; } else { p.dy = y > 0 ? 1 : -1; if (ax) p.dx2 = x > 0 ? 1 : -1; }
    p.axis = horiz ? 1 : 2;
  }

  function place(w, p) {
    const c = Math.floor(p.x), r = Math.floor(p.y), i = r * COLS + c;
    if (!p.alive || p.out >= p.maxB || w.bombAt[i] >= 0 || w.grid[i]) return false;
    let k = 0; while (k < MAXB && w.bombs[k].on) k++; if (k === MAXB) return false;
    const b = w.bombs[k]; b.on = true; b.c = c; b.r = r; b.t = FUSE; b.range = p.range; b.owner = p.i; b.pass = 0;
    for (const q of w.players) if (q.alive && q.x + H > c && q.x - H < c + 1 && q.y + H > r && q.y - H < r + 1) b.pass |= 1 << q.i;
    w.bombAt[i] = k; p.out++; w.on.place(p, b); return true;
  }

  function refresh(w) {                                                          // the maps the bots (and the checks below) read, once a step
    danger(w.grid, w.bombs, w.flame, w.danger, null);
    if (w.T >= SD_AT - 2) for (let j = w.sdN; j < w.sdN + 10 && j < SPIRAL.length; j++) w.danger[SPIRAL[j]] = 0;   // the next cells to close count as fire
    w.occ.fill(0);
    for (const p of w.players) if (p.alive) w.occ[Math.floor(p.y) * COLS + Math.floor(p.x)] |= 1 << p.i;
    for (let i = 0; i < N; i++) w.safe[i] = w.danger[i] === INF && w.grid[i] === 0 && w.bombAt[i] < 0 ? 1 : 0;
    for (let i = 0; i < N; i++) {                                                // a safe cell with a way on (not a dead end another bomb could seal)
      w.roomy[i] = 0; if (!w.safe[i]) continue;
      const c = i % COLS, r = (i / COLS) | 0; let n = 0;
      for (let d = 0; d < 4; d++) { const nc = c + DX[d], nr = r + DY[d]; if (nc >= 0 && nr >= 0 && nc < COLS && nr < ROWS && !w.grid[nr * COLS + nc]) n++; }
      if (n >= 2) w.roomy[i] = 1;
    }
  }

  // would a bomb at (c, r) get something: a crate, or another player in line, within the flame
  function worth(w, p, c, r) {
    if (w.occ[r * COLS + c] & ~(1 << p.i)) return true;
    for (let d = 0; d < 4; d++) for (let k = 1; k <= p.range; k++) {
      const cc = c + DX[d] * k, rr = r + DY[d] * k; if (cc < 0 || rr < 0 || cc >= COLS || rr >= ROWS) break;
      const i = rr * COLS + cc, v = w.grid[i]; if (v === 1) break; if (v === 2) return true;
      if (w.occ[i] & ~(1 << p.i)) return true;
    }
    return false;
  }
  function traps(w, p, c, r) {                                                    // would a bomb here leave a foe nearby with no way out?
    for (const q of w.players) if (q !== p && q.alive) {
      const qc = Math.floor(q.x), qr = Math.floor(q.y);
      if (Math.abs(qc - c) + Math.abs(qr - r) <= p.range + 2 && !canEscape(w.grid, w.bombs, w.flame, c, r, p.range, FUSE, q.speed, qc, qr)) return true;
    }
    return false;
  }
  function setDir(w, p, d) { p.dx = DX[d]; p.dy = DY[d]; p.dx2 = p.dy2 = 0; }

  function think(w, p, dt) {                                                     // a bot: runs from fire, bombs only with a way out, then hunts crates / players / power-ups
    p.think -= dt; p.cool -= dt; p.badT -= dt; if (p.badT <= 0) { p.bad.fill(0); p.badT = 4; }
    if (p.think > 0) return; p.think = 0.05;
    const c = Math.floor(p.x), r = Math.floor(p.y), i = r * COLS + c; p.dx = p.dy = p.dx2 = p.dy2 = 0;
    if (w.danger[i] < INF) {                                                      // in danger: the nearest safe cell we reach first
      let d = bfs(w.grid, w.bombs, w.danger, c, r, p.speed, w.roomy, 0.4, null);
      if (d < 0 || lastDist > 6) { const d2 = bfs(w.grid, w.bombs, w.danger, c, r, p.speed, w.safe, 0.4, null); if (d2 >= 0) d = d2; }
      if (d >= 0) { setDir(w, p, d); return; }
      let best = -1, bv = w.danger[i];                                            // no way out: at least the neighbour that burns last
      for (let k = 0; k < 4; k++) { const nc = c + DX[k], nr = r + DY[k]; if (nc < 0 || nr < 0 || nc >= COLS || nr >= ROWS) continue; const ni = nr * COLS + nc; if (w.grid[ni] || w.bombAt[ni] >= 0) continue; if (w.danger[ni] > bv) { bv = w.danger[ni]; best = k; } }
      if (best >= 0) setDir(w, p, best); return;
    }
    if (p.cool <= 0 && p.out < p.maxB && w.bombAt[i] < 0 && !p.bad[i] && (worth(w, p, c, r) || traps(w, p, c, r))) {
      if (canEscape(w.grid, w.bombs, w.flame, c, r, p.range, FUSE, p.speed)) { p.bomb = true; p.cool = 0.2 + w.rand() * 0.4; return; }
      p.bad[i] = 1;
    }
    const g = w.goal; g.fill(0);
    for (let k = 0; k < N; k++) if (w.power[k] && w.safe[k]) g[k] = 1;
    let d = bfs(w.grid, w.bombs, w.danger, c, r, p.speed, g, 99, null);
    if (d >= 0 && lastDist <= 8) { setDir(w, p, d); return; }
    g.fill(0);
    for (let rr = 0; rr < ROWS; rr++) for (let cc = 0; cc < COLS; cc++) { const k = rr * COLS + cc; if (w.safe[k] && !p.bad[k] && worth(w, p, cc, rr)) g[k] = 1; }
    for (const q of w.players) if (q !== p && q.alive) {                          // close in on the nearest foe: any open cell within 2 of it
      const qc = Math.floor(q.x), qr = Math.floor(q.y);
      for (let a = -2; a <= 2; a++) for (let b = -2; b <= 2; b++) if (Math.abs(a) + Math.abs(b) <= 2) { const nc = qc + a, nr = qr + b; if (nc >= 0 && nr >= 0 && nc < COLS && nr < ROWS && w.safe[nr * COLS + nc] && !p.bad[nr * COLS + nc]) g[nr * COLS + nc] = 1; }
    }
    g[i] = 0;
    d = bfs(w.grid, w.bombs, w.danger, c, r, p.speed, g, 99, null);
    if (d >= 0) { setDir(w, p, d); return; }
    const k0 = (w.rand() * 4) | 0;                                                 // nothing to chase: wander
    for (let a = 0; a < 4; a++) { const k = (k0 + a) & 3, nc = c + DX[k], nr = r + DY[k]; if (nc >= 0 && nr >= 0 && nc < COLS && nr < ROWS && w.safe[nr * COLS + nc]) { setDir(w, p, k); return; } }
  }

  function kill(w, p, by) { p.alive = false; p.cause = by; p.dx = p.dy = p.dx2 = p.dy2 = 0; w.on.die(p, by); }
  function closeCell(w, i) {                                                     // sudden death: a pillar drops here
    w.grid[i] = 1; w.power[i] = 0; w.flame[i] = 0;
    const b = w.bombAt[i]; if (b >= 0) { w.bombs[b].on = false; w.players[w.bombs[b].owner].out--; w.bombAt[i] = -1; }
    const c = i % COLS, r = (i / COLS) | 0;
    for (const p of w.players) if (p.alive && Math.floor(p.x) === c && Math.floor(p.y) === r) kill(w, p, -1);
    w.on.sd(i);
  }

  function update(w, dt) {
    const gs = w.grid; w.T += dt;
    refresh(w);
    for (const p of w.players) {
      if (!p.alive) continue;
      if (!p.human) think(w, p, dt);
      if (w.frozen) continue;
      if (p.bomb) { p.bomb = false; place(w, p); }
      if (p.dx || p.dy) {
        const d = p.speed * dt; let ok = false;
        if (p.dx) { ok = step(w, p, p.dx, 0, d, false); if (!ok && p.dy2) ok = step(w, p, 0, p.dy2, d, false); } else { ok = step(w, p, 0, p.dy, d, false); if (!ok && p.dx2) ok = step(w, p, p.dx2, 0, d, false); }
        if (p.dx) p.face = p.dx > 0 ? 3 : 2; else p.face = p.dy > 0 ? 1 : 0;
        if (ok) {
          p.walk += d;
          if (p.dx) { const off = p.y - (Math.floor(p.y) + 0.5); if (Math.abs(off) > 0.001) step(w, p, 0, off > 0 ? -1 : 1, Math.min(Math.abs(off), d * 0.7), true); }   // settle into the lane
          else { const off = p.x - (Math.floor(p.x) + 0.5); if (Math.abs(off) > 0.001) step(w, p, off > 0 ? -1 : 1, 0, Math.min(Math.abs(off), d * 0.7), true); }
        }
      }
    }
    for (let k = 0; k < MAXB; k++) {                                              // a fresh bomb stays walk-through only while its dropper still overlaps it
      const b = w.bombs[k]; if (!b.on || !b.pass) continue;
      for (const q of w.players) if ((b.pass >> q.i) & 1 && !(q.alive && q.x + H > b.c && q.x - H < b.c + 1 && q.y + H > b.r && q.y - H < b.r + 1)) b.pass &= ~(1 << q.i);
    }
    let go = false;
    for (let k = 0; k < MAXB; k++) { const b = w.bombs[k]; if (b.on) { b.t -= dt; if (b.t <= 0) go = true; } }
    if (go) {
      const res = w.res; detonate(gs, w.bombs, w.flame, res);
      for (let j = 0; j < res.nb; j++) { const b = w.bombs[res.bombs[j]]; w.bombAt[b.r * COLS + b.c] = -1; w.players[b.owner].out--; }
      for (let j = 0; j < res.nc; j++) { const i = res.cells[j]; w.flameT[i] = FLAME; w.flameBy[i] = res.cellOwner[j]; w.power[i] = 0; }
      for (let j = 0; j < res.nbr; j++) if (w.rand() < DROP) { const i = res.broken[j], t = 1 + ((w.rand() * 3) | 0); w.power[i] = t; w.on.drop(i, t); }
      w.on.boom(res);
    }
    for (let i = 0; i < N; i++) if (w.flame[i]) { w.flameT[i] -= dt; if (w.flameT[i] <= 0) w.flame[i] = 0; }
    if (!w.frozen) {
      for (const p of w.players) {
        if (!p.alive) continue;
        const i = Math.floor(p.y) * COLS + Math.floor(p.x);
        if (w.flame[i]) { kill(w, p, w.flameBy[i]); continue; }
        const t = w.power[i];
        if (t) { w.power[i] = 0; if (t === 1) p.maxB = Math.min(BOMBS_MAX, p.maxB + 1); else if (t === 2) p.range = Math.min(RANGE_MAX, p.range + 1); else p.speed = Math.min(SPEED_MAX, p.speed + SPEED_UP); w.on.pick(p, t); }
      }
      if (w.T >= SD_AT) { w.sdT -= dt; while (w.sdT <= 0 && w.sdN < SPIRAL.length) { closeCell(w, SPIRAL[w.sdN++]); w.sdT += SD_EVERY; } }
    }
  }
  const alive = (w) => { let n = 0; for (const p of w.players) if (p.alive) n++; return n; };

  const api = { COLS, ROWS, N, INF, FUSE, FLAME, SD_AT, H, SPIRAL, CORNERS, DX, DY, makeGrid, blast, detonate, danger, bfs, canEscape, newRes, createWorld, update, step, place, axisDir, alive, refresh };
  if (typeof module === 'object' && module.exports) module.exports = api; else root.BM = api;
})(this);
