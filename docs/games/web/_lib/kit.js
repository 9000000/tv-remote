'use strict';
// Kit: what the TV Games arcade pages (hockey, tanks, cycles, sumo) share: input from the phones (window.tvpad), Bluetooth pads (Gamepad API)
// and the keyboard; a fixed 60 Hz step with a 1280x720 canvas that lowers its own resolution on a slow TV box; generated sound (WebAudio,
// no files); the title screen (players, CPU seats, QR), the score bar, the result screen and a rematch. A game gives Kit.run() its rules:
//   { id, title:{vi,en}, tag:{vi,en}, help:{vi:[..],en:[..]}, total:[min,max] seats, buttons:['A'], start(slots), update(dt), draw(ctx) }
// and calls Kit.over(winnerSlotIndex | -1, scoreLines) when the match is decided. ?auto=N plays N CPUs against each other (tests, fps),
// ?p=2 gives the keyboard a second player, ?fps=1 shows the frame rate, ?mute silences it.
(function () {
  const Q = new URLSearchParams(location.search);
  const VI = (navigator.language || '').toLowerCase().startsWith('vi');
  const W = 1280, H = 720, DT = 1 / 60;
  const PCOL = ['#38bdf8', '#fb7185', '#4ade80', '#facc15'];
  const FONT = 'system-ui, Roboto, "Helvetica Neue", Arial, sans-serif';
  const pad = window.tvpad;
  const words = {
    vi: { cpu: 'Máy', p: 'Người chơi', play: 'Chơi', rematch: 'Chơi lại', menu: 'Về menu', win: 'Thắng!', draw: 'Hòa!', cpuSeats: 'Máy chơi cùng', scan: 'Quét để vào', startHint: 'Nhấn nút bắn / Start để bắt đầu', arrows: 'Trái / phải: đổi số máy', ready: 'Sẵn sàng', go: 'BẮT ĐẦU!' },
    en: { cpu: 'CPU', p: 'Player', play: 'Play', rematch: 'Rematch', menu: 'Menu', win: 'wins!', draw: 'Draw!', cpuSeats: 'CPU players', scan: 'Scan to join', startHint: 'Press the action button / Start to begin', arrows: 'Left / right: number of CPUs', ready: 'Ready', go: 'GO!' },
  }[VI ? 'vi' : 'en'];

  const K = { W, H, VI, DT, words, PCOL, mode: 'title', slots: [], t: 0, shakeAmt: 0, fps: 60, scale: 1 };
  window.Kit = K;
  K.tr = (o) => (o && (VI ? o.vi : o.en)) || '';
  K.clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
  K.rnd = (a, b) => a + Math.random() * (b - a);
  K.pick = (a) => a[(Math.random() * a.length) | 0];

  // ---------------- canvas
  const cv = document.getElementById('c') || document.body.appendChild(Object.assign(document.createElement('canvas'), { id: 'c' }));
  const ctx = cv.getContext('2d', { alpha: true });
  K.ctx = ctx;
  function setScale(s) { K.scale = s; cv.width = Math.round(W * s); cv.height = Math.round(H * s); }
  setScale(Q.get('q') ? +Q.get('q') : 1);

  // ---------------- drawing helpers
  K.rr = (c, x, y, w, h, r) => { r = Math.min(r, w / 2, h / 2); c.beginPath(); c.moveTo(x + r, y); c.arcTo(x + w, y, x + w, y + h, r); c.arcTo(x + w, y + h, x, y + h, r); c.arcTo(x, y + h, x, y, r); c.arcTo(x, y, x + w, y, r); c.closePath(); };
  K.text = (c, s, x, y, size, color, align, weight) => { c.font = (weight || 800) + ' ' + size + 'px ' + FONT; c.textAlign = align || 'center'; c.textBaseline = 'middle'; c.fillStyle = color || '#fff'; c.fillText(s, x, y); };
  const glows = {};
  K.glow = (color, r) => {                                                    // a soft round light, drawn with 'lighter'
    const k = color + r; if (glows[k]) return glows[k];
    const g = document.createElement('canvas'); g.width = g.height = r * 2; const c = g.getContext('2d');
    const gr = c.createRadialGradient(r, r, 0, r, r, r); gr.addColorStop(0, color); gr.addColorStop(1, 'rgba(0,0,0,0)');
    c.fillStyle = gr; c.fillRect(0, 0, r * 2, r * 2); return (glows[k] = g);
  };
  K.light = (c, x, y, color, r, a) => { c.globalAlpha = a == null ? 1 : a; c.globalCompositeOperation = 'lighter'; c.drawImage(K.glow(color, r), x - r, y - r); c.globalCompositeOperation = 'source-over'; c.globalAlpha = 1; };

  // particles: flat arrays, no allocation per frame
  const PN = 400, P = { x: new Float32Array(PN), y: new Float32Array(PN), vx: new Float32Array(PN), vy: new Float32Array(PN), life: new Float32Array(PN), max: new Float32Array(PN), sz: new Float32Array(PN), col: new Array(PN).fill('#fff') };
  let pi = 0;
  K.burst = (x, y, color, n, speed, life, size) => {
    for (let i = 0; i < n; i++) {
      const k = pi++ % PN, a = Math.random() * 6.283, s = speed * (0.25 + Math.random() * 0.75);
      P.x[k] = x; P.y[k] = y; P.vx[k] = Math.cos(a) * s; P.vy[k] = Math.sin(a) * s; P.life[k] = P.max[k] = life * (0.5 + Math.random() * 0.5); P.sz[k] = (size || 5) * (0.6 + Math.random() * 0.8); P.col[k] = color;
    }
  };
  K.updateParts = (dt) => { const f = Math.max(0, 1 - 2.2 * dt); for (let k = 0; k < PN; k++) if (P.life[k] > 0) { P.life[k] -= dt; P.x[k] += P.vx[k] * dt; P.y[k] += P.vy[k] * dt; P.vx[k] *= f; P.vy[k] *= f; } };
  K.drawParts = (c) => { for (let k = 0; k < PN; k++) if (P.life[k] > 0) { const a = P.life[k] / P.max[k], s = P.sz[k] * (0.4 + a); c.globalAlpha = a; c.fillStyle = P.col[k]; c.fillRect(P.x[k] - s / 2, P.y[k] - s / 2, s, s); } c.globalAlpha = 1; };
  K.shake = (a) => { K.shakeAmt = Math.max(K.shakeAmt, a); };
  K.shakeApply = (c) => { if (K.shakeAmt > 0.3) c.translate(K.rnd(-K.shakeAmt, K.shakeAmt), K.rnd(-K.shakeAmt, K.shakeAmt)); };

  // ---------------- sound: tones and noise made on the spot, at most a dozen at once
  let ac = null, live = 0;
  const audio = () => {
    if (Q.has('mute')) return null;
    if (ac === null) { try { ac = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { ac = false; } }
    if (ac && ac.state === 'suspended') ac.resume().catch(() => { });
    return ac || null;
  };
  K.tone = (f, d, type, v, slide, delay) => {
    const a = audio(); if (!a || live > 12) return;
    const t = a.currentTime + (delay || 0), o = a.createOscillator(), g = a.createGain();
    o.type = type || 'square'; o.frequency.setValueAtTime(f, t); if (slide) o.frequency.exponentialRampToValueAtTime(Math.max(30, f * slide), t + d);
    g.gain.setValueAtTime(v == null ? 0.08 : v, t); g.gain.exponentialRampToValueAtTime(0.0001, t + d);
    o.connect(g); g.connect(a.destination); live++; o.onended = () => { live--; }; o.start(t); o.stop(t + d + 0.02);
  };
  let nbuf = null;
  K.noise = (d, v, f0, f1) => {
    const a = audio(); if (!a || live > 12) return;
    if (!nbuf) { nbuf = a.createBuffer(1, a.sampleRate, a.sampleRate); const b = nbuf.getChannelData(0); for (let i = 0; i < b.length; i++) b[i] = Math.random() * 2 - 1; }
    const t = a.currentTime, s = a.createBufferSource(), fl = a.createBiquadFilter(), g = a.createGain();
    s.buffer = nbuf; fl.type = 'lowpass'; fl.frequency.setValueAtTime(f0 || 2000, t); fl.frequency.exponentialRampToValueAtTime(f1 || 200, t + d);
    g.gain.setValueAtTime(v || 0.2, t); g.gain.exponentialRampToValueAtTime(0.0001, t + d);
    s.connect(fl); fl.connect(g); g.connect(a.destination); live++; s.onended = () => { live--; }; s.start(t); s.stop(t + d + 0.02);
  };
  K.sfx = {
    ui: () => K.tone(660, 0.07, 'triangle', 0.1),
    ok: () => { K.tone(520, 0.09, 'square', 0.07); K.tone(780, 0.14, 'square', 0.07, 0, 0.08); },
    tick: () => K.tone(880, 0.06, 'sine', 0.1),
    go: () => { K.tone(440, 0.12, 'sawtooth', 0.08); K.tone(880, 0.3, 'sawtooth', 0.08, 0, 0.1); },
    hit: (v) => K.tone(180 + (v || 0) * 140, 0.09, 'triangle', 0.18, 0.6),
    goal: () => { K.noise(0.5, 0.25, 1800, 120); [523, 659, 784, 1046].forEach((f, i) => K.tone(f, 0.18, 'square', 0.07, 0, i * 0.09)); },
    shoot: () => K.tone(520, 0.12, 'sawtooth', 0.07, 0.35),
    bounce: () => K.tone(300, 0.05, 'square', 0.05, 0.8),
    boom: () => { K.noise(0.55, 0.3, 2500, 90); K.tone(110, 0.4, 'sawtooth', 0.12, 0.3); },
    dash: () => K.noise(0.18, 0.12, 3500, 600),
    fall: () => K.tone(600, 0.6, 'sine', 0.12, 0.12),
    win: () => [523, 659, 784, 1046, 1318].forEach((f, i) => K.tone(f, 0.22, 'triangle', 0.1, 0, i * 0.11)),
  };

  // ---------------- input: phones, pads, keyboard -> K.pad(n) = { x, y, held{}, hit{} } for pad player n (1..4)
  const S = {};
  const st = (n) => S[n] || (S[n] = { x: 0, y: 0, held: {}, hit: {}, ax: 0, ay: 0, dp: {}, gp: {}, gx: 0, gy: 0, kb: {} });
  K.pad = (n) => st(n);
  const DIRS = ['UP', 'DOWN', 'LEFT', 'RIGHT'];
  if (pad) {
    pad.on('button', (e) => {
      const s = st(e.player); if (!e.btn) return;
      if (DIRS.includes(e.btn)) s.dp[e.btn] = e.down; else { s.held[e.btn] = e.down; if (e.down) s.hit[e.btn] = true; }
      if (e.down) audio();
    });
    pad.on('axis', (e) => { const s = st(e.player); s.ax = e.x || 0; s.ay = e.y || 0; });
    pad.on('join', () => { if (K.mode === 'title') K.layout(); });
    pad.on('leave', (e) => { const s = S[e.player]; if (s) { s.dp = {}; s.held = {}; s.ax = s.ay = 0; } });
  }
  const KB = { ArrowUp: [1, 'UP'], ArrowDown: [1, 'DOWN'], ArrowLeft: [1, 'LEFT'], ArrowRight: [1, 'RIGHT'], z: [1, 'A'], x: [1, 'B'], Enter: [1, 'START'], ' ': [1, 'A'],
    w: [2, 'UP'], s: [2, 'DOWN'], a: [2, 'LEFT'], d: [2, 'RIGHT'], f: [2, 'A'], g: [2, 'B'], i: [3, 'UP'], k: [3, 'DOWN'], j: [3, 'LEFT'], l: [3, 'RIGHT'], h: [3, 'A'], ',': [4, 'UP'], '.': [4, 'DOWN'], ';': [4, 'A'] };
  const kb = (e, down) => {
    const m = KB[e.key.length === 1 ? e.key.toLowerCase() : e.key]; if (!m) return;
    const s = st(m[0]); if (DIRS.includes(m[1])) s.kb[m[1]] = down; else { s.kb[m[1]] = down; if (down && !e.repeat) s.hit[m[1]] = true; }
    if (down) audio(); e.preventDefault();
  };
  addEventListener('keydown', (e) => kb(e, true)); addEventListener('keyup', (e) => kb(e, false));
  const strong = (a, b) => (Math.abs(a) >= Math.abs(b) ? a : b);
  function poll() {
    let gps = []; try { gps = navigator.getGamepads ? navigator.getGamepads() : []; } catch (e) { }
    for (let n = 1; n <= 4; n++) {
      const s = st(n), g = gps[n - 1];
      let gx = 0, gy = 0;
      if (g && g.connected) {
        gx = Math.abs(g.axes[0]) > 0.15 ? g.axes[0] : 0; gy = Math.abs(g.axes[1]) > 0.15 ? g.axes[1] : 0;
        const b = (i) => !!(g.buttons[i] && g.buttons[i].pressed);
        if (b(14)) gx = -1; if (b(15)) gx = 1; if (b(12)) gy = -1; if (b(13)) gy = 1;
        const names = { 0: 'A', 1: 'B', 2: 'X', 3: 'Y', 9: 'START' };
        for (const i in names) { const was = !!s.gp[names[i]], now = b(+i); s.gp[names[i]] = now; if (now && !was) s.hit[names[i]] = true; }
      }
      const dx = (s.dp.RIGHT || s.kb.RIGHT ? 1 : 0) - (s.dp.LEFT || s.kb.LEFT ? 1 : 0), dy = (s.dp.DOWN || s.kb.DOWN ? 1 : 0) - (s.dp.UP || s.kb.UP ? 1 : 0);
      s.x = strong(strong(s.ax, dx), gx); s.y = strong(strong(s.ay, dy), gy);
      if (Math.hypot(s.x, s.y) > 1) { const m = Math.hypot(s.x, s.y); s.x /= m; s.y /= m; }
      s.act = !!(s.held.A || s.held.B || s.kb.A || s.kb.B || s.gp.A || s.gp.B || s.held.X || s.held.Y);        // the action button, any of the face buttons
      s.pulse = !!(s.hit.A || s.hit.B || s.hit.X || s.hit.Y);                                                    // pressed since the last step
      s.start = !!(s.hit.START);
      s.edgeL = false; s.edgeR = false;
      const sx = s.x > 0.6 ? 1 : s.x < -0.6 ? -1 : 0; if (sx !== s.lastSx) { if (sx < 0) s.edgeL = true; if (sx > 0) s.edgeR = true; s.lastSx = sx; }
    }
  }
  const clearHits = () => { for (const n in S) S[n].hit = {}; };

  // ---------------- seats
  K.humans = () => {
    let a = [];
    if (Q.get('auto')) return [];
    try { a = pad ? pad.players().map((x) => (typeof x === 'object' ? x.player : x)).filter((n) => n >= 1 && n <= 4) : []; } catch (e) { }
    if (!a.length) a = Q.get('p') === '2' ? [1, 2] : [1];                                  // no phone: the keyboard / the TV remote is player 1
    return a.sort();
  };
  const pname = (n) => { try { return (pad && pad.name(n)) || words.p + ' ' + n; } catch (e) { return words.p + ' ' + n; } };
  let cpuWant = null, G = null;
  function seats() {
    const hs = K.humans(), [mn, mx] = G.total, auto = +Q.get('auto') || 0;
    let nc = auto ? auto : cpuWant == null ? Math.max(0, mn - hs.length) : cpuWant;
    nc = K.clamp(nc, Math.max(0, mn - hs.length), Math.max(0, mx - hs.length));
    if (auto) hs.length = 0;
    const used = new Set(hs), out = [];
    hs.forEach((n) => out.push({ human: true, pad: n, name: pname(n), color: PCOL[n - 1] }));
    for (let i = 0; i < nc; i++) { let c = 0; while (used.has(c + 1)) c++; used.add(c + 1); out.push({ human: false, pad: 0, name: words.cpu + ' ' + (i + 1), color: PCOL[c] }); }
    out.forEach((s, i) => { s.i = i; s.score = 0; });
    return { out, nc, hs };
  }
  K.layout = () => { if (pad && G) { try { pad.layout('all', { buttons: K.mode !== 'play' ? ['A', 'START'] : G.buttons || ['A'] }); } catch (e) { } } };

  // ---------------- screens
  let qrImg = null, titleT = 0, overInfo = null, countdown = 0, lastNum = 0;
  function frameBg(c, t) {                                                               // drifting orbs, only on title / result
    for (let i = 0; i < 5; i++) { const a = t * 0.15 + i * 1.3; K.light(c, W / 2 + Math.cos(a * 1.1 + i) * 520, H / 2 + Math.sin(a * 0.9 + i * 2) * 260, PCOL[i % 4], 260, 0.16); }
  }
  function drawTitle(c) {
    frameBg(c, titleT);
    const sc = seats(), pulse = 1 + Math.sin(titleT * 3) * 0.02;
    c.save(); c.translate(W / 2, 150); c.scale(pulse, pulse);
    K.text(c, K.tr(G.title), 4, 5, 104, 'rgba(0,0,0,.5)'); K.text(c, K.tr(G.title), 0, 0, 104, '#fff'); c.restore();
    K.text(c, K.tr(G.tag), W / 2, 236, 30, '#9fb4d8', 'center', 600);
    const help = (G.help && G.help[VI ? 'vi' : 'en']) || [];
    help.forEach((l, i) => K.text(c, l, W / 2, 300 + i * 38, 26, '#d7e2f5', 'center', 500));
    const n = sc.out.length, cw = 250, gx = W / 2 - (n * cw + (n - 1) * 20) / 2;
    sc.out.forEach((s, i) => {
      const x = gx + i * (cw + 20), y = 450;
      c.fillStyle = 'rgba(255,255,255,.07)'; K.rr(c, x, y, cw, 84, 18); c.fill();
      c.fillStyle = s.color; K.rr(c, x, y, 10, 84, 5); c.fill();
      c.beginPath(); c.arc(x + 48, y + 42, 20, 0, 6.283); c.fill();
      K.text(c, s.name.length > 12 ? s.name.slice(0, 11) + '…' : s.name, x + 82, y + 30, 24, '#fff', 'left');
      K.text(c, s.human ? 'P' + s.pad : 'CPU', x + 82, y + 59, 19, '#94a3b8', 'left', 600);
    });
    if (G.total[1] > sc.hs.length) K.text(c, '◀  ' + words.cpuSeats + ': ' + sc.nc + '  ▶', W / 2, 580, 24, '#9fb4d8', 'center', 600);
    const a = 0.55 + 0.45 * Math.sin(titleT * 4);
    c.globalAlpha = a; K.text(c, words.startHint, W / 2, 640, 32, '#fff'); c.globalAlpha = 1;
    if (qrImg && qrImg.complete && qrImg.naturalWidth) {
      c.imageSmoothingEnabled = false; c.fillStyle = '#fff'; K.rr(c, W - 196, H - 214, 172, 172, 12); c.fill(); c.drawImage(qrImg, W - 188, H - 206, 156, 156);
      c.imageSmoothingEnabled = true; K.text(c, words.scan, W - 110, H - 24, 20, '#9fb4d8', 'center', 600);
    }
  }
  K.hud = (c, slots, extra) => {                                                         // the score bar across the top
    const n = slots.length, cw = Math.min(290, (W - 60) / n - 14), gx = W / 2 - (n * cw + (n - 1) * 14) / 2;
    slots.forEach((s, i) => {
      const x = gx + i * (cw + 14), y = 14, dim = extra && extra(s) === 'out';
      c.globalAlpha = dim ? 0.45 : 1; c.fillStyle = 'rgba(8,12,28,.72)'; K.rr(c, x, y, cw, 54, 14); c.fill();
      c.fillStyle = s.color; K.rr(c, x, y, 8, 54, 4); c.fill();
      K.text(c, s.name.length > 9 ? s.name.slice(0, 8) + '…' : s.name, x + 22, y + 27, 22, '#e8eefc', 'left');
      K.text(c, String(s.score), x + cw - 20, y + 28, 38, s.color, 'right', 900);
      c.globalAlpha = 1;
    });
  };
  K.banner = (c, big, small, color) => {
    c.fillStyle = 'rgba(4,8,20,.66)'; c.fillRect(0, H / 2 - 90, W, 180);
    K.text(c, big, W / 2, H / 2 - 18, 92, color || '#fff', 'center', 900); if (small) K.text(c, small, W / 2, H / 2 + 52, 30, '#cbd5e1', 'center', 600);
  };
  function drawOver(c) {
    c.fillStyle = 'rgba(4,8,20,.74)'; c.fillRect(0, 0, W, H);
    frameBg(c, titleT);
    const w = overInfo.win >= 0 ? K.slots[overInfo.win] : null;
    K.text(c, w ? w.name : words.draw, W / 2, 190, 100, w ? w.color : '#fff', 'center', 900);
    if (w) K.text(c, words.win, W / 2, 270, 50, '#fff');
    (overInfo.lines || []).forEach((l, i) => K.text(c, l, W / 2, 350 + i * 40, 28, '#cbd5e1', 'center', 600));
    const a = 0.6 + 0.4 * Math.sin(titleT * 4);
    c.globalAlpha = a; K.text(c, '● ' + words.rematch + '     Start: ' + words.menu, W / 2, 600, 34, '#fff'); c.globalAlpha = 1;
  }
  K.over = (win, lines) => {
    if (K.mode !== 'play') return;
    K.mode = 'over'; overInfo = { win, lines, t: 0 }; K.sfx.win(); K.layout();
    if (+Q.get('auto')) setTimeout(() => begin(), 3500);
  };
  function begin() {
    const sc = seats(); K.slots = sc.out; K.mode = 'play'; K.t = 0; K.layout(); G.start(K.slots); K.sfx.go();
  }
  function toTitle() { K.mode = 'title'; cpuWant = null; K.layout(); }

  // ---------------- the loop
  let acc = 0, last = performance.now(), fcount = 0, ftime = 0, lowFor = 0, fpsEl = null, logT = 0;
  function step() {
    K.t += DT; titleT += DT; K.shakeAmt *= 0.9;
    if (K.mode === 'title') {
      const hs = K.humans(); let go = false, dl = 0;
      hs.forEach((n) => { const s = st(n); if (s.pulse || s.start) go = true; if (s.edgeL) dl--; if (s.edgeR) dl++; });
      if (dl) { const sc = seats(); cpuWant = sc.nc + dl; const sc2 = seats(); if (sc2.nc !== sc.nc) K.sfx.ui(); cpuWant = sc2.nc; }
      if (go || +Q.get('auto')) { begin(); }
    } else if (K.mode === 'over') {
      overInfo.t += DT; G.update(DT);
      if (overInfo.t > 1.2) K.humans().forEach((n) => { const s = st(n); if (s.start) { K.sfx.ui(); toTitle(); } else if (s.pulse) { K.sfx.ok(); begin(); } });
    } else G.update(DT);
  }
  function render() {
    ctx.setTransform(K.scale, 0, 0, K.scale, 0, 0); ctx.clearRect(0, 0, W, H);
    if (K.mode === 'title') drawTitle(ctx);
    else { ctx.save(); G.draw(ctx); ctx.restore(); if (K.mode === 'over') drawOver(ctx); }
  }
  function frame(now) {
    requestAnimationFrame(frame);
    let d = (now - last) / 1000; last = now; if (d > 0.1) d = 0.1;
    acc += d; poll();
    let n = 0;
    while (acc >= DT && n < 3) { step(); clearHits(); acc -= DT; n++; }
    if (n === 3) acc = 0;
    render();
    fcount++; ftime += d;
    if (ftime >= 1) {
      K.fps = Math.round(fcount / ftime); fcount = 0; ftime = 0; window.__fps = K.fps;
      if (K.mode === 'play' && K.fps < 42 && K.scale > 0.6) { if (++lowFor >= 3) { setScale(Math.max(0.6, K.scale - 0.15)); lowFor = 0; } } else lowFor = 0;
      if (Q.has('fps')) { fpsEl = fpsEl || document.body.appendChild(Object.assign(document.createElement('div'), { id: 'fps' })); fpsEl.textContent = K.fps + ' fps  x' + K.scale.toFixed(2); }
      if (Q.has('fps') && ++logT % 2 === 0) console.log('FPS ' + K.fps + ' scale ' + K.scale.toFixed(2) + ' mode ' + K.mode);
    }
  }
  K.run = (game) => {
    G = game; K.G = game; document.title = K.tr(game.title);
    if (pad && pad.qr) { try { qrImg = new Image(); qrImg.src = pad.qr(156); } catch (e) { qrImg = null; } }
    K.layout(); requestAnimationFrame(frame);
  };
})();
