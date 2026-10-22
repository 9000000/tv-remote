'use strict';
// Cờ tỷ phú (Tycoon): the rules and the bots, pure (no DOM), loaded by the TV page and by node tests. Ported from gamehub24's
// monopoly/ (Hanoi / Saigon streets, the 40 tiles, its prices, rents, chance and chest cards), simplified to play in 20-30 minutes:
//  - 30 rounds (every player's turn is one round), then the richest (cash + properties + houses) wins; or the last one standing;
//  - no auctions and no trades: a tile you do not buy stays free;
//  - going short, the bank sells your houses and mortgages your tiles by itself (cheapest first) before you go bankrupt.
// A game is G = newGame(n); every move is apply(G, {act, id}) and returns '' or why not (an error key).
(function (root) {
  const START = 15000, SALARY = 2000, FINE = 500, ROUNDS = 30;
  const GROUP_COLORS = ['#8b5a2b', '#7dd3fc', '#f472b6', '#fb923c', '#ef4444', '#facc15', '#22a04b', '#3b5bdb'];
  const GROUPS = [[1, 3], [6, 8, 9], [11, 13, 14], [16, 18, 19], [21, 23, 24], [26, 27, 29], [31, 32, 34], [37, 39]];
  const RAILS = [5, 15, 25, 35], UTILS = [12, 28];

  const B = [];
  const add = (id, t, vi, en, o) => { B[id] = Object.assign({ id, t, vi, en: en || vi }, o); };
  const prop = (id, vi, g, price, rent, hc, sh) => add(id, 'prop', vi, vi, { g, price, rent, hc, sh: sh || vi });
  add(0, 'go', 'KHỞI HÀNH', 'GO');
  prop(1, 'Hàng Bài', 0, 600, [20, 100, 300, 900, 1600, 2500], 500);
  add(2, 'chest', 'Cộng đồng', 'Chest');
  prop(3, 'Hàng Đào', 0, 600, [40, 200, 600, 1800, 3200, 4500], 500);
  add(4, 'tax', 'Thuế thu nhập', 'Income tax', { n: 2000 });
  add(5, 'rail', 'Ga Hà Nội', 'Hanoi Stn', { price: 2000 });
  prop(6, 'Hàng Gai', 1, 1000, [60, 300, 900, 2700, 4000, 5500], 500);
  add(7, 'chance', 'Cơ hội', 'Chance');
  prop(8, 'Hàng Bông', 1, 1000, [60, 300, 900, 2700, 4000, 5500], 500);
  prop(9, 'Hàng Mã', 1, 1200, [80, 400, 1000, 3000, 4500, 6000], 500);
  add(10, 'jail', 'Nhà tù', 'Jail');
  prop(11, 'Phố Huế', 2, 1400, [100, 500, 1500, 4500, 6250, 7500], 1000);
  add(12, 'util', 'Công ty Điện', 'Electric Co', { price: 1500 });
  prop(13, 'Bà Triệu', 2, 1400, [100, 500, 1500, 4500, 6250, 7500], 1000);
  prop(14, 'Tràng Tiền', 2, 1600, [120, 600, 1800, 5000, 7000, 9000], 1000);
  add(15, 'rail', 'Ga Sài Gòn', 'Saigon Stn', { price: 2000 });
  prop(16, 'Lê Lợi', 3, 1800, [140, 700, 2000, 5500, 7500, 9500], 1000);
  add(17, 'chest', 'Cộng đồng', 'Chest');
  prop(18, 'Hàm Nghi', 3, 1800, [140, 700, 2000, 5500, 7500, 9500], 1000);
  prop(19, 'Nguyễn Huệ', 3, 2000, [160, 800, 2200, 6000, 8000, 10000], 1000);
  add(20, 'park', 'Đỗ xe miễn phí', 'Free parking');
  prop(21, 'Đồng Khởi', 4, 2200, [180, 900, 2500, 7000, 8750, 10500], 1500);
  add(22, 'chance', 'Cơ hội', 'Chance');
  prop(23, 'Lê Thánh Tôn', 4, 2200, [180, 900, 2500, 7000, 8750, 10500], 1500);
  prop(24, 'Hai Bà Trưng', 4, 2400, [200, 1000, 3000, 7500, 9250, 11000], 1500);
  add(25, 'rail', 'Ga Đà Nẵng', 'Da Nang Stn', { price: 2000 });
  prop(26, 'Phan Bội Châu', 5, 2600, [220, 1100, 3300, 8000, 9750, 11500], 1500, 'P.B. Châu');
  prop(27, 'Phan Chu Trinh', 5, 2600, [220, 1100, 3300, 8000, 9750, 11500], 1500, 'P.C. Trinh');
  add(28, 'util', 'Công ty Nước', 'Water Co', { price: 1500 });
  prop(29, 'Lý Thái Tổ', 5, 2800, [240, 1200, 3600, 8500, 10250, 12000], 1500);
  add(30, 'gojail', 'Vào tù', 'Go to jail');
  prop(31, 'Trần Hưng Đạo', 6, 3000, [260, 1300, 3900, 9000, 11000, 12750], 2000, 'T.H. Đạo');
  prop(32, 'Điện Biên Phủ', 6, 3000, [260, 1300, 3900, 9000, 11000, 12750], 2000, 'Điện B. Phủ');
  add(33, 'chest', 'Cộng đồng', 'Chest');
  prop(34, 'Lê Duẩn', 6, 3200, [280, 1500, 4500, 10000, 12000, 14000], 2000);
  add(35, 'rail', 'Ga Huế', 'Hue Stn', { price: 2000 });
  add(36, 'chance', 'Cơ hội', 'Chance');
  prop(37, 'Phú Mỹ Hưng', 7, 3500, [350, 1750, 5000, 11000, 13000, 15000], 2000);
  add(38, 'tax', 'Thuế xa xỉ', 'Luxury tax', { n: 1000 });
  prop(39, 'Thủ Thiêm', 7, 4000, [500, 2000, 6000, 14000, 17000, 20000], 2000);

  // cards: a = collect | pay | move (to tile n) | back (n tiles) | jail | free | each (pay every player) | from (collect from every player) | repair
  const C = (vi, en, a, n, n2) => ({ vi, en, a, n, n2 });
  const CHANCE = [
    C('Đi đến ô Khởi Hành', 'Advance to GO', 'move', 0), C('Đi vào Tù', 'Go to Jail', 'jail'),
    C('Ngân hàng trả bạn 500đ', 'Bank pays you 500', 'collect', 500), C('Nộp phạt 150đ', 'Pay 150 fine', 'pay', 150),
    C('Đi đến Đồng Khởi', 'Advance to Dong Khoi', 'move', 21), C('Lùi 3 ô', 'Go back 3 spaces', 'back', 3),
    C('Trả mỗi người chơi 500đ', 'Pay each player 500', 'each', 500), C('Nhận 1500đ', 'Collect 1500', 'collect', 1500),
    C('Thẻ ra tù miễn phí', 'Get out of jail free', 'free'), C('Sửa chữa: 250đ/nhà, 1000đ/khách sạn', 'Repairs: 250/house, 1000/hotel', 'repair', 250, 1000),
    C('Đi đến Hàm Nghi', 'Advance to Ham Nghi', 'move', 18), C('Đi đến Trần Hưng Đạo', 'Advance to Tran Hung Dao', 'move', 31),
    C('Đi đến Thủ Thiêm', 'Advance to Thu Thiem', 'move', 39), C('Phạt lái xe khi say. Trả 200đ', 'Drunk driving fine. Pay 200', 'pay', 200),
    C('Thắng giải ô chữ. Nhận 1000đ', 'Won a crossword. Collect 1000', 'collect', 1000),
  ];
  const CHEST = [
    C('Đi đến ô Khởi Hành', 'Advance to GO', 'move', 0), C('Lỗi ngân hàng, nhận 2000đ', 'Bank error, collect 2000', 'collect', 2000),
    C('Phí bác sĩ, trả 500đ', "Doctor's fee, pay 500", 'pay', 500), C('Bán cổ phiếu, nhận 500đ', 'Sale of stock, get 500', 'collect', 500),
    C('Thẻ ra tù miễn phí', 'Get out of jail free', 'free'), C('Đi vào Tù', 'Go to Jail', 'jail'),
    C('Sinh nhật! Nhận 100đ từ mỗi người', 'Birthday! Collect 100 from each', 'from', 100), C('Hoàn thuế, nhận 200đ', 'Tax refund, get 200', 'collect', 200),
    C('Trả viện phí, 1000đ', 'Pay hospital, 1000', 'pay', 1000), C('Thừa kế 1000đ', 'Inherit 1000', 'collect', 1000),
    C('Đáo hạn bảo hiểm. Nhận 1000đ', 'Insurance matures. Collect 1000', 'collect', 1000), C('Nộp học phí 1500đ', 'Pay school fees 1500', 'pay', 1500),
    C('Nhận tiền tư vấn 250đ', 'Consultancy fee. Collect 250', 'collect', 250), C('Tu sửa đường: 400đ/nhà, 1150đ/khách sạn', 'Street repairs: 400/house, 1150/hotel', 'repair', 400, 1150),
  ];

  const shuffle = (a, rnd) => { for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };
  const range = (n) => [...Array(n).keys()];

  function newGame(n, rnd) {
    rnd = rnd || Math.random;
    return {
      n, rnd, round: 1, p: 0, phase: 'roll', dbl: 0, again: false, dice: [0, 0], rolls: 0, pend: -1, over: false, reason: '',
      pos: Array(n).fill(0), money: Array(n).fill(START), jail: Array(n).fill(-1), card: Array(n).fill(0), out: [],
      owner: Array(40).fill(-1), h: Array(40).fill(0), m: Array(40).fill(false), log: [], seq: 0,
      deck: { chance: shuffle(range(CHANCE.length), rnd), chest: shuffle(range(CHEST.length), rnd) },
    };
  }
  const alive = (G, p) => !G.out.includes(p);
  const ev = (G, k, o) => { G.seq++; G.log.push(Object.assign({ k, seq: G.seq }, o)); if (G.log.length > 30) G.log.shift(); };
  const owned = (G, p) => B.filter((T) => G.owner[T.id] === p && (T.t === 'prop' || T.t === 'rail' || T.t === 'util'));
  const groupOf = (id) => (B[id].t === 'prop' ? GROUPS[B[id].g] : null);
  const fullSet = (G, p, id) => { const g = groupOf(id); return !!g && g.every((x) => G.owner[x] === p); };
  const net = (G, p) => G.money[p] + owned(G, p).reduce((s, T) => s + (G.m[T.id] ? T.price / 2 : T.price) + G.h[T.id] * (T.hc || 0), 0);

  function rent(G, id) {
    const T = B[id], o = G.owner[id];
    if (o < 0 || G.m[id]) return 0;
    if (T.t === 'rail') return 250 * RAILS.filter((r) => G.owner[r] === o).length;
    if (T.t === 'util') return 40 + (G.dice[0] + G.dice[1]) * (UTILS.every((u) => G.owner[u] === o) ? 10 : 4);
    if (T.t !== 'prop') return 0;
    if (G.h[id] > 0) return T.rent[G.h[id]];
    return fullSet(G, o, id) ? T.rent[0] * 2 : T.rent[0];
  }

  // ---- money, houses, mortgages
  const hmax = (G, id) => Math.max(...groupOf(id).map((x) => G.h[x])), hmin = (G, id) => Math.min(...groupOf(id).map((x) => G.h[x]));
  const mortValue = (id) => B[id].price / 2, redeemCost = (id) => Math.ceil(B[id].price * 0.55);
  function canBuild(G, p, id) {
    const T = B[id];
    if (!T || T.t !== 'prop' || G.owner[id] !== p) return 'notowner';
    if (!fullSet(G, p, id)) return 'set';
    if (groupOf(id).some((x) => G.m[x])) return 'mortgaged';
    if (G.h[id] >= 5) return 'max';
    if (G.h[id] > hmin(G, id)) return 'even';
    return G.money[p] >= T.hc ? '' : 'money';
  }
  function canSell(G, p, id) {
    const T = B[id];
    if (!T || T.t !== 'prop' || G.owner[id] !== p || G.h[id] <= 0) return 'nohouse';
    return G.h[id] < hmax(G, id) ? 'even' : '';
  }
  function canMortgage(G, p, id) {
    const T = B[id];
    if (!T || !T.price || G.owner[id] !== p) return 'notowner';
    if (G.m[id]) return G.money[p] >= redeemCost(id) ? '' : 'money';
    if (T.t === 'prop' && groupOf(id).some((x) => G.h[x] > 0)) return 'houses';
    return '';
  }
  function raise(G, p, need) {          // the bank sells houses (highest first), then mortgages the cheapest tiles, until there is [need]
    while (G.money[p] < need) {
      const hs = owned(G, p).filter((T) => T.t === 'prop' && G.h[T.id] > 0 && G.h[T.id] === hmax(G, T.id));
      if (hs.length) { const T = hs.sort((a, b) => b.hc - a.hc)[0]; G.h[T.id]--; G.money[p] += T.hc / 2; ev(G, 'sell', { p, id: T.id, auto: 1 }); continue; }
      const ms = owned(G, p).filter((T) => !G.m[T.id]).sort((a, b) => a.price - b.price);
      if (!ms.length) break;
      G.m[ms[0].id] = true; G.money[p] += mortValue(ms[0].id); ev(G, 'mort', { p, id: ms[0].id, auto: 1 });
    }
    return G.money[p] >= need;
  }
  function bankrupt(G, p, to) {
    G.out.push(p); ev(G, 'broke', { p, to });
    for (const T of owned(G, p)) {
      G.h[T.id] = 0;
      if (to >= 0) G.owner[T.id] = to; else { G.owner[T.id] = -1; G.m[T.id] = false; }
    }
    if (to >= 0) G.card[to] += G.card[p];
    G.card[p] = 0; G.jail[p] = -1;
    if (range(G.n).filter((x) => alive(G, x)).length <= 1) { G.over = true; G.reason = 'last'; }
  }
  /** [p] pays [amt] to player [to] (-1 the bank); true when paid, false when [p] went bankrupt. */
  function pay(G, p, amt, to) {
    if (amt <= 0) return true;
    if (G.money[p] < amt) raise(G, p, amt);
    if (G.money[p] >= amt) { G.money[p] -= amt; if (to >= 0) G.money[to] += amt; return true; }
    const rest = G.money[p]; G.money[p] = 0; if (to >= 0) G.money[to] += rest;
    bankrupt(G, p, to); return false;
  }

  // ---- moving and landing
  function toJail(G, p) { G.pos[p] = 10; G.jail[p] = 0; G.dbl = 0; G.again = false; G.pend = -1; ev(G, 'jail', { p }); }
  function moveTo(G, p, to, collect) {
    if (collect && to <= G.pos[p]) { G.money[p] += SALARY; ev(G, 'salary', { p, n: SALARY }); }
    G.pos[p] = to;
  }
  function drawCard(G, p, type) {
    const deck = G.deck[type], list = type === 'chance' ? CHANCE : CHEST, i = deck.shift(), c = list[i];
    if (c.a !== 'free') deck.push(i);
    ev(G, 'card', { p, c, type });
    switch (c.a) {
      case 'collect': G.money[p] += c.n; break;
      case 'pay': pay(G, p, c.n, -1); break;
      case 'move': moveTo(G, p, c.n, true); land(G, p); break;
      case 'back': G.pos[p] = (G.pos[p] + 40 - c.n) % 40; land(G, p); break;
      case 'jail': toJail(G, p); break;
      case 'free': G.card[p]++; break;
      case 'each': for (const q of range(G.n)) if (q !== p && alive(G, q) && alive(G, p)) pay(G, p, c.n, q); break;
      case 'from': for (const q of range(G.n)) if (q !== p && alive(G, q) && alive(G, p)) pay(G, q, c.n, p); break;
      case 'repair': {
        let hs = 0, ho = 0; for (const T of owned(G, p)) { if (G.h[T.id] === 5) ho++; else hs += G.h[T.id]; }
        pay(G, p, hs * c.n + ho * c.n2, -1); break;
      }
    }
  }
  function land(G, p) {
    const id = G.pos[p], T = B[id];
    switch (T.t) {
      case 'prop': case 'rail': case 'util': {
        const o = G.owner[id];
        if (o < 0) { if (G.money[p] >= T.price) G.pend = id; else ev(G, 'cantbuy', { p, id }); }
        else if (o !== p && !G.m[id]) { const r = rent(G, id); ev(G, 'rent', { p, to: o, id, n: r }); pay(G, p, r, o); }
        break;
      }
      case 'tax': ev(G, 'tax', { p, n: T.n }); pay(G, p, T.n, -1); break;
      case 'gojail': toJail(G, p); break;
      case 'chance': case 'chest': drawCard(G, p, T.t); break;
    }
  }
  function nextTurn(G) {
    const p = G.p; let q = p;
    do q = (q + 1) % G.n; while (!alive(G, q));
    if (q <= p) G.round++;
    G.dbl = 0; G.again = false; G.pend = -1;
    if (G.round > ROUNDS) { G.over = true; G.reason = 'rounds'; return; }
    G.p = q; G.phase = 'roll';
  }
  function settle(G) {          // after a move: what comes next in this turn
    if (G.over) return;
    const p = G.p;
    if (!alive(G, p)) { nextTurn(G); return; }
    G.phase = G.pend >= 0 ? 'buy' : G.again ? 'roll' : 'act';
  }

  // ---- the moves of a player on turn
  function roll(G) {
    if (G.phase !== 'roll') return 'phase';
    const p = G.p, d1 = 1 + Math.floor(G.rnd() * 6), d2 = 1 + Math.floor(G.rnd() * 6), sum = d1 + d2, dbl = d1 === d2;
    G.dice = [d1, d2]; G.rolls++; ev(G, 'roll', { p, d: [d1, d2] });
    const go = () => { const old = G.pos[p], to = (old + sum) % 40; if (old + sum >= 40) { G.money[p] += SALARY; ev(G, 'salary', { p, n: SALARY }); } G.pos[p] = to; land(G, p); };
    if (G.jail[p] >= 0) {
      G.again = false;
      if (dbl) { G.jail[p] = -1; ev(G, 'outdbl', { p }); go(); }
      else if (++G.jail[p] >= 3) { ev(G, 'fine', { p }); if (pay(G, p, FINE, -1)) { G.jail[p] = -1; go(); } }
      else { ev(G, 'stay', { p, n: G.jail[p] }); G.phase = 'act'; return ''; }
    } else {
      if (dbl) { if (++G.dbl >= 3) { ev(G, 'dbl3', { p }); toJail(G, p); settle(G); return ''; } G.again = true; } else G.again = false;
      go();
    }
    settle(G); return '';
  }
  function buy(G) {
    if (G.phase !== 'buy' || G.pend < 0) return 'phase';
    const p = G.p, id = G.pend, T = B[id];
    if (G.money[p] < T.price) return 'money';
    G.money[p] -= T.price; G.owner[id] = p; G.pend = -1; ev(G, 'buy', { p, id, n: T.price });
    settle(G); return '';
  }
  function skip(G) {
    if (G.phase !== 'buy') return 'phase';
    ev(G, 'skip', { p: G.p, id: G.pend }); G.pend = -1; settle(G); return '';
  }
  const manage = (G) => (G.phase === 'roll' || G.phase === 'act' ? '' : 'phase');
  function build(G, id) {
    const p = G.p, bad = manage(G) || canBuild(G, p, id); if (bad) return bad;
    G.money[p] -= B[id].hc; G.h[id]++; ev(G, 'build', { p, id, h: G.h[id] }); return '';
  }
  function sell(G, id) {
    const p = G.p, bad = manage(G) || canSell(G, p, id); if (bad) return bad;
    G.money[p] += B[id].hc / 2; G.h[id]--; ev(G, 'sell', { p, id }); return '';
  }
  function mort(G, id) {
    const p = G.p, bad = manage(G) || canMortgage(G, p, id); if (bad) return bad;
    if (G.m[id]) { G.money[p] -= redeemCost(id); G.m[id] = false; ev(G, 'redeem', { p, id }); }
    else { G.m[id] = true; G.money[p] += mortValue(id); ev(G, 'mort', { p, id }); }
    return '';
  }
  function jailPay(G) {
    const p = G.p;
    if (G.phase !== 'roll' || G.jail[p] < 0) return 'phase';
    if (G.money[p] < FINE) return 'money';
    G.money[p] -= FINE; G.jail[p] = -1; ev(G, 'fine', { p }); return '';
  }
  function jailCard(G) {
    const p = G.p;
    if (G.phase !== 'roll' || G.jail[p] < 0 || G.card[p] < 1) return 'phase';
    G.card[p]--; G.jail[p] = -1; ev(G, 'freecard', { p }); return '';
  }
  function end(G) { if (G.phase !== 'act') return 'phase'; nextTurn(G); return ''; }

  function apply(G, m) {
    if (G.over) return 'over';
    const id = m.id | 0;
    switch (m.act) {
      case 'roll': return roll(G); case 'buy': return buy(G); case 'skip': return skip(G);
      case 'build': return build(G, id); case 'sell': return sell(G, id); case 'mort': return mort(G, id);
      case 'jailpay': return jailPay(G); case 'jailcard': return jailCard(G); case 'end': return end(G);
    }
    return 'act';
  }

  // ---- the final order: those still in by their worth, then those out, the last one out first
  function rank(G) {
    return range(G.n).filter((x) => alive(G, x)).sort((a, b) => net(G, b) - net(G, a)).concat(G.out.slice().reverse());
  }

  // ---- a bot: one step of its turn (buy sensibly, build on sets, keep a cash reserve)
  const RESERVE = 1500;
  function bot(G) {
    const p = G.p, M = G.money[p];
    if (G.phase === 'buy') {
      const T = B[G.pend], after = M - T.price, g = groupOf(T.id);
      const mine = g ? g.filter((x) => G.owner[x] === p).length : 0, completes = g && mine === g.length - 1;
      const rival = g && g.some((x) => G.owner[x] >= 0 && G.owner[x] !== p);          // blocks a rival's set
      if (after >= RESERVE || (completes && after >= 200) || (T.t === 'rail' && after >= 600) || (rival && after >= 800)) return { act: 'buy' };
      return { act: 'skip' };
    }
    if (G.phase === 'roll' && G.jail[p] >= 0) {
      if (G.card[p] > 0) return { act: 'jailcard' };
      if (M > 7000 && G.round < 22) return { act: 'jailpay' };
    }
    for (const T of owned(G, p)) if (G.m[T.id] && M - redeemCost(T.id) >= 4000) return { act: 'mort', id: T.id };
    let best = -1, score = 0;
    for (const T of owned(G, p)) {
      if (T.t !== 'prop' || canBuild(G, p, T.id) || M - T.hc < RESERVE + 500) continue;
      const s = (T.rent[G.h[T.id] + 1] - T.rent[G.h[T.id]]) / T.hc;
      if (s > score) { score = s; best = T.id; }
    }
    if (best >= 0) return { act: 'build', id: best };
    return { act: G.phase === 'roll' ? 'roll' : 'end' };
  }

  const TY = {
    START, SALARY, FINE, ROUNDS, GROUP_COLORS, GROUPS, RAILS, UTILS, BOARD: B, CHANCE, CHEST,
    newGame, apply, bot, rank, rent, net, owned, alive, fullSet, canBuild, canSell, canMortgage, mortValue, redeemCost, groupOf,
  };
  if (typeof module === 'object' && module.exports) module.exports = TY; else root.TY = TY;
})(this);
