'use strict';
// Mèo nổ (Boom Cats) for TV Games: the table on the TV, each hand on its player's phone (the pad's {cards} layout; a victim,
// a card to name or a place for the bomb are the pad's buttons with an empty hand). Seats, bots, phones joining, the clock and
// scores: _lib/table.js; the rules and the bots: rules.js (a state machine, G.phase). This file: timing (the Nope window, the
// bots' pauses, the explosion), the phones' layouts and the TV.
const W = Object.assign({}, Table.W, Table.VI ? {
  kn: { attack: 'Tấn công', skip: 'Bỏ lượt', favor: 'Thiện ý', shuffle: 'Xáo bài', future: 'Xem trước', nope: 'Chặn!', defuse: 'Gỡ bom', bomb: 'Mèo nổ', c1: 'Mèo xanh', c2: 'Mèo đỏ', c3: 'Mèo tím', c4: 'Mèo vàng', c5: 'Mèo lá' },
  ks: { attack: 'Người sau đi 2 lượt', skip: 'Hết lượt, khỏi rút', favor: 'Xin 1 lá của ai đó', shuffle: 'Xáo chồng bài rút', future: 'Xem 3 lá trên cùng', nope: 'Chặn lá vừa đánh', defuse: 'Cứu khi rút phải bom', bomb: 'Nổ tung!', cat: 'Đôi/bộ 3: cướp bài' },
  play: 'Đánh', draw: 'Rút bài', nopeBtn: '🚫 Chặn!', ok: 'Xong', give: 'Cho',
  mine: (o) => (o > 1 ? `Lượt của bạn: còn ${o} lượt rút` : 'Lượt của bạn: đánh bài hoặc rút'),
  nopeNote: (n, c) => `${n} đánh ${c}: có chặn không?`, pickT: 'Chọn người chơi', pickN: 'Đòi lá nào?', peek: '3 lá trên cùng (rút trước = bên phải)',
  ins: 'Gỡ được bom! Nhét lại ở đâu?', pos: ['Trên cùng', 'Thứ 2', 'Thứ 3', 'Ngẫu nhiên', 'Dưới đáy'], giveQ: (n) => `${n} xin bạn 1 lá: chọn lá để cho`,
  out: 'Bạn đã nổ tung', done: (r) => `Bạn về ${r}`,
  err: { turn: 'Chưa tới lượt bạn', card: 'Chọn bài trước', combo: 'Chỉ đánh được 2-3 lá giống nhau', single: 'Lá này không đánh một mình', victim: 'Người này không có bài', name: 'Lá này không đòi được', none: 'Bạn không có lá Chặn', cap: 'Chặn đủ rồi', pos: 'Chọn chỗ' },
  hold: (n, c) => (c ? `${n} đánh ${c}` : n),
  cardN: (b, n) => (n === 2 ? `Đôi ${b}` : n === 3 ? `Bộ 3 ${b}` : b), to: (n) => `→ ${n}`, asks: (k) => `đòi ${k}`,
  qNope: 'Có ai chặn không?', nopedBy: (k) => (k % 2 ? `Bị chặn! (×${k})` : `Chặn lại! (×${k})`), stamp: 'BỊ CHẶN',
  pl: (n) => `${n} đang chơi`, wTarget: (n) => `${n} chọn người chơi…`, wName: (n) => `${n} đòi lá nào…`, wPeek: (n) => `${n} xem 3 lá trên cùng…`,
  wIns: (n) => `${n} nhét bom lại…`, wGive: (a, n) => `${a} chọn lá cho ${n}…`, turns: (o) => `còn ${o} lượt rút`,
  e: {
    steal: (a, b, k) => `${a} cướp 1 lá của ${b}${k ? ` (${k})` : ''}`, miss: (a, b, k) => `${a} đòi ${k} nhưng ${b} không có`, favor: (a, b) => `${b} cho ${a} 1 lá`,
    defuse: (a) => `🛡️ ${a} gỡ được bom!`, boom: (a) => `${a} nổ tung!`, noped: () => '🚫 Lá bài bị chặn', draw: (a) => `${a} rút 1 lá`,
  },
  bombs: (k) => `💣 còn ${k} trong chồng`, pile: 'Chồng bài rút', first: (n) => `${n} đi trước`,
} : {
  kn: { attack: 'Attack', skip: 'Skip', favor: 'Favor', shuffle: 'Shuffle', future: 'Peek 3', nope: 'Nope!', defuse: 'Defuse', bomb: 'Boom Cat', c1: 'Blue cat', c2: 'Red cat', c3: 'Plum cat', c4: 'Gold cat', c5: 'Green cat' },
  ks: { attack: 'Next takes 2 turns', skip: 'End turn, no draw', favor: 'Ask one for a card', shuffle: 'Shuffle the pile', future: 'Peek at the top 3', nope: 'Cancel a card played', defuse: 'Saves you from a boom', bomb: 'Boom!', cat: 'Pair/3: steal cards' },
  play: 'Play', draw: 'Draw', nopeBtn: '🚫 Nope!', ok: 'Done', give: 'Give',
  mine: (o) => (o > 1 ? `Your turn: ${o} draws to go` : 'Your turn: play cards or draw'),
  nopeNote: (n, c) => `${n} plays ${c}: Nope it?`, pickT: 'Pick a player', pickN: 'Ask for which card?', peek: 'Top 3 cards (drawn first = right)',
  ins: 'Defused! Where does the bomb go?', pos: ['On top', '2nd', '3rd', 'Anywhere', 'Bottom'], giveQ: (n) => `${n} asks you for a card: pick one to give`,
  out: 'You blew up', done: (r) => `You came ${r}`,
  err: { turn: 'Not your turn yet', card: 'Pick cards first', combo: 'Only 2-3 identical cards go together', single: 'That card cannot be played alone', victim: 'That player has no cards', name: 'Cannot ask for that', none: 'You have no Nope', cap: 'Enough Nopes', pos: 'Pick a place' },
  hold: (n, c) => (c ? `${n} plays ${c}` : n),
  cardN: (b, n) => (n === 2 ? `Pair of ${b}` : n === 3 ? `Three ${b}` : b), to: (n) => `→ ${n}`, asks: (k) => `asks for ${k}`,
  qNope: 'Anyone Nope?', nopedBy: (k) => (k % 2 ? `Noped! (×${k})` : `Noped back! (×${k})`), stamp: 'NOPED',
  pl: (n) => `${n} is playing`, wTarget: (n) => `${n} picks a player…`, wName: (n) => `${n} names a card…`, wPeek: (n) => `${n} peeks at the top 3…`,
  wIns: (n) => `${n} puts the bomb back…`, wGive: (a, n) => `${a} picks a card for ${n}…`, turns: (o) => `${o} draws to go`,
  e: {
    steal: (a, b, k) => `${a} steals a card from ${b}${k ? ` (${k})` : ''}`, miss: (a, b, k) => `${a} asks for ${k} but ${b} has none`, favor: (a, b) => `${b} gives ${a} a card`,
    defuse: (a) => `🛡️ ${a} defused the bomb!`, boom: (a) => `${a} blew up!`, noped: () => '🚫 The card was Noped', draw: (a) => `${a} draws a card`,
  },
  bombs: (k) => `💣 ${k} left in the pile`, pile: 'Draw pile', first: (n) => `${n} starts`,
});
const TURN = 40, NOPE_MS = 3500, NOPE_HUMAN = 5000, NOPE_QUICK = 1500, HOLD_BOOM = 2400, HOLD_DEFUSE = 1400;
const KD = {                                                    // each kind of card: its picture (emoji) and colour; no copied art
  attack: { e: '⚔️', bg: '#ea580c' }, skip: { e: '⏭️', bg: '#2563eb' }, favor: { e: '🤝', bg: '#db2777' }, shuffle: { e: '🔀', bg: '#7c3aed' },
  future: { e: '🔮', bg: '#0891b2' }, nope: { e: '🚫', bg: '#475569' }, defuse: { e: '🛡️', bg: '#16a34a' }, bomb: { e: '💣', bg: '#dc2626' },
  c1: { e: '🐱', bg: '#1e40af' }, c2: { e: '🐱', bg: '#991b1b' }, c3: { e: '🐱', bg: '#6b21a8' }, c4: { e: '🐱', bg: '#a16207' }, c5: { e: '🐱', bg: '#166534' },
};
const $ = (s) => document.querySelector(s);
const h = (t, c, x) => { const e = document.createElement(t); if (c) e.className = c; if (x != null) e.textContent = x; return e; };
const g = { G: MK.create(4), hold: false, pt: 0, nb: [], seen: 0, evt: '', nopeKey: 0, nopeMs: NOPE_MS, shown: null, bombs: 0 };
const nm = (t, s) => t.name(s);
const sub = (k) => (MK.isCat(k) ? W.ks.cat : W.ks[k]);
const phoneCard = (c) => ({ id: c.id, i: KD[c.k].e, k: KD[c.k].e, t: W.kn[c.k], sub: sub(c.k), bg: KD[c.k].bg });
const lastName = (L) => W.cardN(W.kn[L.base], L.kind === 'pair' ? 2 : L.kind === 'three' ? 3 : 1);

const table = Table({
  seats: 4, turnSecs: TURN, botMs: [900, 1500], cards: true, remote: true,
  deal(t) {
    g.fresh = true;
    g.G = MK.create(4); g.hold = false; g.seen = 0; g.evt = ''; g.shown = null; clearTimers();
    if (t.lastWinner >= 0) g.G.turn = t.lastWinner;               // the last winner starts the next game
    t.turn = g.G.turn; g.evt = W.first(nm(t, t.turn));
  },
  bot(t, s) {
    const G = g.G;
    if (g.hold || G.phase !== 'play' || G.turn !== s) return;
    const m = MK.botMove(G, s);
    if (m.act === 'draw') MK.draw(G, s); else MK.play(G, s, m.ids);
    if (G.evn === g.seen) Sfx.play(m.act === 'draw' ? 'pickup' : 'card');   // a bot's play was silent; a boom / defuse has its own sound in after()
    after(t);
  },
  timeUp(t, s) {                                                  // a player away from the phone: draw (sub-phases have their own timers)
    const G = g.G;
    if (!g.hold && G.phase === 'play' && G.turn === s) { MK.draw(G, s); Sfx.play('pickup'); after(t); } else t.left = TURN;
  },
  tick: clock,
  act(t, s, m) {
    const G = g.G;
    if (g.hold) return W.err.turn;
    const one = Array.isArray(m.cards) && m.cards.length ? String(m.cards[0]) : '';
    let e = 'turn', a = String(m.act);
    if (a === 'play') e = MK.play(G, s, m.cards);
    else if (a === 'draw') e = MK.draw(G, s);
    else if (a === 'nope') e = MK.nope(G, s);
    else if (a === 'ok') e = MK.peekOk(G, s);
    else if (a === 'give') e = MK.give(G, s, one);
    else if (/^t[0-3]$/.test(a)) e = MK.target(G, s, +a[1]);
    else if (a.startsWith('n:')) e = MK.name(G, s, a.slice(2));
    else if (a.startsWith('ins:')) e = MK.insert(G, s, { 0: 0, 1: 1, 2: 2, r: -1, b: 99 }[a.slice(4)]);
    if (e) return W.err[e] || W.err.turn;
    Sfx.play(a === 'draw' ? 'pickup' : a === 'play' ? 'card' : a === 'nope' ? 'hit' : 'ui'); t.say(s, ''); after(t); return '';
  },
  view: (t, s) => view(t, s), draw,
});

function clearTimers() { clearTimeout(g.pt); g.nb.forEach(clearTimeout); g.nb = []; }
const after0 = (ms, fn) => setTimeout(fn, ms);

/** Something changed in the rules: show it, then wait for whoever must act (a bot after a pause, a phone with a limit, the Nope window). */
function after(t) {
  const G = g.G; clearTimers();
  if (G.evn !== g.seen) {
    g.seen = G.evn; const ev = G.ev, a = nm(t, ev.seat), b = ev.target != null ? nm(t, ev.target) : '';
    const E = W.e, k = ev.card ? W.kn[ev.card] : '', n = ev.name ? W.kn[ev.name] : '';
    g.evt = ev.type === 'steal' ? E.steal(a, b, k) : ev.type === 'miss' ? E.miss(a, b, n) : ev.type === 'favor' ? E.favor(a, b) : ev.type === 'noped' ? E.noped()
      : ev.type === 'defuse' ? E.defuse(a) : ev.type === 'boom' ? E.boom(a) : '';
    const snd = { steal: 'hit', miss: 'bad', favor: 'pickup', noped: 'bad', defuse: 'score', boom: 'boom' }[ev.type]; if (snd) Sfx.play(snd);   // plain plays already clicked in act
    if (ev.type === 'boom' || ev.type === 'defuse') {            // a moment: nothing else moves while the screen shows it
      g.hold = true; t.buzz(ev.seat, ev.type === 'boom' ? 600 : 150); g.boom = ev; t.turn = G.turn; t.changed();
      g.pt = after0(ev.type === 'boom' ? HOLD_BOOM : HOLD_DEFUSE, () => { g.hold = false; g.boom = null; after(t); });
      return;
    }
  }
  if (G.phase === 'over' || !t.seats.some((x) => t.human(x) && G.alive[x])) { clearTimers(); g.hold = false; t.turn = G.turn; t.over(MK.ranking(G)); return; }
  t.turn = G.turn;
  if (G.phase === 'play') { t.turnTo(G.turn); return; }
  t.left = TURN; t.changed();
  if (G.phase === 'nope') { armNope(t); return; }
  const a = MK.actor(G);
  g.pt = after0(t.human(a) ? (G.phase === 'peek' ? 12000 : 25000) : 900 + Math.random() * 700, () => { MK.auto(g.G); after(t); });
}
function armNope(t) {
  const G = g.G, anyone = t.seats.some((x) => G.alive[x] && MK.has(G, x, 'nope')), mine = t.seats.some((x) => t.human(x) && G.alive[x] && MK.has(G, x, 'nope'));
  const ms = mine ? NOPE_HUMAN : anyone ? NOPE_MS : NOPE_QUICK;   // a person must read the card on the TV and reach the button: longer than bots need
  g.nopeMs = ms; g.nopeKey++; t.changed();
  g.pt = after0(ms, () => { MK.settle(g.G); after(t); });
  t.seats.forEach((x) => {
    if (t.human(x) || !G.alive[x] || !MK.has(G, x, 'nope')) return;
    g.nb.push(after0(700 + Math.random() * Math.max(0, ms - 1300), () => { if (g.G.phase === 'nope' && MK.botNope(g.G, x)) { MK.nope(g.G, x); Sfx.play('hit'); after(t); } }));
  });
}

// ---- the phones
const cut = (n, k) => (n.length > k ? n.slice(0, k - 1) + '…' : n);   // a button holds ~12 chars
function view(t, s) {
  const G = g.G, me = MK.actor(G) === s && !t.done, who = nm(t, G.turn);
  const own = () => G.hands[s].map(phoneCard);
  if (t.done) return { cards: { hand: [], note: W.done(W.place[t.order.indexOf(s)] || ''), turn: false, acts: [{ id: 'again', t: W.again, main: true }] } };
  if (!G.alive[s]) return { cards: { hand: [], note: W.out, turn: false, acts: [] } };
  const L = G.last, played = L ? lastName(L) : '';
  switch (G.phase) {
    case 'play':
      return { cards: { hand: own(), note: me ? W.mine(G.owe) : W.wait(who), turn: me, acts: me ? [{ id: 'play', t: W.play }, { id: 'draw', t: W.draw, main: true }] : [] } };
    case 'nope': {
      const can = MK.has(G, s, 'nope');
      return { cards: { hand: own(), note: can ? W.nopeNote(who, played) : W.hold(who, played), turn: can, acts: can ? [{ id: 'nope', t: W.nopeBtn, main: true }] : [] } };
    }
    case 'target':
      return me ? { cards: { hand: [], note: W.pickT, turn: true, acts: MK.victims(G, s).map((x) => ({ id: 't' + x, t: `${cut(nm(t, x), 9)} (${G.hands[x].length})` })) } }
        : { cards: { hand: own(), note: W.wTarget(who), turn: false, acts: [] } };
    case 'name':
      return me ? { cards: { hand: [], note: W.pickN, turn: true, acts: MK.NAMEABLE.map((k) => ({ id: 'n:' + k, t: `${KD[k].e} ${W.kn[k]}` })) } }
        : { cards: { hand: own(), note: W.wName(who), turn: false, acts: [] } };
    case 'peek':
      return me ? { cards: { hand: G.peek.slice().reverse().map((c, i) => Object.assign(phoneCard(c), { id: 'pk' + i })), note: W.peek, turn: true, acts: [{ id: 'ok', t: W.ok, main: true }] } }
        : { cards: { hand: own(), note: W.wPeek(who), turn: false, acts: [] } };
    case 'insert':
      return me ? { cards: { hand: [], note: W.ins, turn: true, acts: ['0', '1', '2', 'r', 'b'].map((k, i) => ({ id: 'ins:' + k, t: W.pos[i], main: i === 0 })) } }
        : { cards: { hand: own(), note: W.wIns(who), turn: false, acts: [] } };
    case 'give':
      return me ? { cards: { hand: own(), note: W.giveQ(who), turn: true, acts: [{ id: 'give', t: W.give, main: true }] } }
        : { cards: { hand: own(), note: W.wGive(nm(t, G.pend.target), who), turn: false, acts: [] } };
  }
  return { cards: { hand: own(), note: '', turn: false, acts: [] } };
}

// ---- the TV
const POS = ['s', 'e', 'n', 'w'];                               // seat 1 at the bottom, then counter-clockwise as people sit
function cardEl(k, big) {
  const e = h('div', 'kc' + (MK.isCat(k) ? ' cat' : '') + (big ? ' big' : ''));
  e.style.setProperty('--kb', KD[k].bg);
  e.append(h('b', '', KD[k].e), h('span', '', W.kn[k]));
  return e;
}
function clock(t) { const k = document.querySelector('.seat.turn .timer i'); if (k) k.style.width = Math.max(0, t.left / TURN * 100) + '%'; }
function status(t) {
  const G = g.G, who = nm(t, G.turn);
  switch (G.phase) {
    case 'target': return W.wTarget(who);
    case 'name': return W.wName(who);
    case 'peek': return W.wPeek(who);
    case 'insert': return W.wIns(who);
    case 'give': return W.wGive(nm(t, G.pend.target), who);
    case 'nope': return '';
  }
  return G.owe > 1 ? `${who} · ${W.turns(G.owe)}` : W.pl(who);
}
let lastDraw = 0;
function draw(t) {
  if (!t.ok) { $('#msg').replaceChildren(h('b', '', t.pad ? W.old : W.nopad), h('span', '', t.pad ? W.oldh : '')); return; }
  const G = g.G, act = t.done ? -1 : G.phase === 'give' ? G.pend.target : G.turn, rank = t.done ? t.order : G.outOrder.slice().reverse();
  $('#seats').replaceChildren(...t.seats.map((s) => {
    const dead = !G.alive[s];
    const e = h('div', 'seat ' + POS[s] + (s === act && !dead && !t.done ? ' turn' : '') + (dead ? ' dead' : '') + (G.pend && G.pend.target === s && G.phase !== 'give' ? ' tg' : ''));
    e.style.setProperty('--pc', t.COLORS[s]);
    const head = h('div', 'name'); head.append(h('span', 'dot'), h('span', '', (t.human(s) ? '' : '🤖 ') + t.name(s)));
    const backs = h('div', 'backs'); for (let i = 0, k = Math.min(G.hands[s].length, 14); i < k; i++) backs.append(h('i'));
    const info = h('div', 'info');
    info.append(h('span', 'n', dead ? '💥' : String(G.hands[s].length)), h('span', 'tot', W.total + ' ' + (t.scores[s] > 0 ? '+' : '') + t.scores[s]));
    if (s === G.turn && !dead && G.owe > 1 && !t.done) info.append(h('span', 'owe', '×' + G.owe));
    if (dead) { const r = rank.indexOf(s); if (r >= 0 && t.done) info.append(h('span', 'place', W.place[r])); }
    if (s === t.turn && G.phase === 'play' && !t.done && t.human(s) && !g.hold) { const k = h('div', 'timer'); k.append(h('i')); k.firstChild.style.width = (t.left / TURN * 100) + '%'; e.append(k); }
    e.append(head, backs, info);
    return e;
  }));
  // the draw pile: a stack of backs, how many cards, how many bombs are in it (public: one fewer than players alive)
  const dk = $('#deck'), stack = h('div', 'stack'); for (let i = 0; i < 3; i++) stack.append(h('i'));
  if (lastDraw > G.draw.length && lastDraw - G.draw.length < 20) stack.classList.add('take');   // a card left the deck (a new game refills it: no pop)
  const dropN = lastDraw - G.draw.length; lastDraw = G.draw.length;
  dk.replaceChildren(stack, h('b', '', String(G.draw.length)), h('span', '', W.bombs(MK.aliveList(G).length - 1)));
  if (window.Cards) { Cards.flow({ lens: G.hands.map((x) => x.length), dropN, deck: '#deck .stack', like: '#deck .stack i', pos: POS, fresh: g.fresh }); g.fresh = false; }
  // the discard: the cards just played, large
  const L = G.last, pile = $('#disc'), what = $('#what');
  if (L) {
    if (g.shown !== L) {
      const els = Array.from({ length: L.n }, () => cardEl(L.base, true));      // they fly from the player's hand to the pile
      g.shown = L; pile.className = ''; pile.replaceChildren(...els); void pile.offsetWidth; pile.classList.add('in');
      if (window.Cards) Cards.flyIn(els, (L.by === 0 && Cards.held()) || [Cards.at(POS[L.by])], { gap: 70 });
    }
    pile.classList.toggle('noped', L.noped);
    let stamp = pile.querySelector('.stamp');
    if (L.noped && !stamp) pile.append(h('div', 'stamp', W.stamp)); else if (!L.noped && stamp) stamp.remove();
    what.textContent = nm(t, L.by) + ' · ' + lastName(L) + (L.target >= 0 ? ' ' + W.to(nm(t, L.target)) : '') + (L.name ? ' · ' + W.asks(W.kn[L.name]) : '');
  } else { pile.replaceChildren(); pile.className = ''; g.shown = null; what.textContent = ''; }
  $('#msg').replaceChildren(h('b', '', t.done ? '' : status(t)));
  $('#evt').textContent = t.done ? '' : g.evt;
  // the Nope window: a bar that runs down, and how many Nopes so far
  const nb = $('#nope');
  if (G.phase === 'nope' && !t.done) {
    if (nb.dataset.key !== String(g.nopeKey)) {
      nb.dataset.key = String(g.nopeKey); const bar = h('div', 'bar'), i = h('i'); i.style.animationDuration = g.nopeMs + 'ms'; bar.append(i);
      nb.replaceChildren(h('b', '', '🚫 ' + W.qNope), bar, h('span', 'cnt', G.pend.nopes.length ? W.nopedBy(G.pend.nopes.length) : ''), h('span', 'wh', ''));
    }
    nb.querySelector('.cnt').textContent = G.pend.nopes.length ? W.nopedBy(G.pend.nopes.length) : '';
    nb.querySelector('.wh').textContent = what.textContent; what.textContent = '';
    nb.hidden = false;
  } else { nb.hidden = true; nb.dataset.key = ''; }
  const bm = $('#boom');
  if (g.boom && g.hold) {
    const bo = g.boom.type === 'boom';
    if (bm.dataset.k !== String(g.seen)) {
      bm.dataset.k = String(g.seen); bm.className = bo ? 'bang' : 'safe';
      bm.replaceChildren(h('i', '', bo ? '💥' : '🛡️'), h('b', '', bo ? W.e.boom(t.name(g.boom.seat)) : W.e.defuse(t.name(g.boom.seat)).replace('🛡️ ', '')));
    }
    bm.hidden = false;
  } else { bm.hidden = true; bm.dataset.k = ''; }
  const ov = $('#over');
  ov.hidden = !t.done || !t.games || g.hold;
  if (t.done && t.games && !g.hold) {
    ov.replaceChildren(h('h1', '', '🏆 ' + W.win(t.name(t.order[0]))), ...t.order.map((s, r) => {
      const e = h('div', 'row' + (r ? '' : ' win')), p = t.points(r); e.style.setProperty('--pc', t.COLORS[s]);
      e.append(h('span', 'pl', W.place[r]), h('span', 'dot'), h('span', 'nm', t.name(s)), h('span', 'pt', (p > 0 ? '+' : '') + p + ' ' + W.pts), h('span', 'tt', String(t.scores[s])));
      return e;
    }), h('p', '', W.newg));
  }
}
if (table.ok) {
  table.go();
  // Table hands a leaving phone's seat to a bot, but a bot is woken only in the play phase: in a sub-phase (pick a victim, a card,
  // a place for the bomb) the game would sit out the phone's 25 s. Re-arm it with the bot's short pause instead.
  const rearm = () => {
    const G = g.G;
    if (!table.done && !g.hold && !['play', 'nope', 'over'].includes(G.phase) && !table.human(MK.actor(G))) after(table);
  };
  table.pad.on('leave', rearm);
} else draw(table);