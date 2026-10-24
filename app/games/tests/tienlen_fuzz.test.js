// Tiến lên fuzz (docs/games/web/tienlen/rules.js). Three layers, all seeded:
//  1. every subset of random hands is classified by an independent oracle (rank counts) and must match combo();
//  2. legal() must hold exactly the (type, length, value) plays the oracle finds that beat the table, for random tables;
//  3. whole games with 2-4 players (random legal players and the three bot personas) following the same flow as
//     tienlen.js: 52 cards conserved, someone can always lead, nobody is stuck, the game ends.
const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const TL = require(path.join(__dirname, '../../../docs/games/web/tienlen/rules.js'));
const { rng, games } = require('./_fuzz.js');

const rk = (c) => Math.floor(c / 10);
/** Independent classification: from the multiset of ranks only. null, or {t, n, top} (top = the biggest card). */
function oracle(cards) {
  const n = cards.length, s = cards.slice().sort((a, b) => a - b), top = s[n - 1];
  const cnt = {}; s.forEach((c) => { cnt[rk(c)] = (cnt[rk(c)] || 0) + 1; });
  const rs = Object.keys(cnt).map(Number).sort((a, b) => a - b), vals = rs.map((r) => cnt[r]);
  const run = rs.every((r, i) => !i || r === rs[i - 1] + 1) && !rs.includes(15);
  if (n === 1) return { t: 'single', n, top };
  if (rs.length === 1) return n <= 4 ? { t: ['', '', 'pair', 'triple', 'quad'][n], n, top } : null;
  if (run && vals.every((v) => v === 1) && n >= 3) return { t: 'straight', n, top };
  if (run && vals.every((v) => v === 2) && (n === 6 || n === 8)) return { t: n === 6 ? 'pairs3' : 'pairs4', n, top };
  return null;
}
/** Documented table (rules.js beats() header and the VI help): may play [c] on [l]? Both oracle shapes. */
function oBeats(c, l) {
  if (!l) return true;
  const two = rk(l.top) === 15;
  if (l.t === 'single' && two && ['quad', 'pairs3', 'pairs4'].includes(c.t)) return true;
  if (l.t === 'pair' && two && ['quad', 'pairs4'].includes(c.t)) return true;
  if (l.t === 'pairs3' && ['quad', 'pairs4'].includes(c.t)) return true;
  if (l.t === 'quad' && c.t === 'pairs4') return true;
  return c.t === l.t && c.n === l.n && c.top > l.top;
}
const sig = (o) => o.t + o.n + ':' + o.top;
const subsets = (hand) => { const out = []; for (let m = 1; m < (1 << hand.length); m++) out.push(hand.filter((_, i) => m >> i & 1)); return out; };
const tableFrom = (hand, r) => { const all = subsets(hand).filter(oracle); return all.length ? r.pick(all) : null; };

test('tienlen fuzz: combo() agrees with an independent classifier on every subset', () => {
  const N = games(60);
  for (let g = 0; g < N; g++) {
    const r = rng(5000 + g), d = TL.shuffle(TL.deck(), r), n = [6, 8, 10, 13][g % 4];
    // biased hands (a few ranks) so pairs, runs and quads are common, not only random junk
    const hand = (g % 2 ? d.slice(0, n) : d.filter((c) => rk(c) <= 8 || rk(c) === 15).slice(0, n)).sort((a, b) => a - b);
    for (const sub of subsets(hand)) {
      const o = oracle(sub), c = TL.combo(r.shuffle(sub.slice()));
      if (!o) assert.strictEqual(c, null, 'seed ' + (5000 + g) + ' ' + sub.map(TL.id));
      else { assert.ok(c, 'seed ' + (5000 + g) + ' ' + sub.map(TL.id)); assert.strictEqual(c.type, o.t); assert.strictEqual(c.n, o.n); }
    }
  }
});

test('tienlen fuzz: legal() is exactly what the table allows (no missing play, no extra one)', () => {
  const N = games(150);
  for (let g = 0; g < N; g++) {
    const r = rng(9000 + g), tag = 'seed ' + (9000 + g);
    const d = TL.shuffle(TL.deck(), r), hand = d.slice(0, 13).sort((a, b) => a - b);
    // the table: a combo some other hand could hold, from the rest of the deck (often a 2, a quad or a run to hit the chops)
    const rest = d.slice(13), pool = g % 3 === 0 ? rest.filter((c) => rk(c) >= 13) : rest.slice(0, 13);
    const tc = g % 7 === 0 ? null : tableFrom(pool.slice(0, 12), r), last = tc && oracle(tc), lastC = tc && TL.combo(tc);
    const want = new Set();
    subsets(hand).forEach((s) => { const o = oracle(s); if (o && oBeats(o, last)) want.add(sig(o)); });
    const got = new Set(); const lg = TL.legal(hand, lastC);
    lg.forEach((p) => {
      assert.ok(p.cards.every((c) => hand.includes(c)) && new Set(p.cards).size === p.cards.length, tag);
      const o = oracle(p.cards); assert.ok(o, tag + ' not a combo');
      assert.ok(oBeats(o, last), tag + ' offered ' + p.cards.map(TL.id) + ' on ' + (last && sig(last)));
      got.add(sig(o));
    });
    assert.deepStrictEqual([...got].sort(), [...want].sort(), tag + ' table ' + (last && sig(last)) + ' hand ' + hand.map(TL.id));
    // hint: empty only when nothing is legal; otherwise a legal play, holding the picked card when one is given
    const picked = g % 2 ? [r.pick(hand)] : [];
    const h = TL.hint(hand, picked, lastC);
    if (!lg.length) assert.deepStrictEqual(h, [], tag);
    else if (!picked.length) assert.ok(h.length, tag + ' hint empty with legal plays');
    if (h.length) {
      assert.ok(oBeats(oracle(h), last), tag + ' hint illegal'); assert.ok(h.every((c) => hand.includes(c)), tag);
      if (picked.length) assert.ok(h.includes(picked[0]), tag);
    }
  }
});

test('tienlen fuzz: whole games, 2-4 players, 52 cards conserved, nobody stuck', () => {
  const N = games(600);
  let tricks = 0, chops = 0, finished = 0;
  for (let g = 0; g < N; g++) {
    const r = rng(20000 + g), tag = 'seed ' + (20000 + g), n = 2 + (g % 3);
    const hands = TL.deal(n, r), undealt = TL.deck().filter((c) => !hands.some((h) => h.includes(c)));
    const kind = Array.from({ length: n }, () => r.pick(['random', 'balanced', 'aggressive', 'cautious']));
    const played = [], out = [];
    let last = null, by = -1, passed = new Set(), turn = r.int(n), steps = 0;
    const active = (s) => !out.includes(s);
    const near = (s) => hands.some((h, o) => o !== s && active(o) && h.length > 0 && h.length <= 3);
    const conserved = () => assert.deepStrictEqual(hands.flat().concat(played, undealt).sort((a, b) => a - b), TL.deck(), tag + ' cards lost');
    conserved();
    while (out.length < n - 1) {
      if (++steps > 5000) assert.fail(tag + ' did not end');
      const hand = hands[turn], lc = last && last.c;
      let cards = null;
      if (kind[turn] === 'random') {
        const lg = TL.legal(hand, lc);
        // a random player passes at times even when it could play, never when leading
        cards = lg.length && (!lc || r() < 0.8) ? r.pick(lg).cards : null;
        if (!lc) assert.ok(cards && cards.length, tag + ' nothing to lead with');
      } else {
        cards = TL.bot(hand, { last: lc, near: near(turn) }, kind[turn]);
        if (!lc) assert.ok(cards && cards.length, tag + ' bot passed a lead');
      }
      if (cards && cards.length) {
        assert.ok(cards.every((c) => hand.includes(c)), tag + ' card not in hand');
        const c = TL.combo(cards); assert.ok(c, tag + ' not a combo ' + cards.map(TL.id));
        assert.ok(TL.beats(c, lc), tag + ' bot played what does not beat');
        if (TL.chop(c, lc)) chops++;
        hands[turn] = hand.filter((x) => !cards.includes(x)); played.push(...cards);
        last = { c, by: turn }; by = turn; if (!lc) tricks++;
        if (!hands[turn].length) out.push(turn);
        conserved();
      } else { assert.ok(lc, tag); passed.add(turn); }
      if (out.length >= n - 1) break;
      // the same advance() as tienlen.js
      const left = [...Array(n).keys()].filter((x) => x !== by && active(x) && !passed.has(x));
      if (!left.length) {
        let lead = by; for (let k = 1; !active(lead); k++) lead = (by + k) % n;
        last = null; passed = new Set(); turn = lead;
      } else { let x = turn; do x = (x + 1) % n; while (!left.includes(x)); turn = x; }
      assert.ok(active(turn), tag + ' turn went to a player who is out');
    }
    finished++;
  }
  assert.strictEqual(finished, N); assert.ok(tricks > 0 && chops > 0, 'tricks ' + tricks + ' chops ' + chops);
});
