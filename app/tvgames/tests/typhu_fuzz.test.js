// Cờ tỷ phú fuzz (docs/games/web/typhu/rules.js): whole games with 2-4 players, each turn played by the bot or by a random
// player who throws every action at the engine (including illegal ones), seats switching between the two mid-turn (a phone
// leaving hands its seat to the bot). Seeded; FUZZ=n for more games.
// Invariants: money is an integer >= 0 and moves only by the bank flows the log names (salary, tax, cards, fines, buying,
// building, selling, mortgaging) or between players; a refused move changes nothing; bankrupt players hold nothing and are
// skipped; houses are even and only on complete unmortgaged sets; the game ends by round 30 or last standing; never throws.
const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const TY = require(path.join(__dirname, '../../../docs/games/web/typhu/rules.js'));
const { rng, games } = require('./_fuzz.js');

const B = TY.BOARD;
const snap = (G) => JSON.stringify(Object.assign({}, G, { rnd: 0, log: 0, seq: 0 }));
const sum = (a) => a.reduce((x, y) => x + y, 0);

function invariants(G, tag) {
  G.money.forEach((m, p) => assert.ok(Number.isInteger(m) && m >= 0, tag + ' money ' + m + ' p' + p));
  for (let id = 0; id < 40; id++) {
    const o = G.owner[id], T = B[id];
    if (o >= 0) { assert.ok(T.price, tag + ' owner on unbuyable tile ' + id); assert.ok(TY.alive(G, o), tag + ' dead owns ' + id); }
    else { assert.strictEqual(G.h[id], 0, tag); assert.strictEqual(G.m[id], false, tag + ' free tile mortgaged ' + id); }
    assert.ok(G.h[id] >= 0 && G.h[id] <= 5, tag);
    if (G.h[id] > 0) {
      assert.strictEqual(T.t, 'prop', tag); assert.ok(TY.fullSet(G, o, id), tag + ' houses without a set ' + id);
      assert.ok(TY.groupOf(id).every((x) => !G.m[x]), tag + ' houses beside a mortgaged tile ' + id);
    }
  }
  TY.GROUPS.forEach((g) => { const hs = g.map((x) => G.h[x]); assert.ok(Math.max(...hs) - Math.min(...hs) <= 1, tag + ' uneven ' + hs); });
  G.out.forEach((p) => {
    assert.strictEqual(G.money[p], 0, tag); assert.strictEqual(G.card[p], 0, tag);
    assert.ok(!B.some((T) => G.owner[T.id] === p), tag + ' bankrupt still owns');
  });
  assert.strictEqual(new Set(G.out).size, G.out.length, tag);
  G.pos.forEach((x) => assert.ok(Number.isInteger(x) && x >= 0 && x < 40, tag));
  G.jail.forEach((j, p) => { assert.ok(j >= -1 && j <= 2, tag + ' jail ' + j); if (j >= 0) assert.strictEqual(G.pos[p], 10, tag); });
  assert.ok(sum(G.card) <= 2, tag + ' free cards ' + G.card);
  if (!G.over) { assert.ok(TY.alive(G, G.p), tag + ' turn on a bankrupt player'); assert.ok(G.round >= 1 && G.round <= TY.ROUNDS, tag + ' round ' + G.round); }
}

/** What the log says the bank paid out (+) or took in (-) while one move ran; null when someone went bankrupt (partial payments). */
function bankFlow(G, since, before) {
  let d = 0;
  for (const e of G.log.filter((x) => x.seq > since)) {
    switch (e.k) {
      case 'broke': return null;
      case 'salary': d += e.n; break;
      case 'tax': d -= e.n; break;
      case 'fine': d -= TY.FINE; break;
      case 'buy': d -= e.n; break;
      case 'build': d -= B[e.id].hc; break;
      case 'sell': d += B[e.id].hc / 2; break;
      case 'mort': d += TY.mortValue(e.id); break;
      case 'redeem': d -= TY.redeemCost(e.id); break;
      case 'card':
        if (e.c.a === 'collect') d += e.c.n; else if (e.c.a === 'pay') d -= e.c.n;
        else if (e.c.a === 'repair') {                    // houses/hotels the card's player had when it was drawn
          let hs = 0, ho = 0; B.forEach((T) => { if (before.owner[T.id] === e.p) { if (before.h[T.id] === 5) ho++; else hs += before.h[T.id]; } });
          d -= hs * e.c.n + ho * e.c.n2;
        }
        break;
    }
  }
  return d;
}

function randomMove(G, r) {
  const p = G.p, ids = () => r.int(40);
  const pool = [{ act: 'roll' }, { act: 'end' }, { act: 'buy' }, { act: 'skip' }, { act: 'jailpay' }, { act: 'jailcard' },
    { act: 'build', id: ids() }, { act: 'sell', id: ids() }, { act: 'mort', id: ids() }, { act: 'bogus' }];
  // lean toward the move the phase wants so games move on, but keep the junk coming
  if (r() < 0.6) {
    if (G.phase === 'buy') return r() < 0.5 ? { act: 'buy' } : { act: 'skip' };
    if (G.phase === 'roll') return { act: 'roll' };
    const own = TY.owned(G, p);
    if (own.length && r() < 0.5) return { act: r.pick(['build', 'sell', 'mort']), id: r.pick(own).id };
    return { act: 'end' };
  }
  return r.pick(pool);
}

test('typhu fuzz: random + bot games, 2-4 players, money and tiles conserved, nobody stuck', () => {
  const N = games(300);
  let moves = 0, refused = 0, byRounds = 0, byLast = 0, broke = 0, flows = 0;
  for (let g = 0; g < N; g++) {
    const r = rng(40000 + g), tag = 'seed ' + (40000 + g), n = 2 + (g % 3);
    const G = TY.newGame(n, r);
    // per seat: the chance a move is the bot's (seats flip at random: a phone leaving hands the seat to the bot)
    let botP = Array.from({ length: n }, () => r());
    let guard = 0;
    while (!G.over) {
      if (++guard > 20000) assert.fail(tag + ' did not end, round ' + G.round + ' phase ' + G.phase);
      if (r() < 0.02) botP[r.int(n)] = r.pick([0, 1]);
      const m = r() < botP[G.p] ? TY.bot(G) : randomMove(G, r);
      const before = JSON.parse(snap(G)), since = G.seq, moneyBefore = sum(G.money);
      let why; try { why = TY.apply(G, m); } catch (e) { assert.fail(tag + ' threw ' + e.stack + ' on ' + JSON.stringify(m)); }
      moves++;
      assert.strictEqual(typeof why, 'string', tag);
      if (why) { refused++; assert.strictEqual(snap(G), JSON.stringify(before), tag + ' refused move changed state: ' + JSON.stringify(m) + ' ' + why); continue; }
      invariants(G, tag);
      const fl = bankFlow(G, since, before);
      if (fl != null) { flows++; assert.strictEqual(sum(G.money) - moneyBefore, fl, tag + ' money not conserved on ' + JSON.stringify(m)); }
      else broke++;
      // the bot never asks for something the engine refuses
      if (m === undefined) assert.fail(tag);
    }
    invariants(G, tag);
    if (G.reason === 'last') { byLast++; assert.strictEqual(n - G.out.length, 1, tag); } else { byRounds++; assert.strictEqual(G.reason, 'rounds', tag); assert.ok(G.out.length < n - 1, tag); }
    const rk = TY.rank(G); assert.deepStrictEqual(rk.slice().sort(), [...Array(n).keys()], tag + ' rank is not every player once');
    assert.ok(TY.alive(G, rk[0]), tag + ' a bankrupt player wins');
    assert.strictEqual(TY.apply(G, { act: 'roll' }), 'over', tag);
  }
  assert.ok(moves > 0 && refused > 0 && byRounds > 0 && broke > 0 && flows > 0, `moves ${moves} refused ${refused} rounds ${byRounds} last ${byLast} broke ${broke}`);
});

test('typhu fuzz: bot-only games never get a refusal and always end', () => {
  const N = games(200);
  for (let g = 0; g < N; g++) {
    const r = rng(70000 + g), tag = 'seed ' + (70000 + g), G = TY.newGame(2 + (g % 3), r);
    let guard = 0;
    while (!G.over) {
      if (++guard > 20000) assert.fail(tag + ' did not end');
      const m = TY.bot(G), why = TY.apply(G, m);
      assert.strictEqual(why, '', tag + ' bot move refused: ' + JSON.stringify(m) + ' ' + why + ' phase ' + G.phase);
    }
  }
});

test('typhu: redeeming costs the mortgage value + 10% exactly (no float round-up: 1400 -> 770, not 771)', () => {
  for (const T of TY.BOARD.filter((x) => x.price)) {
    const exact = (T.price / 2) * 11 / 10;                   // half the price plus a tenth, in integers: price * 11 / 20
    assert.strictEqual(TY.redeemCost(T.id), Math.ceil(Math.round(exact * 1e6) / 1e6), 'tile ' + T.id + ' price ' + T.price);
  }
  assert.strictEqual(TY.redeemCost(11), 770);
});
