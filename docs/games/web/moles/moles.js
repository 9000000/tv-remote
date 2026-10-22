'use strict';
// Whack-a-Mole: moles pop out of 12 holes, aim and Fire to whack them (golden = 3, quick). A grey rabbit is not a target: -2.
// First hit counts. 45 seconds; it gets faster. Alone you play against CPU whackers. With the TV remote alone (no phone 1): arrows jump the
// crosshair from hole to hole, OK whacks.
(function () {
  const K = window.Kit, Ptr = window.Ptr, W = 1280, H = 720, TIME = 45, COLS = 4, ROWS = 3;
  const holes = []; for (let r = 0; r < ROWS; r++) for (let c = 0; c < COLS; c++) holes.push({ x: 190 + c * 300 + (r % 2 ? 40 : -10), y: 250 + r * 160, m: null, hit: 0 });
  let left = 0, spawnT = 0, over = false, bg = null; const txt = [];

  function makeBg() {
    const c = document.createElement('canvas'); c.width = W; c.height = H; const g = c.getContext('2d');
    const gr = g.createLinearGradient(0, 0, 0, H); gr.addColorStop(0, '#12331f'); gr.addColorStop(1, '#0a1c12'); g.fillStyle = gr; g.fillRect(0, 0, W, H);
    for (let i = 0; i < 160; i++) { g.fillStyle = 'rgba(120,220,140,' + K.rnd(0.03, 0.12) + ')'; g.fillRect(K.rnd(0, W), K.rnd(120, H), 3, K.rnd(8, 20)); }
    return c;
  }
  function popUp() {
    const free = holes.filter((h) => !h.m && h.hit <= 0); if (!free.length) return;
    const h = K.pick(free), r = Math.random(), kind = r < 0.12 ? 'gold' : r < 0.26 ? 'bad' : 'mole', prog = (TIME - left) / TIME;
    h.m = { kind, t: 0, life: (kind === 'gold' ? 0.75 : K.rnd(0.9, 1.5)) * (1 - prog * 0.3), hit: false };
  }
  const pts = (m) => (m.kind === 'gold' ? 3 : m.kind === 'bad' ? -2 : 1);
  function whack(si, x, y) {
    const s = K.slots[si]; if (!s || over) return;
    for (const h of holes) {
      const m = h.m; if (!m || m.hit) continue;
      const up = Math.min(1, m.t / 0.12) * Math.min(1, (m.life - m.t) / 0.15 + 0.2);
      if (up < 0.45) continue;
      if (Math.hypot(x - h.x, y - (h.y - 40)) <= 76) {
        m.hit = true; h.hit = 0.35; const p = pts(m); s.score = Math.max(0, s.score + p); h.who = s.color;
        if (p > 0) { K.sfx.hit(p / 3); K.burst(h.x, h.y - 50, s.color, 14, 260, 0.5, 6); try { window.tvpad && s.human && window.tvpad.vibrate(s.pad, 20); } catch (e) { } }
        else { K.sfx.boom(); K.shake(8); try { window.tvpad && s.human && window.tvpad.vibrate(s.pad, 120); } catch (e) { } }
        txt.push({ x: h.x, y: h.y - 110, t: 0.8, s: (p > 0 ? '+' : '') + p, c: s.color }); return;
      }
    }
    K.tone(190, 0.05, 'square', 0.04, 0.7);
  }
  function cpu(s, dt) {
    const p = Ptr.get(100 + s.i); s.pp = p; s.wait = (s.wait || 0) - dt;
    if (!s.tg || !s.tg.m || s.tg.m.hit) { s.tg = null; if (s.wait <= 0) { const c = holes.filter((h) => h.m && !h.m.hit && h.m.kind !== 'bad' && h.m.t > 0.3 + s.i * 0.05); s.tg = c[0] || null; if (s.tg) s.wait = K.rnd(0.2, 0.5); } }
    if (s.tg) { Ptr.steer(p, s.tg.x, s.tg.y - 40, dt, 1500); if (Math.hypot(p.x - s.tg.x, p.y - s.tg.y + 40) < 40) { whack(s.i, p.x, p.y); s.tg = null; s.wait = K.rnd(0.35, 0.8); } }
  }
  let cur = 5;                                                       // the hole the remote's crosshair sits on (row 1, column 1)
  const remote = () => !(window.tvpad && window.tvpad.players().some((x) => (typeof x === 'object' ? x.player : x) === 1));
  function park() { const h = holes[cur], p = Ptr.get(1); p.x = h.x; p.y = h.y - 40; }
  const ARR = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
  window.addEventListener('keydown', (e) => {
    if (K.mode !== 'play' || over || !remote() || !K.slots.some((s) => s.human && s.pad === 1)) return;
    const d = ARR[e.key];
    if (d) { const c = K.clamp(cur % COLS + d[0], 0, COLS - 1), r = K.clamp((cur / COLS | 0) + d[1], 0, ROWS - 1); cur = r * COLS + c; park(); K.sfx.ui(); e.preventDefault(); }
    else if (e.key === 'Enter' && !e.repeat) { const p = Ptr.get(1); p.fire = 0; whack(K.slots.findIndex((s) => s.human && s.pad === 1), p.x, p.y); }
  });
  Ptr.onFire((n) => { const si = K.slots.findIndex((s) => s.human && s.pad === n); if (si >= 0) { const p = Ptr.get(n); whack(si, p.x, p.y); } });
  Ptr.use({ buttons: ['A'] });
  const G = {
    id: 'moles', title: { vi: 'Đập chuột chũi', en: 'Whack-a-Mole' },
    tag: { vi: 'Ngắm bằng touchpad, bấm Bắn để đập chuột — ai nhanh tay hơn', en: 'Aim with the touchpad, tap Fire to whack — fastest hand wins' },
    help: { vi: ['Chuột vàng 3 điểm, nhanh', 'Thỏ xám không phải mục tiêu: trừ 2', 'Chỉ có remote: mũi tên chọn lỗ, OK để đập'], en: ['Golden mole = 3, and quick', 'The grey rabbit is not a target: -2'] },
    total: [1, 4], buttons: ['A'],
    start(slots) { holes.forEach((h) => { h.m = null; h.hit = 0; }); txt.length = 0; left = TIME; spawnT = 0.5; over = false; bg = bg || makeBg(); slots.forEach((s) => { s.tg = null; if (s.human) Ptr.get(s.pad); }); Ptr.reset(); cur = 5; if (remote()) park(); },
    update(dt) {
      Ptr.update(dt); if (K.mode === 'over') return;
      left -= dt; spawnT -= dt;
      if (spawnT <= 0) { popUp(); if (Math.random() < 0.4) popUp(); spawnT = K.rnd(0.3, 0.7) * (1 - (TIME - left) / TIME * 0.4); }
      holes.forEach((h) => { if (h.hit > 0) { h.hit -= dt; if (h.hit <= 0) h.m = null; } else if (h.m) { h.m.t += dt; if (h.m.t >= h.m.life) h.m = null; } });
      K.slots.forEach((s) => { if (!s.human) cpu(s, dt); });
      for (let i = txt.length - 1; i >= 0; i--) { txt[i].t -= dt; txt[i].y -= 60 * dt; if (txt[i].t <= 0) txt.splice(i, 1); }
      if (left <= 0 && !over) { over = true; left = 0; const top = Math.max(...K.slots.map((s) => s.score)), ws = K.slots.filter((s) => s.score === top); K.over(ws.length === 1 ? ws[0].i : -1, K.slots.map((s) => s.name + ': ' + s.score)); }
    },
    draw(c) {
      K.shakeApply(c); c.drawImage(bg || (bg = makeBg()), 0, 0);
      for (const h of holes) {
        c.fillStyle = '#05100a'; c.beginPath(); c.ellipse(h.x, h.y, 86, 30, 0, 0, 6.283); c.fill();
        const m = h.m;
        if (m) {
          const up = Math.min(1, m.t / 0.12) * Math.min(1, (m.life - m.t) / 0.15 + 0.2), top = h.y - 20 - up * 100, col = m.kind === 'gold' ? '#fbbf24' : m.kind === 'bad' ? '#94a3b8' : '#a16a3c';
          c.save(); c.beginPath(); c.rect(h.x - 100, h.y - 220, 200, 220 + 6); c.clip();
          if (m.kind === 'gold') K.light(c, h.x, top + 40, '#fbbf24', 100, 0.45);
          c.fillStyle = col; c.beginPath(); c.ellipse(h.x, top + 62, 56, 66, 0, 0, 6.283); c.fill();
          if (m.kind === 'bad') { c.beginPath(); c.ellipse(h.x - 24, top - 10, 12, 36, -0.15, 0, 6.283); c.ellipse(h.x + 24, top - 10, 12, 36, 0.15, 0, 6.283); c.fill(); }
          c.fillStyle = '#fff'; const ey = top + 40; c.beginPath(); c.arc(h.x - 20, ey, 11, 0, 6.283); c.arc(h.x + 20, ey, 11, 0, 6.283); c.fill();
          c.fillStyle = '#111827'; c.beginPath(); c.arc(h.x - 20 + (m.hit ? 0 : 2), ey, m.hit ? 3 : 5, 0, 6.283); c.arc(h.x + 20 + (m.hit ? 0 : -2), ey, m.hit ? 3 : 5, 0, 6.283); c.fill();
          c.fillStyle = m.kind === 'bad' ? '#fda4af' : '#4a2c12'; c.beginPath(); c.ellipse(h.x, ey + 24, 14, 9, 0, 0, 6.283); c.fill();
          if (m.hit) { c.strokeStyle = h.who; c.lineWidth = 6; c.beginPath(); c.arc(h.x, top + 40, 70 + (0.35 - h.hit) * 120, 0, 6.283); c.stroke(); }
          c.restore();
        }
        c.fillStyle = '#16351f'; c.beginPath(); c.ellipse(h.x, h.y + 4, 90, 24, 0, 0, Math.PI); c.fill();           // the hole's front lip
      }
      K.drawParts(c);
      for (const t of txt) { c.globalAlpha = Math.min(1, t.t * 2); K.text(c, t.s, t.x, t.y, 46, t.c, 'center', 900); c.globalAlpha = 1; }
      K.slots.forEach((s) => { if (!s.human && s.pp) { const p = s.pp; c.globalAlpha = 0.55; c.strokeStyle = s.color; c.lineWidth = 4; c.beginPath(); c.arc(p.x, p.y, 20, 0, 6.283); c.stroke(); c.globalAlpha = 1; } });
      Ptr.draw(c, K.slots); K.hud(c, K.slots);
      K.text(c, Math.ceil(left) + 's', W / 2, 96, 40, left < 10 ? '#fb7185' : '#e2e8f0', 'center', 900);
    },
  };
  K.run(G);
})();
