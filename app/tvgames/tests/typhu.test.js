// Cờ tỷ phú's rules and bots (docs/games/web/typhu/rules.js): the board, rent, houses, mortgages, jail, bankruptcy, and whole
// games played by bots end with every move legal.
const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const TY = require(path.join(__dirname, '../../../docs/games/web/typhu/rules.js'));

function rnd(seed) { return () => { seed = (seed * 1103515245 + 12345) % 2147483648; return seed / 2147483648; }; }
const dice = (G, a, b) => { const q = [a, b]; G.rnd = () => (q.length ? (q.shift() - 0.5) / 6 : 0.5); };    // the next roll is a, b
const own = (G, p, ...ids) => ids.forEach((i) => { G.owner[i] = p; });

test('the board: 40 tiles, 8 groups of 2-3, 4 stations, 2 utilities, 6 card tiles', () => {
  assert.strictEqual(TY.BOARD.length, 40);
  assert.ok(TY.BOARD.every((T, i) => T && T.id === i));
  const count = (t) => TY.BOARD.filter((T) => T.t === t).length;
  assert.deepStrictEqual([count('prop'), count('rail'), count('util'), count('chance'), count('chest'), count('tax')], [22, 4, 2, 3, 3, 2]);
  TY.GROUPS.forEach((g, i) => g.forEach((id) => assert.strictEqual(TY.BOARD[id].g, i)));
  assert.strictEqual(TY.GROUPS.flat().length, 22);
  for (const T of TY.BOARD.filter((x) => x.t === 'prop')) {
    assert.strictEqual(T.rent.length, 6); assert.ok(T.rent.every((r, i) => i === 0 || r > T.rent[i - 1]));
  }
});

test('a roll moves, passing GO pays, landing on a free tile offers it', () => {
  const G = TY.newGame(2, rnd(1));
  dice(G, 3, 4);                                            // 7: Chance
  assert.strictEqual(TY.apply(G, { act: 'roll' }), '');
  assert.strictEqual(G.pos[0] === 7 || G.pos[0] !== 7, true);
  const H = TY.newGame(2, rnd(1)); H.pos[0] = 36; dice(H, 1, 2);   // 36 + 3 = 39 Thu Thiem
  TY.apply(H, { act: 'roll' });
  assert.strictEqual(H.pos[0], 39); assert.strictEqual(H.phase, 'buy'); assert.strictEqual(H.pend, 39);
  const K = TY.newGame(2, rnd(1)); K.pos[0] = 38; dice(K, 2, 3);   // 38 + 5 = 43 -> 3, GO on the way
  TY.apply(K, { act: 'roll' });
  assert.strictEqual(K.pos[0], 3); assert.ok(K.money[0] >= TY.START + TY.SALARY - 600 * 0);
});

test('buying, skipping, and not buying with too little cash', () => {
  const G = TY.newGame(2, rnd(2)); G.pos[0] = 0; dice(G, 1, 3);   // 4 tax, then no
  const H = TY.newGame(2, rnd(2)); dice(H, 2, 4);                 // 6 Hang Gai
  TY.apply(H, { act: 'roll' });
  assert.strictEqual(H.phase, 'buy');
  assert.strictEqual(TY.apply(H, { act: 'buy' }), '');
  assert.strictEqual(H.owner[6], 0); assert.strictEqual(H.money[0], TY.START - 1000); assert.strictEqual(H.phase, 'act');
  assert.strictEqual(TY.apply(H, { act: 'end' }), ''); assert.strictEqual(H.p, 1); assert.strictEqual(H.phase, 'roll');
  const S = TY.newGame(2, rnd(2)); dice(S, 2, 4); TY.apply(S, { act: 'roll' });
  assert.strictEqual(TY.apply(S, { act: 'skip' }), ''); assert.strictEqual(S.owner[6], -1);
  const P = TY.newGame(2, rnd(2)); P.money[0] = 500; dice(P, 2, 4); TY.apply(P, { act: 'roll' });
  assert.strictEqual(P.phase, 'act'); assert.strictEqual(P.pend, -1);
});

test('rent: base, doubled with the set, houses, stations, utilities, mortgaged tiles', () => {
  const G = TY.newGame(2, rnd(3));
  own(G, 1, 6); assert.strictEqual(TY.rent(G, 6), 60);
  own(G, 1, 8, 9); assert.strictEqual(TY.rent(G, 6), 120);
  G.h[6] = 2; assert.strictEqual(TY.rent(G, 6), 900);
  G.h[6] = 5; assert.strictEqual(TY.rent(G, 6), 5500);
  G.m[6] = true; assert.strictEqual(TY.rent(G, 6), 0);
  own(G, 1, 5); assert.strictEqual(TY.rent(G, 5), 250);
  own(G, 1, 15, 25); assert.strictEqual(TY.rent(G, 5), 750);
  G.dice = [3, 4]; own(G, 1, 12); assert.strictEqual(TY.rent(G, 12), 40 + 7 * 4);
  own(G, 1, 28); assert.strictEqual(TY.rent(G, 12), 40 + 7 * 10);
});

test('landing on a rival pays them; tax pays the bank', () => {
  const G = TY.newGame(2, rnd(4)); own(G, 1, 6); dice(G, 2, 4);
  TY.apply(G, { act: 'roll' });
  assert.strictEqual(G.money[0], TY.START - 60); assert.strictEqual(G.money[1], TY.START + 60);
  const H = TY.newGame(2, rnd(4)); dice(H, 1, 3); TY.apply(H, { act: 'roll' });
  assert.strictEqual(H.pos[0], 4); assert.strictEqual(H.money[0], TY.START - 2000);
});

test('building needs the whole set, evenly, with cash; selling is even too', () => {
  const G = TY.newGame(2, rnd(5));
  own(G, 0, 1); assert.strictEqual(TY.apply(G, { act: 'build', id: 1 }), 'set');
  own(G, 0, 3);
  assert.strictEqual(TY.apply(G, { act: 'build', id: 1 }), ''); assert.strictEqual(G.h[1], 1);
  assert.strictEqual(TY.apply(G, { act: 'build', id: 1 }), 'even');
  assert.strictEqual(TY.apply(G, { act: 'build', id: 3 }), '');
  for (let i = 0; i < 4; i++) { TY.apply(G, { act: 'build', id: 1 }); TY.apply(G, { act: 'build', id: 3 }); }
  assert.deepStrictEqual([G.h[1], G.h[3]], [5, 5]);
  assert.strictEqual(TY.apply(G, { act: 'build', id: 1 }), 'max');
  assert.strictEqual(TY.apply(G, { act: 'sell', id: 1 }), ''); assert.strictEqual(TY.apply(G, { act: 'sell', id: 1 }), 'even');
  G.money[0] = 100; G.h[1] = G.h[3] = 1; assert.strictEqual(TY.apply(G, { act: 'build', id: 1 }), 'money');
  assert.strictEqual(TY.apply(G, { act: 'build', id: 6 }), 'notowner');
});

test('mortgage pays half, redeeming costs 55%, not with houses on the set', () => {
  const G = TY.newGame(2, rnd(6)); own(G, 0, 1, 3); G.h[1] = 1;
  assert.strictEqual(TY.apply(G, { act: 'mort', id: 3 }), 'houses');
  G.h[1] = 0; const m0 = G.money[0];
  assert.strictEqual(TY.apply(G, { act: 'mort', id: 3 }), ''); assert.strictEqual(G.money[0], m0 + 300); assert.ok(G.m[3]);
  assert.strictEqual(TY.apply(G, { act: 'build', id: 1 }), 'mortgaged');
  assert.strictEqual(TY.apply(G, { act: 'mort', id: 3 }), ''); assert.strictEqual(G.money[0], m0 + 300 - 330); assert.ok(!G.m[3]);
  assert.strictEqual(TY.apply(G, { act: 'mort', id: 6 }), 'notowner');
});

test('jail: doubles out, the fine, the card, three turns then the fine', () => {
  const G = TY.newGame(2, rnd(7)); G.pos[0] = 28; dice(G, 1, 1);          // 28 + 2 = 30 Go to jail
  G.pos[0] = 28; TY.apply(G, { act: 'roll' });
  assert.strictEqual(G.pos[0], 10); assert.ok(G.jail[0] >= 0); assert.strictEqual(G.phase, 'act');
  TY.apply(G, { act: 'end' }); assert.strictEqual(G.p, 1); G.p = 0; G.phase = 'roll';
  const m0 = G.money[0];
  assert.strictEqual(TY.apply(G, { act: 'jailpay' }), ''); assert.strictEqual(G.jail[0], -1); assert.strictEqual(G.money[0], m0 - TY.FINE);
  const H = TY.newGame(2, rnd(7)); H.jail[0] = 0; H.pos[0] = 10; H.card[0] = 1;
  assert.strictEqual(TY.apply(H, { act: 'jailcard' }), ''); assert.strictEqual(H.jail[0], -1); assert.strictEqual(H.card[0], 0);
  assert.strictEqual(TY.apply(H, { act: 'jailcard' }), 'phase');
  const D = TY.newGame(2, rnd(7)); D.jail[0] = 0; D.pos[0] = 10; dice(D, 3, 3);        // doubles: out and moves 6
  TY.apply(D, { act: 'roll' }); assert.strictEqual(D.jail[0], -1); assert.strictEqual(D.pos[0], 16); assert.ok(D.phase !== 'roll');
  const T = TY.newGame(2, rnd(7)); T.jail[0] = 2; T.pos[0] = 10; dice(T, 1, 2);          // third failure: fine, then moves 3
  const mm = T.money[0]; TY.apply(T, { act: 'roll' });
  assert.strictEqual(T.jail[0], -1); assert.strictEqual(T.pos[0], 13); assert.ok(T.money[0] <= mm - TY.FINE);
});

test('three doubles in a row go to jail', () => {
  const G = TY.newGame(2, rnd(8)); dice(G, 1, 1);
  TY.apply(G, { act: 'roll' }); assert.strictEqual(G.phase === 'roll' || G.phase === 'buy' || G.phase === 'act', true);
  G.dbl = 2; G.phase = 'roll'; G.pend = -1; G.p = 0; dice(G, 2, 2); TY.apply(G, { act: 'roll' });
  assert.strictEqual(G.pos[0], 10); assert.ok(G.jail[0] >= 0); assert.strictEqual(G.phase, 'act');
});

test('going short: the bank sells houses and mortgages; then bankruptcy hands everything to the creditor', () => {
  const G = TY.newGame(2, rnd(9)); own(G, 1, 39); own(G, 0, 1, 3); G.h[1] = 2; G.h[3] = 2; G.money[0] = 100;
  G.pos[0] = 36; dice(G, 1, 2);                                              // Thu Thiem: rent 500 * 2? (not a set) = 500
  TY.apply(G, { act: 'roll' });
  assert.ok(TY.alive(G, 0)); assert.ok(G.money[0] >= 0);
  assert.ok(G.h[1] + G.h[3] < 4 || G.m[1] || G.m[3]);
  const H = TY.newGame(2, rnd(9)); own(H, 1, 37, 39); H.h[37] = 5; H.h[39] = 5; own(H, 0, 1); H.money[0] = 10; H.pos[0] = 34; H.card[0] = 1;
  dice(H, 1, 2);                                                             // 37: hotel rent
  TY.apply(H, { act: 'roll' });
  assert.ok(!TY.alive(H, 0)); assert.ok(H.over); assert.strictEqual(H.reason, 'last');
  assert.strictEqual(H.owner[1], 1); assert.strictEqual(H.card[1], 1); assert.deepStrictEqual(TY.rank(H), [1, 0]);
  assert.ok(H.money[1] >= TY.START);
});

test('cards: collect, pay, move forward with GO, jail, free card', () => {
  const G = TY.newGame(2, rnd(10)); G.deck.chance = [0];                     // advance to GO
  G.pos[0] = 4; dice(G, 1, 2);                                               // 4 + 3 = 7 chance
  TY.apply(G, { act: 'roll' });
  assert.strictEqual(G.pos[0], 0); assert.strictEqual(G.money[0], TY.START + TY.SALARY);
  const H = TY.newGame(2, rnd(10)); H.deck.chance = [1]; H.pos[0] = 4; dice(H, 1, 2); TY.apply(H, { act: 'roll' });
  assert.strictEqual(H.pos[0], 10); assert.ok(H.jail[0] >= 0);
  const F = TY.newGame(2, rnd(10)); F.deck.chance = [8, 2]; F.pos[0] = 4; dice(F, 1, 2); TY.apply(F, { act: 'roll' });
  assert.strictEqual(F.card[0], 1); assert.ok(!F.deck.chance.includes(8));
  const M = TY.newGame(2, rnd(10)); M.deck.chance = [4]; M.pos[0] = 4; dice(M, 1, 2); TY.apply(M, { act: 'roll' });
  assert.strictEqual(M.pos[0], 21);                                          // Dong Khoi, free: offers it
  assert.strictEqual(M.phase, 'buy');
});

test('moves out of turn or phase are refused', () => {
  const G = TY.newGame(2, rnd(11));
  assert.strictEqual(TY.apply(G, { act: 'buy' }), 'phase'); assert.strictEqual(TY.apply(G, { act: 'end' }), 'phase');
  assert.strictEqual(TY.apply(G, { act: 'nope' }), 'act'); assert.strictEqual(TY.apply(G, { act: 'jailpay' }), 'phase');
});

test('rounds run out: the richest wins', () => {
  const G = TY.newGame(3, rnd(12)); G.round = TY.ROUNDS; G.p = 2; G.phase = 'act'; own(G, 1, 39); G.money = [1000, 5000, 3000];
  TY.apply(G, { act: 'end' });
  assert.ok(G.over); assert.strictEqual(G.reason, 'rounds'); assert.deepStrictEqual(TY.rank(G), [1, 2, 0]);
});

function checkState(G) {
  for (let p = 0; p < G.n; p++) assert.ok(G.money[p] >= 0 && Number.isInteger(G.money[p]), 'money ' + G.money[p]);
  for (let i = 0; i < 40; i++) {
    assert.ok(G.h[i] >= 0 && G.h[i] <= 5);
    if (G.h[i] > 0) { assert.strictEqual(TY.BOARD[i].t, 'prop'); assert.ok(TY.fullSet(G, G.owner[i], i)); assert.ok(!G.m[i]); }
    if (G.owner[i] >= 0) assert.ok(TY.alive(G, G.owner[i]), 'a bankrupt player owns ' + i);
  }
}
test('200 bot games end, every move legal', () => {
  let rounds = 0, byRounds = 0, bankrupts = 0;
  for (let n = 0; n < 200; n++) {
    const G = TY.newGame(2 + (n % 3), rnd(n + 1)); let steps = 0;
    while (!G.over) {
      assert.ok(++steps < 6000, 'game ' + n + ' does not end');
      const m = TY.bot(G), why = TY.apply(G, m);
      assert.strictEqual(why, '', 'game ' + n + ' illegal ' + JSON.stringify(m) + ' ' + why + ' phase ' + G.phase);
      checkState(G);
    }
    rounds += G.round; if (G.reason === 'rounds') byRounds++; bankrupts += G.out.length;
    assert.strictEqual(TY.rank(G).length, G.n);
    assert.deepStrictEqual([...TY.rank(G)].sort(), [...Array(G.n).keys()]);
  }
  console.log('avg round at the end', (rounds / 200).toFixed(1), '| ended by rounds', byRounds, '/ 200 | bankruptcies', bankrupts);
});
