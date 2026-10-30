// Bài màu (Uno-like) fuzz (docs/games/web/colors/rules.js): whole games with 2-4 players where each seat is a bot or a random
// player, throwing random illegal calls in between. Seeded; FUZZ=n for more games.
// Invariants: 108 distinct cards always (hands + draw + discard), a refused call changes nothing, every effect matches the
// documented rule (Skip / two-player Reverse skip, Reverse flips the direction, +2 / +4 make the next draw and lose the turn,
// Wild asks a colour, no stacking), the player on turn always has an action (play, draw or pass), the game ends with the first
// empty hand and order() lists everyone once.
const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const UL = require(path.join(__dirname, '../../../docs/games/web/colors/rules.js'));
const { rng, games } = require('./_fuzz.js');

const snap = (st) => JSON.stringify(st);
const total = (st) => st.hands.reduce((a, h) => a + h.length, 0) + st.draw.length + st.discard.length;
function conserved(st, tag) {
  assert.strictEqual(total(st), 108, tag + ' cards ' + total(st));
  const ids = [].concat(...st.hands, st.draw, st.discard).map((c) => c.id).sort((a, b) => a - b);
  assert.deepStrictEqual(ids, Array.from({ length: 108 }, (_, i) => i), tag + ' ids');
  assert.ok(st.turn >= 0 && st.turn < st.n && (st.dir === 1 || st.dir === -1), tag);
  assert.ok(st.color >= 0 && st.color <= 3, tag + ' colour ' + st.color);
}
const mod = (a, n) => ((a % n) + n) % n;

test('colors fuzz: random + bot games, 2-4 players, effects as documented, never stuck', () => {
  const N = games(800);
  let plays = 0, draws = 0, passes = 0, refused = 0, reshuffles = 0, wins = 0;
  for (let g = 0; g < N; g++) {
    const r = rng(150000 + g), tag = 'seed ' + (150000 + g), n = 2 + (g % 3), st = UL.newGame(n, r);
    st.hands.forEach((h) => assert.ok(h.length >= 7 && h.length <= 9, tag + ' dealt hand'));     // 7, or 9 for a first card of +2 on the player who draws
    assert.notStrictEqual(UL.topCard(st).c, UL.WILD, tag + ' wild start');
    conserved(st, tag);
    const bots = Array.from({ length: n }, () => r() < 0.5);
    let guard = 0;
    while (st.winner < 0) {
      if (++guard > 6000) assert.fail(tag + ' did not end');
      // junk calls: wrong seat, wrong card, double draw, pass without draw, bad colour
      if (r() < 0.4) {
        const before = snap(st), s = r.int(n), c = r.pick(st.hands[r.int(n)].concat([{ id: -5 }]));
        const res = r.pick([() => UL.play(st, s, c.id), () => UL.chooseColor(st, s, r.pick([-1, 4, 9, 2])), () => UL.draw(st, s), () => UL.pass(st, s)])();
        if (!res.ok) { refused++; assert.strictEqual(snap(st), before, tag + ' refused call changed state: ' + res.why); }
        else { // a legal call went through by chance: it is just a move; re-check and carry on
          conserved(st, tag); continue;
        }
      }
      const s = st.turn;
      assert.strictEqual(st.pend < 0 || st.pend === s, true, tag + ' pending colour of another seat');
      const hadDeck = st.draw.length;
      let res;
      if (bots[s]) res = UL.doBot(st, s);
      else {
        const pl = UL.playable(st, s);
        if (st.pend === s) res = UL.chooseColor(st, s, r.int(4));
        else if (pl.length && r() < 0.8) res = UL.play(st, s, r.pick(pl).id);
        else if (st.drawn != null) res = UL.pass(st, s);
        else res = UL.draw(st, s);
      }
      assert.ok(res.ok, tag + ' the player on turn was refused: ' + res.why + ' seat ' + s);
      if (st.draw.length > hadDeck) reshuffles++;
      conserved(st, tag);
      const ev = st.last && st.last.ev;
      if (ev === 'win') { assert.strictEqual(st.winner, s, tag); assert.strictEqual(st.hands[s].length, 0, tag); break; }
      assert.ok(st.hands.every((h) => h.length > 0), tag + ' an empty hand but no winner');
      if (ev === 'took') draws++; if (ev === 'pass') passes++;
      plays++;
    }
    assert.ok(st.winner >= 0, tag); wins++;
    const o = UL.order(st); assert.deepStrictEqual(o.slice().sort(), [...Array(n).keys()], tag + ' order');
    assert.strictEqual(o[0], st.winner, tag);
    assert.strictEqual(st.hands.filter((h) => h.length === 0).length, 1, tag + ' more than one empty hand');
    assert.strictEqual(UL.play(st, st.turn, 0).ok, false, tag + ' play after the win');
  }
  assert.ok(plays > 0 && draws > 0 && passes > 0 && refused > 0 && wins === N, `plays ${plays} draws ${draws} passes ${passes} refused ${refused} wins ${wins}`);
});

test('colors fuzz: every card effect moves the turn / hands as documented', () => {
  const N = games(1500);
  const seen = {};
  for (let g = 0; g < N; g++) {
    const r = rng(170000 + g), tag = 'seed ' + (170000 + g), n = 2 + (g % 3), st = UL.newGame(n, r);
    // a few random warm-up moves so the table is not always fresh
    for (let k = 0; k < r.int(25) && st.winner < 0; k++) { const a = UL.doBot(st, st.turn); void a; if (st.pend >= 0) UL.chooseColor(st, st.pend, r.int(4)); }
    if (st.winner >= 0 || st.pend >= 0) continue;
    const s = st.turn, pl = UL.playable(st, s).filter((c) => st.hands[s].length > 1);
    if (!pl.length) continue;
    const card = r.pick(pl), dir = st.dir, handsBefore = st.hands.map((h) => h.length), pile = st.draw.length + st.discard.length;
    const res = UL.play(st, s, card.id); assert.ok(res.ok, tag);
    let col = st.color;
    if (card.c === UL.WILD) { assert.ok(res.color && st.pend === s, tag + ' wild must wait for its colour'); assert.strictEqual(UL.chooseColor(st, s, 3).ok, true); col = 3; assert.strictEqual(st.color, 3, tag); }
    else assert.strictEqual(st.color, card.c, tag + ' colour after a plain card');
    seen[card.k] = (seen[card.k] || 0) + 1;
    const nxt = mod(s + dir, n);
    if (card.k === UL.SKIP || (card.k === UL.REV && n === 2)) { assert.strictEqual(st.turn, mod(s + 2 * dir, n), tag + ' skip'); assert.strictEqual(st.dir, dir, tag); }
    else if (card.k === UL.REV) { assert.strictEqual(st.dir, -dir, tag + ' reverse'); assert.strictEqual(st.turn, mod(s - dir, n), tag); }
    else if (card.k === UL.DRAW2 || card.k === UL.WILD4) {
      const k = card.k === UL.DRAW2 ? 2 : 4;
      assert.strictEqual(st.hands[nxt].length, handsBefore[nxt] + (pile >= k ? k : Math.min(k, pile)), tag + ' victim draws ' + k);
      assert.strictEqual(st.turn, mod(s + 2 * dir, n), tag + ' the victim loses the turn'); assert.strictEqual(st.dir, dir, tag);
    } else { assert.strictEqual(st.turn, nxt, tag); assert.strictEqual(st.dir, dir, tag); }
    st.hands.forEach((h, q) => { if (q !== s && !((card.k === UL.DRAW2 || card.k === UL.WILD4) && q === nxt)) assert.strictEqual(h.length, handsBefore[q], tag + ' a bystander changed hand'); });
    assert.strictEqual(st.hands[s].length, handsBefore[s] - 1, tag);
    conserved(st, tag);
  }
  [0, 1, 5, 9, UL.SKIP, UL.REV, UL.DRAW2, UL.WILDK, UL.WILD4].forEach((k) => assert.ok(seen[k] > 0, 'kind ' + k + ' never exercised'));
});
