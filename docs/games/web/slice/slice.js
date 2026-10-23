'use strict';
// Fruit Slice: fruit is thrown up from below; swipe the crosshair across it (fast enough) to slice it. Several in one swipe = a combo bonus.
// A black bomb costs 5 points. 60 seconds. Alone you play against CPU slicers.
(function () {
  const K = window.Kit, Ptr = window.Ptr, W = 1280, H = 720, TIME = 60, G0 = 1000;
  const FRUIT = [{ n: 'apple', c: '#ef4444', in: '#fecaca', r: 46 }, { n: 'orange', c: '#fb923c', in: '#fed7aa', r: 48 }, { n: 'lime', c: '#84cc16', in: '#ecfccb', r: 42 },
    { n: 'melon', c: '#16a34a', in: '#fb7185', r: 62 }, { n: 'plum', c: '#a855f7', in: '#e9d5ff', r: 40 }, { n: 'lemon', c: '#facc15', in: '#fef9c3', r: 44 }];
  let F = [], halves = [], left = 0, spawnT = 0, over = false, bg = null, flash = 0;
  const trail = {};                                                       // per seat: recent crosshair points
  const txt = [];

  function makeBg() {
    const c = document.createElement('canvas'); c.width = W; c.height = H; const g = c.getContext('2d');
    const gr = g.createLinearGradient(0, 0, W, H); gr.addColorStop(0, '#1b1038'); gr.addColorStop(1, '#0c2a3a'); g.fillStyle = gr; g.fillRect(0, 0, W, H);
    g.strokeStyle = 'rgba(255,255,255,.05)'; g.lineWidth = 2; for (let i = -H; i < W; i += 70) { g.beginPath(); g.moveTo(i, H); g.lineTo(i + H, 0); g.stroke(); }
    return c;
  }
  function throwOne(bomb) {
    const f = bomb ? { n: 'bomb', c: '#0f172a', in: '#0f172a', r: 44 } : K.pick(FRUIT);
    const x = K.rnd(220, W - 220), tx = W / 2 + K.rnd(-200, 200);
    const vy = -K.rnd(820, 1020), t = -2 * vy / G0;
    F.push({ f, x, y: H + f.r + 10, vx: (tx - x) / t, vy, a: K.rnd(0, 6), va: K.rnd(-4, 4), bomb: !!bomb, dead: false });
  }
  function slice(si, fr, ang) {
    const s = K.slots[si];
    if (fr.bomb) { s.score = Math.max(0, s.score - 5); K.sfx.boom(); K.shake(16); flash = 0.5; K.burst(fr.x, fr.y, '#fb7185', 26, 380, 0.7, 8); txt.push({ x: fr.x, y: fr.y, t: 0.9, s: '-5', c: '#fb7185' });
      try { window.tvpad && s.human && window.tvpad.vibrate(s.pad, 150); } catch (e) { } return; }
    s.score += 1; s.cut = (s.cut || 0) + 1; s.cutT = 0.35;
    if (s.cut === 3) { s.score += 2; txt.push({ x: fr.x, y: fr.y - 50, t: 1, s: 'COMBO +2', c: s.color }); } else if (s.cut > 3) { s.score += 1; txt.push({ x: fr.x, y: fr.y - 50, t: 0.8, s: '+1', c: s.color }); }
    K.tone(500 + s.cut * 90, 0.09, 'triangle', 0.09); K.noise(0.12, 0.1, 4000, 800);
    K.burst(fr.x, fr.y, fr.f.in, 14, 300, 0.5, 6); K.burst(fr.x, fr.y, fr.f.c, 8, 220, 0.5, 5);
    for (let k = 0; k < 2; k++) halves.push({ f: fr.f, x: fr.x, y: fr.y, vx: fr.vx + (k ? 1 : -1) * (120 + Math.abs(Math.cos(ang)) * 140), vy: fr.vy * 0.3 - 80, a: fr.a, va: (k ? 1 : -1) * K.rnd(3, 6), side: k, life: 1.2, rot: ang });
    try { window.tvpad && s.human && window.tvpad.vibrate(s.pad, 18); } catch (e) { }
  }
  function segHit(x1, y1, x2, y2, cx, cy, r) {                           // does the segment come within r of the point
    const dx = x2 - x1, dy = y2 - y1, l2 = dx * dx + dy * dy || 1; let t = ((cx - x1) * dx + (cy - y1) * dy) / l2; t = t < 0 ? 0 : t > 1 ? 1 : t;
    return Math.hypot(x1 + dx * t - cx, y1 + dy * t - cy) <= r;
  }
  function cpu(s, dt) {
    const p = Ptr.get(100 + s.i); s.pp = p; s.wait = (s.wait || 0) - dt;
    if (!s.tg || s.tg.dead || s.tg.y > H + 40) { s.tg = s.wait > 0 ? null : F.filter((f) => !f.bomb && !f.dead && f.y < H - 60 && f.y > 120)[0] || null; if (s.tg) s.wait = K.rnd(0.25, 0.7); }
    if (s.tg) Ptr.steer(p, s.tg.x + (s.tg.x > p.x ? 90 : -90), s.tg.y + K.rnd(-10, 10), dt, 1900); else Ptr.steer(p, W / 2, H * 0.6, dt, 300);
  }
  Ptr.use({ buttons: ['A'] });
  const G = {
    id: 'slice', title: { vi: 'Cắt trái cây', en: 'Fruit Slice' },
    tag: { vi: 'Rê nhanh ngón tay trên touchpad để chém trái cây, tránh bom — 60 giây', en: 'Swipe fast on the touchpad to slice the fruit, dodge the bombs — 60 seconds' },
    help: { vi: ['Chém liền 3 quả trở lên có điểm thưởng', 'Bom đen trừ 5 điểm'], en: ['Slice 3 or more in one swipe for a bonus', 'A black bomb costs 5 points'] },
    total: [1, 4], buttons: [],
    start(slots) { F = []; halves = []; txt.length = 0; left = TIME; spawnT = 0.4; over = false; bg = bg || makeBg(); flash = 0; for (const k in trail) delete trail[k]; slots.forEach((s) => { s.cut = 0; s.cutT = 0; s.tg = null; if (s.human) Ptr.get(s.pad); }); Ptr.reset(); },
    update(dt) {
      Ptr.update(dt);
      if (K.mode === 'over') return;
      left -= dt; spawnT -= dt; flash = Math.max(0, flash - dt); K.timeTick(left);
      if (spawnT <= 0) { const n = Math.random() < 0.35 ? 3 : Math.random() < 0.5 ? 2 : 1; for (let i = 0; i < n; i++) throwOne(Math.random() < 0.13); spawnT = K.rnd(1.1, 1.8) - (TIME - left) / TIME * 0.4; }
      for (const f of F) { f.vy += G0 * dt; f.x += f.vx * dt; f.y += f.vy * dt; f.a += f.va * dt; }
      F = F.filter((f) => !f.dead && f.y < H + 120);
      halves.forEach((h) => { h.vy += G0 * dt; h.x += h.vx * dt; h.y += h.vy * dt; h.a += h.va * dt; h.life -= dt; }); halves = halves.filter((h) => h.life > 0 && h.y < H + 100);
      K.slots.forEach((s, si) => {
        if (!s.human) cpu(s, dt);
        const p = s.human ? Ptr.get(s.pad) : s.pp, tr = trail[si] || (trail[si] = []);
        tr.push(p.x, p.y, K.t); while (tr.length > 36 || (tr.length > 3 && K.t - tr[2] > 0.25)) tr.splice(0, 3);
        s.cutT -= dt; if (s.cutT <= 0) s.cut = 0;
        if (p.sp > 480) for (const f of F) {
          if (f.dead) continue;
          if (segHit(p.px, p.py, p.x, p.y, f.x, f.y, f.f.r + 8)) { f.dead = true; slice(si, f, Math.atan2(p.y - p.py, p.x - p.px)); }
        }
      });
      for (let i = txt.length - 1; i >= 0; i--) { txt[i].t -= dt; txt[i].y -= 70 * dt; if (txt[i].t <= 0) txt.splice(i, 1); }
      if (left <= 0 && !over) { over = true; left = 0; const top = Math.max(...K.slots.map((s) => s.score)), ws = K.slots.filter((s) => s.score === top); K.over(ws.length === 1 ? ws[0].i : -1, K.slots.map((s) => s.name + ': ' + s.score)); }
    },
    draw(c) {
      K.shakeApply(c); c.drawImage(bg || (bg = makeBg()), 0, 0);
      for (const f of F) {
        c.save(); c.translate(f.x, f.y); c.rotate(f.a);
        if (f.bomb) { c.fillStyle = '#0f172a'; c.beginPath(); c.arc(0, 0, f.f.r, 0, 6.283); c.fill(); c.strokeStyle = '#fb7185'; c.lineWidth = 4; c.stroke(); c.fillStyle = '#fb7185'; c.fillRect(-4, -f.f.r - 12, 8, 14); K.text(c, '☠', 0, 3, 46, '#f8fafc'); }
        else { c.fillStyle = f.f.c; c.beginPath(); c.arc(0, 0, f.f.r, 0, 6.283); c.fill(); c.fillStyle = 'rgba(255,255,255,.3)'; c.beginPath(); c.arc(-f.f.r * 0.35, -f.f.r * 0.35, f.f.r * 0.22, 0, 6.283); c.fill(); }
        c.restore();
      }
      for (const h of halves) {
        c.save(); c.translate(h.x, h.y); c.rotate(h.a); c.globalAlpha = Math.min(1, h.life * 2);
        c.fillStyle = h.f.c; c.beginPath(); c.arc(0, 0, h.f.r, h.side ? 0 : Math.PI, h.side ? Math.PI : 6.283); c.fill();
        c.fillStyle = h.f.in; c.beginPath(); c.arc(0, 0, h.f.r * 0.82, h.side ? 0 : Math.PI, h.side ? Math.PI : 6.283); c.fill(); c.restore();
      }
      c.globalAlpha = 1; K.drawParts(c);
      K.slots.forEach((s, si) => {                                                           // blades: a fading ribbon behind the crosshair
        const tr = trail[si]; if (!tr || tr.length < 6) return;
        c.lineCap = 'round'; c.strokeStyle = s.color;
        for (let i = 3; i < tr.length; i += 3) { const a = (i / tr.length); c.globalAlpha = a * 0.9; c.lineWidth = 3 + a * 12; c.beginPath(); c.moveTo(tr[i - 3], tr[i - 2]); c.lineTo(tr[i], tr[i + 1]); c.stroke(); }
        c.globalAlpha = 1;
      });
      for (const t of txt) { c.globalAlpha = Math.min(1, t.t * 2); K.text(c, t.s, t.x, t.y, 40, t.c, 'center', 900); c.globalAlpha = 1; }
      Ptr.draw(c, K.slots);
      if (flash > 0) { c.fillStyle = 'rgba(251,113,133,' + flash * 0.5 + ')'; c.fillRect(0, 0, W, H); }
      K.hud(c, K.slots);
      K.text(c, Math.ceil(left) + 's', W / 2, 96, 40, left < 10 ? '#fb7185' : '#e2e8f0', 'center', 900);
    },
  };
  K.run(G);
})();
