'use strict';
// A 2-4 player quiz for the TV Games app: the phones are the answer buttons (tvpad.layout choices), the TV shows the question.
// Words come in vi / en by the language of the TV (navigator.language); times count ticks, so the Pause card really pauses it.
const VI = (navigator.language || '').toLowerCase().startsWith('vi');
const T = VI ? {
  title: 'Đố vui', join: 'Quét mã QR để vào chơi, 1–4 người', start: 'Nhấn Start trên điện thoại (hoặc OK) để bắt đầu', solo: 'Chơi một mình cũng được',
  q: (i, n) => `Câu ${i} / ${n}`, win: 'Người thắng', again: 'Nhấn Start để chơi lại', p: (n) => `Người chơi ${n}`, pts: 'điểm', nobody: 'Không ai đúng', nogame: 'Cần mở trong app TV Games',
  right: 'Đúng! +', wrong: 'Sai', late: 'Hết giờ', wait: 'Chờ người khác…', tie: 'Hòa!',
} : {
  title: 'Quiz', join: 'Scan the QR code to join, 1-4 players', start: 'Press Start on a phone (or OK) to begin', solo: 'Playing alone is fine too',
  q: (i, n) => `Question ${i} / ${n}`, win: 'Winner', again: 'Press Start to play again', p: (n) => `Player ${n}`, pts: 'pts', nobody: 'Nobody got it', nogame: 'Open this in the TV Games app',
  right: 'Right! +', wrong: 'Wrong', late: 'Time up', wait: 'Waiting for the others…', tie: 'A tie!',
};
// q = question, o = the four answers (A B C D), c = index of the right one
const Q = [
  { q: ['Thủ đô của Úc là gì?', 'What is the capital of Australia?'], o: [['Sydney', 'Canberra', 'Melbourne', 'Perth'], ['Sydney', 'Canberra', 'Melbourne', 'Perth']], c: 1 },
  { q: ['Hành tinh nào gần Mặt Trời nhất?', 'Which planet is closest to the Sun?'], o: [['Sao Kim', 'Trái Đất', 'Sao Thủy', 'Sao Hỏa'], ['Venus', 'Earth', 'Mercury', 'Mars']], c: 2 },
  { q: ['Trái Đất có bao nhiêu châu lục?', 'How many continents are there?'], o: [['5', '6', '7', '8'], ['5', '6', '7', '8']], c: 2 },
  { q: ['Ký hiệu hóa học của vàng là gì?', 'What is the chemical symbol for gold?'], o: [['Ag', 'Au', 'Gd', 'Go'], ['Ag', 'Au', 'Gd', 'Go']], c: 1 },
  { q: ['Đại dương nào lớn nhất?', 'Which is the largest ocean?'], o: [['Đại Tây Dương', 'Ấn Độ Dương', 'Bắc Băng Dương', 'Thái Bình Dương'], ['Atlantic', 'Indian', 'Arctic', 'Pacific']], c: 3 },
  { q: ['Ai vẽ bức Mona Lisa?', 'Who painted the Mona Lisa?'], o: [['Van Gogh', 'Leonardo da Vinci', 'Picasso', 'Michelangelo'], ['Van Gogh', 'Leonardo da Vinci', 'Picasso', 'Michelangelo']], c: 1 },
  { q: ['Con nhện có bao nhiêu chân?', 'How many legs does a spider have?'], o: [['6', '8', '10', '12'], ['6', '8', '10', '12']], c: 1 },
  { q: ['Hành tinh nào lớn nhất Hệ Mặt Trời?', 'Which is the biggest planet in the Solar System?'], o: [['Sao Thổ', 'Sao Hải Vương', 'Sao Mộc', 'Trái Đất'], ['Saturn', 'Neptune', 'Jupiter', 'Earth']], c: 2 },
  { q: ['Nước sôi ở bao nhiêu độ C (ngang mực nước biển)?', 'At what temperature (°C) does water boil at sea level?'], o: [['90', '100', '110', '120'], ['90', '100', '110', '120']], c: 1 },
  { q: ['Động vật nào chạy nhanh nhất trên cạn?', 'Which is the fastest land animal?'], o: [['Sư tử', 'Ngựa', 'Báo gêpa', 'Chó săn'], ['Lion', 'Horse', 'Cheetah', 'Greyhound']], c: 2 },
  { q: ['Con người đặt chân lên Mặt Trăng năm nào?', 'In what year did humans first land on the Moon?'], o: [['1959', '1965', '1969', '1975'], ['1959', '1965', '1969', '1975']], c: 2 },
  { q: ['Thủ đô của Nhật Bản là gì?', 'What is the capital of Japan?'], o: [['Tokyo', 'Osaka', 'Kyoto', 'Nagoya'], ['Tokyo', 'Osaka', 'Kyoto', 'Nagoya']], c: 0 },
  { q: ['Đơn vị tiền tệ của Việt Nam là gì?', 'What is the currency of Vietnam?'], o: [['Baht', 'Đồng', 'Nhân dân tệ', 'Ringgit'], ['Baht', 'Đồng', 'Yuan', 'Ringgit']], c: 1 },
  { q: ['Ngôn ngữ nào có nhiều người nói tiếng mẹ đẻ nhất?', 'Which language has the most native speakers?'], o: [['Tiếng Anh', 'Tiếng Trung', 'Tiếng Tây Ban Nha', 'Tiếng Hindi'], ['English', 'Mandarin Chinese', 'Spanish', 'Hindi']], c: 1 },
  { q: ['Mỗi đội bóng đá có bao nhiêu cầu thủ trên sân?', 'How many players does a football team have on the pitch?'], o: [['9', '10', '11', '12'], ['9', '10', '11', '12']], c: 2 },
  { q: ['Chất nào cứng nhất trong tự nhiên?', 'What is the hardest natural substance?'], o: [['Vàng', 'Sắt', 'Thạch anh', 'Kim cương'], ['Gold', 'Iron', 'Quartz', 'Diamond']], c: 3 },
];
const ROUNDS = 10, SECS = 15, TICK = 0.1, REVEAL = 4.5;
const L = VI ? 0 : 1, KEYS = ['A', 'B', 'C', 'D'], PCOL = ['#3b82f6', '#ef4444', '#22c55e', '#eab308'];
const stage = document.getElementById('stage'), board = document.getElementById('board');
const pad = window.tvpad;
const score = {}, st = { mode: 'lobby', qs: [], i: 0, left: 0, ans: {}, gain: {}, rev: 0 };
let timer = null;

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
  if (pad) pad.layout('all', { buttons: ['START'], dpad: false });
  const ps = players();
  const kids = [h('h1', '', T.title), h('p', 'sub', T.join)];
  const w = h('div', 'who'); ps.forEach((p) => w.append(who(p))); kids.push(w);
  if (ps.length) kids.push(h('p', 'sub', T.start));
  if (!pad) kids.push(h('p', 'sub', T.nogame));
  stage.replaceChildren(...kids); drawBoard();
}

function begin() {
  const ps = players();
  if (!ps.length) return;
  ps.forEach((p) => { score[p] = 0; });
  st.qs = Q.map((q) => q).sort(() => Math.random() - 0.5).slice(0, ROUNDS); st.i = 0;
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
    if (KEYS.includes(btn)) answer(player, btn);
  });
}
// the TV remote: OK starts (the page gets Enter), and A-D by keyboard for trying it on a computer
document.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && (st.mode === 'lobby' || st.mode === 'end')) begin();
});
lobby();
