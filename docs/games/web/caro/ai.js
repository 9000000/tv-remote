'use strict';
// Gomoku rules and the bot, with no DOM so node can test them (app/shared/tests/caro.test.js). Free style: 5 or more in a row wins.
// The board is a flat array of N*N cells, 0 empty, 1 X, 2 O; a cell is y * N + x.
const Caro = (() => {
  const N = 15, DIRS = [[1, 0], [0, 1], [1, 1], [1, -1]];
  const create = () => new Array(N * N).fill(0);

  // The cells of the line through i along (dx, dy), up to 4 each side (a five can reach no further); `at` is where i sits in it.
  function lineOf(i, dx, dy) {
    const x = i % N, y = (i / N) | 0, c = []; let at = 0;
    for (let k = -4; k <= 4; k++) {
      const nx = x + dx * k, ny = y + dy * k;
      if (nx < 0 || ny < 0 || nx >= N || ny >= N) continue;
      if (k === 0) at = c.length;
      c.push(ny * N + nx);
    }
    return { c, at };
  }
  // The run of p's stones through position j of the line: [from, to]
  function runAt(b, c, j, p) {
    let lo = j, hi = j;
    while (lo > 0 && b[c[lo - 1]] === p) lo--;
    while (hi < c.length - 1 && b[c[hi + 1]] === p) hi++;
    return [lo, hi];
  }
  // The five (or longer) run through i, as cell numbers, longest of the four directions; null if none
  function winLine(b, i) {
    const p = b[i]; if (!p) return null;
    const x = i % N, y = (i / N) | 0; let best = null;
    const same = (nx, ny) => nx >= 0 && ny >= 0 && nx < N && ny < N && b[ny * N + nx] === p;
    for (const [dx, dy] of DIRS) {
      let back = 0, fwd = 0;
      while (same(x - dx * (back + 1), y - dy * (back + 1))) back++;
      while (same(x + dx * (fwd + 1), y + dy * (fwd + 1))) fwd++;
      const n = back + fwd + 1;
      if (n >= 5 && (!best || n > best.length)) {
        best = [];
        for (let k = -back; k <= fwd; k++) best.push((y + dy * k) * N + x + dx * k);
      }
    }
    return best;
  }
  const isFull = (b) => b.every((v) => v);

  // Empty cells in the line that would make a five through position `at` for p
  function completions(b, c, at, p) {
    let n = 0;
    for (let j = 0; j < c.length; j++) {
      if (b[c[j]]) continue;
      b[c[j]] = p;
      const [lo, hi] = runAt(b, c, j, p);
      if (hi - lo + 1 >= 5 && lo <= at && at <= hi) n++;
      b[c[j]] = 0;
    }
    return n;
  }
  // What p gets from stone at i along one direction (the stone is put on the board for the count)
  function lineScore(b, i, p, dx, dy) {
    const { c, at } = lineOf(i, dx, dy);
    const [lo, hi] = runAt(b, c, at, p);
    if (hi - lo + 1 >= 5) return 100000;
    const comp = completions(b, c, at, p);
    if (comp >= 2) return 10000;          // open four
    if (comp === 1) return 1200;          // four
    for (let j = 0; j < c.length; j++) {  // open three: one more stone would make an open four
      if (b[c[j]]) continue;
      b[c[j]] = p;
      const open = completions(b, c, at, p) >= 2;
      b[c[j]] = 0;
      if (open) return 1000;
    }
    let most = 0;                         // a closed three / two: the best five-window with no enemy stone
    for (let s = 0; s + 5 <= c.length; s++) {
      if (s > at || s + 4 < at) continue;
      let n = 0, bad = false;
      for (let k = s; k < s + 5; k++) { if (b[c[k]] === p) n++; else if (b[c[k]]) bad = true; }
      if (!bad && n > most) most = n;
    }
    return most >= 3 ? 60 : most === 2 ? 10 : 1;
  }
  // How good is p putting a stone on i: the sum of the four directions, plus a bonus when two of them are threats (a double threat)
  function gain(b, i, p) {
    b[i] = p;
    let sum = 0, threats = 0;
    for (const [dx, dy] of DIRS) { const s = lineScore(b, i, p, dx, dy); sum += s; if (s >= 1000) threats++; }
    b[i] = 0;
    return sum + (threats >= 2 ? 8000 : 0);
  }
  // Empty cells within 2 of a stone (the middle on an empty board)
  function candidates(b) {
    const seen = new Set(), out = [];
    for (let i = 0; i < b.length; i++) {
      if (!b[i]) continue;
      const x = i % N, y = (i / N) | 0;
      for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) {
        const nx = x + dx, ny = y + dy, j = ny * N + nx;
        if (nx < 0 || ny < 0 || nx >= N || ny >= N || b[j] || seen.has(j)) continue;
        seen.add(j); out.push(j);
      }
    }
    return out.length ? out : [(N >> 1) * N + (N >> 1)];
  }
  // The bot's move for p: win > block a win > open four > block an open four / three > double threat > the best sum. Mine counts a bit more than the enemy's,
  // so a win beats a block; ties are broken at random so games differ. -1 when the board is full.
  function bestMove(b, p, rnd = Math.random) {
    const q = 3 - p; let best = -1, top = -1;
    for (const i of candidates(b)) {
      const mid = 1 - (Math.abs((i % N) - 7) + Math.abs(((i / N) | 0) - 7)) / 100;   // a little pull to the middle
      const s = gain(b, i, p) + gain(b, i, q) * 0.95 + mid + rnd() * 0.5;
      if (s > top) { top = s; best = i; }
    }
    return best;
  }
  return { N, DIRS, create, winLine, isFull, bestMove, candidates };
})();
if (typeof module !== 'undefined') module.exports = Caro;
