'use strict';
// Air Hockey: two mallets, one puck, first to 7. The phone's touchpad drags your mallet (Ptr: it chases the aim point, as a hand
// would); a pad's stick or D-pad still moves it; alone you play the CPU.
(function () {
  const K = window.Kit, R = { l: 100, r: 1180, t: 110, b: 680 }, MID = 640, GY = 395, GH = 100, MR = 46, PR = 24, WIN = 7, MAXV = 700;
  let puck, mal, phase, ptimer, last = -1, trail = [], flash = 0, over = false, side0 = 0;

  const mk = (i) => ({ x: i ? R.r - 170 : R.l + 170, y: GY, vx: 0, vy: 0, px: 0, py: 0, tx: 0, ty: 0 });
  const aim = (i) => Ptr.get(K.slots[i].pad);                                 // where the touchpad wants slot i's mallet
  function serve(to) {
    puck = { x: MID, y: GY, vx: 0, vy: 0 }; phase = 'serve'; ptimer = 1.1; trail = []; side0 = to;
    mal = [mk(0), mk(1)];
    for (let i = 0; i < 2; i++) if (K.slots[i].human) { const a = aim(i); a.x = a.px = mal[i].x; a.y = a.py = mal[i].y; }   // each serve: the aim back on the mallet
  }
  function ai(i, m, dt) {                                                       // the CPU's mallet: defend, then drive the puck at the goal
    const dir = i ? -1 : 1, mine = i ? puck.x > MID : puck.x < MID, home = i ? R.r - 150 : R.l + 150;
    let tx = home, ty = K.clamp(puck.y * 0.6 + GY * 0.4, GY - GH + 30, GY + GH - 30), spd = 0.8;
    const behind = (puck.x - m.x) * dir < 0;                                    // the puck is between my mallet and my own goal
    if (mine || (puck.vx * dir < -100 && Math.abs(puck.x - MID) < 260)) {
      if (behind) { tx = puck.x - dir * 90; ty = puck.y + (puck.y < GY ? 80 : -80); spd = 0.95; }
      else { const ax = (i ? R.l : R.r) , ay = GY + (puck.y - GY) * 0.4, dx = ax - puck.x, dy = ay - puck.y, d = Math.hypot(dx, dy) || 1; tx = puck.x - (dx / d) * 14; ty = puck.y - (dy / d) * 14; spd = 1; }
    }
    const dx = tx - m.x, dy = ty - m.y, d = Math.hypot(dx, dy);
    const v = Math.min(MAXV * 0.82 * spd, d * 9);
    return d > 3 ? [dx / d * v / MAXV, dy / d * v / MAXV] : [0, 0];
  }
  function moveMallet(i, m, dt) {
    const s = K.slots[i]; let ix = 0, iy = 0, a = null, top = MAXV;
    if (s.human) {
      const p = K.pad(s.pad); ix = p.x; iy = p.y;
      if (Math.hypot(ix, iy) < 0.15) {                                          // no stick: chase the touchpad's aim, faster than a stick (a flick is a shot)
        a = aim(i); top = MAXV * 1.5;
        const dx = a.x - m.x, dy = a.y - m.y, d = Math.hypot(dx, dy), v = Math.min(top, d * 14);
        [ix, iy] = d > 2 ? [dx / d * v / MAXV, dy / d * v / MAXV] : [0, 0];
      }
    } else { [ix, iy] = ai(i, m, dt); }
    const f = Math.min(1, dt * (a ? 22 : 16));
    m.vx += (ix * MAXV - m.vx) * f; m.vy += (iy * MAXV - m.vy) * f;
    m.px = m.x; m.py = m.y;
    m.x += m.vx * dt; m.y += m.vy * dt;
    const lo = i ? MID + MR : R.l + MR, hi = i ? R.r - MR : MID - MR;
    m.x = K.clamp(m.x, lo, hi); m.y = K.clamp(m.y, R.t + MR, R.b - MR);
    if (s.human) { const p = aim(i); if (a) { p.x = K.clamp(p.x, lo, hi); p.y = K.clamp(p.y, R.t + MR, R.b - MR); } else { p.x = m.x; p.y = m.y; } }   // the aim stays in my half (a swipe past the line is not banked), and follows a stick
    m.rvx = (m.x - m.px) / dt; m.rvy = (m.y - m.py) / dt;
  }
  function goal(i) {                                                            // i scored
    K.slots[i].score++; K.sfx.goal(); K.shake(14); flash = 1;
    const gx = i ? R.l : R.r; K.burst(gx, puck.y, K.slots[i].color, 60, 520, 0.9, 7);
    if (K.slots[i].score >= WIN) { over = true; K.over(i, [K.slots[0].score + '  :  ' + K.slots[1].score]); return; }
    phase = 'goal'; ptimer = 1.4; last = i;
  }
  function puckStep(dt) {
    const sp = Math.hypot(puck.vx, puck.vy), n = Math.max(1, Math.ceil(sp * dt / 14)), h = dt / n;
    for (let s = 0; s < n; s++) {
      puck.x += puck.vx * h; puck.y += puck.vy * h;
      if (puck.y - PR < R.t) { puck.y = R.t + PR; puck.vy = Math.abs(puck.vy) * 0.96; K.sfx.bounce(); }
      if (puck.y + PR > R.b) { puck.y = R.b - PR; puck.vy = -Math.abs(puck.vy) * 0.96; K.sfx.bounce(); }
      const gap = puck.y > GY - GH + 10 && puck.y < GY + GH - 10;
      if (puck.x - PR < R.l && !gap) { puck.x = R.l + PR; puck.vx = Math.abs(puck.vx) * 0.96; K.sfx.bounce(); }
      if (puck.x + PR > R.r && !gap) { puck.x = R.r - PR; puck.vx = -Math.abs(puck.vx) * 0.96; K.sfx.bounce(); }
      if (puck.x < R.l - PR * 1.4) return goal(1);
      if (puck.x > R.r + PR * 1.4) return goal(0);
      for (let i = 0; i < 2; i++) {
        const m = mal[i], dx = puck.x - m.x, dy = puck.y - m.y, d = Math.hypot(dx, dy);
        if (d < MR + PR && d > 0.01) {
          const nx = dx / d, ny = dy / d; puck.x = m.x + nx * (MR + PR); puck.y = m.y + ny * (MR + PR);
          const rel = (puck.vx - m.rvx) * nx + (puck.vy - m.rvy) * ny;
          if (rel < 0) {
            puck.vx -= 1.9 * rel * nx; puck.vy -= 1.9 * rel * ny;
            const v = Math.hypot(puck.vx, puck.vy); if (v > 1500) { puck.vx *= 1500 / v; puck.vy *= 1500 / v; }
            const q = Math.min(1, v / 1200); K.sfx.hit(q); K.burst(puck.x - nx * PR, puck.y - ny * PR, K.slots[i].color, 6 + (q * 10) | 0, 260, 0.35, 5); if (q > 0.6) K.shake(5);
          }
        }
      }
    }
  }
  G = {
    id: 'hockey', title: { vi: 'Air Hockey', en: 'Air Hockey' }, tag: { vi: 'Đẩy puck vào khung thành đối thủ — ai ghi 7 bàn trước thắng', en: 'Slam the puck into their goal — first to 7 wins' },
    help: { vi: ['Rê ngón tay trên bàn di của điện thoại để kéo cây vợt', 'Tay cầm thật: cần analog / D-pad', 'Chơi một mình sẽ đấu với máy'], en: ['Drag on the phone\'s touchpad to move your mallet', 'A real pad: stick / D-pad', 'On your own you play the CPU'] },
    total: [2, 2], buttons: [],
    start() { over = false; last = -1; serve(Math.random() < 0.5 ? 0 : 1); },
    update(dt) {
      K.updateParts(dt); flash *= 0.92; Ptr.update(dt);
      if (over) return;
      for (let i = 0; i < 2; i++) moveMallet(i, mal[i], dt);
      if (phase === 'serve') { ptimer -= dt; if (ptimer <= 0) { phase = 'play'; puck.vx = (side0 ? -1 : 1) * 260; puck.vy = K.rnd(-120, 120); K.sfx.tick(); } return; }
      if (phase === 'goal') { ptimer -= dt; if (ptimer <= 0) serve(last ? 0 : 1); return; }
      puck.vx *= 1 - 0.22 * dt; puck.vy *= 1 - 0.22 * dt;
      puckStep(dt);
      trail.push(puck.x, puck.y); if (trail.length > 16) trail.splice(0, 2);
    },
    draw(c) {
      const [a, b] = K.slots;
      K.shakeApply(c);
      // rink
      c.fillStyle = 'rgba(9,16,40,.88)'; K.rr(c, R.l - 6, R.t - 6, R.r - R.l + 12, R.b - R.t + 12, 40); c.fill();
      c.lineWidth = 6;
      for (let i = 0; i < 2; i++) {                                              // each half's edge in its player's colour, open at the goal
        c.strokeStyle = (i ? b : a).color; c.globalAlpha = 0.9; c.beginPath();
        const x0 = i ? R.r : R.l, dx = i ? -1 : 1, mid = i ? MID : MID;
        c.moveTo(mid, R.t); c.lineTo(x0 - dx * 40, R.t); c.arcTo(x0, R.t, x0, R.t + 40, 40); c.lineTo(x0, GY - GH);
        c.moveTo(x0, GY + GH); c.lineTo(x0, R.b - 40); c.arcTo(x0, R.b, x0 - dx * 40, R.b, 40); c.lineTo(mid, R.b); c.stroke();
        c.globalAlpha = 1;
        // goal mouth
        c.fillStyle = (i ? b : a).color; c.globalAlpha = 0.25 + flash * 0.4 * (last === (i ? 1 : 0) ? 0 : 0) + 0.12; c.fillRect(i ? R.r : R.l - 28, GY - GH, 28, GH * 2); c.globalAlpha = 1;
        K.light(c, x0, GY, (i ? b : a).color, 150, 0.35);
      }
      c.strokeStyle = 'rgba(160,190,255,.35)'; c.lineWidth = 4; c.setLineDash([16, 14]); c.beginPath(); c.moveTo(MID, R.t); c.lineTo(MID, R.b); c.stroke(); c.setLineDash([]);
      c.beginPath(); c.arc(MID, GY, 90, 0, 6.283); c.stroke();
      // puck trail + puck
      for (let i = 0; i < trail.length; i += 2) { c.globalAlpha = i / trail.length * 0.35; c.fillStyle = '#fff'; c.beginPath(); c.arc(trail[i], trail[i + 1], PR * (0.4 + i / trail.length * 0.5), 0, 6.283); c.fill(); }
      c.globalAlpha = 1;
      K.light(c, puck.x, puck.y, '#ffffff', 60, 0.4);
      c.fillStyle = '#f8fafc'; c.beginPath(); c.arc(puck.x, puck.y, PR, 0, 6.283); c.fill();
      c.fillStyle = '#334155'; c.beginPath(); c.arc(puck.x, puck.y, PR * 0.5, 0, 6.283); c.fill();
      // mallets
      for (let i = 0; i < 2; i++) {
        const m = mal[i], col = K.slots[i].color;
        K.light(c, m.x, m.y, col, 90, 0.5);
        c.fillStyle = col; c.beginPath(); c.arc(m.x, m.y, MR, 0, 6.283); c.fill();
        c.fillStyle = 'rgba(0,0,0,.28)'; c.beginPath(); c.arc(m.x, m.y, MR * 0.7, 0, 6.283); c.fill();
        c.fillStyle = '#fff'; c.globalAlpha = 0.85; c.beginPath(); c.arc(m.x, m.y, MR * 0.38, 0, 6.283); c.fill(); c.globalAlpha = 1;
      }
      K.drawParts(c);
      if (flash > 0.05) { c.fillStyle = 'rgba(255,255,255,' + flash * 0.25 + ')'; c.fillRect(0, 0, 1280, 720); }
      K.hud(c, K.slots);
      if (phase === 'goal' && !over) K.text(c, K.VI ? 'BÀN THẮNG!' : 'GOAL!', MID, 395, 110, K.slots[last].color, 'center', 900);
      if (phase === 'serve' && !over) K.text(c, Math.ceil(ptimer) + '', MID, 395, 90, 'rgba(255,255,255,.55)');
    },
  };
  var G;
  Ptr.use({ buttons: [], side: (i) => (i ? 'right' : 'left'), aspect: (MID - R.l) / (R.b - R.t) });   // the touchpad while playing, on the side of your mallet and
                                                                       // shaped like your half; no Fire: the mallet itself hits
  K.run(G);
})();
