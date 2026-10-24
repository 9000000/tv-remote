'use strict';
// Light Cycles: 2-4 bikes leave a wall behind them; hit any wall and you are out (your trail vanishes). Last one riding takes the round,
// first to 3 rounds wins. Stick / D-pad steers, the button boosts (a bar that refills). CPUs fill the empty seats.
(function () {
  const K = window.Kit, N = 18, COLS = 64, ROWS = 32, OX = 64, OY = 112, WIN = 3, TICK = 0.082;
  const DX = [1, 0, -1, 0], DY = [0, 1, 0, -1];
  const STARTS = [[8, 16, 0], [55, 16, 2], [32, 6, 1], [32, 26, 3]];
  let grid, trail, tc, cyc, phase, ptimer, round, over, roundWin, acc, bg;

  const free = (x, y) => x >= 0 && y >= 0 && x < COLS && y < ROWS && !grid[y * COLS + x];
  const cx = (x) => OX + (x + 0.5) * N, cy = (y) => OY + (y + 0.5) * N;
  function paintBg() {
    bg = bg || Object.assign(document.createElement('canvas'), { width: 1280, height: 720 });
    const c = bg.getContext('2d'); c.clearRect(0, 0, 1280, 720);
    c.fillStyle = 'rgba(6,10,28,.92)'; K.rr(c, OX - 12, OY - 12, COLS * N + 24, ROWS * N + 24, 18); c.fill();
    c.strokeStyle = 'rgba(110,140,255,.10)'; c.lineWidth = 1; c.beginPath();
    for (let x = 0; x <= COLS; x += 2) { c.moveTo(OX + x * N + .5, OY); c.lineTo(OX + x * N + .5, OY + ROWS * N); }
    for (let y = 0; y <= ROWS; y += 2) { c.moveTo(OX, OY + y * N + .5); c.lineTo(OX + COLS * N, OY + y * N + .5); }
    c.stroke(); c.strokeStyle = '#4f6bd8'; c.lineWidth = 4; K.rr(c, OX - 6, OY - 6, COLS * N + 12, ROWS * N + 12, 12); c.stroke();
  }
  function segment(c, col, x0, y0, x1, y1) {                                   // one step of a wall: soft glow, colour, bright core
    const ax = Math.min(x0, x1), ay = Math.min(y0, y1), w = Math.abs(x1 - x0), h = Math.abs(y1 - y0), horiz = h === 0;
    c.globalAlpha = 0.2; c.fillStyle = col; c.fillRect(horiz ? ax : ax - 9, horiz ? ay - 9 : ay, horiz ? w : 18, horiz ? 18 : h);   // glow: no overlap along the run, so no beads
    c.globalAlpha = 1; c.fillRect(x1 - 4, y1 - 4, 8, 8);                                                    // the corner square under the body, so the core stays in one piece
    c.fillRect(horiz ? ax : ax - 4, horiz ? ay - 4 : ay, horiz ? w : 8, horiz ? 8 : h);
    c.fillStyle = '#f4faff'; c.fillRect(horiz ? ax : ax - 1.5, horiz ? ay - 1.5 : ay, horiz ? w : 3, horiz ? 3 : h); c.fillRect(x1 - 1.5, y1 - 1.5, 3, 3);
  }
  function repaintTrails() {
    tc.clearRect(0, 0, 1280, 720);
    for (const b of cyc) if (b.alive) for (let i = 1; i < b.path.length; i++) segment(tc, b.s.color, cx(b.path[i - 1][0]), cy(b.path[i - 1][1]), cx(b.path[i][0]), cy(b.path[i][1]));
  }
  function newRound() {
    grid = new Uint8Array(COLS * ROWS); round++; phase = 'ready'; ptimer = 1.6; acc = 0; K.sfx.tick();
    trail = trail || Object.assign(document.createElement('canvas'), { width: 1280, height: 720 }); tc = trail.getContext('2d'); tc.clearRect(0, 0, 1280, 720);
    cyc = K.slots.map((s, i) => {
      const [x, y, d] = STARTS[i]; grid[y * COLS + x] = i + 1;
      return { s, i, x, y, px: x, py: y, dir: d, alive: true, q: [], want: d, path: [[x, y]], energy: 1, boosting: false };
    });
  }
  function area(x, y, limit) {                                                // how much room is there from here? (bounded flood)
    const seen = new Set([y * COLS + x]), q = [[x, y]];
    for (let h = 0; h < q.length && q.length < limit; h++) for (let d = 0; d < 4; d++) { const a = q[h][0] + DX[d], b = q[h][1] + DY[d], k = b * COLS + a; if (free(a, b) && !seen.has(k)) { seen.add(k); q.push([a, b]); } }
    return q.length;
  }
  function chooseCpu(b) {
    let best = b.dir, bs = -1;
    for (const t of [0, 3, 1]) {                                              // straight, left, right
      const d = (b.dir + t) % 4, nx = b.x + DX[d], ny = b.y + DY[d]; if (!free(nx, ny)) continue;
      let clash = false; for (const o of cyc) if (o !== b && o.alive && Math.abs(o.x + DX[o.dir] - nx) + Math.abs(o.y + DY[o.dir] - ny) === 0) clash = true;
      if (clash) continue;
      let run = 0; while (run < 14 && free(nx + DX[d] * (run + 1), ny + DY[d] * (run + 1))) run++;
      const sc = Math.min(run, 12) + area(nx, ny, 140) * 0.22 + (t === 0 ? 2.5 : 0) + Math.random() * 1.5;
      if (sc > bs) { bs = sc; best = d; }
    }
    return best;
  }
  function steer(b, dt) {
    if (b.s.human) {
      const p = K.pad(b.s.pad), m = Math.hypot(p.x, p.y);
      if (m > 0.55) { const d = Math.abs(p.x) > Math.abs(p.y) ? (p.x > 0 ? 0 : 2) : (p.y > 0 ? 1 : 3); if (d !== b.want) { b.want = d; if (d !== b.dir && d !== (b.dir + 2) % 4 && b.q.length < 2) b.q.push(d); } } else b.want = -1;
      b.boost = p.act;
    } else b.boost = false;
  }
  function pass(list) {                                                         // everybody moves one cell; two heads into one cell both crash
    const moves = [];
    for (const b of list) {
      if (!b.alive) continue;
      if (b.s.human) { while (b.q.length) { const d = b.q.shift(); if (d !== (b.dir + 2) % 4) { b.dir = d; break; } } } else b.dir = chooseCpu(b);
      moves.push([b, b.x + DX[b.dir], b.y + DY[b.dir]]);
    }
    const crash = new Set();
    for (const [b, x, y] of moves) { if (!free(x, y)) crash.add(b); for (const [o, ox, oy] of moves) if (o !== b && ox === x && oy === y) crash.add(b); }
    for (const [b, x, y] of moves) {
      if (crash.has(b)) continue;
      grid[y * COLS + x] = b.i + 1; segment(tc, b.s.color, cx(b.x), cy(b.y), cx(x), cy(y)); b.x = x; b.y = y; b.path.push([x, y]);
    }
    let any = false;
    for (const b of crash) { die(b); any = true; }
    if (any) repaintTrails();
  }
  function die(b) {
    b.alive = false; K.sfx.boom(); K.shake(12);
    K.burst(cx(b.x), cy(b.y), b.s.color, 30, 380, 0.8, 8); K.burst(cx(b.x), cy(b.y), '#fff', 10, 260, 0.5, 6);
    for (let i = 0; i < b.path.length; i += 4) K.burst(cx(b.path[i][0]), cy(b.path[i][1]), b.s.color, 2, 160, 0.7, 5);
    for (let i = 0; i < grid.length; i++) if (grid[i] === b.i + 1) grid[i] = 0;
  }
  G = {
    id: 'cycles', title: { vi: 'Đua xe ánh sáng', en: 'Light Cycles' }, tag: { vi: 'Đừng đâm vào vệt sáng — còn chạy cuối cùng thắng ván, 3 ván thắng trận', en: 'Don\'t hit a wall — last bike riding takes the round, first to 3' },
    help: { vi: ['Cần analog / D-pad: rẽ trái, phải, lên, xuống', 'Nút bắn: tăng tốc (thanh năng lượng tự đầy lại)'], en: ['Stick / D-pad: turn', 'Action button: boost (the bar refills by itself)'] },
    total: [2, 4], buttons: ['A'],
    start() { round = 0; over = false; newRound(); paintBg(); },
    update(dt) {
      K.updateParts(dt);
      if (over) return;
      if (phase === 'ready') { cyc.forEach((b) => steer(b, dt)); ptimer -= dt; if (ptimer <= 0) { phase = 'play'; K.sfx.go(); } return; }
      cyc.forEach((b) => { if (b.alive) steer(b, dt); });
      if (phase === 'play') {
        for (const b of cyc) {                                                // boost bar
          if (!b.alive) continue;
          if (b.boost && b.energy > (b.boosting ? 0 : 0.2)) { if (!b.boosting) K.sfx.dash(); b.boosting = true; b.energy = Math.max(0, b.energy - dt * 0.8); } else { b.boosting = false; b.energy = Math.min(1, b.energy + dt * 0.3); }
        }
        const fast = cyc.some((b) => b.alive && b.boosting);
        acc += dt;
        if (acc >= TICK) {
          acc -= TICK; cyc.forEach((b) => { b.px = b.x; b.py = b.y; });
          pass(cyc); if (fast) pass(cyc.filter((b) => b.boosting));
        }
        const alive = cyc.filter((b) => b.alive);
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
      K.shakeApply(c); c.drawImage(bg, 0, 0); c.drawImage(trail, 0, 0);
      const f = phase === 'play' ? Math.min(1, acc / TICK) : 1;
      for (const b of cyc) {
        if (!b.alive) continue;
        const x = cx(b.px) + (cx(b.x) - cx(b.px)) * f, y = cy(b.py) + (cy(b.y) - cy(b.py)) * f;
        K.light(c, x, y, b.s.color, b.boosting ? 70 : 46, 0.9);
        c.fillStyle = '#fff'; c.beginPath(); c.arc(x, y, b.boosting ? 8 : 6.5, 0, 6.283); c.fill();
        if (b.energy < 1) { c.fillStyle = 'rgba(0,0,0,.5)'; c.fillRect(x - 15, y - 20, 30, 5); c.fillStyle = b.s.color; c.fillRect(x - 15, y - 20, 30 * b.energy, 5); }
      }
      K.drawParts(c);
      K.hud(c, K.slots, (s) => (cyc[s.i] && !cyc[s.i].alive ? 'out' : ''));
      if (phase === 'ready') { K.banner(c, (K.VI ? 'Ván ' : 'Round ') + round, null, '#fff'); for (const b of cyc) K.nameTag(c, b.s, cx(b.x), cy(b.y) + (b.y < ROWS / 2 ? 40 : -40)); }
      else if (phase === 'end' && !over) K.banner(c, roundWin >= 0 ? K.slots[roundWin].name : (K.VI ? 'Hòa' : 'Draw'), roundWin >= 0 ? (K.VI ? 'thắng ván này' : 'takes the round') : null, roundWin >= 0 ? K.slots[roundWin].color : '#fff');
    },
  };
  var G;
  K.run(G);
})();
