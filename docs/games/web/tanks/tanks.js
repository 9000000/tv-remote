'use strict';
// Tank Arena: 2-4 tanks in a walled arena, shots bounce twice, last tank standing takes the round, first to 4 rounds wins.
// The stick turns and drives (the tank turns on the spot first), the button fires. CPUs fill the empty seats.
(function () {
  const K = window.Kit, CS = 75, COLS = 16, ROWS = 8, OX = 40, OY = 100, TR = 23, WIN = 4, SPEED = 200, BSPD = 480;
  const CORNERS = [[0, 0], [COLS - 1, ROWS - 1], [COLS - 1, 0], [0, ROWS - 1]];
  let wall, wallCv, tanks, bul, phase, ptimer, round, over, roundWin, lastKill = 0, dist = null;

  const solidCell = (c, r) => c < 0 || r < 0 || c >= COLS || r >= ROWS || wall[r][c];
  const solidAt = (x, y) => solidCell(Math.floor((x - OX) / CS), Math.floor((y - OY) / CS));
  const cellOf = (t) => [K.clamp(Math.floor((t.x - OX) / CS), 0, COLS - 1), K.clamp(Math.floor((t.y - OY) / CS), 0, ROWS - 1)];

  function reach(w) {                                                          // every open cell reachable from the first corner?
    const seen = new Set(['0,0']), q = [[0, 0]];
    while (q.length) { const [c, r] = q.pop(); for (const [dc, dr] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) { const a = c + dc, b = r + dr; if (a < 0 || b < 0 || a >= COLS || b >= ROWS || w[b][a] || seen.has(a + ',' + b)) continue; seen.add(a + ',' + b); q.push([a, b]); } }
    let open = 0; for (const row of w) for (const v of row) if (!v) open++;
    return seen.size === open;
  }
  function makeMap() {
    for (let tries = 0; tries < 30; tries++) {
      const w = Array.from({ length: ROWS }, () => new Array(COLS).fill(false)), hc = COLS / 2, hr = ROWS / 2;
      const put = (c, r) => { for (const [a, b] of [[c, r], [COLS - 1 - c, r], [c, ROWS - 1 - r], [COLS - 1 - c, ROWS - 1 - r]]) w[b][a] = true; };
      for (let i = 0; i < 6 + ((Math.random() * 3) | 0); i++) {
        let c = (Math.random() * hc) | 0, r = (Math.random() * hr) | 0; if (c <= 1 && r <= 1) continue;
        put(c, r); const m = Math.random();
        if (m < 0.4 && c + 1 < hc) put(c + 1, r); else if (m < 0.8 && r + 1 < hr) put(c, r + 1);
      }
      if (reach(w)) return w;
    }
    return Array.from({ length: ROWS }, () => new Array(COLS).fill(false));
  }
  function paintWalls() {
    wallCv = wallCv || Object.assign(document.createElement('canvas'), { width: 1280, height: 720 });
    const c = wallCv.getContext('2d'); c.clearRect(0, 0, 1280, 720);
    c.fillStyle = 'rgba(9,16,40,.9)'; K.rr(c, OX - 10, OY - 10, COLS * CS + 20, ROWS * CS + 20, 22); c.fill();
    c.fillStyle = 'rgba(120,150,255,.07)';
    for (let r = 0; r < ROWS; r++) for (let q = 0; q < COLS; q++) if ((r + q) % 2) c.fillRect(OX + q * CS, OY + r * CS, CS, CS);
    c.strokeStyle = '#4f6bd8'; c.lineWidth = 4; K.rr(c, OX - 10, OY - 10, COLS * CS + 20, ROWS * CS + 20, 22); c.stroke();
    for (let r = 0; r < ROWS; r++) for (let q = 0; q < COLS; q++) if (wall[r][q]) {
      const x = OX + q * CS, y = OY + r * CS;
      c.fillStyle = '#1e2a5a'; K.rr(c, x + 2, y + 2, CS - 4, CS - 4, 10); c.fill();
      c.fillStyle = '#3550b8'; K.rr(c, x + 2, y + 2, CS - 4, CS - 12, 10); c.fill();
      c.strokeStyle = '#8fa8ff'; c.lineWidth = 2; K.rr(c, x + 5, y + 5, CS - 10, CS - 16, 7); c.stroke();
    }
  }
  function newRound() {
    wall = makeMap(); paintWalls(); bul = []; round++; phase = 'ready'; ptimer = 1.6; dist = null;
    tanks = K.slots.map((s, i) => {
      const [c, r] = CORNERS[i], x = OX + (c + 0.5) * CS, y = OY + (r + 0.5) * CS;
      return { s, i, x, y, a: Math.atan2(OY + ROWS * CS / 2 - y, OX + COLS * CS / 2 - x), alive: true, cool: 0, react: 0, aimErr: 0, think: 0, goal: null, dodge: 0 };
    });
  }
  function los(a, b) {                                                          // a straight shot between two points, no wall?
    const d = Math.hypot(b.x - a.x, b.y - a.y), n = Math.ceil(d / 14);
    for (let i = 1; i < n; i++) if (solidAt(a.x + (b.x - a.x) * i / n, a.y + (b.y - a.y) * i / n)) return false;
    return true;
  }
  function field(target) {                                                      // BFS distances from a cell: the CPU walks downhill
    const [tc, tr] = cellOf(target), d = Array.from({ length: ROWS }, () => new Array(COLS).fill(99)); d[tr][tc] = 0; const q = [[tc, tr]];
    for (let h = 0; h < q.length; h++) { const [c, r] = q[h]; for (const [dc, dr] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) { const a = c + dc, b = r + dr; if (solidCell(a, b) || d[b][a] < 99) continue; d[b][a] = d[r][c] + 1; q.push([a, b]); } }
    return d;
  }
  const angDiff = (a, b) => { let d = b - a; while (d > Math.PI) d -= 6.283; while (d < -Math.PI) d += 6.283; return d; };
  function fire(t) {
    if (t.cool > 0 || bul.filter((b) => b.t === t).length >= 4) return;
    t.cool = t.s.human ? 0.28 : 0.7; const bx = t.x + Math.cos(t.a) * (TR + 14), by = t.y + Math.sin(t.a) * (TR + 14);
    bul.push({ x: bx, y: by, vx: Math.cos(t.a) * BSPD, vy: Math.sin(t.a) * BSPD, t, n: 0, age: 0 });
    K.sfx.shoot(); K.burst(bx, by, '#fff', 4, 160, 0.2, 4);
  }
  function think(t, dt) {                                                       // CPU: [turn target angle, speed 0..1, fire?]
    const foes = tanks.filter((o) => o.alive && o !== t); if (!foes.length) return [t.a, 0, false];
    foes.sort((p, q) => Math.hypot(p.x - t.x, p.y - t.y) - Math.hypot(q.x - t.x, q.y - t.y));
    const f = foes[0], d = Math.hypot(f.x - t.x, f.y - t.y), seen = los(t, f);
    // a shot coming at me: sidestep
    let dodge = null;
    for (const b of bul) if (b.t !== t) { const rx = t.x - b.x, ry = t.y - b.y, along = (rx * b.vx + ry * b.vy) / BSPD, cross = Math.abs(rx * b.vy - ry * b.vx) / BSPD; if (along > 0 && along < 150 && cross < 34) { dodge = Math.atan2(b.vx, -b.vy) + (t.i % 2 ? Math.PI : 0); break; } }
    if (dodge != null && Math.random() < 0.9) return [dodge, 1, false];
    if (seen) {
      t.react += dt; if (!t.aimErr || Math.random() < dt * 2) t.aimErr = K.rnd(-0.1, 0.1);
      const want = Math.atan2(f.y - t.y, f.x - t.x) + t.aimErr;
      return [want, d > 380 ? 0.5 : 0, t.react > 0.55 && Math.abs(angDiff(t.a, want)) < 0.1];
    }
    t.react = 0; t.think -= dt;
    if (t.think <= 0 || !t.goal) { const dd = field(f), [c, r] = cellOf(t); let best = null, bv = dd[r][c]; for (const [dc, dr] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) { const a = c + dc, b = r + dr; if (!solidCell(a, b) && dd[b][a] < bv) { bv = dd[b][a]; best = [a, b]; } } t.goal = best; t.think = 0.25; }
    if (!t.goal) return [t.a, 0, false];
    const gx = OX + (t.goal[0] + 0.5) * CS, gy = OY + (t.goal[1] + 0.5) * CS;
    return [Math.atan2(gy - t.y, gx - t.x), 0.85, false];
  }
  function moveTank(t, dt) {
    t.cool -= dt; let want = t.a, spd = 0, shoot = false;
    if (t.s.human) { const p = K.pad(t.s.pad), m = Math.hypot(p.x, p.y); if (m > 0.3) { want = Math.atan2(p.y, p.x); spd = Math.min(1, m); } shoot = p.pulse || p.act; }
    else { const r = think(t, dt); want = r[0]; spd = r[1]; shoot = r[2]; }
    if (spd > 0 || !t.s.human) { const d = angDiff(t.a, want), st = 8.5 * dt; t.a += Math.abs(d) < st ? d : Math.sign(d) * st; spd *= Math.max(0, Math.cos(angDiff(t.a, want))) > 0.55 ? 1 : 0; }
    if (spd > 0) { t.x += Math.cos(t.a) * SPEED * spd * dt; t.y += Math.sin(t.a) * SPEED * spd * dt; }
    if (shoot) fire(t);
    // out of walls and the field
    const c0 = Math.floor((t.x - OX) / CS) - 1, r0 = Math.floor((t.y - OY) / CS) - 1;
    for (let r = r0; r <= r0 + 2; r++) for (let c = c0; c <= c0 + 2; c++) if (solidCell(c, r)) {
      const x0 = OX + c * CS, y0 = OY + r * CS, nx = K.clamp(t.x, x0, x0 + CS), ny = K.clamp(t.y, y0, y0 + CS), dx = t.x - nx, dy = t.y - ny, d = Math.hypot(dx, dy);
      if (d < TR && d > 0.001) { t.x = nx + dx / d * TR; t.y = ny + dy / d * TR; } else if (d <= 0.001) { t.x += 3; }
    }
    t.x = K.clamp(t.x, OX + TR, OX + COLS * CS - TR); t.y = K.clamp(t.y, OY + TR, OY + ROWS * CS - TR);
  }
  function kill(t, by) {
    t.alive = false; K.sfx.boom(); K.shake(16); K.burst(t.x, t.y, t.s.color, 40, 420, 0.9, 8); K.burst(t.x, t.y, '#fff', 14, 300, 0.5, 6); K.burst(t.x, t.y, '#fbbf24', 18, 240, 0.7, 7);
    t.ring = 0.0;
  }
  function moveBullets(dt) {
    for (let i = bul.length - 1; i >= 0; i--) {
      const b = bul[i]; b.age += dt; let dead = b.age > 6;
      const n = 2, h = dt / n;
      for (let s = 0; s < n && !dead; s++) {
        let nx = b.x + b.vx * h, ny = b.y + b.vy * h, hit = false;
        if (solidAt(nx, b.y)) { b.vx = -b.vx; nx = b.x; hit = true; }
        if (solidAt(nx, ny)) { b.vy = -b.vy; ny = b.y; hit = true; }
        b.x = nx; b.y = ny;
        if (hit) { b.n++; K.sfx.bounce(); K.burst(b.x, b.y, '#fff', 3, 120, 0.2, 3); if (b.n > 2) { dead = true; break; } }
        for (const t of tanks) if (t.alive && (t !== b.t || b.age > 0.12) && Math.hypot(t.x - b.x, t.y - b.y) < TR) { kill(t, b.t); dead = true; break; }
      }
      if (dead) bul.splice(i, 1);
    }
  }
  G = {
    id: 'tanks', title: { vi: 'Đại chiến xe tăng', en: 'Tank Arena' }, tag: { vi: 'Đạn nảy tường 2 lần — còn sống cuối cùng thắng ván, 4 ván thắng trận', en: 'Shots bounce twice — last tank standing takes the round, first to 4' },
    help: { vi: ['Cần analog / D-pad: xoay và lái xe', 'Nút bắn: bắn (tối đa 4 viên cùng lúc)'], en: ['Stick / D-pad: turn and drive', 'Action button: fire (up to 4 shots at once)'] },
    total: [2, 4], buttons: ['A'],
    start() { round = 0; over = false; newRound(); },
    update(dt) {
      K.updateParts(dt);
      if (over) { moveBullets(dt); return; }
      if (phase === 'ready') { ptimer -= dt; if (ptimer <= 0) { phase = 'play'; K.sfx.go(); } return; }
      for (const t of tanks) if (t.alive) moveTank(t, dt); else if (t.ring != null) t.ring += dt;
      // tanks push each other apart
      for (let i = 0; i < tanks.length; i++) for (let j = i + 1; j < tanks.length; j++) { const a = tanks[i], b = tanks[j]; if (!a.alive || !b.alive) continue; const dx = b.x - a.x, dy = b.y - a.y, d = Math.hypot(dx, dy); if (d < TR * 2 && d > 0.01) { const p = (TR * 2 - d) / 2; a.x -= dx / d * p; a.y -= dy / d * p; b.x += dx / d * p; b.y += dy / d * p; } }
      moveBullets(dt);
      if (phase === 'play') {
        const alive = tanks.filter((t) => t.alive);
        if (alive.length <= 1) { phase = 'end'; ptimer = 2.1; roundWin = alive[0] ? alive[0].i : -1; if (alive[0]) { K.slots[roundWin].score++; K.sfx.ok(); } }
      } else if (phase === 'end') {
        ptimer -= dt;
        if (ptimer <= 0) {
          const top = K.slots.reduce((m, s) => (s.score > m.score ? s : m), K.slots[0]);
          if (top.score >= WIN) { over = true; K.over(top.i, K.slots.map((s) => s.name + ': ' + s.score)); } else newRound();
        }
      }
    },
    draw(c) {
      K.shakeApply(c); c.drawImage(wallCv, 0, 0);
      for (const b of bul) { K.light(c, b.x, b.y, b.t.s.color, 26, 0.9); c.fillStyle = '#fff'; c.beginPath(); c.arc(b.x, b.y, 4.5, 0, 6.283); c.fill(); }
      for (const t of tanks) {
        if (!t.alive) { if (t.ring != null && t.ring < 0.5) { c.strokeStyle = t.s.color; c.globalAlpha = 1 - t.ring * 2; c.lineWidth = 6; c.beginPath(); c.arc(t.x, t.y, 20 + t.ring * 140, 0, 6.283); c.stroke(); c.globalAlpha = 1; } continue; }
        c.save(); c.translate(t.x, t.y); c.rotate(t.a); c.scale(1.2, 1.2);
        c.fillStyle = 'rgba(0,0,0,.35)'; K.rr(c, -19, -15, 44, 36, 8); c.fill();
        c.fillStyle = '#10172e'; K.rr(c, -22, -20, 44, 12, 4); c.fill(); K.rr(c, -22, 8, 44, 12, 4); c.fill();       // tracks
        c.fillStyle = t.s.color; K.rr(c, -19, -14, 38, 28, 8); c.fill();
        c.fillStyle = 'rgba(0,0,0,.25)'; K.rr(c, -19, -14, 38, 12, 8); c.fill();
        c.fillStyle = '#f1f5f9'; K.rr(c, 0, -4, 30, 8, 3); c.fill();                                                   // barrel
        c.fillStyle = t.s.color; c.beginPath(); c.arc(0, 0, 11, 0, 6.283); c.fill(); c.fillStyle = 'rgba(255,255,255,.45)'; c.beginPath(); c.arc(-2, -2, 5, 0, 6.283); c.fill();
        c.restore();
      }
      K.drawParts(c);
      K.hud(c, K.slots, (s) => (tanks[s.i] && !tanks[s.i].alive ? 'out' : ''));
      if (phase === 'ready') K.banner(c, (K.VI ? 'Ván ' : 'Round ') + round, null, '#fff');
      else if (phase === 'end' && !over) K.banner(c, roundWin >= 0 ? K.slots[roundWin].name : (K.VI ? 'Hòa' : 'Draw'), roundWin >= 0 ? (K.VI ? 'thắng ván này' : 'takes the round') : null, roundWin >= 0 ? K.slots[roundWin].color : '#fff');
    },
  };
  var G;
  K.run(G);
})();
