'use strict';
// Kit: what the TV Games arcade pages (hockey, tanks, cycles, sumo) share: input from the phones (window.tvpad), Bluetooth pads (Gamepad API)
// and the keyboard; a fixed 60 Hz step with a 1280x720 canvas that lowers its own resolution on a slow TV box; generated sound (WebAudio,
// no files); the title screen (the shared _lib/lobby.js: QR, players, CPU seats), the score bar, the result screen and a rematch. A game gives Kit.run() its rules:
//   { id, title:{vi,en}, tag:{vi,en}, help:{vi:[..],en:[..]}, total:[min,max] seats, buttons:['A'], start(slots), update(dt), draw(ctx) }
// and calls Kit.over(winnerSlotIndex | -1, scoreLines) when the match is decided. ?auto=N plays N CPUs against each other (tests, fps),
// ?p=2 gives the keyboard a second player, ?fps=1 shows the frame rate, ?mute silences it.
// Seats follow the phones while a match runs: a phone that drops hands its seat to a CPU (s.human false, s.away true), the same phone back or a new one
// wearing that seat's colour takes it again; a game that keeps per-seat state besides s.human implements seat(slot). Kit steps the sparks itself.
// K.nameTag(c, slot, x, y) names a seat's piece (draw it in the 'ready' phase); K.timeTick(left) ticks the last 5 s of a timed game.
(function () {
  const Q = new URLSearchParams(location.search);
  const VI = (navigator.language || '').toLowerCase().startsWith('vi');
  const W = 1280, H = 720, DT = 1 / 60;
  const PCOL = ['#3b82f6', '#ef4444', '#22c55e', '#eab308'];   // the pad's own colour per slot (pad.js COLORS): a phone matches its seat
  const FONT = 'system-ui, Roboto, "Helvetica Neue", Arial, sans-serif';
  const pad = window.tvpad;
  const words = {
    vi: { cpu: 'Máy', p: 'Người chơi', play: 'Chơi', rematch: 'Chơi lại', menu: 'Về menu', win: 'Thắng!', draw: 'Hòa!', cpuSeats: 'Máy chơi cùng', scan: 'Quét để vào', startHint: 'Nhấn OK / nút bắn để bắt đầu', arrows: 'Trái / phải: đổi số máy', ready: 'Sẵn sàng', go: 'BẮT ĐẦU!' },
    en: { cpu: 'CPU', p: 'Player', play: 'Play', rematch: 'Rematch', menu: 'Menu', win: 'wins!', draw: 'Draw!', cpuSeats: 'CPU players', scan: 'Scan to join', startHint: 'Press OK / the action button to begin', arrows: 'Left / right: number of CPUs', ready: 'Ready', go: 'GO!' },
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
  K.fit = (c, s, maxw, size, weight) => {                                     // s cut with … to fit maxw px at this font
    c.font = (weight || 800) + ' ' + size + 'px ' + FONT; if (c.measureText(s).width <= maxw) return s;
    let n = s.length; while (n > 1 && c.measureText(s.slice(0, n) + '…').width > maxw) n--; return s.slice(0, n) + '…';
  };
  const glows = {};
  // a soft round light, drawn with 'lighter'. Sizes go in 16 px steps, drawn scaled to the exact one, and only GLOWS are kept: a radius
  // changing every frame (Sumo's shrinking floor) made a new ~800 px canvas each frame, kept forever, until the TV ran out of memory
  const GLOWS = 24;
  K.glow = (color, r) => {
    r = Math.max(16, Math.ceil(r / 16) * 16);
    const k = color + r; if (glows[k]) return glows[k];
    const ks = Object.keys(glows); if (ks.length >= GLOWS) delete glows[ks[0]];
    const g = document.createElement('canvas'); g.width = g.height = r * 2; const c = g.getContext('2d');
    const gr = c.createRadialGradient(r, r, 0, r, r, r); gr.addColorStop(0, color); gr.addColorStop(1, 'rgba(0,0,0,0)');
    c.fillStyle = gr; c.fillRect(0, 0, r * 2, r * 2); return (glows[k] = g);
  };
  K.light = (c, x, y, color, r, a) => { c.globalAlpha = a == null ? 1 : a; c.globalCompositeOperation = 'lighter'; c.drawImage(K.glow(color, r), x - r, y - r, r * 2, r * 2); c.globalCompositeOperation = 'source-over'; c.globalAlpha = 1; };

  // particles: flat arrays, no allocation per frame
  const PN = 400, P = { x: new Float32Array(PN), y: new Float32Array(PN), vx: new Float32Array(PN), vy: new Float32Array(PN), life: new Float32Array(PN), max: new Float32Array(PN), sz: new Float32Array(PN), col: new Array(PN).fill('#fff') };
  let pi = 0;
  K.burst = (x, y, color, n, speed, life, size) => {
    for (let i = 0; i < n; i++) {
      const k = pi++ % PN, a = Math.random() * 6.283, s = speed * (0.25 + Math.random() * 0.75);
      P.x[k] = x; P.y[k] = y; P.vx[k] = Math.cos(a) * s; P.vy[k] = Math.sin(a) * s; P.life[k] = P.max[k] = life * (0.5 + Math.random() * 0.5); P.sz[k] = (size || 5) * (0.6 + Math.random() * 0.8); P.col[k] = color;
    }
  };
  let partsDone = false;                                                                 // Kit steps the sparks itself (a game that forgot froze them on screen); a game's own call in the same step is skipped
  K.updateParts = (dt) => { if (partsDone) return; partsDone = true; const f = Math.max(0, 1 - 2.2 * dt); for (let k = 0; k < PN; k++) if (P.life[k] > 0) { P.life[k] -= dt; P.x[k] += P.vx[k] * dt; P.y[k] += P.vy[k] * dt; P.vx[k] *= f; P.vy[k] *= f; } };
  K.drawParts = (c) => { for (let k = 0; k < PN; k++) if (P.life[k] > 0) { const a = P.life[k] / P.max[k], s = P.sz[k] * (0.4 + a); c.globalAlpha = a; c.fillStyle = P.col[k]; c.fillRect(P.x[k] - s / 2, P.y[k] - s / 2, s, s); } c.globalAlpha = 1; };
  K.nameTag = (c, s, x, y) => {                                                          // who is who, in words: a pill in the seat colour (colour alone fails for some eyes)
    const n = K.fit(c, s.name, 220, 22, 800); c.font = '800 22px ' + FONT; const w = c.measureText(n).width + 28;
    c.fillStyle = s.color; K.rr(c, x - w / 2, y - 17, w, 34, 17); c.fill(); K.text(c, n, x, y + 1, 22, '#0b1020');
  };
  K.shake = (a) => { K.shakeAmt = Math.max(K.shakeAmt, a); };
  K.shakeApply = (c) => { if (K.shakeAmt > 0.3) c.translate(K.rnd(-K.shakeAmt, K.shakeAmt), K.rnd(-K.shakeAmt, K.shakeAmt)); };

  // ---------------- sound: _lib/sfx.js owns the AudioContext and the sounds; the games keep calling K.tone / K.noise / K.sfx.x()
  const audio = Sfx.wake;
  let tickN = -1;
  K.timeTick = (left) => { const n = Math.ceil(left); if (n !== tickN) { tickN = n; if (n > 0 && n <= 5 && K.mode === 'play') K.sfx.tick(); } };   // the last five seconds of a timed game
  K.tone = Sfx.tone; K.noise = Sfx.noise; K.sfx = Sfx.s;

  // ---------------- input: phones, pads, keyboard -> K.pad(n) = { x, y, held{}, hit{} } for pad player n (1..4)
  const S = {};
  const st = (n) => S[n] || (S[n] = { x: 0, y: 0, held: {}, hit: {}, ax: 0, ay: 0, dp: {}, gp: {}, gx: 0, gy: 0, kb: {} });
  K.pad = (n) => st(n);
  const DIRS = ['UP', 'DOWN', 'LEFT', 'RIGHT'];
  if (pad) {
    if (pad.ownKeys) { try { pad.ownKeys(); } catch (e) { } }                              // Kit reads every phone itself: player 1's presses must not come back as keys too (a doubled press)
    pad.on('button', (e) => {
      const s = st(e.player); if (!e.btn) return;
      if (DIRS.includes(e.btn)) s.dp[e.btn] = e.down; else { s.held[e.btn] = e.down; if (e.down) s.hit[e.btn] = true; }
      if (e.down) audio();
    });
    pad.on('axis', (e) => { const s = st(e.player); s.ax = e.x || 0; s.ay = e.y || 0; });
    pad.on('join', (e) => { K.layout(); if (K.mode === 'play') adopt(e.player); });             // a phone joining mid-game gets the game's pad, and a seat if one is free
    pad.on('leave', (e) => { const s = S[e.player]; if (s) { s.dp = {}; s.held = {}; s.ax = s.ay = 0; } if (K.mode === 'play') away(e.player); });
  }
  const KB = { ArrowUp: [1, 'UP'], ArrowDown: [1, 'DOWN'], ArrowLeft: [1, 'LEFT'], ArrowRight: [1, 'RIGHT'], z: [1, 'A'], x: [1, 'B'], Enter: [1, 'START'], ' ': [1, 'A'],
    w: [2, 'UP'], s: [2, 'DOWN'], a: [2, 'LEFT'], d: [2, 'RIGHT'], f: [2, 'A'], g: [2, 'B'], i: [3, 'UP'], k: [3, 'DOWN'], j: [3, 'LEFT'], l: [3, 'RIGHT'], h: [3, 'A'], ',': [4, 'UP'], '.': [4, 'DOWN'], ';': [4, 'A'] };
  // one Hold per physical key (_lib/hold.js): a remote that sends a down + up pair every ~50 ms while held (or a down without `repeat`)
  // is one long press, not a press per pair; the key lets go 120 ms after its last up
  const HK = {};
  const kb = (e, down) => {
    const key = e.key.length === 1 ? e.key.toLowerCase() : e.key, m = KB[key]; if (!m) return;
    e.preventDefault();
    const h = HK[key] || (HK[key] = Hold.make({ grace: 120,
      down() { const s = st(m[0]); s.kb[m[1]] = true; s.hit[m[1]] = true; if (key === 'Enter') { s.kb.A = true; s.hit.A = true; } audio(); },   // Enter: the TV remote's OK is Start on the title, the action button in play
      up() { const s = st(m[0]); s.kb[m[1]] = false; if (key === 'Enter') s.kb.A = false; } }));
    if (down) h.down(); else h.up();
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
      s.edgeL = !!s.hit.LEFT; s.edgeR = !!s.hit.RIGHT;                                                     // a key tap shorter than a frame still counts
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
    if (hs.length > mx) hs.length = mx;                                                    // a third phone at an air hockey table watches: the game has two seats
    let nc = auto ? auto : cpuWant == null ? Math.max(0, mn - hs.length) : cpuWant;
    nc = K.clamp(nc, Math.max(0, mn - hs.length), Math.max(0, mx - hs.length));
    if (auto) hs.length = 0;
    const used = new Set(hs), out = [];
    hs.forEach((n) => out.push({ human: true, pad: n, name: pname(n), color: PCOL[n - 1] }));
    for (let i = 0; i < nc; i++) { let c = 0; while (used.has(c + 1)) c++; used.add(c + 1); out.push({ human: false, pad: 0, name: words.cpu + ' ' + (i + 1), color: PCOL[c] }); }
    out.forEach((s, i) => { s.i = i; s.score = 0; });
    return { out, nc, hs };
  }
  // A phone that drops mid-game hands its seat to a CPU (its blob would otherwise stand still and be shoved off); the same phone coming back takes it again,
  // and a new phone takes the CPU seat that wears its own colour. A game that keeps per-seat state reads s.human each step; one that cannot, listens to G.seat(slot).
  function giveSeat(s, human, n) {
    if (human) { s.human = true; s.pad = n; s.name = pname(n); s.away = false; } else { s.human = false; s.away = true; s.name = s.name + ' · ' + words.cpu; }
    if (G && G.seat) G.seat(s); K.layout();
  }
  function away(n) {
    if (Q.get('auto')) return;
    let left = []; try { left = pad.players().map((x) => (typeof x === 'object' ? x.player : x)).filter((m) => m !== n); } catch (e) { }
    if (n === 1 && !left.length) return;                                                  // nobody else is on a phone: the remote / keyboard keeps seat 1
    const s = K.slots.find((x) => x.human && x.pad === n); if (s) giveSeat(s, false, n);
  }
  function adopt(n) {
    if (Q.get('auto') || n < 1 || n > 4) return;
    let s = K.slots.find((x) => x.away && x.pad === n);                                     // the same phone back
    if (!s) s = K.slots.find((x) => !x.human && !x.away && x.color === PCOL[n - 1]);         // or a fresh one: the CPU wearing its colour
    if (s && !K.slots.some((x) => x.human && x.pad === n)) giveSeat(s, true, n);
  }
  K.layout = () => { if (pad && G) { try { pad.layout('all', { buttons: K.mode !== 'play' ? ['A', 'START'] : G.buttons || ['A'] }); } catch (e) { } } };

  // ---------------- screens
  let titleT = 0, overInfo = null, countdown = 0, lastNum = 0;
  function frameBg(c, t) {                                                               // drifting orbs, only on title / result
    for (let i = 0; i < 5; i++) { const a = t * 0.15 + i * 1.3; K.light(c, W / 2 + Math.cos(a * 1.1 + i) * 520, H / 2 + Math.sin(a * 0.9 + i * 2) * 260, PCOL[i % 4], 260, 0.16); }
  }
  // The title screen is the shared lobby (_lib/lobby.js): QR hero, player chips, one OK pill. It is DOM, so the loop below stops drawing
  // the canvas and polls input at 20 Hz while it shows (no rAF: an idle lobby costs ~nothing on a weak TV box).
  function lobbyShow() {
    const sc = seats(), tx = (o) => (o && (VI ? o.vi : o.en)) || '', help = (G.help && G.help[VI ? 'vi' : 'en']) || [];
    let extra = null;
    if (G.total[1] > sc.hs.length) { extra = document.createElement('div'); extra.textContent = '\u25C0  ' + words.cpuSeats + ': ' + sc.nc + '  \u25B6'; extra.style.cssText = 'font-size:2.8vh;color:#b6c4dc'; }
    Lobby.set({ title: tx(G.title), tag: tx(G.tag), min: 0, extra, hints: help.concat(extra ? [words.arrows] : []),
      chips: sc.out.map((s, i) => (s.human ? { id: 'p' + s.pad, name: s.name, color: s.color, label: 'P' + s.pad } : { id: 'c' + i, name: s.name, color: s.color, label: 'C', cpu: true })) });
  }
  K.hud = (c, slots, extra) => {                                                         // the score bar across the top
    const n = slots.length, cw = Math.min(290, (W - 60) / n - 14), gx = W / 2 - (n * cw + (n - 1) * 14) / 2;
    slots.forEach((s, i) => {
      const x = gx + i * (cw + 14), y = 14, dim = extra && extra(s) === 'out';
      c.globalAlpha = dim ? 0.45 : 1; c.fillStyle = 'rgba(8,12,28,.72)'; K.rr(c, x, y, cw, 54, 14); c.fill();
      c.fillStyle = s.color; K.rr(c, x, y, 8, 54, 4); c.fill();
      K.text(c, K.fit(c, s.name, cw - 110, 22), x + 22, y + 27, 22, '#e8eefc', 'left');
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
    K.text(c, w ? K.fit(c, w.name, W - 160, 100, 900) : words.draw, W / 2, 190, 100, w ? w.color : '#fff', 'center', 900);
    if (w) K.text(c, words.win, W / 2, 270, 50, '#fff');
    (overInfo.lines || []).forEach((l, i) => K.text(c, l, W / 2, 350 + i * 40, 28, '#cbd5e1', 'center', 600));
    [words.rematch, words.menu].forEach((l, i) => {                                       // two choices: Left / Right move, OK takes the lit one
      const on = overInfo.sel === i, x = W / 2 + (i ? 20 : -340), y = 570;
      c.save(); if (on) { c.shadowColor = '#fff'; c.shadowBlur = 24; }
      c.fillStyle = on ? '#2563eb' : 'rgba(255,255,255,.1)'; K.rr(c, x, y, 320, 78, 20); c.fill(); c.restore();
      c.lineWidth = on ? 5 : 2; c.strokeStyle = on ? '#fff' : 'rgba(255,255,255,.25)'; K.rr(c, x, y, 320, 78, 20); c.stroke();
      K.text(c, K.fit(c, l, 280, 34), x + 160, y + 40, 34, on ? '#fff' : '#cbd5e1', 'center', 800);
    });
    K.text(c, '◀  ▶  ·  OK', W / 2, 676, 26, '#b6c4dc', 'center', 600);
  }
  K.over = (win, lines) => {
    if (K.mode !== 'play') return;
    K.mode = 'over'; overInfo = { win, lines, t: 0, sel: 0 }; K.layout();
    const w = win >= 0 ? K.slots[win] : null; if (w && !w.human && K.slots.some((x) => x.human)) K.sfx.lose(); else K.sfx.win();   // a CPU beat every phone: a sad jingle, not a fanfare
    if (+Q.get('auto')) setTimeout(() => begin(), 3500);
  };
  function begin() {
    Lobby.hide(); const sc = seats(); K.slots = sc.out; K.mode = 'play'; K.t = 0; K.layout(); G.start(K.slots); K.sfx.go();
  }
  function toTitle() { K.mode = 'title'; cpuWant = null; K.layout(); }

  // ---------------- the loop
  let acc = 0, last = performance.now(), fcount = 0, ftime = 0, lowFor = 0, fpsEl = null, logT = 0;
  function step() {
    K.t += DT; titleT += DT; K.shakeAmt *= 0.9; partsDone = false;
    if (K.mode === 'title') {
      lobbyShow(); const hs = K.humans(); let go = false, dl = 0;
      hs.forEach((n) => { const s = st(n); if (s.pulse || s.start) go = true; if (s.edgeL) dl--; if (s.edgeR) dl++; });
      if (dl) { const sc = seats(); cpuWant = sc.nc + dl; const sc2 = seats(); if (sc2.nc !== sc.nc) K.sfx.ui(); cpuWant = sc2.nc; }
      if (go || +Q.get('auto')) { begin(); }
    } else if (K.mode === 'over') {
      overInfo.t += DT; G.update(DT);
      K.humans().forEach((n) => { const s = st(n); if (s.edgeL && overInfo.sel) { overInfo.sel = 0; K.sfx.ui(); } if (s.edgeR && !overInfo.sel) { overInfo.sel = 1; K.sfx.ui(); } });
      if (overInfo.t > 1.2) K.humans().forEach((n) => { const s = st(n); if (s.pulse) { K.sfx.ok(); if (overInfo.sel) toTitle(); else begin(); } else if (s.start) { K.sfx.ui(); toTitle(); } });   // OK (A, and Enter) takes the lit choice (rematch first), Left / Right switch; a phone's Start = menu
    } else G.update(DT);
    K.updateParts(DT);                                                                    // a no-op when the game stepped them already
  }
  function render() {
    ctx.setTransform(K.scale, 0, 0, K.scale, 0, 0); ctx.clearRect(0, 0, W, H);
    if (K.mode === 'title') return;                                                       // the lobby overlay is the screen
    else { ctx.save(); G.draw(ctx); ctx.restore(); if (K.mode === 'over') drawOver(ctx); }
  }
  let skip = false;
  document.addEventListener('visibilitychange', () => { last = performance.now(); acc = 0; });   // no catch-up burst after the page was hidden
  function frame(now) {
    try { tick(now); } finally { if (K.mode === 'title') setTimeout(() => frame(performance.now()), 50); else requestAnimationFrame(frame); }   // a finally: a throw must not end the loop
  }
  function tick(now) {
    let d = (now - last) / 1000; last = now; if (d > 0.1) d = 0.1;
    acc += d; poll();
    let n = 0;
    while (acc >= DT && n < 3) { step(); clearHits(); acc -= DT; n++; }
    if (n === 3) acc = 0;
    skip = !skip;
    if (!(skip && K.mode !== 'play')) render();                                          // the title and result screens only need 30 fps: half the work on a weak box
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
    K.layout(); requestAnimationFrame(frame);
  };
})();
