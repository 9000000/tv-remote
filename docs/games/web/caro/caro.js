'use strict';
// Gomoku (cờ caro) for TV Games: one board on the TV, 1 phone plays the bot, 2 phones play each other, the others watch.
// Rules and the bot are in ai.js (node-tested); this file is the screen, the pads and the turns.
const VI = (navigator.language || '').toLowerCase().startsWith('vi');
const T = VI ? {
  title: 'Cờ caro', rule: 'Đủ 5 quân liền nhau là thắng', lobbyHint: 'Hoặc nhấn OK để chơi với máy', p: (n) => `Người chơi ${n}`, bot: 'Máy',
  turn: (n) => `Lượt của ${n}`, yours: 'Đến lượt bạn', think: 'Máy đang nghĩ…', win: (n) => `${n} thắng!`, draw: 'Hòa!',
  again: 'Nhấn A hoặc Start trên điện thoại (hoặc OK) để chơi ván mới', watch: 'Hai người đang chơi, bạn xem nhé', watching: (n) => `${n} người đang xem`, first: 'đi trước',
} : {
  title: 'Gomoku', rule: 'Five in a row wins', lobbyHint: 'Or press OK to play the computer', p: (n) => `Player ${n}`, bot: 'Computer',
  turn: (n) => `${n}'s turn`, yours: 'Your turn', think: 'Thinking…', win: (n) => `${n} wins!`, draw: 'A draw!',
  again: 'Press A or Start on a phone (or OK) for a new game', watch: 'Two players are playing, enjoy watching', watching: (n) => `${n} watching`, first: 'goes first',
};
const N = Caro.N, BOT_MS = 500, HOLD_FIRST = 280, HOLD_EVERY = 90;
const CUR = ['#0e9f4a', '#7c3aed'], XC = '#2563eb', OC = '#dc2626';       // cursors read on the tan board and next to both stones
const pad = window.tvpad;
const cv = document.getElementById('cv'), side = document.getElementById('side'), ctx = cv.getContext('2d');
const g = { b: Caro.create(), seat: [1, 0], cur: [{ x: 7, y: 7 }, { x: 7, y: 7 }], starter: 0, ts: 0, last: -1, wl: null, over: false, wait: false, wins: [0, 0], draw: false };
let botTimer = 0, endAt = 0, lobbyDone = false;   // lobbyDone: nobody on a phone and OK was pressed: the remote plays the computer
const holds = {};
const h = (t, c, x) => { const e = document.createElement(t); if (c) e.className = c; if (x != null) e.textContent = x; return e; };
const players = () => (pad ? pad.players() : [1]);
const pname = (p) => { try { return (pad && pad.name(p)) || T.p(p); } catch (e) { return T.p(p); } };
const markOf = (s) => (s === g.starter ? 1 : 2);
const sname = (s) => (g.seat[s] ? pname(g.seat[s]) : T.bot);
const say = (p, t) => { if (pad && p) pad.say(p, t); };
const buzz = (p, ms) => { if (pad && p) pad.vibrate(p, ms); };

function layouts() {
  if (!pad) return;
  players().forEach((p) => pad.layout(p, g.seat.includes(p) ? { buttons: ['A', 'START'], dpad: true } : { buttons: [], dpad: false }));
}
function tell() {                       // the line on each phone
  players().forEach((p) => {
    const s = g.seat.indexOf(p);
    say(p, s < 0 ? T.watch : g.over ? T.again : g.ts === s ? T.yours : '');
  });
}

function newGame() {
  clearTimeout(botTimer);
  if (g.over) g.starter ^= 1;             // the other one starts the next game (flipped here, so the marks on screen stay until then)
  const ps = players();
  g.seat = pad && ps.length ? [ps[0] || 0, ps[1] || 0] : [1, 0];   // no phone: seat 1 is the TV remote (Enter / arrows), against the computer
  g.wait = false;
  g.b = Caro.create(); g.last = -1; g.wl = null; g.over = false; g.draw = false; g.ts = g.starter;
  g.cur.forEach((c) => { c.x = 7; c.y = 7; });
  layouts(); tell(); render(); maybeBot();
}
function endTurnOrGame(i) {
  const s = g.ts;
  g.wl = Caro.winLine(g.b, i);
  if (g.wl) { g.over = true; g.wins[s]++; Sfx.play(g.seat[s] ? 'win' : 'lose'); players().forEach((p) => buzz(p, g.seat[s] === p ? 600 : 150)); }
  else if (Caro.isFull(g.b)) { g.over = true; g.draw = true; Sfx.play('bad'); }
  if (g.over) endAt = performance.now();
  else { g.ts ^= 1; buzz(g.seat[g.ts], 80); if (g.seat[g.ts] && !g.seat[s]) Sfx.play('turn'); }   // a bot just moved: tell the human it is their turn
  tell(); render(); maybeBot();
}
function place(i) {
  g.b[i] = markOf(g.ts); g.last = i; Sfx.play('place');
  endTurnOrGame(i);
}
function maybeBot() {
  clearTimeout(botTimer);
  if (g.over || g.wait || g.seat[g.ts]) return;
  botTimer = setTimeout(() => {
    if (g.over || g.wait || g.seat[g.ts]) return;
    const m = Caro.bestMove(g.b, markOf(g.ts));
    if (m >= 0) place(m);
  }, BOT_MS);
}

function move(s, dx, dy) {
  const c = g.cur[s]; c.x = Math.max(0, Math.min(N - 1, c.x + dx)); c.y = Math.max(0, Math.min(N - 1, c.y + dy)); Sfx.play('move'); drawBoard();   // the side panel is unchanged by a cursor step: no DOM rebuild per step
}
const DIR = { LEFT: [-1, 0], RIGHT: [1, 0], UP: [0, -1], DOWN: [0, 1] };
function hold(s, btn, down, noStep) {      // a held D-pad repeats; noStep: the key already took the first step
  const k = s + btn;
  if (holds[k]) { clearTimeout(holds[k].t); clearInterval(holds[k].i); } delete holds[k];
  if (!down) return;
  if (!noStep) move(s, ...DIR[btn]);
  const o = holds[k] = {};
  o.t = setTimeout(() => { o.i = setInterval(() => move(s, ...DIR[btn]), HOLD_EVERY); }, HOLD_FIRST);
}
function press(s, btn, down, noStep) {
  if (btn === 'START') { if (down && g.over) newGame(); return; }
  if (DIR[btn]) { hold(s, btn, down, noStep); return; }
  if (btn === 'A' && down && g.over) { if (performance.now() - endAt > 1200) newGame(); return; }   // A also deals again, but not on the press that placed the winning stone
  if (btn !== 'A' || !down || g.over || g.wait || g.ts !== s || !g.seat[s]) return;
  const c = g.cur[s], i = c.y * N + c.x;
  if (g.b[i]) { buzz(g.seat[s], 40); Sfx.play('bad'); } else place(i);
}

if (pad) {
  pad.on('button', ({ player, btn, down }) => {
    const dup = player === 1 && down && !!DIR[btn] && twice('pad', btn);   // still held: it repeats from here
    const s = g.seat.indexOf(player); if (s >= 0) press(s, btn, down, dup); else if (btn === 'START' && down && g.over) newGame();
  });
  // the stick: 8 ways like the D-pad (GameServer.dirs: dead zone 0.35, 45° each), held = repeats
  const stick = {};
  pad.on('axis', ({ player, x, y }) => {
    const now = new Set();
    if (Math.hypot(x, y) >= 0.35) {
      const a = Math.atan2(y, x) * 180 / Math.PI;
      if (a > -67.5 && a < 67.5) now.add('RIGHT'); if (a > 112.5 || a < -112.5) now.add('LEFT');
      if (a > 22.5 && a < 157.5) now.add('DOWN'); if (a < -22.5 && a > -157.5) now.add('UP');
    }
    const was = stick[player] || new Set(); stick[player] = now;
    const s = g.seat.indexOf(player); if (s < 0) return;
    was.forEach((d) => { if (!now.has(d)) press(s, d, false); });
    now.forEach((d) => { if (!was.has(d)) press(s, d, true, player === 1 && twice('pad', d)); });
  });
  if (pad.ownKeys) pad.ownKeys();       // every phone is read here: player 1's presses must not come back as arrow keys too
  pad.on('join', ({ player }) => {
    if (g.wait || (g.seat[0] === 1 && players().length === 1 && g.seat[1] === 0 && !g.b.some((v) => v))) { newGame(); return; }   // the first phone starts clean
    const s = g.seat.indexOf(0);          // a phone takes over the computer's seat; the game goes on
    if (s >= 0 && !g.seat.includes(player)) { g.seat[s] = player; clearTimeout(botTimer); }
    layouts(); tell(); render(); maybeBot();
  });
  pad.on('leave', ({ player }) => {
    const s = g.seat.indexOf(player);
    if (s >= 0) { g.seat[s] = 0; const w = players().find((q) => q !== player && !g.seat.includes(q)); if (w) g.seat[s] = w;   // a watcher sits down; else the computer plays on
      Object.keys(holds).filter((k) => k[0] === String(s)).forEach((k) => hold(s, k.slice(1), false)); }
    if (!g.seat[0] && !g.seat[1]) { if (players().length) g.wait = true; else g.seat[0] = 1; }   // the remote takes seat 1 back
    layouts(); tell(); render(); maybeBot();
  });
  pad.on('rename', () => render());
}
const KEY = { ArrowLeft: 'LEFT', ArrowRight: 'RIGHT', ArrowUp: 'UP', ArrowDown: 'DOWN' };
// The remote steps once per press and repeats on the system's own key repeat, not hold()'s timer: an IR remote (FPT Play's) sends
// the key up only some 300 ms after a short press, past HOLD_FIRST, so one press moved two cells
let keyAt = 0;
// A TV Games build before ownKeys (tvpad.js) also turns player 1's phone presses into arrow keys: the press comes twice, from
// the pad and as a key, in either order. The second within 150 ms is dropped
const lastDir = {};
function twice(src, dir) {
  const now = performance.now(), o = lastDir[dir]; lastDir[dir] = { src, t: now };
  return !!o && o.src !== src && now - o.t < 150;
}
document.addEventListener('keydown', (e) => {
  if (lobbyOn()) { e.preventDefault(); if (e.key === 'Enter' && !e.repeat && !g.wait) { lobbyDone = true; render(); } return; }   // the lobby takes the keys: OK starts against the computer
  if (e.key === 'Enter') { if (g.over) { if (performance.now() - endAt > 1200) newGame(); } else press(0, 'A', true); }
  else if (KEY[e.key]) {
    e.preventDefault();
    if (e.repeat ? e.timeStamp - keyAt < HOLD_EVERY : twice('key', KEY[e.key])) return;
    keyAt = e.timeStamp; move(0, ...DIR[KEY[e.key]]);
  }
});

// ---- drawing
function render() { drawBoard(); drawSide(); }
function drawBoard() {
  const dpr = window.devicePixelRatio || 1, px = Math.round(cv.clientHeight * dpr) || 720;
  if (cv.width !== px) { cv.width = px; cv.height = px; }
  const u = px / (N + 1), at = (k) => (k + 1) * u;
  ctx.clearRect(0, 0, px, px);
  ctx.fillStyle = '#d9b26b'; ctx.fillRect(0, 0, px, px);
  ctx.strokeStyle = '#6b4a1c'; ctx.lineWidth = Math.max(1, u * 0.05);
  ctx.beginPath();
  for (let k = 0; k < N; k++) { ctx.moveTo(at(0), at(k)); ctx.lineTo(at(N - 1), at(k)); ctx.moveTo(at(k), at(0)); ctx.lineTo(at(k), at(N - 1)); }
  ctx.stroke();
  ctx.fillStyle = '#6b4a1c';
  [3, 7, 11].forEach((a) => [3, 7, 11].forEach((b) => { ctx.beginPath(); ctx.arc(at(a), at(b), u * 0.1, 0, 7); ctx.fill(); }));
  const win = new Set(g.wl || []);
  for (let i = 0; i < g.b.length; i++) {
    const v = g.b[i]; if (!v) continue;
    const x = at(i % N), y = at((i / N) | 0);
    if (win.has(i)) { ctx.fillStyle = '#facc15'; ctx.beginPath(); ctx.arc(x, y, u * 0.56, 0, 7); ctx.fill(); }
    ctx.fillStyle = v === 1 ? XC : OC; ctx.beginPath(); ctx.arc(x, y, u * 0.4, 0, 7); ctx.fill();
    ctx.strokeStyle = '#fff'; ctx.lineWidth = u * 0.09; ctx.lineCap = 'round'; ctx.beginPath();
    if (v === 1) { const r = u * 0.17; ctx.moveTo(x - r, y - r); ctx.lineTo(x + r, y + r); ctx.moveTo(x + r, y - r); ctx.lineTo(x - r, y + r); }
    else ctx.arc(x, y, u * 0.17, 0, 7);
    ctx.stroke();
    if (i === g.last) { ctx.strokeStyle = '#111'; ctx.lineWidth = u * 0.07; ctx.beginPath(); ctx.arc(x, y, u * 0.5, 0, 7); ctx.stroke(); }
  }
  if (g.wl) {
    ctx.strokeStyle = '#111'; ctx.lineWidth = u * 0.1; ctx.beginPath();
    const a = g.wl[0], z = g.wl[g.wl.length - 1];
    ctx.moveTo(at(a % N), at((a / N) | 0)); ctx.lineTo(at(z % N), at((z / N) | 0)); ctx.stroke();
  }
  if (!g.over && !g.wait) for (let n = 0; n < 2; n++) {          // each human's cursor; the one to move is thicker and drawn last, so two on one cell both show
    const s = n ? g.ts : g.ts ^ 1;
    if (!g.seat[s]) continue;
    const c = g.cur[s], x = at(c.x), y = at(c.y), r = u * (g.ts === s ? 0.5 : 0.6), k = u * 0.28;
    ctx.strokeStyle = CUR[s]; ctx.lineWidth = u * (g.ts === s ? 0.12 : 0.07); ctx.lineCap = 'square';
    ctx.beginPath();
    [[-1, -1], [1, -1], [1, 1], [-1, 1]].forEach(([sx, sy]) => { ctx.moveTo(x + sx * r, y + sy * (r - k)); ctx.lineTo(x + sx * r, y + sy * r); ctx.lineTo(x + sx * (r - k), y + sy * r); });
    ctx.stroke();
  }
}

// the lobby shows before the first stone with no phone on (the remote would play the computer), or while phones wait for a seat
const lobbyOn = () => !!pad && (g.wait || (!lobbyDone && !players().length && !g.b.some((v) => v) && !g.over));
function drawSide() {
  const kids = [h('h1', '', T.title), h('p', 'sub', T.rule)];
  if (lobbyOn()) { side.replaceChildren(); Lobby.set({ title: T.title, tag: T.rule, min: 0, go: g.wait ? false : undefined, hints: [T.lobbyHint],
    chips: players().map((p) => ({ id: 'p' + p, name: pname(p), color: CUR[Math.min(1, p - 1)], label: 'P' + p })) }); return; }
  Lobby.hide();
  {
    for (let s = 0; s < 2; s++) {
      const m = markOf(s), e = h('div', 'seat' + (!g.over && g.ts === s ? ' turn' : ''));
      e.style.setProperty('--pc', CUR[s]);
      e.append(h('span', 'mk ' + (m === 1 ? 'x' : 'o'), m === 1 ? '✕' : '○'), h('span', 'nm', sname(s)));
      if (s === g.starter && !g.over && !g.b.some((v) => v)) e.append(h('span', 'first', T.first));   // its own tag: a long name is cut, never this
      if (g.seat[s]) { const d = h('span', 'cur'); e.append(d); }
      e.append(h('span', 'sc', String(g.wins[s])));
      kids.push(e);
    }
    const win = g.over && !g.draw ? g.ts : -1;           // the winner is the one who just moved (ts is not flipped when the game ends)
    const msg = g.over ? (g.draw ? T.draw : T.win(sname(win))) : (g.seat[g.ts] ? T.turn(sname(g.ts)) : T.think);
    kids.push(h('p', 'msg' + (g.over && !g.draw ? ' win' : ''), msg));
    if (g.over) kids.push(h('p', 'sub', T.again));
    const w = players().filter((p) => !g.seat.includes(p)).length;
    if (w) kids.push(h('p', 'sub', T.watching(w)));
  }
  side.replaceChildren(...kids);
}

window.addEventListener('resize', render);
newGame();
Credit.key = () => g.over;   // Down plays while a game is on
