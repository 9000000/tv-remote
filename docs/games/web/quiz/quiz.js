'use strict';
// A 2-4 player quiz for the TV Games app: the phones are the answer buttons (tvpad.layout choices), the TV shows the question.
// Words come in vi / en by the language of the TV (navigator.language); times count ticks, so the Pause card really pauses it.
const VI = (navigator.language || '').toLowerCase().startsWith('vi');
const T = VI ? {
  title: 'Đố vui', join: 'Quét mã QR để vào chơi, 1–4 người', start: 'Nhấn Start trên điện thoại (hoặc OK) để bắt đầu', solo: 'Chơi một mình cũng được',
  q: (i, n) => `Câu ${i} / ${n}`, win: 'Người thắng', again: 'Nhấn Start để chơi lại', all: 'Tất cả', cat: 'Chủ đề', loading: 'Đang tải câu hỏi…', fail: 'Không tải được câu hỏi. Kiểm tra mạng của TV rồi thử lại.', p: (n) => `Người chơi ${n}`, pts: 'điểm', nobody: 'Không ai đúng', nogame: 'Cần mở trong app TV Games',
  right: 'Đúng! +', wrong: 'Sai', late: 'Hết giờ', wait: 'Chờ người khác…', tie: 'Hòa!',
} : {
  title: 'Quiz', join: 'Scan the QR code to join, 1-4 players', start: 'Press Start on a phone (or OK) to begin', solo: 'Playing alone is fine too',
  q: (i, n) => `Question ${i} / ${n}`, win: 'Winner', again: 'Press Start to play again', all: 'All', cat: 'Topic', loading: 'Loading questions…', fail: 'Could not load the questions. Check the TV\'s network and try again.', p: (n) => `Player ${n}`, pts: 'pts', nobody: 'Nobody got it', nogame: 'Open this in the TV Games app',
  right: 'Right! +', wrong: 'Wrong', late: 'Time up', wait: 'Waiting for the others…', tie: 'A tie!',
};
// The question bank is data next to this page (bank.json: {categories, questions}): fetched fresh, the last good copy kept in localStorage.
const store = { get(k) { try { return JSON.parse(localStorage.getItem(k)); } catch (e) { return null; } }, set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { } } };
let BANK = null, catIdx = store.get('quiz.cat') || 0;      // catIdx 0 = every topic, i = BANK.categories[i - 1]
const okBank = (b) => b && Array.isArray(b.questions) && b.questions.length >= 10 && Array.isArray(b.categories);
async function loadBank() {
  const src = (new URLSearchParams(location.search)).get('bank') || 'bank.json';     // ?bank= only for testing a bank before it is published
  const ctl = new AbortController(), t = setTimeout(() => ctl.abort(), 3000);
  try { const r = await fetch(src, { signal: ctl.signal, cache: 'no-cache' }); const b = await r.json(); if (okBank(b)) { store.set('quiz.bank', b); return b; } } catch (e) { } finally { clearTimeout(t); }
  const c = store.get('quiz.bank'); return okBank(c) ? c : null;
}
// N questions: from the chosen topic or (all topics) spread over them, skipping the ones seen in the last rounds
function pick(n) {
  const cat = catIdx ? BANK.categories[catIdx - 1].id : null;
  const pool = BANK.questions.filter((q) => !cat || q.cat === cat);
  let seen = store.get('quiz.seen') || [];
  let fresh = pool.filter((q) => !seen.includes(q.id));
  if (fresh.length < n) { seen = seen.filter((id) => !pool.some((q) => q.id === id)); fresh = pool.slice(); }
  fresh.sort(() => Math.random() - 0.5);
  const out = [], per = {}, cap = cat ? n : 2;
  for (const q of fresh) { if (out.length >= n) break; if ((per[q.cat] || 0) < cap) { per[q.cat] = (per[q.cat] || 0) + 1; out.push(q); } }
  for (const q of fresh) { if (out.length >= n) break; if (!out.includes(q)) out.push(q); }
  store.set('quiz.seen', seen.concat(out.map((q) => q.id)).slice(-150));
  return out;
}
function catName() { return catIdx ? BANK.categories[catIdx - 1][VI ? 'vi' : 'en'] : T.all; }
function setCat(d) { if (!BANK) return; const n = BANK.categories.length + 1; catIdx = (catIdx + d + n) % n; store.set('quiz.cat', catIdx); lobby(); }
const ROUNDS = 10, SECS = 15, TICK = 0.1, REVEAL = 4.5;
const L = VI ? 0 : 1, KEYS = ['A', 'B', 'C', 'D'], PCOL = ['#3b82f6', '#ef4444', '#22c55e', '#eab308'];
const stage = document.getElementById('stage'), board = document.getElementById('board');
const pad = window.tvpad;
const score = {}, st = { mode: 'lobby', qs: [], i: 0, left: 0, ans: {}, gain: {}, rev: 0 };
let timer = null, loading = true;

const h = (t, c, x) => { const e = document.createElement(t); if (c) e.className = c; if (x != null) e.textContent = x; return e; };
const col = (p) => PCOL[p - 1];
const pname = (p) => { try { return pad.name(p) || T.p(p); } catch (e) { return T.p(p); } };   // the name typed on the phone, else Player N
function who(p, extra) { const e = h('div', 'chip'); e.style.setProperty('--pc', col(p)); e.append(h('span', 'dot'), h('span', '', pname(p) + (extra || ''))); return e; }
function players() { return pad ? pad.players() : []; }

function drawBoard() {
  board.replaceChildren(...players().map((p) => { const e = who(p, ' · ' + (score[p] || 0)); return e; }));
}

function lobby() {
  st.mode = 'lobby'; clearInterval(timer);
  if (pad) pad.layout('all', { buttons: BANK ? ['L', 'START', 'R'] : ['START'], dpad: false });
  const ps = players();
  const kids = [h('h1', '', T.title), h('p', 'sub', T.join)];
  const w = h('div', 'who'); ps.forEach((p) => w.append(who(p))); kids.push(w);
  if (!BANK) kids.push(h('p', 'sub', loading ? T.loading : T.fail));
  else { const c = h('div', 'cats'); c.append(h('span', 'arr', '◀'), h('span', 'cat', T.cat + ': ' + catName()), h('span', 'arr', '▶')); kids.push(c); }
  if (ps.length && BANK) kids.push(h('p', 'sub', T.start));
  if (!pad) kids.push(h('p', 'sub', T.nogame));
  stage.replaceChildren(...kids); drawBoard();
}

function begin() {
  const ps = players();
  if (!ps.length) return;
  ps.forEach((p) => { score[p] = 0; });
  if (!BANK) return;
  st.qs = pick(ROUNDS); st.i = 0;
  if (pad) pad.layout('all', { choices: KEYS });
  ask();
}

function ask() {
  const q = st.qs[st.i];
  st.mode = 'ask'; st.left = SECS; st.ans = {}; st.gain = {};
  const o = h('div', 'opts');
  q.o[L].forEach((t, k) => { const e = h('div', 'opt'); e.dataset.k = KEYS[k]; e.append(h('b', '', KEYS[k]), h('span', '', t)); o.append(e); });
  const bar = h('div', 'bar'); bar.append(h('i'));
  const got = h('div', 'who'); got.id = 'got';
  stage.replaceChildren(h('div', 'num', T.q(st.i + 1, st.qs.length)), h('div', 'q', q.q[L]), o, bar, got, h('div', 'timer', String(SECS)));
  if (pad) pad.say('all', '');
  tickUi(); drawBoard();
  clearInterval(timer); timer = setInterval(() => { st.left = Math.max(0, st.left - TICK); tickUi(); if (st.left <= 0) reveal(); }, TICK * 1000);
}

function tickUi() {
  const b = stage.querySelector('.bar i'), t = stage.querySelector('.timer');
  if (b) b.style.width = (st.left / SECS * 100) + '%';
  if (t) t.textContent = String(Math.ceil(st.left));
  const got = document.getElementById('got');      // who has answered: a dot per player, not what
  if (got) got.replaceChildren(...Object.keys(st.ans).map((p) => { const d = h('span', 'dot'); d.style.setProperty('--pc', col(p)); return d; }));
}

function answer(p, k) {
  if (st.mode !== 'ask' || st.ans[p] != null || !(p in score)) return;
  st.ans[p] = { k, left: st.left };
  if (pad) pad.say(p, T.wait);
  tickUi();
  if (players().every((x) => st.ans[x] != null)) reveal();
}

function reveal() {
  if (st.mode !== 'ask') return;
  clearInterval(timer); st.mode = 'reveal';
  const q = st.qs[st.i], right = KEYS[q.c];
  players().forEach((p) => {
    const a = st.ans[p];
    if (a && a.k === right) { st.gain[p] = 100 + Math.round(a.left / SECS * 100); score[p] = (score[p] || 0) + st.gain[p]; if (pad) { pad.say(p, T.right + st.gain[p]); pad.vibrate(p, 120); } }
    else { st.gain[p] = 0; if (pad) { pad.say(p, a ? T.wrong : T.late); pad.vibrate(p, 450); } }
  });
  stage.querySelectorAll('.opt').forEach((e) => e.classList.add(e.dataset.k === right ? 'right' : 'wrong'));
  const t = stage.querySelector('.timer'); if (t) t.remove();
  const rows = h('div', 'who');
  players().forEach((p) => { const g = st.gain[p]; const e = who(p); const s = h('span', g ? 'plus' : 'minus', g ? '+' + g : (st.ans[p] ? '✗' : '·')); e.append(s); rows.append(e); });
  const g = document.getElementById('got'); if (g) g.remove();
  stage.append(rows); drawBoard();
  st.rev = REVEAL; clearInterval(timer);
  timer = setInterval(() => { st.rev -= TICK; if (st.rev <= 0) next(); }, TICK * 1000);
}

function next() {
  clearInterval(timer);
  if (++st.i < st.qs.length) ask(); else finish();
}

function finish() {
  st.mode = 'end';
  const ps = players().slice().sort((a, b) => (score[b] || 0) - (score[a] || 0));
  const top = ps.length ? score[ps[0]] || 0 : 0, tied = ps.filter((p) => (score[p] || 0) === top).length > 1;
  const rank = h('div', 'rank');
  ps.forEach((p) => { const r = h('div', 'row' + ((score[p] || 0) === top && ps.length > 1 ? ' win' : '')); r.style.setProperty('--pc', col(p));
    r.append(h('span', 'dot'), h('span', '', (score[p] === top && ps.length > 1 ? '🏆 ' : '') + pname(p)), h('span', '', (score[p] || 0) + ' ' + T.pts)); rank.append(r); });
  stage.replaceChildren(h('h1', '', ps.length > 1 ? (tied ? T.tie : T.win + ': ' + T.p(ps[0])) : T.title), rank, h('p', 'sub', T.again));
  if (pad) { pad.layout('all', { buttons: ['START'], dpad: false }); pad.say('all', ''); ps.forEach((p, k) => pad.vibrate(p, (score[p] || 0) === top ? 600 : 100)); }
  drawBoard();
}

if (pad) {
  pad.on('join', ({ player }) => { score[player] = score[player] || 0; if (st.mode === 'lobby') lobby(); else drawBoard(); });
  pad.on('leave', () => { if (!players().length && st.mode !== 'lobby') lobby(); else if (st.mode === 'lobby') lobby(); else { drawBoard(); if (st.mode === 'ask' && players().length && players().every((x) => st.ans[x] != null)) reveal(); } });
  pad.on('button', ({ player, btn, down }) => {
    if (!down) return;
    if (btn === 'START' && (st.mode === 'lobby' || st.mode === 'end')) { begin(); return; }
    if ((btn === 'L' || btn === 'R') && st.mode === 'lobby') { setCat(btn === 'L' ? -1 : 1); return; }
    if (KEYS.includes(btn)) answer(player, btn);
  });
}
// the TV remote: OK starts (the page gets Enter), and A-D by keyboard for trying it on a computer
document.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && (st.mode === 'lobby' || st.mode === 'end')) begin();
  else if ((e.key === 'ArrowLeft' || e.key === 'ArrowRight') && st.mode === 'lobby') setCat(e.key === 'ArrowLeft' ? -1 : 1);
});
lobby();
loadBank().then((b) => { BANK = b; loading = false; if (catIdx > (b ? b.categories.length : 0)) catIdx = 0; if (st.mode === 'lobby') lobby(); });
