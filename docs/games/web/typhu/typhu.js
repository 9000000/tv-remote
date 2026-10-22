'use strict';
// Cờ tỷ phú (Tycoon) for TV Games: the board on the TV, each player's choices on their phone (the pad's {cards} layout: big named
// buttons, and the player's own tiles as cards to build on / mortgage). Seats, bots, phones joining, the clock and scores:
// _lib/table.js; rules and bots: rules.js. 30 rounds, then the richest wins.
const W = Object.assign({}, Table.W, Table.VI ? {
  title: 'CỜ TỶ PHÚ', round: (n, m) => `Vòng ${n}/${m}`, cash: 'Tiền', worth: 'Tài sản', tilesn: (n) => `${n} ô`, free: 'Chưa có chủ', owner: 'Chủ', rentw: 'Thuê',
  roll: 'Gieo xúc xắc', buy: 'Mua', skip: 'Bỏ qua', build: 'Xây nhà', sell: 'Bán nhà', mort: 'Thế chấp / Chuộc', end: 'Kết thúc lượt',
  jailpay: (n) => `Nộp ${n} ra tù`, jailcard: 'Dùng thẻ ra tù', hotel: 'Khách sạn', houses: (n) => `${n} nhà`, mortgaged: 'thế chấp', rentl: (n) => `thuê ${n}`,
  note_roll: 'Gieo xúc xắc đi!', note_jail: 'Đang ở tù: đổ đôi, nộp phạt hoặc dùng thẻ', note_buy: (nm, p) => `Mua ${nm} giá ${p}?`,
  note_act: 'Xây nhà, thế chấp hoặc kết thúc lượt', note_pick: 'chọn ô của bạn rồi bấm nút', note_out: 'Bạn đã phá sản', note_wait: (n) => `Chờ ${n}…`,
  err: { phase: 'Chưa làm được lúc này', notowner: 'Ô này không phải của bạn', set: 'Cần đủ bộ màu mới xây được', even: 'Phải xây / bán đều các ô', money: 'Không đủ tiền',
    max: 'Đã có khách sạn', mortgaged: 'Bộ này có ô đang thế chấp', houses: 'Bán hết nhà của bộ màu trước', nohouse: 'Ô này chưa có nhà', act: 'Không hiểu lệnh', over: 'Ván đã hết' },
  pickone: 'Chọn một ô trước', jailtxt: 'Tù', brk: 'Phá sản', rounds: (n) => `Hết ${n} vòng: ai giàu nhất thắng`, last: 'Mọi người khác đã phá sản',
  ev: {
    roll: (e, n) => `${n(e.p)} gieo ${e.d[0]} + ${e.d[1]}`, salary: (e, n) => `${n(e.p)} qua Khởi Hành, nhận ${m(e.n)}`,
    buy: (e, n, T) => `${n(e.p)} mua ${T(e.id)} giá ${m(e.n)}`, skip: (e, n, T) => `${n(e.p)} bỏ qua ${T(e.id)}`, cantbuy: (e, n, T) => `${n(e.p)} không đủ tiền mua ${T(e.id)}`,
    rent: (e, n, T) => `${n(e.p)} trả ${n(e.to)} tiền thuê ${m(e.n)} (${T(e.id)})`, tax: (e, n) => `${n(e.p)} nộp thuế ${m(e.n)}`,
    jail: (e, n) => `${n(e.p)} vào tù!`, outdbl: (e, n) => `${n(e.p)} đổ đôi và ra tù`, stay: (e, n) => `${n(e.p)} ở tù (lượt ${e.n}/3)`,
    fine: (e, n) => `${n(e.p)} nộp ${m(TY.FINE)} ra tù`, freecard: (e, n) => `${n(e.p)} dùng thẻ ra tù`, dbl3: (e, n) => `${n(e.p)} đổ đôi 3 lần: vào tù!`,
    card: (e, n) => `${n(e.p)} rút thẻ: ${e.c.vi}`, build: (e, n, T) => `${n(e.p)} xây ${e.h === 5 ? 'khách sạn' : 'nhà thứ ' + e.h} ở ${T(e.id)}`,
    sell: (e, n, T) => `${n(e.p)} bán nhà ở ${T(e.id)}${e.auto ? ' (để trả nợ)' : ''}`, mort: (e, n, T) => `${n(e.p)} thế chấp ${T(e.id)}${e.auto ? ' (để trả nợ)' : ''}`,
    redeem: (e, n, T) => `${n(e.p)} chuộc ${T(e.id)}`, broke: (e, n) => `${n(e.p)} phá sản!`,
  },
} : {
  title: 'TYCOON', round: (n, m) => `Round ${n}/${m}`, cash: 'Cash', worth: 'Worth', tilesn: (n) => `${n} tile${n === 1 ? '' : 's'}`, free: 'For sale', owner: 'Owner', rentw: 'Rent',
  roll: 'Roll dice', buy: 'Buy', skip: 'Skip', build: 'Build house', sell: 'Sell house', mort: 'Mortgage / Redeem', end: 'End turn',
  jailpay: (n) => `Pay ${n} to leave`, jailcard: 'Use jail card', hotel: 'Hotel', houses: (n) => `${n} house${n > 1 ? 's' : ''}`, mortgaged: 'mortgaged', rentl: (n) => `rent ${n}`,
  note_roll: 'Roll the dice!', note_jail: 'In jail: roll doubles, pay the fine or use a card', note_buy: (nm, p) => `Buy ${nm} for ${p}?`,
  note_act: 'Build, mortgage or end your turn', note_pick: 'pick one of your tiles, then a button', note_out: 'You are bankrupt', note_wait: (n) => `Waiting for ${n}…`,
  err: { phase: 'Not possible right now', notowner: 'That tile is not yours', set: 'You need the whole colour set to build', even: 'Build / sell evenly across the set', money: 'Not enough cash',
    max: 'Already a hotel', mortgaged: 'A tile in this set is mortgaged', houses: 'Sell the set\'s houses first', nohouse: 'No house on this tile', act: 'Unknown command', over: 'The game is over' },
  pickone: 'Pick a tile first', jailtxt: 'Jail', brk: 'Bankrupt', rounds: (n) => `${n} rounds are up: the richest wins`, last: 'Everyone else went bankrupt',
  ev: {
    roll: (e, n) => `${n(e.p)} rolls ${e.d[0]} + ${e.d[1]}`, salary: (e, n) => `${n(e.p)} passes GO, collects ${m(e.n)}`,
    buy: (e, n, T) => `${n(e.p)} buys ${T(e.id)} for ${m(e.n)}`, skip: (e, n, T) => `${n(e.p)} skips ${T(e.id)}`, cantbuy: (e, n, T) => `${n(e.p)} cannot afford ${T(e.id)}`,
    rent: (e, n, T) => `${n(e.p)} pays ${n(e.to)} rent ${m(e.n)} (${T(e.id)})`, tax: (e, n) => `${n(e.p)} pays tax ${m(e.n)}`,
    jail: (e, n) => `${n(e.p)} goes to jail!`, outdbl: (e, n) => `${n(e.p)} rolls doubles and leaves jail`, stay: (e, n) => `${n(e.p)} stays in jail (turn ${e.n}/3)`,
    fine: (e, n) => `${n(e.p)} pays ${m(TY.FINE)} to leave jail`, freecard: (e, n) => `${n(e.p)} uses a jail card`, dbl3: (e, n) => `${n(e.p)} rolled doubles 3 times: jail!`,
    card: (e, n) => `${n(e.p)} draws: ${e.c.en}`, build: (e, n, T) => `${n(e.p)} builds ${e.h === 5 ? 'a hotel' : 'house ' + e.h} on ${T(e.id)}`,
    sell: (e, n, T) => `${n(e.p)} sells a house on ${T(e.id)}${e.auto ? ' (to pay a debt)' : ''}`, mort: (e, n, T) => `${n(e.p)} mortgages ${T(e.id)}${e.auto ? ' (to pay a debt)' : ''}`,
    redeem: (e, n, T) => `${n(e.p)} redeems ${T(e.id)}`, broke: (e, n) => `${n(e.p)} is bankrupt!`,
  },
});
const TURN = 45;
const $ = (s) => document.querySelector(s);
const h = (t, c, x) => { const e = document.createElement(t); if (c) e.className = c; if (x != null) e.textContent = x; return e; };
const m = (n) => { const s = String(Math.round(n)).replace(/\B(?=(\d{3})+$)/g, Table.VI ? '.' : ','); return Table.VI ? s + 'đ' : '$' + s; };
const B = TY.BOARD, tname = (id) => (Table.VI ? B[id].vi : B[id].en);
const k = (n) => (n < 1000 ? String(n) : (n % 1000 === 0 ? n / 1000 : Math.round(n / 100) / 10) + 'k');
const DIE = ['', '⚀', '⚁', '⚂', '⚃', '⚄', '⚅'];
let G = null; const afk = [];

const table = Table({
  seats: 4, turnSecs: TURN, botMs: [800, 1300], cards: true, remote: true,
  deal(t) { G = TY.newGame(4); t.turn = 0; afk.length = 0; },
  bot(t, s) {
    let why = TY.apply(G, TY.bot(G));
    if (why) why = TY.apply(G, { act: G.phase === 'buy' ? 'skip' : G.phase === 'roll' ? 'roll' : 'end' });
    post(t);
  },
  timeUp(t, s) {                                                // a player away from the phone: the game goes on, one quick step at a time
    afk[s] = true;
    TY.apply(G, { act: G.phase === 'buy' ? 'skip' : G.phase === 'roll' ? 'roll' : 'end' });
    post(t);
  },
  tick: clock,
  act(t, s, msg) {
    if (s !== G.p) return W.notyet;
    const acts = ['roll', 'buy', 'skip', 'build', 'sell', 'mort', 'jailpay', 'jailcard', 'end'];
    if (!acts.includes(msg.act)) return W.err.act;
    if (msg.act === 'build' || msg.act === 'sell' || msg.act === 'mort') {       // every picked tile gets the move; the first refusal is told
      const ids = (Array.isArray(msg.cards) ? msg.cards : []).slice(0, 12).map((c) => parseInt(c, 10)).filter((x, i, a) => x >= 0 && x < 40 && a.indexOf(x) === i);
      if (!ids.length) return W.pickone;
      let why = '', done = 0;
      for (const id of ids) { const w = TY.apply(G, { act: msg.act, id }); if (w) why = why || (W.err[w] || W.err.phase); else done++; }
      if (!done) return why;
    } else {
      const why = TY.apply(G, { act: msg.act });
      if (why) return W.err[why] || W.err.phase;
    }
    afk[s] = false; t.say(s, ''); post(t); return '';
  },
  view, draw,
});

function post(t) {
  if (G.over) { finish(t); return; }
  t.turnTo(G.p);
  if (afk[G.p]) t.left = 10;
}
function finish(t) { t.over(TY.rank(G)); }

// ---- the phones
function cardOf(id) {
  const T = B[id], hs = G.h[id];
  const sub = G.m[id] ? W.mortgaged : T.t === 'prop' ? (hs ? (hs === 5 ? W.hotel : W.houses(hs)) + ' · ' : '') + k(TY.rent(G, id)) : W.rentl(k(TY.rent(G, id)));
  const name = T.t === 'prop' ? T.sh : tname(id);
  const o = { id: String(id), t: name.length > 14 ? name.slice(0, 13) + '…' : name, sub, look: 'deed', bg: T.t === 'prop' ? TY.GROUP_COLORS[T.g] : '#475569' };
  if (hs) o.i = hs === 5 ? '🏨' : '🏠'.repeat(hs);                  // a deed card: the houses on it
  if (G.m[id]) o.off = true;                                       // mortgaged: dimmed
  return o;
}
function view(t, s) {
  let own = TY.owned(G, s);
  if (own.length > 10) own = own.filter((T) => T.t === 'prop' || G.m[T.id]);   // a long hand overlaps on the pad: leave out the stations / utilities (the bank mortgages them itself when short)
  const mine = own.map((T) => cardOf(T.id)), out = !TY.alive(G, s), me = s === G.p && !t.done && !out;
  if (t.done) return { cards: { hand: mine, note: G.reason === 'last' ? W.last : W.rounds(TY.ROUNDS), turn: false, acts: [{ id: 'again', t: W.again, main: true }] } };
  const cash = `${m(G.money[s])}`;
  if (out) return { cards: { hand: [], note: W.note_out, turn: false, acts: [] } };
  if (!me) return { cards: { hand: mine, note: `${W.note_wait(t.name(G.p))} · ${cash}`, turn: false, acts: [] } };
  let acts, note, hand = mine;
  const jailed = G.jail[s] >= 0;
  if (G.phase === 'buy') {
    const T = B[G.pend]; hand = [];
    note = `${W.note_buy(tname(T.id), m(T.price))} (${cash})`;
    acts = [{ id: 'buy', t: W.buy + ' ' + m(T.price), main: true }, { id: 'skip', t: W.skip }];
  } else {
    const mg = [{ id: 'build', t: W.build }, { id: 'sell', t: W.sell }, { id: 'mort', t: W.mort }];
    if (G.phase === 'roll') {
      note = `${cash} · ${jailed ? W.note_jail : W.note_roll}`;
      acts = [{ id: 'roll', t: W.roll, main: true }];
      if (jailed) { acts.push({ id: 'jailpay', t: W.jailpay(m(TY.FINE)), off: G.money[s] < TY.FINE }); if (G.card[s] > 0) acts.push({ id: 'jailcard', t: W.jailcard }); }
    } else { note = `${cash} · ${W.note_act}`; acts = [{ id: 'end', t: W.end, main: true }]; }
    if (mine.length) { acts.push(...mg); note += ' (' + W.note_pick + ')'; hand = mine; } else hand = [];
  }
  return { cards: { hand, note, turn: true, acts: acts.slice(0, 6) } };
}

// ---- the TV: the board is built once, then only what changed is touched
const POS = (id) => (id <= 10 ? [11, 11 - id] : id <= 20 ? [21 - id, 1] : id <= 30 ? [1, id - 19] : [id - 29, 11]);
const ICON = { go: '➡', jail: '🔒', park: '🅿', gojail: '👮', chance: '❓', chest: '🎁', tax: '💸', rail: '🚆', util: '💡' };
const tiles = [], tcache = [];
function build() {
  const board = $('#board');
  for (let id = 0; id < 40; id++) {
    const T = B[id], [r, c] = POS(id), corner = id % 10 === 0;
    const side = corner ? 'cn' : id < 10 ? 'sb' : id < 20 ? 'sl' : id < 30 ? 'st' : 'sr';
    const e = h('div', `tile ${side} k-${T.t}`); e.style.gridRow = r; e.style.gridColumn = c;
    if (T.t === 'prop') e.style.setProperty('--g', TY.GROUP_COLORS[T.g]);
    const band = h('i', 'band'), inn = h('div', 'in'), tk = h('span', 'tk');
    inn.append(h('b', 'nm', tname(id)));
    if (ICON[T.t]) inn.append(h('u', 'ic', ICON[T.t]));
    if (T.price) inn.append(h('small', 'pr', k(T.price)));
    if (T.t === 'tax') inn.append(h('small', 'pr', k(T.n)));
    e.append(...(T.t === 'prop' ? [band] : []), inn, tk);
    board.append(e); tiles[id] = { e, band, tk }; tcache[id] = '';
  }
}
function clock(t) { const bar = document.querySelector('.pl.turn .timer i'); if (bar) bar.style.width = Math.max(0, t.left / TURN * 100) + '%'; }
let lastRolls = -1, lastSeq = -1;
function draw(t) {
  if (!t.ok) { $('#msg').replaceChildren(h('b', '', t.pad ? W.old : W.nopad), h('span', '', t.pad ? W.oldh : '')); return; }
  if (!tiles.length) build();
  if (!G) return;
  const nm = (p) => t.name(p), T = tname;
  // tiles: owner outline, houses, mortgage, tokens
  for (let id = 0; id < 40; id++) {
    const ti = tiles[id], o = G.owner[id], here = [];
    for (let p = 0; p < G.n; p++) if (TY.alive(G, p) && G.pos[p] === id) here.push(p);
    const key = o + '|' + G.h[id] + '|' + G.m[id] + '|' + here.join() + '|' + (G.jail[here[0]] >= 0 ? 'j' : '');
    if (tcache[id] === key) continue;
    tcache[id] = key;
    ti.e.style.setProperty('--oc', o >= 0 ? t.COLORS[o] : 'transparent');
    ti.e.classList.toggle('owned', o >= 0); ti.e.classList.toggle('mort', G.m[id]);
    if (ti.band) ti.band.textContent = G.h[id] === 5 ? '🏨' : G.h[id] ? '🏠'.repeat(G.h[id]) : '';
    ti.tk.replaceChildren(...here.map((p) => { const k = h('b', 'token', String(p + 1)); k.style.background = t.COLORS[p]; return k; }));
  }
  // players
  const pl = (side, seats) => $(side).replaceChildren(...seats.map((p) => {
    const e = h('div', 'pl' + (p === G.p && !t.done ? ' turn' : '') + (TY.alive(G, p) ? '' : ' out')); e.style.setProperty('--pc', t.COLORS[p]);
    const top = h('div', 'top'), tok = h('b', 'token', String(p + 1)); tok.style.background = t.COLORS[p];
    top.append(tok, h('span', 'nm', (t.human(p) ? '' : '🤖 ') + t.name(p)));
    if (!TY.alive(G, p)) top.append(h('span', 'st', W.brk)); else if (G.jail[p] >= 0) top.append(h('span', 'st', '🔒 ' + W.jailtxt));
    if (G.card[p]) top.append(h('span', 'st', '🎫' + (G.card[p] > 1 ? G.card[p] : '')));
    const own = TY.owned(G, p), chips = h('div', 'chips');
    own.forEach((q) => { const c = h('i', G.m[q.id] ? 'mort' : ''); c.style.background = q.t === 'prop' ? TY.GROUP_COLORS[q.g] : '#94a3b8'; if (G.h[q.id]) c.textContent = G.h[q.id] === 5 ? '★' : G.h[q.id]; chips.append(c); });
    e.append(top, h('div', 'mon', m(G.money[p])),
      h('div', 'sub', `${W.worth} ${m(TY.net(G, p))} · ${W.tilesn(own.length)}`), chips);
    if (p === G.p && !t.done && t.human(p)) { const k = h('div', 'timer'); k.append(h('i')); k.firstChild.style.width = (t.left / TURN * 100) + '%'; e.append(k); }
    return e;
  }));
  pl('#left', [0, 1]); pl('#right', [2, 3]);
  // the middle
  $('#hdr').replaceChildren(h('b', '', W.title), h('span', '', W.round(Math.min(G.round, TY.ROUNDS), TY.ROUNDS)));
  const dc = $('#dice');
  if (!dc.firstChild) dc.append(h('span'), h('span'));
  dc.children[0].textContent = DIE[G.dice[0]] || ''; dc.children[1].textContent = DIE[G.dice[1]] || '';
  if (G.rolls !== lastRolls) { lastRolls = G.rolls; dc.classList.remove('roll'); void dc.offsetWidth; dc.classList.add('roll'); }
  const deed = $('#deed'), id = G.pend >= 0 ? G.pend : G.pos[G.p], D = B[id], buyable = D.price;
  if (t.done) deed.replaceChildren();
  else if (buyable) {
    const o = G.owner[id], bar = h('div', 'bar'); bar.style.background = D.t === 'prop' ? TY.GROUP_COLORS[D.g] : '#64748b';
    const ln = h('div', 'ln');
    ln.append(h('b', '', T(id)), h('span', '', m(D.price)));
    const info = h('div', 'who', o < 0 ? W.free : `${W.owner}: ${nm(o)} · ${W.rentw} ${m(TY.rent(G, id))}`);
    deed.replaceChildren(bar, ln, info);
  } else { const ln = h('div', 'ln'); ln.append(h('b', '', T(id))); deed.replaceChildren(h('div', 'bar flat'), ln); }
  const last = G.log.slice(-5).reverse();
  $('#say').textContent = t.done ? '' : last.length ? W.ev[last[0].k](last[0], nm, T) : '';
  $('#log').replaceChildren(...last.slice(1).map((e, i) => h('div', 'l' + i, W.ev[e.k](e, nm, T))));
  lastSeq = G.seq;
  $('#msg').replaceChildren();
  const ov = $('#over');
  ov.hidden = !t.done || !t.games;
  if (t.done && t.games) {
    ov.replaceChildren(h('h1', '', '🏆 ' + W.win(t.name(t.order[0]))), h('p', 'why', G.reason === 'last' ? W.last : W.rounds(TY.ROUNDS)), ...t.order.map((s, r) => {
      const e = h('div', 'row' + (r ? '' : ' win')), p = t.points(r); e.style.setProperty('--pc', t.COLORS[s]);
      e.append(h('span', 'pl2', W.place[r]), h('span', 'dot'), h('span', 'nm', t.name(s)), h('span', 'pt', TY.alive(G, s) ? m(TY.net(G, s)) : W.brk), h('span', 'tt', (p > 0 ? '+' : '') + p + ' ' + W.pts));
      return e;
    }), h('p', '', W.newg));
  }
}
if (table.ok) table.go(); else draw(table);
