'use strict';
// Shared by the *_fuzz.test.js files: a seeded PRNG (a failing game replays from its seed) and the game count.
// FUZZ=50000 node --test app/tvgames/tests/ runs the heavy mode; the normal suite uses each file's small default.
function rng(seed) {                                   // mulberry32
  let a = seed >>> 0;
  const r = () => {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  r.int = (n) => Math.floor(r() * n);
  r.pick = (xs) => xs[Math.floor(r() * xs.length)];
  r.shuffle = (xs) => { for (let i = xs.length - 1; i > 0; i--) { const j = Math.floor(r() * (i + 1)); const t = xs[i]; xs[i] = xs[j]; xs[j] = t; } return xs; };
  return r;
}
/** Number of games: FUZZ from the environment, else the file's default. */
const games = (dflt) => (process.env.FUZZ ? Math.max(1, parseInt(process.env.FUZZ, 10) || dflt) : dflt);
module.exports = { rng, games };
