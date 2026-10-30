// Caro / gomoku fuzz (docs/games/web/caro/ai.js): free style, five OR MORE in a row wins (ai.js header, the page's rule line).
// Games are random-vs-random, random-vs-bot and bot-vs-bot, plus random boards for winLine. Seeded; FUZZ=n for more.
// Invariants: winLine(b, i) agrees with a brute-force scan of the whole board for every stone; the first move that makes a
// run of 5+ is the end of the game, and the returned run is exactly that run (all one colour, in a line, through i, maximal);
// the bot always answers with an empty cell (-1 only on a full board), takes a win it has, blocks the opponent's win when it
// has none of its own; a full board without a five is a draw.
const test = require('node:test');
const assert = require('node:assert');
const path = require('path');
const C = require(path.join(__dirname, '../../../docs/games/web/caro/ai.js'));
const { rng, games } = require('./_fuzz.js');
const N = C.N;

/** Brute force: is there a run of >= 5 of p anywhere (any direction)? Returns the longest run length through each cell via scanning. */
function longest(b, i) {
  const p = b[i]; if (!p) return 0;
  const x = i % N, y = (i / N) | 0; let best = 1;
  for (const [dx, dy] of [[1, 0], [0, 1], [1, 1], [1, -1]]) {
    let n = 1;
    for (let k = 1; ; k++) { const nx = x + dx * k, ny = y + dy * k; if (nx < 0 || ny < 0 || nx >= N || ny >= N || b[ny * N + nx] !== p) break; n++; }
    for (let k = 1; ; k++) { const nx = x - dx * k, ny = y - dy * k; if (nx < 0 || ny < 0 || nx >= N || ny >= N || b[ny * N + nx] !== p) break; n++; }
    if (n > best) best = n;
  }
  return best;
}
const anyFive = (b, p) => b.some((v, i) => v === p && longest(b, i) >= 5);
const winning = (b, i, p) => { b[i] = p; const w = longest(b, i) >= 5; b[i] = 0; return w; };

function checkLine(b, i, wl) {
  assert.ok(wl && wl.length >= 5 && wl.includes(i));
  assert.ok(wl.every((c) => b[c] === b[i]), 'run holds another colour');
  const dx = (wl[1] % N) - (wl[0] % N), dy = ((wl[1] / N) | 0) - ((wl[0] / N) | 0);
  wl.forEach((c, k) => { assert.strictEqual(c, wl[0] + k * (dy * N + dx), 'run is not a straight line'); });
  const ex = (wl[0] % N) - dx, ey = ((wl[0] / N) | 0) - dy, fx = (wl[wl.length - 1] % N) + dx, fy = ((wl[wl.length - 1] / N) | 0) + dy;
  const same = (x, y) => x >= 0 && y >= 0 && x < N && y < N && b[y * N + x] === b[i];
  assert.ok(!same(ex, ey) && !same(fx, fy), 'run is not maximal');
}

test('caro fuzz: winLine agrees with a brute-force scan on random boards', () => {
  const B = games(2000);
  for (let g = 0; g < B; g++) {
    const r = rng(120000 + g), b = C.create(), dens = 0.15 + r() * 0.5;
    for (let i = 0; i < b.length; i++) if (r() < dens) b[i] = 1 + r.int(2);
    // sprinkle a run now and then so fives are not rare
    if (g % 3 === 0) { const [dx, dy] = r.pick(C.DIRS), len = 3 + r.int(5), p = 1 + r.int(2), x = r.int(N), y = r.int(N); for (let k = 0; k < len; k++) { const nx = x + dx * k, ny = y + dy * k; if (nx >= 0 && ny >= 0 && nx < N && ny < N) b[ny * N + nx] = p; } }
    for (let i = 0; i < b.length; i++) {
      const wl = C.winLine(b, i), n = longest(b, i);
      if (!b[i]) assert.strictEqual(wl, null, 'winLine on an empty cell');
      else if (n >= 5) { checkLine(b, i, wl); assert.ok(wl.length >= 5 && wl.length <= N); }
      else assert.strictEqual(wl, null, `seed ${120000 + g} cell ${i}: run of ${n} reported as a win`);
    }
  }
});

test('caro fuzz: whole games (random / bot mixes) end at the first five, a full board is a draw', () => {
  const G = games(30);
  let wins = 0, draws = 0, moves = 0, took = 0, blocked = 0;
  for (let g = 0; g < G; g++) {
    const r = rng(130000 + g), tag = 'seed ' + (130000 + g), b = C.create(), kind = g % 3;   // 0 random both, 1 random vs bot, 2 bot both
    let p = 1, done = false;
    for (let k = 0; k < N * N && !done; k++) {
      const empty = []; b.forEach((v, i) => { if (!v) empty.push(i); });
      const bot = kind === 2 || (kind === 1 && p === 2);
      let i;
      if (bot) {
        i = C.bestMove(b, p, r);
        assert.ok(Number.isInteger(i) && i >= 0 && i < b.length && b[i] === 0, tag + ' the bot picked a taken cell ' + i);
        const mine = empty.filter((c) => C.candidates(b).includes(c) && winning(b, c, p)), theirs = empty.filter((c) => C.candidates(b).includes(c) && winning(b, c, 3 - p));
        if (mine.length) { assert.ok(mine.includes(i), tag + ' the bot missed its win'); took++; }
        else if (theirs.length) { assert.ok(theirs.includes(i), tag + ' the bot did not block'); blocked++; }
      } else i = r.pick(empty);
      b[i] = p; moves++;
      const wl = C.winLine(b, i), five = longest(b, i) >= 5;
      assert.strictEqual(!!wl, five, tag + ' win detection');
      assert.strictEqual(anyFive(b, 1) || anyFive(b, 2), five, tag + ' a five appeared elsewhere / was missed');
      if (wl) { checkLine(b, i, wl); wins++; done = true; }
      else if (C.isFull(b)) { draws++; done = true; assert.ok(!anyFive(b, 1) && !anyFive(b, 2), tag); assert.strictEqual(C.bestMove(b, p, r), -1, tag + ' bot on a full board'); }
      p = 3 - p;
    }
    assert.ok(done, tag + ' did not end');
  }
  assert.ok(wins > 0 && moves > 0 && took > 0 && blocked > 0, `wins ${wins} draws ${draws} moves ${moves} took ${took} blocked ${blocked}`);
});

test('caro: the bot never returns an occupied cell, even with no empty cell within 2 of a stone', () => {
  const full = C.create().fill(1); assert.strictEqual(C.bestMove(full, 2), -1);          // was the middle cell, which is taken
  assert.strictEqual(C.bestMove(C.create(), 1), 112);                                    // empty board: the middle
  // a filled block with a lone empty cell far from every stone
  const b = C.create(); for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) b[y * N + x] = 1 + ((x + y) % 2);
  const far = 14 * N + 14; assert.ok(C.candidates(b).every((i) => b[i] === 0));
  for (let i = 0; i < b.length; i++) if (!b[i] && i !== far && i % N < 12) b[i] = 1 + (i % 2);
  const m = C.bestMove(b, 1); assert.ok(m >= 0 && b[m] === 0, 'got ' + m);
});

test('caro: a five of its own beats blocking one, even when the block scores more (seed 131031 move 104)', () => {
  // X can finish column 12 at (12, 11); O's stone at (10, 6) would also make O five and add open lines, which used to outscore the win
  const rows = [
    '...............',
    '....O..........',
    '.....X..X......',
    '...X..XOO......',
    '...O.OXXX.O....',
    '...XOOOOXXXO...',
    '...XOXOXXO.OOO.',
    '..OXXXOXOO..XX.',
    '.OXOOOXOX.XOXXO',
    '...XXOXOOOXXXXO',
    '..OXOOOXOX.OXO.',
    '.....OXOXOOX.X.',
    '....XXOOXXXXO.O',
    '.....XOXXOO.X..',
    '...O...........'];
  const b = C.create(); rows.forEach((row, y) => [...row].forEach((ch, x) => { b[y * N + x] = ch === 'X' ? 1 : ch === 'O' ? 2 : 0; }));
  assert.ok(winning(b, 11 * N + 12, 1));
  assert.strictEqual(C.bestMove(b, 1, () => 0.5), 11 * N + 12);
});
