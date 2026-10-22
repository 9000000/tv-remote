'use strict';
// Bài màu / Color Cards for TV Games: the table on the TV, each hand on its player's phone (the pad's {cards} layout). Seats,
// bots, phones joining, the clock and scores: _lib/table.js; rules and bots: rules.js.
const W = Object.assign({}, Table.W, Table.VI ? {
  play: 'Đánh', draw: 'Rút bài', pass: 'Bỏ lượt', mine: 'Lượt của bạn: chọn 1 lá rồi Đánh', pick: 'Chọn màu', one: 'Chọn đúng 1 lá', bad: 'Lá này không đánh được',
  drawn: 'Rút rồi: đánh lá mới hoặc bỏ lượt', nodraw: 'Rút bài trước', color: ['Đỏ', 'Vàng', 'Xanh lá', 'Xanh dương'], uno: 'Uno!',
  ev: { skip: (a, b) => `${b} mất lượt`, rev: () => 'Đổi chiều!', draw: (a, b, n) => `${b} rút ${n} lá và mất lượt`, took: (a) => `${a} rút bài`, pass: (a) => `${a} bỏ lượt` },
  col: (n, c) => `${n} chọn màu ${c}`, start: (n) => `${n} đi trước`, left: 'lá',
} : {
  play: 'Play', draw: 'Draw', pass: 'Pass', mine: 'Your turn: pick 1 card, then Play', pick: 'Pick a colour', one: 'Pick exactly 1 card', bad: 'That card cannot be played',
  drawn: 'Drawn: play the new card or pass', nodraw: 'Draw first', color: ['Red', 'Yellow', 'Green', 'Blue'], uno: 'Uno!',
  ev: { skip: (a, b) => `${b} loses a turn`, rev: () => 'Reverse!', draw: (a, b, n) => `${b} draws ${n} and loses a turn`, took: (a) => `${a} draws`, pass: (a) => `${a} passes` },
  col: (n, c) => `${n} picks ${c}`, start: (n) => `${n} starts`, left: 'cards',
});
const TURN = 30, HEX = UL.HEX;
const $ = (s) => document.querySelector(s);
const h = (t, c, x) => { const e = document.createElement(t); if (c) e.className = c; if (x != null) e.textContent = x; return e; };
const g = { st: UL.newGame(4), msg: '' };

const table = Table({
  seats: 4, turnSecs: TURN, botMs: [900, 1500], cards: true,
  deal(t) {
    g.st = UL.newGame(4);
    const l = g.st.last;
    g.msg = l ? W.ev[l.ev](t.name(0), t.name(l.victim), l.n) : '';
    t.turn = g.st.turn;
    if (!g.msg) g.msg = W.start(t.name(t.turn));
  },
  bot: (t, s) => { UL.doBot(g.st, s); after(t); },
  timeUp: (t, s) => { UL.doBot(g.st, s); after(t); },              // a player away from the phone: the game goes on
  tick: (t) => { const k = document.querySelector('.seat.turn .timer i'); if (k) k.style.width = Math.max(0, t.left / TURN * 100) + '%'; },
  act(t, s, m) {
    const st = g.st;
    if (s !== st.turn) return W.notyet;
    let r;
    if (m.act === 'play') {
      const ids = Array.isArray(m.cards) ? m.cards : [];
      if (ids.length !== 1) return W.one;
      r = UL.play(st, s, parseInt(ids[0], 10));
    } else if (m.act === 'draw') r = UL.draw(st, s);
    else if (m.act === 'pass') r = UL.pass(st, s);
    else if (/^c[0-3]$/.test(m.act)) r = UL.chooseColor(st, s, +m.act[1]);
    else return '';
    if (!r.ok) return r.why === 'turn' ? W.notyet : r.why === 'nodraw' ? W.nodraw : W.bad;
    after(t); return '';
  },
  view: (t, s) => view(t, s), draw,
});

/** After any move: the message, then the next turn (a wild waits on its player for the colour). */
function after(t) {
  const st = g.st, l = st.last;
  if (l && l.ev === 'win') { g.msg = ''; t.over(UL.order(st)); return; }
  g.msg = !l ? '' : st.pend >= 0 ? t.name(st.pend) + ' · ' + W.pick : l.ev === 'took' ? W.ev.took(t.name(l.seat)) : l.ev === 'pass' ? W.ev.pass(t.name(l.seat))
    : l.ev ? W.ev[l.ev](t.name(l.seat), t.name(l.victim), l.n) + (l.card.c === UL.WILD ? ' · ' + W.col[l.color] : '')
      : l.card && l.card.c === UL.WILD ? W.col(t.name(l.seat), W.color[l.color]) : '';
  if (l && l.ev === 'draw' && t.human(l.victim)) t.buzz(l.victim, 250);
  t.turnTo(st.pend >= 0 ? st.pend : st.turn);
}

// ---- the phones
function view(t, s) {
  const st = g.st, me = s === st.turn && !t.done;
  if (t.done) return { cards: { hand: [], note: t.order[0] === s ? W.win(t.name(s)) : W.wait(t.name(t.order[0])), turn: false, acts: [{ id: 'again', t: W.again, main: true }] } };
  if (me && st.pend === s) return { cards: { hand: [], note: W.pick, turn: true, acts: W.color.map((c, i) => ({ id: 'c' + i, t: c, i: ['🔴', '🟡', '🟢', '🔵'][i], main: i === UL.bestColor(st.hands[s]) })) } };
  const ok = me ? new Set(UL.playable(st, s).map((c) => c.id)) : null;
  const hand = st.hands[s].map((c) => {
    const w = UL.PWORD(c.k), o = { id: String(c.id), t: w, k: w, look: 'oval', bg: HEX[c.c] };
    if (ok && !ok.has(c.id)) o.off = true;                         // what cannot go on the pile now is dimmed
    return o;
  });
  const note = me ? (st.drawn != null ? W.drawn : W.mine) : W.wait(t.name(st.turn));
  const acts = [{ id: 'draw', t: W.draw, off: !me || st.drawn != null }, { id: 'pass', t: W.pass, off: !me || st.drawn == null }, { id: 'play', t: W.play, main: true, off: !me }];
  return { cards: { hand, note, turn: me, acts } };
}

// ---- the TV
const POS = ['s', 'e', 'n', 'w'];                               // seat 1 at the bottom, then counter-clockwise as people sit
function cardEl(c, cls) {
  const e = h('div', 'card ' + (cls || '') + (c.c === UL.WILD ? ' wild' : ''));
  e.style.setProperty('--b', HEX[c.c]);
  const w = UL.WORD(c.k);
  e.append(h('s'), h('b', '', w), h('i', '', w), h('u', '', w));   // s: the tilted white oval, b: the value on it
  return e;
}
function draw(t) {
  if (!t.ok) { $('#msg').replaceChildren(h('b', '', t.pad ? W.old : W.nopad), h('span', '', t.pad ? W.oldh : '')); return; }
  const st = g.st;
  $('#seats').replaceChildren(...t.seats.map((s) => {
    const e = h('div', 'seat ' + POS[s] + (s === t.turn && !t.done ? ' turn' : '')), n = st.hands[s].length;
    e.style.setProperty('--pc', t.COLORS[s]);
    const head = h('div', 'name'); head.append(h('span', 'dot'), h('span', '', (t.human(s) ? '' : '🤖 ') + t.name(s)));
    const backs = h('div', 'backs'); for (let i = 0; i < Math.min(n, 14); i++) backs.append(h('i'));
    const info = h('div', 'info');
    info.append(h('span', 'n', n + ' ' + W.left), h('span', 'tot', W.total + ' ' + (t.scores[s] > 0 ? '+' : '') + t.scores[s]));
    if (n === 1 && !t.done) info.append(h('span', 'unob', W.uno));
    if (s === t.turn && !t.done && t.human(s)) { const k = h('div', 'timer'); k.append(h('i')); k.firstChild.style.width = (t.left / TURN * 100) + '%'; e.append(k); }
    e.append(head, backs, info);
    return e;
  }));
  const top = UL.topCard(st), tb = $('#table');
  tb.style.setProperty('--cc', HEX[st.color]);
  const pile = $('#pile'), k = String(top.id);
  if (pile.dataset.shown !== k) {
    pile.dataset.shown = k; pile.className = st.last && st.last.seat >= 0 ? 'from-' + POS[st.last.seat] : '';
    pile.replaceChildren(cardEl(top, 'big')); void pile.offsetWidth; pile.classList.add('in');
  }
  const stock = $('#stock'); stock.replaceChildren(h('i'), h('span', '', String(st.draw.length)));
  const dir = $('#dir'); dir.textContent = st.dir === 1 ? '↺' : '↻';
  $('#what').textContent = W.color[st.color];
  $('#msg').replaceChildren(h('b', '', t.done ? '' : g.msg));
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
