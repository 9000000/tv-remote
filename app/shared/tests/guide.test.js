// The user guide (docs/guide.html) stays short enough to read on a phone: a wall of text was the user's complaint.
// A longer topic gets steps, a table (`.cmp`) or one-line tips (`ul.gli`), not more sentences.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');

const HTML = fs.readFileSync(path.join(__dirname, '../../../docs/guide.html'), 'utf8');
const BLOCK_MAX = 40;                                                // words in one paragraph, step, tip or cell, per language
const TOPIC_MAX = 250;                                               // English words of prose in one topic: tables, code and animations not counted

const words = (h) => h.replace(/<pre[\s\S]*?<\/pre>/g, '').replace(/<[^>]+>/g, ' ').split(/\s+/).filter(Boolean).length;
const topics = [...HTML.matchAll(/<article class="topic" id="([^"]+)"[^>]*>([\s\S]*?)<\/article>/g)].map(([, id, body]) => ({ id, body }));
const spans = (body, lang) => [...body.matchAll(new RegExp(`<span class="${lang}">([\\s\\S]*?)</span>(?=<span class="(?:vi|en)">|<pre|</div>|</p>|</li>|</td>|</th>|</h3>|</h4>|$)`, 'g'))].map((m) => m[1]);

test('the guide has topics', () => assert.ok(topics.length > 30, topics.length));

test(`no paragraph, step, tip or cell over ${BLOCK_MAX} words`, () => {
  const long = [];
  for (const t of topics) for (const lang of ['vi', 'en']) for (const s of spans(t.body, lang)) {
    const n = words(s);
    if (n > BLOCK_MAX) long.push(`${t.id} (${lang}, ${n}): ${s.replace(/<[^>]+>/g, '').slice(0, 60)}…`);
  }
  assert.deepStrictEqual(long, [], 'split these into steps, a table or tips');
});

test(`no topic over ${TOPIC_MAX} words of prose`, () => {
  const prose = (b) => b.replace(/<table[\s\S]*?<\/table>/g, '').replace(/<figure[\s\S]*?<\/figure>/g, '');
  const long = topics.map((t) => [t.id, spans(prose(t.body), 'en').reduce((a, s) => a + words(s), 0)]).filter(([, n]) => n > TOPIC_MAX);
  assert.deepStrictEqual(long, [], 'cut it, or move the rare cases into a table');
});

// docs/guide/ (the guide for AI agents) is written by dev/gen/guide_md.js from guide.html: run it after editing the page
test('the side list names every topic once', () => {
  const side = HTML.slice(HTML.indexOf('gs-group'), HTML.indexOf('<section class="ggroup"'));
  assert.deepStrictEqual([...side.matchAll(/data-t="([^"]+)"/g)].map((m) => m[1]).sort(), topics.map((t) => t.id).sort());
});

test('docs/guide/*.md match guide.html', () => {
  const { build } = require('../../../dev/gen/guide_md.js');
  const dir = path.join(__dirname, '../../../docs/guide');
  const want = build();
  const have = fs.readdirSync(dir).flatMap((l) => fs.readdirSync(path.join(dir, l)).map((f) => l + '/' + f));
  assert.deepStrictEqual(have.sort(), Object.keys(want).sort(), 'run: node dev/gen/guide_md.js');
  for (const [f, s] of Object.entries(want)) assert.strictEqual(fs.readFileSync(path.join(dir, f), 'utf8'), s, f + ' is stale: run node dev/gen/guide_md.js');
});
