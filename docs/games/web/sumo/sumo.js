'use strict';
// Sumo Bump: 2-4 blobs on a round platform that keeps shrinking. Shove the others off the edge; last one standing takes the round,
// first to 3 rounds wins. Stick / D-pad moves, the button dashes (a heavy hit, then it recharges). CPUs fill the empty seats.
(function () {
  const K = window.Kit, CX = 640, CY = 402, R0 = 288, RMIN = 120, PR = 42, WIN = 3, ACC = 1150, DRAG = 2.3, DASHV = 760;
  let ps, phase, ptimer, round, over, roundWin, R, age;

  function newRound() {
    round++; phase = 'ready'; ptimer = 1.6; R = R0; age = 0; K.sfx.tick();
    const n = K.slots.length;
    ps = K.slots.map((s, i) => {
      const a = n === 2 ? (i ? 0 : Math.PI) : -Math.PI / 2 + i * 6.283 / n + (n === 3 ? 0 : Math.PI / 4);
      return { s, i, x: CX + Math.cos(a) * R0 * 0.55, y: CY + Math.sin(a) * R0 * 0.55, vx: 0, vy: 0, fx: -Math.cos(a), fy: -Math.sin(a), alive: true, fall: 0, cool: 0, dash: 0, trail: [], react: 0 };
    });
  }
  function cpu(p, dt) {                                                        // returns [ix, iy, dash?]
    const dc = Math.hypot(p.x - CX, p.y - CY);
    if (dc > R - 85) return [(CX - p.x) / dc, (CY - p.y) / dc, false];        // near the edge: back to the middle
    let foe = null, fd = 1e9;
    for (const o of ps) if (o !== p && o.alive && !o.fall) { const d = Math.hypot(o.x - p.x, o.y - p.y); if (d < fd) { fd = d; foe = o; } }
    if (!foe) return [(CX - p.x) / (dc || 1), (CY - p.y) / (dc || 1), false];
    const fc = Math.hypot(foe.x - CX, foe.y - CY) || 1;
    // go to the side of the foe that faces the middle, then push outwards
    const bx = foe.x + (CX - foe.x) / fc * 62, by = foe.y + (CY - foe.y) / fc * 62;
    let tx = fd < 130 ? foe.x : bx, ty = fd < 130 ? foe.y : by, dx = tx - p.x, dy = ty - p.y, d = Math.hypot(dx, dy) || 1;
    const sp = Math.hypot(p.vx, p.vy) || 1, align = (p.vx * (foe.x - p.x) + p.vy * (foe.y - p.y)) / (sp * fd);
    if (fd < 170 && align > 0.75 && p.cool <= 0) p.react += dt; else p.react = 0;
    return [dx / d * 0.92, dy / d * 0.92, p.react > 0.3];
  }
  function dash(p, ix, iy) {
    if (p.cool > 0) return;
    let dx = ix, dy = iy, m = Math.hypot(dx, dy); if (m < 0.2) { dx = p.fx; dy = p.fy; m = 1; }
    p.vx = p.vx * 0.3 + dx / m * DASHV; p.vy = p.vy * 0.3 + dy / m * DASHV; p.dash = 0.32; p.cool = 1.5; K.sfx.dash(); K.burst(p.x, p.y, p.s.color, 10, 260, 0.4, 6);
  }
  function collide(a, b) {
    const dx = b.x - a.x, dy = b.y - a.y, d = Math.hypot(dx, dy);
    if (d >= PR * 2 || d < 0.01) return;
    const nx = dx / d, ny = dy / d, ma = a.dash > 0 ? 2.6 : 1, mb = b.dash > 0 ? 2.6 : 1;
    const ov = PR * 2 - d; a.x -= nx * ov * mb / (ma + mb); a.y -= ny * ov * mb / (ma + mb); b.x += nx * ov * ma / (ma + mb); b.y += ny * ov * ma / (ma + mb);
    const rel = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny;
    if (rel < 0) {
      const j = -(1 + 0.95) * rel / (1 / ma + 1 / mb);
      a.vx -= j / ma * nx; a.vy -= j / ma * ny; b.vx += j / mb * nx; b.vy += j / mb * ny;
      const q = Math.min(1, -rel / 700); K.sfx.hit(q); K.burst((a.x + b.x) / 2, (a.y + b.y) / 2, '#fff', 5 + (q * 9) | 0, 240, 0.35, 5); if (q > 0.5) K.shake(5 + q * 6);
    }
  }
  G = {
    id: 'sumo', title: { vi: 'Sumo húc', en: 'Sumo Bump' }, tag: { vi: 'Húc đối thủ rơi khỏi sàn đang thu nhỏ — 3 ván thắng trận', en: 'Shove everyone off the shrinking floor — first to 3 rounds' },
    help: { vi: ['Cần analog / D-pad: di chuyển', 'Nút bắn: lao tới (cú húc nặng hơn, rồi phải chờ)'], en: ['Stick / D-pad: move', 'Action button: dash (a heavier hit, then it recharges)'] },
    total: [2, 4], buttons: ['A'],
    start() { round = 0; over = false; newRound(); },
    update(dt) {
      K.updateParts(dt);
      if (over) return;
      if (phase === 'ready') { ptimer -= dt; if (ptimer <= 0) { phase = 'play'; K.sfx.go(); } return; }
      age += dt; if (phase === 'play' && age > 5) R = Math.max(RMIN, R0 - (age - 5) * (age < 25 ? 8 : 5));
      for (const p of ps) {
        if (!p.alive) continue;
        if (p.fall) { p.fall += dt; p.x += p.vx * dt; p.y += p.vy * dt; if (p.fall > 0.9) p.alive = false; continue; }
        let ix = 0, iy = 0, wantDash = false;
        if (phase === 'end') { ix = iy = 0; }
        else if (p.s.human) { const q = K.pad(p.s.pad); ix = q.x; iy = q.y; wantDash = q.pulse; }
        else [ix, iy, wantDash] = cpu(p, dt);
        p.cool -= dt; p.dash -= dt;
        if (wantDash) dash(p, ix, iy);
        const m = Math.hypot(ix, iy); if (m > 0.2) { p.fx = ix / m; p.fy = iy / m; }
        p.vx += ix * ACC * dt; p.vy += iy * ACC * dt;
        const f = Math.max(0, 1 - DRAG * dt); p.vx *= f; p.vy *= f;
        p.x += p.vx * dt; p.y += p.vy * dt;
        if (p.dash > 0) { p.trail.push(p.x, p.y); if (p.trail.length > 12) p.trail.splice(0, 2); } else if (p.trail.length) p.trail.splice(0, 2);
      }
      for (let i = 0; i < ps.length; i++) for (let j = i + 1; j < ps.length; j++) if (ps[i].alive && ps[j].alive && !ps[i].fall && !ps[j].fall) collide(ps[i], ps[j]);
      for (const p of ps) if (p.alive && !p.fall && Math.hypot(p.x - CX, p.y - CY) > R + PR * 0.35) { p.fall = 0.001; K.sfx.fall(); K.burst(p.x, p.y, p.s.color, 16, 220, 0.6, 6); }
      if (phase === 'play') {
        const up = ps.filter((p) => p.alive && !p.fall);
        if (up.length <= 1) { phase = 'end'; ptimer = 2.2; roundWin = up[0] ? up[0].i : -1; if (up[0]) { K.slots[roundWin].score++; K.sfx.ok(); } }
      } else if (phase === 'end') {
        ptimer -= dt;
        if (ptimer <= 0) {
          const top = K.slots.reduce((m, s) => (s.score > m.score ? s : m), K.slots[0]);
          if (top.score >= WIN) { over = true; K.over(top.i, K.slots.map((s) => s.name + ': ' + s.score)); } else newRound();
        }
      }
    },
    draw(c) {
      K.shakeApply(c);
      // the floor
      const warn = phase === 'play' && R < R0 - 1;
      K.light(c, CX, CY + 10, '#3b5bdb', R + 120, 0.28);
      c.fillStyle = '#0b1230'; c.beginPath(); c.arc(CX, CY + 14, R + 6, 0, 6.283); c.fill();                 // the slab's side
      c.fillStyle = '#17224f'; c.beginPath(); c.arc(CX, CY, R, 0, 6.283); c.fill();
      c.strokeStyle = 'rgba(130,160,255,.14)'; c.lineWidth = 3;
      for (let k = 1; k <= 3; k++) { c.beginPath(); c.arc(CX, CY, R * k / 4, 0, 6.283); c.stroke(); }
      c.beginPath(); c.moveTo(CX - R, CY); c.lineTo(CX + R, CY); c.moveTo(CX, CY - R); c.lineTo(CX, CY + R); c.stroke();
      c.strokeStyle = warn ? '#fb7185' : '#6d8cff'; c.lineWidth = 7; c.beginPath(); c.arc(CX, CY, R, 0, 6.283); c.stroke();
      // the blobs
      for (const p of ps) {
        if (!p.alive) continue;
        const sc = p.fall ? Math.max(0, 1 - p.fall / 0.9) : 1, r = PR * sc;
        for (let i = 0; i < p.trail.length; i += 2) { c.globalAlpha = (i / p.trail.length) * 0.35; c.fillStyle = p.s.color; c.beginPath(); c.arc(p.trail[i], p.trail[i + 1], r * 0.9, 0, 6.283); c.fill(); }
        c.globalAlpha = sc;
        if (!p.fall) { c.fillStyle = 'rgba(0,0,0,.35)'; c.beginPath(); c.ellipse(p.x + 4, p.y + 10, r, r * 0.8, 0, 0, 6.283); c.fill(); }
        if (p.dash > 0) K.light(c, p.x, p.y, p.s.color, 90, 0.8);
        c.fillStyle = p.s.color; c.beginPath(); c.arc(p.x, p.y, r, 0, 6.283); c.fill();
        c.fillStyle = 'rgba(255,255,255,.3)'; c.beginPath(); c.arc(p.x - r * 0.25, p.y - r * 0.3, r * 0.55, 0, 6.283); c.fill();
        // eyes look where the blob is heading
        const ex = p.fx * r * 0.3, ey = p.fy * r * 0.3;
        for (const s of [-1, 1]) { const ox = -p.fy * s * r * 0.38, oy = p.fx * s * r * 0.38; c.fillStyle = '#fff'; c.beginPath(); c.arc(p.x + ox + ex, p.y + oy + ey, r * 0.24, 0, 6.283); c.fill(); c.fillStyle = '#0f172a'; c.beginPath(); c.arc(p.x + ox + ex * 1.6, p.y + oy + ey * 1.6, r * 0.12, 0, 6.283); c.fill(); }
        c.globalAlpha = 1;
        if (!p.fall && p.cool > 0) { c.strokeStyle = 'rgba(255,255,255,.55)'; c.lineWidth = 4; c.beginPath(); c.arc(p.x, p.y, PR + 8, -Math.PI / 2, -Math.PI / 2 + 6.283 * (1 - p.cool / 1.5)); c.stroke(); }
      }
      K.drawParts(c);
      K.hud(c, K.slots, (s) => (ps[s.i] && (!ps[s.i].alive || ps[s.i].fall) ? 'out' : ''));
      if (phase === 'ready') { K.banner(c, (K.VI ? 'Ván ' : 'Round ') + round, null, '#fff'); for (const p of ps) K.nameTag(c, p.s, p.x, p.y - PR - 30); }
      else if (phase === 'end' && !over) K.banner(c, roundWin >= 0 ? K.slots[roundWin].name : (K.VI ? 'Hòa' : 'Draw'), roundWin >= 0 ? (K.VI ? 'thắng ván này' : 'takes the round') : null, roundWin >= 0 ? K.slots[roundWin].color : '#fff');
    },
  };
  var G;
  K.run(G);
})();
