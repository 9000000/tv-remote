// Mèo nổ's rules and bots (docs/games/web/meono/rules.js): the deal, each card's effect, Nope chains, defuse / bomb, and whole
// bot-only games that end with a winner and only legal moves.
const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const MK = require(path.join(__dirname, '../../../docs/games/web/meono/rules.js'));

let seed = 1;
const rng = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; };
const card = (k, n) => ({ id: k + '-t' + n, k });
// a game with hands / pile set by hand: cards named by kind
function game(n, hands, pile) {
  const G = MK.create(n, rng); let no = 0;
  G.hands = hands.map((h) => h.map((k) => card(k, ++no)));
  G.draw = (pile || []).map((k) => card(k, ++no)).reverse();       // pile[0] is the top
  G.turn = 0; G.owe = 1; G.phase = 'play'; G.alive = Array(n).fill(true); G.disc = []; G.outOrder = [];
  return G;
}
const id = (G, s, k) => G.hands[s].find((c) => c.k === k).id;
const ids = (G, s, k, n) => G.hands[s].filter((c) => c.k === k).slice(0, n).map((c) => c.id);
const total = (G) => G.hands.reduce((a, h) => a + h.length, 0) + G.draw.length + G.disc.length + (G.bomb ? 1 : 0);

test('the deal: 7 cards each with a Defuse, n-1 bombs, extra Defuses', () => {
  for (const n of [2, 3, 4]) {
    const G = MK.create(n, rng);
    assert.ok(G.hands.every((h) => h.length === 7 && h.filter((c) => c.k === 'defuse').length === 1 && !h.some((c) => c.k === 'bomb')));
    assert.strictEqual(G.draw.filter((c) => c.k === 'bomb').length, n - 1);
    assert.strictEqual(G.draw.filter((c) => c.k === 'defuse').length, n === 2 ? 1 : 2);
    assert.strictEqual(new Set([...G.draw, ...G.hands.flat()].map((c) => c.id)).size, G.draw.length + 7 * n);
  }
});

test('draw: a card goes to the hand and the turn passes', () => {
  const G = game(3, [['skip'], ['skip'], ['skip']], ['attack', 'nope']);
  assert.strictEqual(MK.draw(G, 1), 'turn');
  assert.strictEqual(MK.draw(G, 0), '');
  assert.ok(MK.has(G, 0, 'attack')); assert.strictEqual(G.turn, 1); assert.strictEqual(G.owe, 1);
});

test('attack: next player owes 2 turns; stacks onto 4', () => {
  const G = game(3, [['attack', 'skip'], ['attack', 'skip'], ['skip']], ['skip', 'skip', 'skip', 'skip', 'skip']);
  assert.strictEqual(MK.play(G, 0, [id(G, 0, 'attack')]), ''); assert.strictEqual(G.phase, 'nope'); MK.settle(G);
  assert.deepStrictEqual([G.turn, G.owe], [1, 2]);
  MK.play(G, 1, [id(G, 1, 'attack')]); MK.settle(G);
  assert.deepStrictEqual([G.turn, G.owe], [2, 4]);
  MK.draw(G, 2); assert.deepStrictEqual([G.turn, G.owe], [2, 3]);       // still owes
});

test('skip: ends one turn without drawing; with 2 owed it keeps the player', () => {
  const G = game(3, [['skip', 'skip'], ['skip'], ['skip']], ['attack']);
  MK.play(G, 0, [id(G, 0, 'skip')]); MK.settle(G);
  assert.deepStrictEqual([G.turn, G.owe, G.draw.length], [1, 1, 1]);
  G.turn = 0; G.owe = 2; G.hands[0].push(card('skip', 99));
  MK.play(G, 0, [id(G, 0, 'skip')]); MK.settle(G);
  assert.deepStrictEqual([G.turn, G.owe], [0, 1]);
});

test('favor: pick a victim, the victim gives a card of their choice', () => {
  const G = game(3, [['favor'], ['c1', 'nope'], []], []);
  assert.deepStrictEqual(MK.victims(G, 0), [1]);                            // 2 has no cards
  MK.play(G, 0, [id(G, 0, 'favor')]);
  assert.strictEqual(G.phase, 'target');
  assert.strictEqual(MK.target(G, 0, 2), 'victim');
  assert.strictEqual(MK.target(G, 0, 0), 'victim');
  assert.strictEqual(MK.target(G, 0, 1), ''); assert.strictEqual(G.phase, 'nope');
  MK.settle(G); assert.strictEqual(G.phase, 'give'); assert.strictEqual(MK.actor(G), 1);
  assert.strictEqual(MK.give(G, 0, id(G, 1, 'nope')), 'turn');
  assert.strictEqual(MK.give(G, 1, 'zzz'), 'card');
  assert.strictEqual(MK.give(G, 1, id(G, 1, 'nope')), '');
  assert.ok(MK.has(G, 0, 'nope')); assert.strictEqual(G.phase, 'play'); assert.strictEqual(G.turn, 0);
});

test('favor with nobody holding a card goes straight to the window and does nothing', () => {
  const G = game(2, [['favor'], []], []);
  MK.play(G, 0, [id(G, 0, 'favor')]); assert.strictEqual(G.phase, 'nope'); MK.settle(G); assert.strictEqual(G.phase, 'play');
});

test('shuffle changes the pile, keeps its cards', () => {
  const G = game(2, [['shuffle'], ['skip']], Array(30).fill('skip').map((k, i) => (i % 3 ? 'attack' : 'favor')));
  const before = G.draw.map((c) => c.id).join();
  MK.play(G, 0, [id(G, 0, 'shuffle')]); MK.settle(G);
  assert.notStrictEqual(G.draw.map((c) => c.id).join(), before);
  assert.strictEqual(G.draw.length, 30);
});

test('see the future: the top 3 in order, private phase, then OK', () => {
  const G = game(2, [['future'], ['skip']], ['attack', 'favor', 'nope', 'skip']);
  MK.play(G, 0, [id(G, 0, 'future')]); MK.settle(G);
  assert.strictEqual(G.phase, 'peek'); assert.deepStrictEqual(G.peek.map((c) => c.k), ['attack', 'favor', 'nope']);
  assert.strictEqual(MK.peekOk(G, 1), 'turn');
  assert.strictEqual(MK.peekOk(G, 0), ''); assert.strictEqual(G.phase, 'play'); assert.strictEqual(G.peek, null);
});

test('Nope: one cancels (the card is still spent), two cancel each other, only with a Nope card', () => {
  let G = game(3, [['attack'], ['nope'], ['nope', 'nope']], ['skip']);
  MK.play(G, 0, [id(G, 0, 'attack')]);
  assert.strictEqual(MK.nope(G, 0), 'none');
  assert.strictEqual(MK.nope(G, 1), '');
  assert.strictEqual(MK.settle(G), 'noped');
  assert.deepStrictEqual([G.turn, G.owe, G.phase], [0, 1, 'play']); assert.strictEqual(G.last.noped, true);
  assert.strictEqual(G.disc.length, 2);
  G = game(3, [['attack'], ['nope'], ['nope', 'nope']], ['skip']);
  MK.play(G, 0, [id(G, 0, 'attack')]); MK.nope(G, 1); MK.nope(G, 2);
  assert.strictEqual(MK.settle(G), 'attack'); assert.deepStrictEqual([G.turn, G.owe], [1, 2]);
  assert.strictEqual(MK.nope(G, 2), 'turn');                              // the window is closed
});

test('Nope chain stops at the cap', () => {
  const G = game(2, [['attack', ...Array(8).fill('nope')], Array(8).fill('nope')], ['skip']);
  MK.play(G, 0, [id(G, 0, 'attack')]);
  for (let i = 0; i < MK.NOPE_CAP; i++) assert.strictEqual(MK.nope(G, i % 2), '');
  assert.strictEqual(MK.nope(G, 0), 'cap');
});

test('pair steals a random card; three steals the named one or misses', () => {
  const G = game(3, [['c1', 'c1', 'c2', 'c2', 'c2'], ['attack', 'skip'], ['defuse']], ['nope']);
  assert.strictEqual(MK.play(G, 0, [id(G, 0, 'c1'), id(G, 0, 'c2')]), 'combo');
  assert.strictEqual(MK.play(G, 0, [id(G, 0, 'c1')]), 'single');
  assert.strictEqual(MK.play(G, 0, ['zz']), 'card');
  MK.play(G, 0, ids(G, 0, 'c1', 2));
  MK.target(G, 0, 1); MK.settle(G);
  assert.strictEqual(G.hands[0].length, 4); assert.strictEqual(G.hands[1].length, 1); assert.strictEqual(G.ev.type, 'steal');
  MK.play(G, 0, ids(G, 0, 'c2', 3));
  MK.target(G, 0, 2); assert.strictEqual(G.phase, 'name');
  assert.strictEqual(MK.name(G, 0, 'bomb'), 'name');
  MK.name(G, 0, 'defuse'); MK.settle(G);
  assert.ok(MK.has(G, 0, 'defuse')); assert.strictEqual(G.hands[2].length, 0); assert.strictEqual(G.ev.card, 'defuse');
  const H = game(3, [['c1', 'c1', 'c1'], ['attack'], ['skip']], []);
  MK.play(H, 0, ids(H, 0, 'c1', 3)); MK.target(H, 0, 1); MK.name(H, 0, 'defuse'); MK.settle(H);
  assert.strictEqual(H.ev.type, 'miss'); assert.strictEqual(H.hands[1].length, 1);
});

test('a noped pair steals nothing', () => {
  const G = game(3, [['c1', 'c1'], ['nope', 'skip'], ['skip']], []);
  MK.play(G, 0, ids(G, 0, 'c1', 2)); MK.target(G, 0, 1); MK.nope(G, 1); MK.settle(G);
  assert.strictEqual(G.hands[0].length, 0); assert.strictEqual(G.hands[1].length, 1);
});

test('bomb with a Defuse: put it back; without: out of the game', () => {
  const G = game(3, [['defuse', 'skip'], ['skip'], ['skip']], ['bomb', 'attack', 'favor', 'skip']);
  MK.draw(G, 0); assert.strictEqual(G.phase, 'insert'); assert.strictEqual(G.alive[0], true);
  assert.strictEqual(MK.insert(G, 0, 'x'), 'pos'); assert.strictEqual(MK.insert(G, 1, 0), 'turn');
  MK.insert(G, 0, 2);
  assert.deepStrictEqual(G.draw.slice().reverse().map((c) => c.k), ['attack', 'favor', 'bomb', 'skip']);
  assert.deepStrictEqual([G.turn, G.phase], [1, 'play']);
  G.draw.push(card('bomb', 77));                                             // player 1 has no Defuse
  MK.draw(G, 1);
  assert.strictEqual(G.alive[1], false); assert.strictEqual(G.hands[1].length, 0); assert.strictEqual(G.turn, 2);
  assert.strictEqual(G.ev.type, 'boom'); assert.deepStrictEqual(G.outOrder, [1]);
});

test('the last one alive wins; ranking puts the last out second', () => {
  const G = game(3, [['skip'], ['skip'], ['skip', 'skip']], ['bomb', 'bomb']);
  MK.draw(G, 0); MK.draw(G, 1);
  assert.strictEqual(G.phase, 'over'); assert.strictEqual(G.winner, 2);
  assert.deepStrictEqual(MK.ranking(G), [2, 1, 0]);
});

test('insert at the bottom / anywhere, and defuse during an attack keeps the second turn', () => {
  const G = game(2, [['defuse'], ['skip']], ['bomb', 'skip', 'skip']);
  G.owe = 2;
  MK.draw(G, 0); MK.insert(G, 0, 99);
  assert.strictEqual(G.draw[0].k, 'bomb'); assert.deepStrictEqual([G.turn, G.owe, G.phase], [0, 1, 'play']);
  G.draw.push(card('bomb', 5)); G.hands[0].push(card('defuse', 5));
  MK.draw(G, 0); MK.insert(G, 0, -1); assert.strictEqual(G.draw.filter((c) => c.k === 'bomb').length, 2);
});

test('the phases refuse the wrong player or the wrong move', () => {
  const G = game(3, [['attack'], ['skip'], ['skip']], ['skip']);
  assert.strictEqual(MK.play(G, 1, [id(G, 1, 'skip')]), 'turn');
  assert.strictEqual(MK.target(G, 0, 1), 'turn'); assert.strictEqual(MK.insert(G, 0, 0), 'turn');
  assert.strictEqual(MK.give(G, 0, 'x'), 'turn'); assert.strictEqual(MK.peekOk(G, 0), 'turn');
  assert.strictEqual(MK.nope(G, 1), 'turn'); assert.strictEqual(MK.settle(G), '');
});

test('bot-only games: 200 games of 2-4 players end with a winner, all cards kept, only legal moves', () => {
  let nopes = 0, steals = 0, booms = 0;
  for (let g = 0; g < 200; g++) {
    const n = 2 + (g % 3), G = MK.create(n, rng), cards0 = total(G);
    let steps = 0;
    while (G.phase !== 'over') {
      assert.ok(++steps < 5000, 'game ' + g + ' does not end');
      if (G.phase === 'nope') { nopes += G.pend.nopes.length; MK.autoNope(G); if (G.ev && G.ev.type === 'steal') steals++; }
      else {
        assert.ok(G.alive[MK.actor(G)], 'a dead player acts');
        const e = MK.auto(G); assert.strictEqual(e, '', 'phase ' + G.phase + ' ' + e);
        if (G.ev && G.ev.type === 'boom') booms++;
      }
      assert.strictEqual(total(G), cards0, 'cards are kept');
      assert.ok(G.draw.filter((c) => c.k === 'bomb').length + (G.bomb ? 1 : 0) >= MK.aliveList(G).length - 1 || G.phase === 'over');
    }
    assert.strictEqual(MK.aliveList(G).length, 1); assert.strictEqual(MK.ranking(G).length, n); assert.strictEqual(G.winner, MK.ranking(G)[0]);
    assert.strictEqual(new Set(MK.ranking(G)).size, n);
  }
  assert.ok(booms > 200 && steals > 0, `booms ${booms} steals ${steals} nopes ${nopes}`);
});
