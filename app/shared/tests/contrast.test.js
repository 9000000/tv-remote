// WCAG contrast of the colour tokens in css/base.css, both themes (the runtime check on real rendering is dev/contrast.py).
const test = require('node:test'), assert = require('node:assert');
const fs = require('fs'), path = require('path');
const { css } = require('./styles.js');

const block = (sel) => { const i = css.indexOf(sel + ' {'); assert.ok(i >= 0, sel); return css.slice(i, css.indexOf('}', i)); };
const tokens = (b) => Object.fromEntries([...b.matchAll(/--([\w-]+):\s*(#[0-9a-fA-F]{3,6})\b/g)].map((m) => [m[1], m[2]]));
const light = tokens(block(':root')), dark = { ...light, ...tokens(block(':root[data-theme=dark]')) };
const lin = (c) => (c /= 255) <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
const lum = (h) => { h = h.slice(1); if (h.length === 3) h = [...h].map((c) => c + c).join(''); const [r, g, b] = [0, 2, 4].map((i) => lin(parseInt(h.substr(i, 2), 16))); return 0.2126 * r + 0.7152 * g + 0.0722 * b; };
const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };

const PAIRS = [['ink', 'bg'], ['ink', 'card'], ['ink', 'btn'], ['muted', 'bg'], ['muted', 'card'], ['accent-ink', 'accent'],
  ['accent-text', 'bg'], ['accent-text', 'card'], ['boot', 'card'],
  ...['ok', 'warn', 'bad'].flatMap((k) => [[k, 'card'], [k, k + '-bg']])];
for (const [name, t] of [['light', light], ['dark', dark]])
  for (const [fg, bg] of PAIRS)
    test(`${name}: --${fg} on --${bg} >= 4.5`, () => {
      const r = ratio(t[fg], t[bg]);
      assert.ok(r >= 4.5, `${t[fg]} on ${t[bg]} is ${r.toFixed(2)}:1`);
    });

// Heuristic: a rule that fills with var(--accent) must set its text to var(--accent-ink) or #fff in the same rule
// (otherwise it inherits --ink, dark on blue). Only plain `background:var(--accent)` declarations are looked at; :hover
// rules (the colour comes from the base rule) and the progress bar's fill (`.bar i`, no text) are skipped.
test('rules that fill with var(--accent) use --accent-ink or #fff for text', () => {
  const bad = [];
  for (const m of css.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    const decl = m[2];
    if (/:hover|\.bar i/.test(m[1])) continue;
    if (!/(^|[;\s])background(-color)?:\s*var\(--accent\)/.test(decl)) continue;
    if (!/(^|[;\s])color:\s*(var\(--accent-ink\b|#fff\b|#ffffff\b)/.test(decl)) bad.push(m[1].trim().replace(/\s+/g, ' ').slice(-80));
  }
  assert.deepEqual(bad, [], 'accent fill without light text');
});
