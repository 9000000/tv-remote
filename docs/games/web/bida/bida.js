'use strict';
// Bi-a (8-ball pool) for TV Games: a top-down table on the TV drawn on one 1280x720 canvas, each player's phone is the cue (the pad's pointer
// layout: drag = turn the cue, hold A = charge, let go = shoot). Seats, bots, phones joining, the turn clock, scores and new game: _lib/table.js
// (2 seats; players 3 and 4 only watch). The balls are real-time, so the TV runs its own fixed-step loop (rules.js) between turns.
const W = Object.assign({}, Table.W, Table.VI ? {
  solids: 'Bi màu', stripes: 'Bi sọc', openT: 'Bàn mở', hintAim: 'Kéo để xoay cơ · giữ A lấy lực · thả để đánh', hintPlace: 'Kéo để đặt bi cái · bấm A để xác nhận',
  badSpot: 'Chỗ này chưa đặt được bi', power: 'Lực', breakT: (n) => `${n} khai cuộc`, turnOf: (n) => `Lượt của ${n}`, goOn: (n) => `${n} đánh tiếp`,
  hand: (n) => `${n} được đặt bi cái`, takes: (n, g) => `${n} đánh ${g}`, watch: 'Bạn đang xem trận này',
  f: { scratch: 'Lỗi: bi cái xuống lỗ', nohit: 'Lỗi: không chạm bi nào', wrong: 'Lỗi: chạm sai bi đầu tiên', rail: 'Lỗi: không bi nào chạm băng' },
  why: { clear: (n) => `${n} đưa bi 8 xuống lỗ`, early: (n) => `${n} làm bi 8 xuống lỗ quá sớm`, foul: (n) => `${n} làm bi 8 xuống lỗ cùng lỗi` },
} : {
  solids: 'Solids', stripes: 'Stripes', openT: 'Open table', hintAim: 'Drag to turn the cue · hold A to charge · release to shoot', hintPlace: 'Drag to place the cue ball · press A to confirm',
  badSpot: 'The ball cannot go there', power: 'Power', breakT: (n) => `${n} breaks`, turnOf: (n) => `${n} to play`, goOn: (n) => `${n} shoots again`,
  hand: (n) => `${n} has the cue ball in hand`, takes: (n, g) => `${n} plays ${g}`, watch: 'You are watching this game',
  f: { scratch: 'Foul: cue ball in the pocket', nohit: 'Foul: no ball was hit', wrong: 'Foul: wrong ball hit first', rail: 'Foul: no ball reached a rail' },
  why: { clear: (n) => `${n} potted the 8-ball`, early: (n) => `${n} potted the 8-ball too early`, foul: (n) => `${n} potted the 8-ball with a foul` },
});
const TURN = 45;
const SC = 1.3, OX = 120, OY = 124, FONT = 'system-ui, Roboto, "Helvetica Neue", Arial, sans-serif';
const COL = ['#fff', '#f6c915', '#1e5bd8', '#e0262c', '#6a2c91', '#f27a1a', '#138a3c', '#7b1d1d', '#111'];
const cv = document.getElementById('c'), cx = cv.getContext('2d');
const S = {
  g: null, phase: 'idle', ang: 0, px: 0, py: 0, chg: 0, charging: false, bot: null, level: 1, lastA: 0, hold: 0,
  k: [{ L: 0, R: 0, U: 0, D: 0, ax: 0, ay: 0 }, { L: 0, R: 0, U: 0, D: 0, ax: 0, ay: 0 }],
  msg: '', banner: '', bannerT: 0, shotId: 0, hinted: '', fireT: 0,
};
const wrap = (a) => { while (a > Math.PI) a -= 2 * Math.PI; while (a < -Math.PI) a += 2 * Math.PI; return a; };
const ease = (p) => (p < 0.5 ? 2 * p * p : 1 - 2 * (1 - p) * (1 - p));
const gname = (gr) => (gr === 0 ? W.solids : W.stripes);

// ---------------------------------------------------------------------------------------------- the table
const table = Table({
  seats: 2, turnSecs: TURN, botMs: [900, 1500], remote: true,   // no phone in seat 1: the TV remote (arrows + OK) plays it
  deal(t) {
    S.g = BD.create(); S.g.turn = t.lastWinner >= 0 ? t.lastWinner : 0; t.turn = S.g.turn;       // the last winner breaks
    S.level = [0, 1, 1, 2][Math.floor(Math.random() * 4)]; S.bot = null; S.charging = false; S.chg = 0; S.shotId++;
    S.phase = 'aim'; S.px = S.g.x[0]; S.py = S.g.y[0]; S.ang = 0; S.banner = ''; S.msg = W.breakT(t.name(t.turn));
  },
  bot(t, s) {
    if (S.phase !== 'aim' && S.phase !== 'place') return;
    const mv = BD.botMove(S.g, S.level);
    S.bot = { mv, t: 0, stage: S.g.hand ? 0 : 1, a0: S.ang, x0: S.px, y0: S.py, dur: 0 };
  },
  timeUp(t, s) {                                                  // away from the phone: the game goes on with an easy shot
    if (S.phase === 'roll') { t.left = 20; return; }
    if (S.phase !== 'aim' && S.phase !== 'place') return;
    const mv = BD.botMove(S.g, 0); S.bot = null; S.charging = false;
    if (S.g.hand) { if (!BD.place(S.g, mv.x, mv.y)) { const p = BD.freeSpot(S.g, mv.x, mv.y, S.g.zone); BD.place(S.g, p[0], p[1]); } }
    S.ang = mv.ang; S.chg = mv.pw; fire();
  },
  act: () => '',
  view(t, s) {
    if (t.done) return { buttons: ['START'], dpad: false };
    if (s === t.turn) return { pointer: true, buttons: ['A'] };
    return { buttons: [], dpad: false };
  },
  draw(t) { prompt(t); },
});

// a line on the phone whose turn it is (once per shot)
function prompt(t) {
  if (!t.ok || !S.g || t.done) return;
  const key = S.shotId + ':' + t.turn + ':' + S.phase;
  if (key === S.hinted || (S.phase !== 'aim' && S.phase !== 'place')) return;
  S.hinted = key; t.say(t.turn, S.phase === 'place' ? W.hintPlace : W.hintAim);
}
function nearestBall() {
  const g = S.g; let bd = 1e9, bi = 1;
  for (let i = 1; i < 16; i++) { if (g.pot[i]) continue; const d = (g.x[i] - g.x[0]) ** 2 + (g.y[i] - g.y[0]) ** 2; if (d < bd) { bd = d; bi = i; } }
  S.ang = Math.atan2(g.y[bi] - g.y[0], g.x[bi] - g.x[0]);
}
function fire() {
  S.phase = 'roll'; S.fireT = 0.5;
  BD.shoot(S.g, S.ang, S.chg); S.charging = false; S.bot = null;
  table.say(table.turn, ''); table.changed();
}
function finish(res) {
  const g = S.g, t = table, me = t.turn, nm = t.name(me), other = t.name(1 - me), who = t.name(g.turn);
  S.chg = 0; S.shotId++;
  if (res.over) {
    S.phase = 'idle'; S.banner = ''; S.msg = W.why[res.why](nm);
    t.over(res.winner === 0 ? [0, 1] : [1, 0]); return;
  }
  S.phase = g.hand ? 'place' : 'aim';
  if (g.hand) { S.px = g.x[0]; S.py = g.y[0]; } nearestBall();
  if (res.foul) { S.banner = W.f[res.foul]; S.bannerT = 3; S.msg = W.hand(who); t.buzz(me, 300); }
  else if (res.assigned) { S.banner = ''; S.msg = W.takes(nm, gname(g.grp[me])); }
  else if (res.cont) S.msg = W.goOn(who);
  else S.msg = W.turnOf(who);
  t.turnTo(g.turn);
}
const myTurn = () => table.ok && !table.done && S.g && table.human(table.turn) && !S.bot && (S.phase === 'aim' || S.phase === 'place');
function aDown(seat) {
  const now = performance.now();
  if (seat !== table.turn || !myTurn() || now - S.lastA < 250) return;                // the phone's A and its mirrored key both arrive: once
  S.lastA = now;
  if (S.phase === 'place') {
    if (BD.place(S.g, S.px, S.py)) { S.phase = 'aim'; nearestBall(); table.changed(); } else { table.say(seat, W.badSpot); table.buzz(seat, 120); }
  } else { S.charging = true; S.chg = 0; }
}
function aUp(seat) {
  if (seat !== table.turn || !S.charging) return;
  S.charging = false;
  if (S.chg < 0.04) { S.chg = 0; return; }
  fire();
}

// ---------------------------------------------------------------------------------------------- input
if (table.ok) {
  const pad = table.pad;
  pad.on('pointer', (e) => {
    const s = e.player - 1;
    if (s !== table.turn || !myTurn() || e.dx == null) return;
    if (S.phase === 'place') { S.px += e.dx * 1.85; S.py += e.dy * 1.85; clampSpot(); } else S.ang = wrap(S.ang + (e.dx + e.dy * 0.3) * 0.003);
  });
  pad.on('button', (e) => {
    const s = e.player - 1;
    if (s < 0 || s > 1) return;
    if (e.btn === 'A') { if (e.down) aDown(s); else aUp(s); return; }
    const k = S.k[s];
    if (e.btn === 'LEFT' || e.btn === 'RIGHT' || e.btn === 'UP' || e.btn === 'DOWN') k[e.btn[0]] = e.down ? 1 : 0;
  });
  pad.on('axis', (e) => { const s = e.player - 1; if (s >= 0 && s < 2) { S.k[s].ax = Math.abs(e.x) > 0.25 ? e.x : 0; S.k[s].ay = Math.abs(e.y) > 0.25 ? e.y : 0; } });
  const KEYS = { ArrowLeft: 'L', ArrowRight: 'R', ArrowUp: 'U', ArrowDown: 'D' };
  document.addEventListener('keydown', (e) => {
    if (KEYS[e.key]) S.k[0][KEYS[e.key]] = 1;
    else if ((e.key === ' ' || e.key === 'z' || e.key === 'Z' || (e.key === 'Enter' && !table.done)) && !e.repeat) aDown(0);
  });
  document.addEventListener('keyup', (e) => { if (KEYS[e.key]) S.k[0][KEYS[e.key]] = 0; else if (e.key === ' ' || e.key === 'z' || e.key === 'Z' || e.key === 'Enter') aUp(0); });
  const watcher = (p) => { if (p > 2) { try { pad.layout(p, { buttons: [], dpad: false }); pad.say(p, W.watch); } catch (e) { /* older TV Games */ } } };
  pad.on('join', (e) => watcher(e.player));
  pad.players().forEach(watcher);
}
function clampSpot() {
  const hi = S.g.zone === 'head' ? BD.HEAD : BD.W - BD.R;
  S.px = Math.max(BD.R, Math.min(hi, S.px)); S.py = Math.max(BD.R, Math.min(BD.H - BD.R, S.py));
}

// ---------------------------------------------------------------------------------------------- sprites
const BR = BD.R * SC, SZ = Math.ceil(BR * 2 + 10);
const spr = [];
function sprite(id) {
  const c = document.createElement('canvas'); c.width = c.height = SZ; const q = c.getContext('2d'), m = SZ / 2, r = BR;
  q.fillStyle = 'rgba(0,0,0,.35)'; q.beginPath(); q.arc(m + 2.5, m + 3.5, r, 0, 7); q.fill();
  const col = COL[id > 8 ? id - 8 : id] || '#fff';
  q.save(); q.beginPath(); q.arc(m, m, r, 0, 7); q.clip();
  q.fillStyle = id > 8 ? '#f4f1e8' : col; q.fillRect(0, 0, SZ, SZ);
  if (id > 8) { q.fillStyle = col; q.fillRect(0, m - r * 0.52, SZ, r * 1.04); }
  q.restore();
  if (id > 0) {
    q.fillStyle = '#f7f5ee'; q.beginPath(); q.arc(m, m, r * 0.5, 0, 7); q.fill();
    q.fillStyle = '#111'; q.font = '800 ' + Math.round(r * 0.72) + 'px ' + FONT; q.textAlign = 'center'; q.textBaseline = 'middle'; q.fillText(String(id), m, m + 0.6);
  }
  const sh = q.createRadialGradient(m - r * 0.35, m - r * 0.4, r * 0.05, m, m, r);
  sh.addColorStop(0, 'rgba(255,255,255,.55)'); sh.addColorStop(0.35, 'rgba(255,255,255,0)'); sh.addColorStop(1, 'rgba(0,0,0,.35)');
  q.fillStyle = sh; q.beginPath(); q.arc(m, m, r, 0, 7); q.fill();
  return c;
}
for (let i = 0; i < 16; i++) spr.push(sprite(i));

// the table, drawn once
const bg = document.createElement('canvas'); bg.width = 1280; bg.height = 720;
(function () {
  const q = bg.getContext('2d'), tw = BD.W * SC, th = BD.H * SC, X = OX, Y = OY;
  const g0 = q.createLinearGradient(0, 0, 0, 720); g0.addColorStop(0, '#0d1226'); g0.addColorStop(1, '#070a16'); q.fillStyle = g0; q.fillRect(0, 0, 1280, 720);
  const rr = (x, y, w, h, r) => { q.beginPath(); q.moveTo(x + r, y); q.arcTo(x + w, y, x + w, y + h, r); q.arcTo(x + w, y + h, x, y + h, r); q.arcTo(x, y + h, x, y, r); q.arcTo(x, y, x + w, y, r); q.closePath(); };
  q.shadowColor = 'rgba(0,0,0,.6)'; q.shadowBlur = 24; q.shadowOffsetY = 8;
  const wd = q.createLinearGradient(0, Y - 32, 0, Y + th + 32); wd.addColorStop(0, '#7a4a26'); wd.addColorStop(0.5, '#5b3419'); wd.addColorStop(1, '#3f230f');
  q.fillStyle = wd; rr(X - 32, Y - 32, tw + 64, th + 64, 26); q.fill(); q.shadowColor = 'transparent';
  q.fillStyle = '#0b4a2a'; rr(X - 15, Y - 15, tw + 30, th + 30, 10); q.fill();                       // cushions
  const fl = q.createRadialGradient(X + tw / 2, Y + th / 2, 40, X + tw / 2, Y + th / 2, tw * 0.62); fl.addColorStop(0, '#1f8a4f'); fl.addColorStop(1, '#126a39');
  q.fillStyle = fl; q.fillRect(X, Y, tw, th);
  q.strokeStyle = 'rgba(0,0,0,.28)'; q.lineWidth = 2; q.strokeRect(X + 1, Y + 1, tw - 2, th - 2);
  q.strokeStyle = 'rgba(255,255,255,.14)'; q.lineWidth = 2; q.beginPath(); q.moveTo(X + BD.HEAD * SC, Y + 6); q.lineTo(X + BD.HEAD * SC, Y + th - 6); q.stroke();
  q.fillStyle = 'rgba(255,255,255,.2)'; q.beginPath(); q.arc(X + BD.W * 0.72 * SC, Y + th / 2, 3, 0, 7); q.fill();
  q.fillStyle = '#e8d9b0'; for (let i = 1; i < 8; i++) { if (i === 4) continue; const x = X + tw * i / 8; q.fillRect(x - 3, Y - 26, 6, 6); q.fillRect(x - 3, Y + th + 20, 6, 6); }
  for (let i = 1; i < 4; i++) { const y = Y + th * i / 4; q.fillRect(X - 26, y - 3, 6, 6); q.fillRect(X + tw + 20, y - 3, 6, 6); }
  for (let p = 0; p < 18; p += 3) {
    const px = X + BD.PK[p] * SC, py = Y + BD.PK[p + 1] * SC, r = BD.PK[p + 2] * SC * 0.82;
    q.fillStyle = '#05080a'; q.beginPath(); q.arc(px, py, r, 0, 7); q.fill();
    q.strokeStyle = '#c9b27a'; q.lineWidth = 3; q.beginPath(); q.arc(px, py, r + 1, 0, 7); q.stroke();
  }
})();

// ---------------------------------------------------------------------------------------------- sound (a click per frame at most, made on the spot)
let AC = null;
const MUTE = /[?&]mute/.test(location.search);
function click(f, v, d) {
  if (MUTE) return;
  try {
    if (!AC) AC = new (window.AudioContext || window.webkitAudioContext)();
    if (AC.state === 'suspended') AC.resume();
    const o = AC.createOscillator(), a = AC.createGain(), t = AC.currentTime;
    o.type = 'triangle'; o.frequency.setValueAtTime(f, t); o.frequency.exponentialRampToValueAtTime(f * 0.55, t + d);
    a.gain.setValueAtTime(v, t); a.gain.exponentialRampToValueAtTime(0.001, t + d); o.connect(a); a.connect(AC.destination); o.start(t); o.stop(t + d + 0.02);
  } catch (e) { AC = null; }
}
function sounds(g) {
  const e = g.ev; let ball = 0, rail = 0, pk = 0;
  for (let i = 0; i < e.n; i++) { if (e.k[i] === 0) ball = Math.max(ball, e.s[i]); else if (e.k[i] === 1) rail = Math.max(rail, e.s[i]); else pk = 1; }
  e.n = 0;
  if (ball > 8) click(900 + Math.min(700, ball), Math.min(0.25, 0.04 + ball / 3000), 0.07);
  if (rail > 15) click(260, Math.min(0.2, 0.03 + rail / 4000), 0.09);
  if (pk) click(150, 0.25, 0.18);
}

// ---------------------------------------------------------------------------------------------- the frame
function think(dt) {
  const b = S.bot, mv = b.mv, g = S.g; b.t += dt;
  if (b.stage === 0) {                                                   // the cue ball goes to its spot
    if (b.dur === 0) b.dur = 0.8;
    const p = ease(Math.min(1, b.t / b.dur)); S.px = b.x0 + (mv.x - b.x0) * p; S.py = b.y0 + (mv.y - b.y0) * p;
    if (b.t >= b.dur) {
      if (!BD.place(g, mv.x, mv.y)) { const f = BD.freeSpot(g, mv.x, mv.y, g.zone); mv.x = f[0]; mv.y = f[1]; BD.place(g, f[0], f[1]); }
      S.phase = 'aim'; b.stage = 1; b.t = 0; b.dur = 0; b.a0 = S.ang;
    }
  } else if (b.stage === 1) {                                            // the cue turns
    const d = wrap(mv.ang - b.a0);
    if (b.dur === 0) b.dur = 0.5 + Math.abs(d) * 0.4;
    S.ang = b.a0 + d * ease(Math.min(1, b.t / b.dur));
    if (b.t >= b.dur + 0.25) { b.stage = 2; b.t = 0; b.dur = 0.3 + mv.pw * 0.7; }
  } else if (b.stage === 2) {                                            // and is drawn back
    S.chg = mv.pw * Math.min(1, b.t / b.dur);
    if (b.t >= b.dur) { S.ang = mv.ang; S.chg = mv.pw; fire(); }
  }
}
function human(dt) {
  const k = S.k[table.turn], g = S.g;
  let h = (k.R - k.L) || k.ax, v = (k.D - k.U) || k.ay;
  if (S.phase === 'place') {
    if (h || v) { S.px += h * 220 * dt; S.py += v * 220 * dt; clampSpot(); }
    return;
  }
  if (h) { S.hold += dt; S.ang = wrap(S.ang + h * (S.hold < 0.35 ? 0.12 : Math.min(0.9, 0.12 + (S.hold - 0.35) * 1.2)) * dt); } else S.hold = 0;
  if (S.charging) S.chg = Math.min(1, S.chg + dt / 1.1);
}
function logic(dt) {
  if (!table.ok || !S.g) return;
  if (S.bannerT > 0) S.bannerT -= dt;
  if (S.fireT > 0) S.fireT -= dt;
  if (S.phase === 'roll') {
    const res = BD.update(S.g, dt); sounds(S.g);
    if (res) finish(res);
  } else if (S.phase === 'aim' || S.phase === 'place') {
    if (S.bot) think(dt); else if (!table.done && table.human(table.turn)) human(dt);
  }
}

const DASH = [9, 8], NODASH = [];
function fit(text, size, maxW, weight) {
  let s = size; cx.font = (weight || 800) + ' ' + s + 'px ' + FONT;
  while (cx.measureText(text).width > maxW && s > 12) { s -= 2; cx.font = (weight || 800) + ' ' + s + 'px ' + FONT; }
}
function rrect(x, y, w, h, r) { cx.beginPath(); cx.moveTo(x + r, y); cx.arcTo(x + w, y, x + w, y + h, r); cx.arcTo(x + w, y + h, x, y + h, r); cx.arcTo(x, y + h, x, y, r); cx.arcTo(x, y, x + w, y, r); cx.closePath(); }
function ballAt(id, x, y, a) { if (a != null) cx.globalAlpha = a; cx.drawImage(spr[id], OX + x * SC - SZ / 2, OY + y * SC - SZ / 2); if (a != null) cx.globalAlpha = 1; }
function centered(text, x, y, size, color, weight) { fit(text, size, 1000, weight); cx.textAlign = 'center'; cx.textBaseline = 'middle'; cx.fillStyle = color; cx.fillText(text, x, y); }

function card(s) {
  const t = table, g = S.g, w = 440, x = s ? 1280 - 74 - w : 74, y = 8, h = 72, on = s === t.turn && !t.done;
  cx.fillStyle = 'rgba(14,20,40,.92)'; rrect(x, y, w, h, 16); cx.fill();
  cx.lineWidth = on ? 4 : 2; cx.strokeStyle = on ? t.COLORS[s] : 'rgba(255,255,255,.12)'; cx.stroke();
  cx.fillStyle = t.COLORS[s]; cx.beginPath(); cx.arc(x + 26, y + 26, 10, 0, 7); cx.fill();
  const nm = (t.human(s) ? '' : '🤖 ') + t.name(s);
  fit(nm, 26, 215); cx.textAlign = 'left'; cx.textBaseline = 'middle'; cx.fillStyle = '#fff'; cx.fillText(nm, x + 46, y + 26);
  const gr = g.grp[s];
  fit(gr < 0 ? W.openT : gname(gr), 20, 215, 600); cx.fillStyle = '#9aa5bd'; cx.fillText(gr < 0 ? W.openT : gname(gr), x + 22, y + 53);
  if (gr >= 0) {                                                    // the group's balls: lit while on the table, faint once potted; then the 8
    const base = gr === 0 ? 1 : 9;
    for (let i = 0; i < 8; i++) {
      const id = i < 7 ? base + i : 8, px = x + w - 200 + i * 24 + 12, py = y + h / 2, alive = !g.pot[id];
      cx.globalAlpha = alive ? 1 : 0.16; cx.drawImage(spr[id], px - 12, py - 12, 24, 24 * 1);
    }
    cx.globalAlpha = 1;
  }
}
function guide(g) {
  const ray = BD.ray(g, S.ang), px = OX + g.x[0] * SC, py = OY + g.y[0] * SC, gx = OX + ray.gx * SC, gy = OY + ray.gy * SC;
  cx.lineWidth = 2.5; cx.strokeStyle = 'rgba(255,255,255,.75)'; cx.setLineDash(DASH);
  cx.beginPath(); cx.moveTo(px, py); cx.lineTo(gx, gy); cx.stroke(); cx.setLineDash(NODASH);
  cx.strokeStyle = 'rgba(255,255,255,.9)'; cx.lineWidth = 2; cx.beginPath(); cx.arc(gx, gy, BR, 0, 7); cx.stroke();
  if (ray.ball > 0) {
    const bx = OX + g.x[ray.ball] * SC, by = OY + g.y[ray.ball] * SC, nx = (bx - gx) / (2 * BR), ny = (by - gy) / (2 * BR);
    const dx = Math.cos(S.ang), dy = Math.sin(S.ang), dd = dx * nx + dy * ny, tx = dx - dd * nx, ty = dy - dd * ny;
    cx.strokeStyle = '#fde047'; cx.lineWidth = 3; cx.beginPath(); cx.moveTo(bx, by); cx.lineTo(bx + nx * 90, by + ny * 90); cx.stroke();
    cx.strokeStyle = 'rgba(255,255,255,.55)'; cx.lineWidth = 2; cx.beginPath(); cx.moveTo(gx, gy); cx.lineTo(gx + tx * 60, gy + ty * 60); cx.stroke();
  }
}
function stick(g, x, y) {
  const dx = Math.cos(S.ang), dy = Math.sin(S.ang), pull = S.chg * 80 * SC, near = BR + 6 + pull, len = 360;
  const x0 = x - dx * near, y0 = y - dy * near, x1 = x0 - dx * len, y1 = y0 - dy * len, xm = x0 - dx * len * 0.62, ym = y0 - dy * len * 0.62;
  cx.lineCap = 'round';
  cx.strokeStyle = '#e9c98f'; cx.lineWidth = 7; cx.beginPath(); cx.moveTo(x0, y0); cx.lineTo(xm, ym); cx.stroke();
  cx.strokeStyle = '#3a2412'; cx.lineWidth = 10; cx.beginPath(); cx.moveTo(xm, ym); cx.lineTo(x1, y1); cx.stroke();
  cx.strokeStyle = '#7fb6e8'; cx.lineWidth = 7; cx.beginPath(); cx.moveTo(x0, y0); cx.lineTo(x0 - dx * 6, y0 - dy * 6); cx.stroke();
  cx.lineCap = 'butt';
}
function powerBar() {
  const x = 640, y = 690, w = 566, h = 18, on = S.charging || S.chg > 0;
  cx.fillStyle = 'rgba(255,255,255,.1)'; rrect(x, y, w, h, 9); cx.fill();
  if (S.chg > 0) {
    const gr = cx.createLinearGradient(x, 0, x + w, 0); gr.addColorStop(0, '#4ade80'); gr.addColorStop(0.6, '#facc15'); gr.addColorStop(1, '#ef4444');
    cx.fillStyle = gr; rrect(x, y, Math.max(h, w * S.chg), h, 9); cx.fill();
  }
  cx.font = '700 17px ' + FONT; cx.textAlign = 'right'; cx.textBaseline = 'middle'; cx.fillStyle = on ? '#fff' : '#8a94ad'; cx.fillText(W.power + (S.chg > 0 ? ' ' + Math.round(S.chg * 100) + '%' : ''), x - 14, y + h / 2 + 1);
}
function overlay() {
  const t = table, w = 640, h = 330, x = 320, y = 190;
  cx.fillStyle = 'rgba(5,8,18,.96)'; rrect(x, y, w, h, 24); cx.fill(); cx.strokeStyle = 'rgba(255,255,255,.2)'; cx.lineWidth = 2; cx.stroke();
  centered('🏆 ' + W.win(t.name(t.order[0])), 640, y + 56, 42, '#fde047');
  fit(S.msg, 24, w - 40, 600); cx.fillStyle = '#c3cbe0'; cx.textAlign = 'center'; cx.fillText(S.msg, 640, y + 106);
  t.order.forEach((s, r) => {
    const yy = y + 160 + r * 52, p = t.points(r);
    cx.fillStyle = t.COLORS[s]; cx.beginPath(); cx.arc(x + 60, yy, 10, 0, 7); cx.fill();
    fit(t.name(s), 28, 260); cx.textAlign = 'left'; cx.fillStyle = r ? '#c3cbe0' : '#fff'; cx.fillText(t.name(s), x + 84, yy);
    cx.textAlign = 'right'; cx.fillStyle = '#9aa5bd'; cx.font = '700 24px ' + FONT; cx.fillText((p > 0 ? '+' : '') + p + ' ' + W.pts, x + w - 150, yy);
    cx.fillStyle = '#fff'; cx.font = '800 30px ' + FONT; cx.fillText(String(t.scores[s]), x + w - 40, yy);
  });
  fit(W.newg, 20, w - 40, 600); cx.fillStyle = '#9aa5bd'; cx.textAlign = 'center'; cx.fillText(W.newg, 640, y + h - 26);
}
function render() {
  const t = table;
  cx.drawImage(bg, 0, 0);
  if (!t.ok) {
    centered(t.pad ? W.old : W.nopad, 640, 330, 40, '#fff'); if (t.pad) centered(W.oldh, 640, 390, 26, '#9aa5bd', 600); return;
  }
  const g = S.g; if (!g) return;
  const cuePlace = S.phase === 'place';
  for (let i = 1; i < 16; i++) if (!g.pot[i]) ballAt(i, g.x[i], g.y[i]);
  if (cuePlace) {
    const ok = !!(g.hand && BD.canPlace(g, S.px, S.py));
    ballAt(0, S.px, S.py, ok ? 1 : 0.4);
    cx.strokeStyle = ok ? '#86efac' : '#f87171'; cx.lineWidth = 3; cx.beginPath(); cx.arc(OX + S.px * SC, OY + S.py * SC, BR + 6, 0, 7); cx.stroke();
    if (g.zone === 'head') { cx.fillStyle = 'rgba(255,255,255,.07)'; cx.fillRect(OX, OY, BD.HEAD * SC, BD.H * SC); }
  } else if (!g.pot[0]) ballAt(0, g.x[0], g.y[0]);
  if (S.phase === 'aim' && !t.done) { guide(g); stick(g, OX + g.x[0] * SC, OY + g.y[0] * SC); powerBar(); }
  else if (S.phase === 'place') powerBar();
  card(0); card(1);
  cx.textAlign = 'center'; cx.textBaseline = 'middle'; cx.fillStyle = '#fff'; cx.font = '800 40px ' + FONT; cx.fillText(t.scores[0] + ' : ' + t.scores[1], 640, 36);
  if (!t.done && t.human(t.turn)) { cx.font = '700 20px ' + FONT; cx.fillStyle = t.left <= 8 ? '#f87171' : '#9aa5bd'; cx.fillText('⏱ ' + Math.max(0, t.left), 640, 66); }
  if (!t.done) { fit(S.msg, 26, 540, 700); cx.fillStyle = '#e5e9f5'; cx.textAlign = 'left'; cx.textBaseline = 'middle'; cx.fillText(S.msg, 74, 699); }
  if (S.bannerT > 0 && S.banner) {
    const a = Math.min(1, S.bannerT * 2);
    cx.globalAlpha = a; cx.fillStyle = 'rgba(30,8,8,.9)'; rrect(330, OY + 18, 620, 56, 16); cx.fill();
    centered(S.banner, 640, OY + 47, 30, '#fca5a5'); cx.globalAlpha = 1;
  }
  if (t.done && t.games) overlay();
}
let last = performance.now();
function frame(now) {
  const dt = Math.min(0.05, (now - last) / 1000); last = now;
  logic(dt); render(); requestAnimationFrame(frame);
}
window.__bida = { S, table, BD };
if (table.ok) table.go();
requestAnimationFrame(frame);
Credit.key = () => table.done;   // Down plays while a game is on
