// Mèo nổ fuzz (docs/games/web/meono/rules.js): whole games with 2-4 players, every phase driven by random legal moves, random
// ILLEGAL calls (which must be refused without touching the state), the bots' auto() (a phone that left hands its seat to the
// bot at any moment) and Nope storms. Seeded; FUZZ=n for more games.
// Invariants: every card id exists exactly once (hands + pile + discard + the defused bomb), the counts are the printed ones,
// bombs = living players - 1 (plus none in anyone's hand), the turn is on a living player, an even number of Nopes lets a card
// through and an odd number cancels it, someone can always act, the game ends with exactly one survivor, ranking lists everyone.
const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const MK = require(path.join(__dirname, '../../../docs/games/web/meono/rules.js'));
const { rng, games } = require('./_fuzz.js');

const snap = (G) => JSON.stringify(Object.assign({}, G, { r: 0 }));
const all = (G) => [].concat(...G.hands, G.draw, G.disc, G.bomb ? [G.bomb] : []);

function invariants(G, tag, total) {
  const cards = all(G), ids = new Set(cards.map((c) => c.id));
  assert.strictEqual(cards.length, total, tag + ' card count ' + cards.length + ' vs ' + total);
  assert.strictEqual(ids.size, total, tag + ' duplicate card id');
  const alive = MK.aliveList(G);
  const live = cards.filter((c) => c.k === 'bomb').length - G.disc.filter((c) => c.k === 'bomb').length;   // exploded bombs lie in the discard
  assert.strictEqual(G.hands.flat().filter((c) => c.k === 'bomb').length, 0, tag + ' a bomb in a hand');
  assert.strictEqual(live, alive.length - 1, tag + ' live bombs vs living players');
  G.alive.forEach((a, s) => { if (!a) assert.strictEqual(G.hands[s].length, 0, tag + ' dead player holds cards'); });
  assert.ok(G.phase === 'over' || G.alive[G.turn], tag + ' turn on a dead player');
  assert.ok(G.owe >= 1, tag + ' owe ' + G.owe);
  if (G.phase === 'over') { assert.strictEqual(alive.length, 1, tag); assert.strictEqual(G.winner, alive[0], tag); }
  else assert.ok(alive.length >= 2, tag);
  if (G.phase === 'give') assert.ok(G.alive[G.pend.target] && G.hands[G.pend.target].length > 0, tag + ' give from nobody');
  assert.strictEqual(G.phase === 'insert', !!G.bomb, tag + ' bomb in hand of the game vs phase');
}

/** A random call of any function: legal or not. */
function randomCall(G, r) {
  const s = r.int(G.n), t = r.int(G.n), hand = G.hands[s];
  const id = () => (hand.length && r() < 0.9 ? r.pick(hand).id : 'x-0');
  switch (r.int(10)) {
    case 0: return () => MK.play(G, s, [id()]);
    case 1: return () => MK.play(G, s, [id(), id(), id()].slice(0, 1 + r.int(3)));
    case 2: return () => MK.play(G, s, hand.filter((c) => c.k === (hand.length ? r.pick(hand).k : 'c1')).map((c) => c.id).slice(0, 1 + r.int(3)));
    case 3: return () => MK.target(G, s, t);
    case 4: return () => MK.name(G, s, r.pick(MK.NAMEABLE.concat(['bomb', 'zzz'])));
    case 5: return () => MK.nope(G, s);
    case 6: return () => MK.give(G, s, id());
    case 7: return () => MK.draw(G, s);
    case 8: return () => MK.insert(G, s, r.pick([-1, 0, 1, 2, 99, -5, 'a', NaN]));
    default: return () => MK.peekOk(G, s);
  }
}

test('meono fuzz: random + bot games, 2-4 players, cards conserved, Nope parity, nobody stuck', () => {
  const N = games(500);
  let steps = 0, refused = 0, booms = 0, nopes = 0, steals = 0;
  for (let g = 0; g < N; g++) {
    const r = rng(60000 + g), tag = 'seed ' + (60000 + g), n = 2 + (g % 3), G = MK.create(n, r);
    const total = all(G).length;
    // 56 printed cards + n defuse in hands + 2 (1 for two players) in the pile + n-1 bombs
    assert.strictEqual(total, Object.values(MK.COUNT).reduce((a, b) => a + b, 0) + n + (n === 2 ? 1 : 2) + n - 1, tag);
    invariants(G, tag, total);
    const botP = Array.from({ length: n }, () => r()), noper = Array.from({ length: n }, () => r());
    let guard = 0;
    while (G.phase !== 'over') {
      if (++guard > 30000) assert.fail(tag + ' did not end: phase ' + G.phase + ' alive ' + MK.aliveList(G) + ' draw ' + G.draw.length);
      // junk first: an illegal call must be refused and leave no trace; a legal one is just a move, the invariants decide
      if (r() < 0.5) {
        const before = snap(G), call = randomCall(G, r);
        let why; try { why = call(); } catch (e) { assert.fail(tag + ' threw ' + e.stack); }
        steps++;
        if (why) { refused++; assert.strictEqual(typeof why, 'string'); assert.strictEqual(snap(G), before, tag + ' refused call changed the state (' + why + ')'); }
        else if (G.phase === 'nope' && G.ev == null) { /* a legal play opened the window */ }
        invariants(G, tag, total);
        if (G.phase === 'over') break;
      }
      const evBefore = G.evn, a = MK.actor(G);
      if (G.phase === 'nope') {
        // the Nope window: some players Nope at random (up to the cap), then it closes with the parity rule
        const p = G.pend;
        for (let k = 0; k < 12 && r() < 0.6; k++) {
          const s = r.int(n), could = G.alive[s] && MK.has(G, s, 'nope') && G.pend.nopes.length < MK.NOPE_CAP, len = G.pend.nopes.length;
          const why = MK.nope(G, s);
          assert.strictEqual(why === '', could, tag + ' nope legality ' + why);
          assert.strictEqual(G.pend.nopes.length, len + (why === '' ? 1 : 0), tag);
          if (why === '') nopes++;
        }
        const kind = p.kind, by = G.turn;
        if (r() < 0.5) { for (let s = 0; s < n; s++) if (G.phase === 'nope' && MK.botNope(G, s)) MK.nope(G, s); }
        const odd2 = G.pend.nopes.length % 2 === 1;
        const res = MK.settle(G);
        assert.strictEqual(G.last.noped, odd2, tag + ' parity');
        assert.strictEqual(res, odd2 ? 'noped' : kind, tag);
        if (odd2) assert.ok(G.ev && G.ev.type === 'noped' && G.ev.seat === by, tag);
        if (G.ev && G.ev.type === 'steal') steals++;
        invariants(G, tag, total);
        continue;
      }
      // the actor: a bot (auto) or a "human" picking random legal moves
      let why;
      if (r() < botP[a]) why = MK.auto(G);
      else {
        const hand = G.hands[a];
        switch (G.phase) {
          case 'play': {
            const opts = ['draw'];
            hand.forEach((c) => { if (MK.ACTIONS.includes(c.k)) opts.push(c.id); });
            MK.CATS.forEach((k) => { const ids = hand.filter((c) => c.k === k).map((c) => c.id); if (ids.length >= 2) opts.push(ids.slice(0, 2 + (ids.length > 2 && r() < 0.5 ? 1 : 0))); });
            const o = r() < 0.5 ? 'draw' : r.pick(opts);
            why = o === 'draw' ? MK.draw(G, a) : MK.play(G, a, Array.isArray(o) ? o : [o]);
            break;
          }
          case 'target': why = MK.target(G, a, r.pick(MK.victims(G, a))); break;
          case 'name': why = MK.name(G, a, r.pick(MK.NAMEABLE)); break;
          case 'give': why = MK.give(G, a, r.pick(G.hands[a]).id); break;
          case 'peek': why = MK.peekOk(G, a); break;
          case 'insert': why = MK.insert(G, a, r.pick([-1, 0, 1, 2, 5, 99])); break;
          default: assert.fail(tag + ' unknown phase ' + G.phase);
        }
      }
      steps++;
      assert.strictEqual(why, '', tag + ' the actor was refused in phase ' + G.phase);
      if (G.ev && G.evn !== evBefore && G.ev.type === 'boom') booms++;
      invariants(G, tag, total);
    }
    invariants(G, tag, total);
    const rk = MK.ranking(G); assert.deepStrictEqual(rk.slice().sort(), [...Array(n).keys()], tag + ' ranking');
    assert.strictEqual(rk[0], G.winner, tag); assert.strictEqual(G.outOrder.length, n - 1, tag);
  }
  assert.ok(refused > 0 && booms > 0 && nopes > 0 && steals > 0, `steps ${steps} refused ${refused} booms ${booms} nopes ${nopes} steals ${steals}`);
});

test('meono fuzz: bot-only games (auto + autoNope) end with one survivor', () => {
  const N = games(300);
  for (let g = 0; g < N; g++) {
    const r = rng(80000 + g), tag = 'seed ' + (80000 + g), G = MK.create(2 + (g % 3), r);
    let guard = 0;
    while (G.phase !== 'over') {
      if (++guard > 20000) assert.fail(tag + ' did not end');
      if (G.phase === 'nope') MK.autoNope(G); else assert.strictEqual(MK.auto(G), '', tag + ' ' + G.phase);
    }
    assert.strictEqual(MK.aliveList(G).length, 1, tag);
  }
});
