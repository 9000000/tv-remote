'use strict';
// Balloon Pop: 45 seconds, balloons float up from below, aim with the phone's touchpad and Fire to pop them. Small = more points, gold =
// 5 and quick, a black bomb costs 3. Every pop in a row (no miss) builds a streak bonus. Alone you play against CPU aimers.
(function () {
  const K = window.Kit, Ptr = window.Ptr, W = 1280, H = 720, TIME = 45, MAXB = 16;
  const COL = ['#ff5d8f', '#38bdf8', '#4ade80', '#fbbf24', '#a78bfa', '#fb923c'];
  let B = [], pops = [], left = 0, spawnT = 0, over = false, bg = null, shot = [];
  const txt = [];                                                         // floating "+3" texts

  function makeBg() {
    const c = document.createElement('canvas'); c.width = W; c.height = H; const g = c.getContext('2d');
    const gr = g.createLinearGradient(0, 0, 0, H); gr.addColorStop(0, '#0a1030'); gr.addColorStop(0.6, '#1c1650'); gr.addColorStop(1, '#3a1a5e');
    g.fillStyle = gr; g.fillRect(0, 0, W, H);
    for (let i = 0; i < 90; i++) { g.globalAlpha = K.rnd(0.2, 0.8); g.fillStyle = '#fff'; g.fillRect(K.rnd(0, W), K.rnd(0, H * 0.7), 2, 2); }
    g.globalAlpha = 1;
    g.fillStyle = '#0b0a26'; g.beginPath(); g.moveTo(0, H); for (let x = 0; x <= W; x += 40) g.lineTo(x, H - 50 - Math.abs(Math.sin(x * 0.011) * 40) - (x % 160 < 40 ? 24 : 0)); g.lineTo(W, H); g.fill();   // a city's edge
    return c;
  }
  const cache = {};
  function sprite(b) {
    const key = b.kind + b.col + Math.round(b.r), hit = cache[key]; if (hit) return hit;
    const r = Math.round(b.r), pad = b.kind === 'gold' ? Math.round(r * 1.2) : 4, w = (r + pad) * 2, h = w;
    const cv = document.createElement('canvas'); cv.width = w; cv.height = h; const g = cv.getContext('2d'), x = w / 2, y = h / 2;
    if (b.kind === 'gold') K.light(g, x, y, '#fde047', r * 2.2, 0.5);
    g.fillStyle = b.col; g.beginPath(); g.ellipse(x, y, r * 0.86, r, 0, 0, 6.283); g.fill();
    g.fillStyle = 'rgba(255,255,255,.4)'; g.beginPath(); g.ellipse(x - r * 0.3, y - r * 0.35, r * 0.2, r * 0.32, -0.5, 0, 6.283); g.fill();
    if (b.kind === 'bomb') { g.strokeStyle = '#fb7185'; g.lineWidth = 4; g.beginPath(); g.ellipse(x, y, r * 0.86, r, 0, 0, 6.283); g.stroke(); }                 // a red rim: a dark bomb vanishes on the night sky
    if (b.kind === 'bomb') K.text(g, '\u2620', x, y + 2, r * 0.9, '#f8fafc'); else if (b.kind === 'gold') K.text(g, '\u2605', x, y + 2, r * 0.9, '#a16207');
    else { g.fillStyle = b.col; g.beginPath(); g.moveTo(x - 6, y + r + 6); g.lineTo(x + 6, y + r + 6); g.lineTo(x, y + r - 2); g.fill(); }
    return (cache[key] = cv);
  }
  function spawn() {
    if (B.length >= MAXB) return;
    const r = Math.random(), kind = r < 0.1 ? 'gold' : r < 0.2 ? 'bomb' : 'n';
    const rad = kind === 'gold' ? 30 : kind === 'bomb' ? 40 : Math.round(K.rnd(34, 62));
    B.push({ x: K.rnd(90, W - 90), y: H + rad + 10, r: rad, kind, col: kind === 'gold' ? '#fde047' : kind === 'bomb' ? '#1e293b' : K.pick(COL),
      vy: (kind === 'gold' ? -190 : kind === 'bomb' ? -K.rnd(160, 220) : -K.rnd(80, 150)) * (1 + (TIME - left) / TIME * 0.5), ph: K.rnd(0, 6.28), sw: K.rnd(10, 36) });
  }
  const pts = (b) => (b.kind === 'gold' ? 5 : b.kind === 'bomb' ? -3 : b.r < 42 ? 3 : b.r < 52 ? 2 : 1);
  function shoot(si, x, y) {                                               // one shot of seat si at (x, y): the topmost balloon under it
    const s = K.slots[si]; if (!s || over) return;
    let hit = null;
    for (let i = B.length - 1; i >= 0; i--) { const b = B[i]; if (Math.hypot(b.x - x, b.y - y) <= b.r + 6) { hit = i; break; } }
    if (hit == null) { s.streak = 0; K.burst(x, y, '#ffffff', 5, 90, 0.3, 4); K.tone(200, 0.05, 'square', 0.04, 0.7); return; }
    const b = B.splice(hit, 1)[0]; let p = pts(b);
    if (p > 0) { s.streak = (s.streak || 0) + 1; if (s.streak % 3 === 0) p += 1; K.sfx.hit(Math.min(1, p / 5)); K.tone(700 + p * 90, 0.08, 'triangle', 0.08); }
    else { s.streak = 0; K.sfx.boom(); K.shake(10); try { window.tvpad && s.human && window.tvpad.vibrate(s.pad, 120); } catch (e) { } }
    s.score = Math.max(0, s.score + p);
    K.burst(b.x, b.y, b.kind === 'bomb' ? '#fb7185' : b.col, 18, 260, 0.6, 7);
    txt.push({ x: b.x, y: b.y, t: 0.8, s: (p > 0 ? '+' : '') + p, c: s.color });
    if (p > 0 && s.human) { try { window.tvpad && window.tvpad.vibrate(s.pad, 20); } catch (e) { } }
  }
  function ai(s, dt) {                                                      // a CPU aimer: picks the nearest good balloon, glides to it, fires when close (it misses sometimes)
    const p = Ptr.get(100 + s.i); s.pp = p;
    if (!s.tg || !B.includes(s.tg) || s.tg.kind === 'bomb') s.tg = B.filter((b) => b.kind !== 'bomb' && b.y < H - 30).sort((a, b) => Math.hypot(a.x - p.x, a.y - p.y) - Math.hypot(b.x - p.x, b.y - p.y))[0] || null;
    s.wait = (s.wait || 0) - dt;
    if (s.tg) {
      const sp = 520 + s.i * 10; Ptr.steer(p, s.tg.x + K.rnd(-8, 8), s.tg.y + K.rnd(-8, 8), dt, sp);
      if (Math.hypot(s.tg.x - p.x, s.tg.y - p.y) < s.tg.r * 0.8 && s.wait <= 0) { s.wait = K.rnd(0.45, 0.9); shoot(s.i, p.x + (Math.random() < 0.25 ? K.rnd(-70, 70) : 0), p.y); s.tg = null; }
    }
  }
  Ptr.onFire((n) => { const si = K.slots.findIndex((s) => s.human && s.pad === n); if (si >= 0) { const p = Ptr.get(n); shoot(si, p.x, p.y); } });
  Ptr.use({ buttons: ['A'] });

  const G = {
    id: 'balloons', title: { vi: 'Bắn bóng bay', en: 'Balloon Pop' },
    tag: { vi: 'Ngắm bằng touchpad trên điện thoại, bấm Bắn để nổ bóng — 45 giây', en: 'Aim with your phone\'s touchpad, tap Fire to pop — 45 seconds' },
    help: { vi: ['Bóng nhỏ nhiều điểm, bóng vàng 5 điểm, bom đen trừ 3', 'Nổ liên tiếp không trượt sẽ có điểm thưởng'], en: ['Small balloons score more, gold = 5, a black bomb costs 3', 'Pops in a row without a miss add a bonus'] },
    total: [1, 4], buttons: ['A'],
    start(slots) { B = []; txt.length = 0; left = TIME; spawnT = 0; over = false; bg = bg || makeBg(); slots.forEach((s) => { s.streak = 0; s.tg = null; if (s.human) Ptr.get(s.pad); }); Ptr.reset(); for (let i = 0; i < 6; i++) { spawn(); B[B.length - 1].y = K.rnd(150, H); } },
    update(dt) {
      if (K.mode === 'over') { Ptr.update(dt); return; }
      Ptr.update(dt); left -= dt; spawnT -= dt; K.timeTick(left);
      if (spawnT <= 0) { spawn(); spawnT = K.rnd(0.22, 0.5) * (1 - (TIME - left) / TIME * 0.35); }
      for (let i = B.length - 1; i >= 0; i--) { const b = B[i]; b.y += b.vy * dt; b.x += Math.sin(K.t * 1.6 + b.ph) * b.sw * dt; if (b.y < -b.r - 40) B.splice(i, 1); }
      K.slots.forEach((s) => { if (!s.human) ai(s, dt); });
      for (let i = txt.length - 1; i >= 0; i--) { txt[i].t -= dt; txt[i].y -= 60 * dt; if (txt[i].t <= 0) txt.splice(i, 1); }
      if (left <= 0 && !over) {
        over = true; left = 0; const top = Math.max(...K.slots.map((s) => s.score)), ws = K.slots.filter((s) => s.score === top);
        K.over(ws.length === 1 ? ws[0].i : -1, K.slots.map((s) => s.name + ': ' + s.score));
      }
    },
    draw(c) {
      K.shakeApply(c); c.drawImage(bg || (bg = makeBg()), 0, 0);
      c.strokeStyle = 'rgba(255,255,255,.35)'; c.lineWidth = 2; c.beginPath();                    // every string in one stroke
      for (const b of B) { c.moveTo(b.x, b.y + b.r); c.quadraticCurveTo(b.x + Math.sin(K.t * 3 + b.ph) * 8, b.y + b.r + 30, b.x, b.y + b.r + 56); }
      c.stroke();
      for (const b of B) { const sp = sprite(b); c.drawImage(sp, b.x - sp.width / 2, b.y - sp.height / 2); }   // bodies are drawn once, then copied: the TV box's GPU is slow at gradients and paths
      K.drawParts(c);
      for (const t of txt) { c.globalAlpha = Math.min(1, t.t * 2); K.text(c, t.s, t.x, t.y, 44, t.c, 'center', 900); c.globalAlpha = 1; }
      K.slots.forEach((s) => { if (!s.human && s.pp) { const p = s.pp; c.globalAlpha = 0.55; c.strokeStyle = s.color; c.lineWidth = 4; c.beginPath(); c.arc(p.x, p.y, 20, 0, 6.283); c.stroke(); c.globalAlpha = 1; } });
      Ptr.draw(c, K.slots);
      K.hud(c, K.slots);
      K.text(c, Math.ceil(left) + 's', W / 2, 96, 40, left < 10 ? '#fb7185' : '#e2e8f0', 'center', 900);
    },
  };
  K.run(G);
})();
