// dev/DECISIONS.md is the list; each decision lives in dev/decisions/<part>.md under one `## ` heading. The list and the
// pointers in code (`dev/decisions/<part>.md "<heading start>"`) must name headings that exist, or a reader lands nowhere.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const ROOT = path.join(__dirname, '../../..');
const DIR = path.join(ROOT, 'dev/decisions');
const heads = {};
for (const f of fs.readdirSync(DIR).filter((f) => f.endsWith('.md')))
  heads[f] = fs.readFileSync(path.join(DIR, f), 'utf8').split('\n').filter((l) => l.startsWith('## ')).map((l) => l.slice(3).trim());

test('the list in dev/DECISIONS.md names every decision, under its file', () => {
  const list = fs.readFileSync(path.join(ROOT, 'dev/DECISIONS.md'), 'utf8');
  const listed = {};
  let file = null;
  for (const l of list.split('\n')) {
    const m = l.match(/^## \[.*\]\(decisions\/(.+\.md)\)/);
    if (m) { file = m[1]; listed[file] = []; continue; }
    if (file && l.startsWith('- ')) listed[file].push(l.slice(2).trim());
  }
  assert.deepStrictEqual(listed, heads, 'run the list again from the headings in dev/decisions/');
});

test('code that points at a decision names one that exists', () => {
  const out = execFileSync('git', ['grep', '-n', '-o', '-E', 'decisions/[a-z-]+\\.md "[^"]+"', '--', ':!dev/DECISIONS.md'], { cwd: ROOT, encoding: 'utf8' });
  const bad = [];
  for (const line of out.trim().split('\n').filter(Boolean)) {
    const m = line.match(/decisions\/([a-z-]+\.md) "([^"]+)"/);
    if (!(heads[m[1]] || []).some((h) => h.startsWith(m[2]))) bad.push(line);
  }
  assert.deepStrictEqual(bad, []);
});
