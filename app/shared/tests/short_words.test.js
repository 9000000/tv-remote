// node --test app/shared/tests/   A line of the page is short (the owner, again on 2026-10-25: "description lại dài"): every vi.json
// entry is at most MAX words. The long ones that were already there are listed in long_words.json; that list only shrinks:
// a new long entry fails, and so does a listed one that got short (take it off the list).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const MAX = 25;
const vi = JSON.parse(fs.readFileSync(path.join(__dirname, '../i18n/vi.json'), 'utf8'));
const kept = new Set(JSON.parse(fs.readFileSync(path.join(__dirname, 'long_words.json'), 'utf8')));
const words = (s) => (typeof s === 'string' ? s : Object.values(s).join(' ')).replace(/<[^>]+>|\{\w+\}/g, ' ').split(/\s+/).filter(Boolean).length;

test(`every line of the page is at most ${MAX} words (long_words.json only shrinks)`, () => {
  const bad = [];
  for (const [id, s] of Object.entries(vi)) {
    const n = typeof s === 'object' ? Math.max(...Object.values(s).map(words)) : words(s);
    if (n > MAX && !kept.has(id)) bad.push(`${id}: ${n} words, keep it under ${MAX} (details go in a dialog or the guide, see DESIGN.md "Words")`);
    if (n <= MAX && kept.has(id)) bad.push(`${id} is short now: take it off long_words.json`);
  }
  for (const id of kept) if (!(id in vi)) bad.push(`${id} is gone: take it off long_words.json`);
  assert.deepEqual(bad, []);
});
