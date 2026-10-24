'use strict';
// Tiến lên for TV Games: the table on the TV, each hand on its player's phone (the pad's {cards} layout). Seats, bots,
// phones joining, the clock and scores: _lib/table.js; rules and bots: rules.js.
const W = Object.assign({}, Table.W, Table.VI ? {
  lead: 'Lượt của bạn: đánh gì cũng được', play: 'Đánh', pass: 'Bỏ lượt', hint: 'Gợi ý', bad: 'Bài không hợp lệ',
  low: 'Chưa chặn được bài trên bàn', nolead: 'Đang cầm cái: phải đánh', none: 'Không có bài chặn được', passed: 'Bỏ lượt',
  done: (r) => `Bạn về ${r}`, chop: 'Chặt!', first: (n) => `${n} có 3♠ nên đi trước`, leads: (n) => `${n} đi`,
  T: { single: 'Rác', pair: 'Đôi', triple: 'Sám cô', straight: 'Sảnh', quad: 'Tứ quý', pairs3: '3 đôi thông', pairs4: '4 đôi thông' },
} : {
  lead: 'Your turn: play anything', play: 'Play', pass: 'Pass', hint: 'Hint', bad: 'Not a valid combination',
  low: 'That does not beat the table', nolead: 'You lead: play something', none: 'Nothing beats it', passed: 'Passed',
  done: (r) => `You came ${r}`, chop: 'Chop!', first: (n) => `${n} holds the 3♠ and starts`, leads: (n) => `${n} leads`,
  T: { single: 'Single', pair: 'Pair', triple: 'Triple', straight: 'Straight', quad: 'Four of a kind', pairs3: '3 pairs in a row', pairs4: '4 pairs in a row' },
});
const TURN = 40, PERSONA = ['balanced', 'aggressive', 'cautious'];
const $ = (s) => document.querySelector(s);
const h = (t, c, x) => { const e = document.createElement(t); if (c) e.className = c; if (x != null) e.textContent = x; return e; };
const g = { hands: [[], [], [], []], persona: [], last: null, passed: new Set(), out: [], msg: '' };
const active = (s) => !g.out.includes(s);
const nextSeat = (s, ok) => { for (let k = 1; k <= 4; k++) { const x = (s + k) % 4; if (ok(x)) return x; } return -1; };
const near = (s) => g.hands.some((hd, o) => o !== s && active(o) && hd.length > 0 && hd.length <= 3);

const table = Table({
  seats: 4, turnSecs: TURN, botMs: [900, 1500], cards: true, remote: true,
  deal(t) {
    g.hands = TL.deal(4); g.last = null; g.passed = new Set(); g.out = [];
    g.persona = t.seats.map(() => PERSONA[Math.floor(Math.random() * PERSONA.length)]);
    if (t.lastWinner >= 0) { t.turn = t.lastWinner; g.msg = W.leads(t.name(t.turn)); }     // the last winner leads the next game
    else { t.turn = g.hands.findIndex((hd) => hd.includes(30)); g.msg = W.first(t.name(t.turn)); }
  },
  bot(t, s) {
    const c = TL.bot(g.hands[s], { last: g.last && g.last.c, near: near(s) }, g.persona[s]);
    if (c && c.length) play(t, s, c); else pass(t, s);
  },
  timeUp(t, s) { if (g.last) pass(t, s); else play(t, s, [g.hands[s][0]]); },   // a player away from the phone: the game goes on
  tick: clock,
  act(t, s, m) {
    if (s !== t.turn) return W.notyet;
    const cards = (Array.isArray(m.cards) ? m.cards : []).map(TL.fromId).filter((c) => c != null);
    if (m.act === 'hint') {
      const hnt = TL.hint(g.hands[s], cards, g.last && g.last.c);
      t.send(s, view(t, s, hnt));                                 // none: the picked cards are let go, so Play is not refused next
      return hnt.length ? '' : W.none;
    }
    const why = m.act === 'play' ? play(t, s, cards) : m.act === 'pass' ? pass(t, s) : '';
    if (!why) t.say(s, '');
    return why;
  },
  view: (t, s) => view(t, s), draw,
});

/** [s] plays [cards] (numbers): '' when done, else why not (the words for the phone). */
function play(t, s, cards) {
  const hand = g.hands[s];
  if (!cards.length || cards.some((c) => !hand.includes(c))) return W.bad;
  const c = TL.combo(cards);
  if (!c) return W.bad;
  if (!TL.beats(c, g.last && g.last.c)) return W.low;
  const chop = TL.chop(c, g.last && g.last.c);
  g.hands[s] = hand.filter((x) => !cards.includes(x));
  g.last = { c, cards: cards.slice().sort((a, b) => a - b), by: s, chop };
  g.msg = chop ? W.chop : '';
  Sfx.play(chop ? 'boom' : 'card'); if (chop) t.buzz(s, 300);
  if (!g.hands[s].length) {
    g.out.push(s);
    if (g.out.length >= 3 || !t.seats.some((o) => t.human(o) && active(o))) { finish(t); return ''; }   // no phone left playing: no waiting on bots
  }
  advance(t, s); return '';
}
function pass(t, s) {
  if (!g.last) return W.nolead;
  Sfx.play('ui'); g.passed.add(s); g.msg = ''; advance(t, s); return '';
}
// the next to play: whoever after [from] is still in the trick; nobody = the trick goes to its last player (or, gone out, the next one)
function advance(t, from) {
  const by = g.last.by, left = t.seats.filter((x) => x !== by && active(x) && !g.passed.has(x));
  if (!left.length) {
    const lead = active(by) ? by : nextSeat(by, active);
    g.last = null; g.passed = new Set(); g.msg = W.leads(t.name(lead)); t.turnTo(lead);
  } else t.turnTo(nextSeat(from, (x) => left.includes(x)));
}
function finish(t) {
  const rest = t.seats.filter(active).sort((a, b) => g.hands[a].length - g.hands[b].length);
  g.msg = ''; t.over(g.out.concat(rest));
}

// ---- the phones
function view(t, s, sel) {
  const me = s === t.turn && !t.done, out = (t.done ? t.order : g.out).indexOf(s);
  const note = out >= 0 ? W.done(W.place[out]) : me ? (g.last ? W.turn : W.lead) : W.wait(t.name(t.turn));
  const acts = t.done ? [{ id: 'again', t: W.again, main: true }]
    : [{ id: 'hint', t: W.hint, off: !me }, { id: 'pass', t: W.pass, off: !me || !g.last }, { id: 'play', t: W.play, main: true, off: !me }];
  const o = { hand: g.hands[s].map(TL.id), note, turn: me, acts };
  if (sel) o.sel = sel.map(TL.id);
  return { cards: o };
}

// ---- the TV
const POS = ['s', 'e', 'n', 'w'];                               // seat 1 at the bottom, then counter-clockwise as people sit
function cardEl(c) {
  const e = h('div', 'card'), id = TL.id(c), su = TL.suit(c);
  if (su >= 2) e.classList.add('red');
  e.append(h('b', '', id.slice(0, -1)), h('i', '', '♠♣♦♥'[su]), h('u', '', '♠♣♦♥'[su]));
  return e;
}
function clock(t) { const k = document.querySelector('.seat.turn .timer i'); if (k) k.style.width = Math.max(0, t.left / TURN * 100) + '%'; }
function draw(t) {
  if (!t.ok) { $('#msg').replaceChildren(h('b', '', t.pad ? W.old : W.nopad), h('span', '', t.pad ? W.oldh : '')); return; }
  const order = t.done ? t.order : g.out;
  $('#seats').replaceChildren(...t.seats.map((s) => {
    const e = h('div', 'seat ' + POS[s] + (s === t.turn && !t.done ? ' turn' : '')), r = order.indexOf(s);
    e.style.setProperty('--pc', t.COLORS[s]);
    const head = h('div', 'name'); head.append(h('span', 'dot'), h('span', '', (t.human(s) ? '' : '🤖 ') + t.name(s)));
    const backs = h('div', 'backs'); for (let i = 0; i < g.hands[s].length; i++) backs.append(h('i'));
    const info = h('div', 'info');
    info.append(h('span', 'n', String(g.hands[s].length)), h('span', 'tot', W.total + ' ' + (t.scores[s] > 0 ? '+' : '') + t.scores[s]));
    if (r >= 0) info.append(h('span', 'place p' + r, W.place[r]));
    else if (g.passed.has(s)) info.append(h('span', 'pass', W.passed));
    if (s === t.turn && !t.done && t.human(s)) { const k = h('div', 'timer'); k.append(h('i')); k.firstChild.style.width = (t.left / TURN * 100) + '%'; e.append(k); }
    e.append(head, backs, info);
    return e;
  }));
  const pile = $('#pile');
  if (g.last) {
    const k = g.last.cards.join();
    if (pile.dataset.shown !== k) { pile.dataset.shown = k; pile.className = 'from-' + POS[g.last.by] + (g.last.chop ? ' chop' : ''); pile.replaceChildren(...g.last.cards.map(cardEl)); void pile.offsetWidth; pile.classList.add('in'); }
    $('#what').textContent = t.name(g.last.by) + ' · ' + W.T[g.last.c.type] + (g.last.c.type === 'straight' ? ' ' + g.last.c.n : '');
  } else { pile.replaceChildren(); pile.dataset.shown = ''; $('#what').textContent = ''; }
  $('#msg').replaceChildren(h('b', g.last && g.last.chop ? 'boom' : '', t.done ? '' : g.msg));
  const ov = $('#over');
  ov.hidden = !t.done || !t.games;
  if (t.done && t.games) {
    ov.replaceChildren(h('h1', '', '🏆 ' + W.win(t.name(t.order[0]))), ...t.order.map((s, r) => {
      const e = h('div', 'row' + (r ? '' : ' win')), p = t.points(r); e.style.setProperty('--pc', t.COLORS[s]);
      e.append(h('span', 'pl', W.place[r]), h('span', 'dot'), h('span', 'nm', t.name(s)), h('span', 'pt', (p > 0 ? '+' : '') + p + ' ' + W.pts), h('span', 'tt', String(t.scores[s])));
      return e;
    }), h('p', '', W.newg));
  }
}
if (table.ok) table.go(); else draw(table);
