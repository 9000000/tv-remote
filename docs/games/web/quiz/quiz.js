'use strict';
// A 1-4 player quiz for the TV Games app: the phones are the answer buttons (tvpad.layout choices), the TV shows the question.
// With no phone the TV remote plays alone (arrows pick, OK answers). The best scores are kept on this TV (localStorage).
// Words come in vi / en by the language of the TV (navigator.language); times count ticks, so the Pause card really pauses it.
const VI = (navigator.language || '').toLowerCase().startsWith('vi');
const T = VI ? {
  title: 'Đố vui', join: 'Quét mã QR để vào chơi, 1–4 người', start: 'Nhấn Start trên điện thoại (hoặc OK) để bắt đầu', solo: 'Chơi một mình cũng được',
  q: (i, n) => `Câu ${i} / ${n}`, win: 'Người thắng', again: 'Nhấn Start trên điện thoại hoặc OK để chơi lại', all: 'Tất cả', cat: 'Chủ đề', loading: 'Đang tải câu hỏi…', fail: 'Không tải được câu hỏi. Kiểm tra mạng của TV rồi thử lại.', p: (n) => `Người chơi ${n}`, nobody: 'Không ai đúng',
  right: 'Đúng! +', wrong: 'Sai', late: 'Hết giờ', wait: 'Chờ người khác…', tie: 'Hòa!',
  you: 'Bạn', okSolo: 'Hoặc nhấn OK trên remote để chơi một mình', keys: '▲ ▼ ◀ ▶ chọn · OK trả lời', best: 'Điểm cao', newBest: 'Kỷ lục mới!', score: (n) => `${n} điểm`, okAgain: 'Nhấn OK để chơi lại',
} : {
  title: 'Quiz', join: 'Scan the QR code to join, 1-4 players', start: 'Press Start on a phone (or OK) to begin', solo: 'Playing alone is fine too',
  q: (i, n) => `Question ${i} / ${n}`, win: 'Winner', again: 'Press Start on a phone or OK to play again', all: 'All', cat: 'Topic', loading: 'Loading questions…', fail: 'Could not load the questions. Check the TV\'s network and try again.', p: (n) => `Player ${n}`, nobody: 'Nobody got it',
  right: 'Right! +', wrong: 'Wrong', late: 'Time up', wait: 'Waiting for the others…', tie: 'A tie!',
  you: 'You', okSolo: 'Or press OK on the remote to play alone', keys: '▲ ▼ ◀ ▶ pick · OK answers', best: 'High scores', newBest: 'New record!', score: (n) => `${n} pts`, okAgain: 'Press OK to play again',
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
const score = {}, st = { mode: 'lobby', qs: [], i: 0, left: 0, ans: {}, gain: {}, rev: 0, solo: false, f: 0 };
const ME = 0;                                                     // the TV remote's seat, playing alone
let timer = null, loading = true;

const h = (t, c, x) => { const e = document.createElement(t); if (c) e.className = c; if (x != null) e.textContent = x; return e; };
const col = (p) => PCOL[Math.max(0, p - 1)];
const pname = (p) => { if (p === ME) return T.you; try { return pad.name(p) || T.p(p); } catch (e) { return T.p(p); } };   // the name typed on the phone, else Player N
function who(p, extra) { const e = h('div', 'chip'); e.style.setProperty('--pc', col(p)); e.append(h('span', 'dot'), h('span', '', pname(p) + (extra || ''))); return e; }
function players() { return pad ? pad.players() : []; }
function seats() { return st.solo ? [ME] : players(); }               // who plays this game: the phones, or the remote alone

// the best 10 scores on this TV, any topic, any player: {n name, s score, c topic, d when}
const BEST = 10;
function bestList() { const b = store.get('quiz.best'); return Array.isArray(b) ? b.filter((r) => r && typeof r.s === 'number') : []; }
function addBest(rows) {
  const all = bestList().concat(rows).sort((a, b) => b.s - a.s || a.d - b.d).slice(0, BEST);
  store.set('quiz.best', all); return all;
}
function bestBox(list, mine, n) {
  const box = h('div', 'best'); box.append(h('div', 'bh', '🏆 ' + T.best));
  list.slice(0, n).forEach((r, i) => { const e = h('div', 'br' + (mine.includes(r) ? ' me' : ''));
    e.append(h('span', 'bn', String(i + 1)), h('span', 'bw', r.n), h('span', 'bc', r.c), h('span', 'bs', String(r.s))); box.append(e); });
  return box;
}

function drawBoard() {
  board.replaceChildren(...seats().map((p) => { const e = who(p, ' · ' + (score[p] || 0)); return e; }));
}

function lobby() {
  st.mode = 'lobby'; st.solo = false; clearInterval(timer);
  if (pad) pad.layout('all', { buttons: BANK ? ['L', 'START', 'R'] : ['START'], dpad: false });
  const ps = players();
  const kids = [h('h1', '', T.title), h('p', 'sub', T.join)];
  const w = h('div', 'who'); ps.forEach((p) => w.append(who(p))); kids.push(w);
  if (!BANK) kids.push(h('p', 'sub', loading ? T.loading : T.fail));
  else { const c = h('div', 'cats'); c.append(h('span', 'arr', '◀'), h('span', 'cat', T.cat + ': ' + catName()), h('span', 'arr', '▶')); kids.push(c); }
  if (BANK) kids.push(h('p', 'sub', ps.length ? T.start : T.okSolo));
  const b = bestList(); if (b.length && BANK) kids.push(bestBox(b, [], 3));
  stage.replaceChildren(...kids); drawBoard();
}

function begin(remote) {
  if (!BANK) return;
  st.solo = !players().length;
  if (st.solo && !remote) return;                                  // alone only from the remote's OK
  seats().forEach((p) => { score[p] = 0; });
  st.qs = pick(ROUNDS); st.i = 0;
  if (pad) pad.layout('all', { choices: KEYS });
  Sfx.play('go'); ask();
}

function ask() {
  const q = st.qs[st.i];
  st.mode = 'ask'; st.left = SECS; st.ans = {}; st.gain = {};
  const o = h('div', 'opts');
  q.o[L].forEach((t, k) => { const e = h('div', 'opt'); e.dataset.k = KEYS[k]; e.append(h('b', '', KEYS[k]), h('span', '', t)); o.append(e); });
  const bar = h('div', 'bar'); bar.append(h('i'));
  const got = h('div', 'who'); got.id = 'got';
  stage.replaceChildren(h('div', 'num', T.q(st.i + 1, st.qs.length) + (st.solo ? '   ·   ' + T.keys : '')), h('div', 'q', q.q[L]), o, bar, got, h('div', 'timer', String(SECS)));
  if (st.solo) { st.f = 0; focusOpt(); }
  if (pad) pad.say('all', '');
  tickUi(); drawBoard();
  clearInterval(timer); timer = setInterval(() => { st.left = Math.max(0, st.left - TICK); tickUi(); if (st.left <= 0) reveal(); }, TICK * 1000);
}

function tickUi() {
  const b = stage.querySelector('.bar i'), t = stage.querySelector('.timer');
  if (b) b.style.width = (st.left / SECS * 100) + '%';
  if (t) { const n = Math.ceil(st.left); if (t.textContent !== String(n) && n <= 3 && st.mode === 'ask') Sfx.play('tick'); t.textContent = String(n); }   // the last three seconds tick
  const got = document.getElementById('got');      // who has answered: a dot per player, not what
  if (got) got.replaceChildren(...Object.keys(st.ans).map((p) => { const d = h('span', 'dot'); d.style.setProperty('--pc', col(p)); return d; }));
}

function focusOpt() { stage.querySelectorAll('.opt').forEach((e, k) => e.classList.toggle('f', k === st.f)); }
function answer(p, k) {
  if (st.mode !== 'ask' || st.ans[p] != null || !seats().includes(p)) return;
  st.ans[p] = { k, left: st.left }; Sfx.play('ui');
  if (pad && p !== ME) pad.say(p, T.wait);
  tickUi();
  if (seats().every((x) => st.ans[x] != null)) reveal();
}

function reveal() {
  if (st.mode !== 'ask') return;
  clearInterval(timer); st.mode = 'reveal';
  const q = st.qs[st.i], right = KEYS[q.c];
  seats().forEach((p) => {
    const a = st.ans[p];
    if (a && a.k === right) { st.gain[p] = 100 + Math.round(a.left / SECS * 100); score[p] = (score[p] || 0) + st.gain[p]; if (pad && p !== ME) { pad.say(p, T.right + st.gain[p]); pad.vibrate(p, 120); } }
    else { st.gain[p] = 0; if (pad && p !== ME) { pad.say(p, a ? T.wrong : T.late); pad.vibrate(p, 450); } }
  });
  Sfx.play(seats().some((p) => st.gain[p]) ? 'right' : 'bad');          // one sound for the whole table
  stage.querySelectorAll('.opt').forEach((e) => e.classList.add(e.dataset.k === right ? 'right' : 'wrong'));
  const t = stage.querySelector('.timer'); if (t) t.remove();
  const rows = h('div', 'who');
  if (st.solo) { const a = st.ans[ME]; stage.querySelectorAll('.opt').forEach((e) => e.classList.toggle('mine', !!a && e.dataset.k === a.k)); }
  seats().forEach((p) => { const g = st.gain[p]; const e = who(p); const s = h('span', g ? 'plus' : 'minus', g ? '+' + g : (st.ans[p] ? '✗' : '·')); e.append(s); rows.append(e); });
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
  st.mode = 'end'; Sfx.play('win');
  const ps = seats().slice().sort((a, b) => (score[b] || 0) - (score[a] || 0));
  const top = ps.length ? score[ps[0]] || 0 : 0, tied = ps.filter((p) => (score[p] || 0) === top).length > 1;
  const rank = h('div', 'rank');
  ps.forEach((p) => { const r = h('div', 'row' + ((score[p] || 0) === top && ps.length > 1 ? ' win' : '')); r.style.setProperty('--pc', col(p));
    r.append(h('span', 'dot'), h('span', '', (score[p] === top && ps.length > 1 ? '🏆 ' : '') + pname(p)), h('span', '', String(score[p] || 0))); rank.append(r); });
  const cat = catName(), d = Date.now();
  const mine = ps.filter((p) => (score[p] || 0) > 0).map((p) => ({ n: pname(p), s: score[p], c: cat, d }));
  const list = addBest(mine), kept = list.filter((r) => mine.includes(r));
  const record = kept.length && list[0] === kept[0] && list.length > kept.length;   // beat what was there before
  const head = st.solo ? h('h1', '', T.score(top)) : h('h1', 'one', ps.length > 1 ? (tied ? T.tie : T.win + ': ' + pname(ps[0])) : T.title);
  const body = h('div', 'end');
  if (!st.solo) body.append(rank);
  body.append(bestBox(list, kept, st.solo ? BEST : 5));
  stage.replaceChildren(head, ...(record ? [h('p', 'rec', T.newBest)] : []), body, h('p', 'sub', st.solo ? T.okAgain : T.again));
  if (pad) { pad.layout('all', { buttons: ['START'], dpad: false }); pad.say('all', ''); ps.filter((p) => p !== ME).forEach((p) => pad.vibrate(p, (score[p] || 0) === top ? 600 : 100)); }
  drawBoard();
}

if (pad) {
  pad.on('join', ({ player }) => { score[player] = score[player] || 0; if (st.mode === 'lobby') lobby(); else if (!st.solo) drawBoard(); });
  pad.on('leave', () => { if (st.solo) { if (st.mode === 'lobby') lobby(); return; }       // a phone that came and went: the remote plays on
    if (!players().length && st.mode !== 'lobby') lobby(); else if (st.mode === 'lobby') lobby(); else { drawBoard(); if (st.mode === 'ask' && players().length && players().every((x) => st.ans[x] != null)) reveal(); } });
  pad.on('button', ({ player, btn, down }) => {
    if (!down) return;
    if (btn === 'START' && (st.mode === 'lobby' || (st.mode === 'end' && (!st.solo || players().length)))) { begin(); return; }
    if ((btn === 'L' || btn === 'R') && st.mode === 'lobby') { setCat(btn === 'L' ? -1 : 1); return; }
    if (KEYS.includes(btn)) answer(player, btn);
  });
}
// the TV remote: OK starts (the page gets Enter); alone, the arrows move over the 2x2 answers and OK answers (A-D on a keyboard)
const MOVE = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
document.addEventListener('keydown', (e) => {
  if (e.repeat && e.key === 'Enter') return;                        // a held OK must not answer, then start again
  if (st.solo && st.mode === 'ask') {
    const m = MOVE[e.key], k = (e.key || '').toUpperCase();
    if (m) { const x = (st.f % 2 + m[0] + 2) % 2, y = ((st.f >> 1) + m[1] + 2) % 2; st.f = y * 2 + x; Sfx.play('move'); focusOpt(); e.preventDefault(); }
    else if (e.key === 'Enter') answer(ME, KEYS[st.f]);
    else if (KEYS.includes(k)) answer(ME, k);
    return;
  }
  if (st.solo && st.mode === 'reveal' && e.key === 'Enter') { next(); return; }   // no waiting for the others
  if (e.key === 'Enter' && (st.mode === 'lobby' || st.mode === 'end')) begin(true);
  else if ((e.key === 'ArrowLeft' || e.key === 'ArrowRight') && st.mode === 'lobby') setCat(e.key === 'ArrowLeft' ? -1 : 1);
});
lobby();
loadBank().then((b) => { BANK = b; loading = false; if (catIdx > (b ? b.categories.length : 0)) catIdx = 0; if (st.mode === 'lobby') lobby(); });
