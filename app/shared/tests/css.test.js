// node --test app/shared/tests/   css/*.css stay free of rules nobody sees: a property set twice for the same selector (the
// first one never shows, and the next person edits the wrong one), a class nothing uses, a running line's selector that
// could take its own span again (it wrapped itself 20 times a second, the page jerked in WebKit).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const web = path.join(__dirname, '../web');
const styles = require('./styles.js');

// comments blanked line for line, so the line numbers stay those of the file
const blank = (css) => css.replace(/\/\*[\s\S]*?\*\//g, (c) => c.replace(/[^\n]/g, ' '));
const split = (h) => { const out = []; let d = 0, cur = '';
  for (const ch of h) { if (ch === '(' || ch === '[') d++; if (ch === ')' || ch === ']') d--; if (ch === ',' && !d) { out.push(cur.trim()); cur = ''; } else cur += ch; }
  return out.concat(cur.trim()).filter(Boolean); };
/** every rule: its @media / @supports context, its selectors, its declarations, its line */
function rules(text, ctx = '', base = 0, out = []) {
  let i = 0;
  while (i < text.length) {
    const j = text.indexOf('{', i); if (j < 0) break;
    let d = 1, k = j + 1; while (d && k < text.length) { if (text[k] === '{') d++; else if (text[k] === '}') d--; k++; }
    const head = text.slice(i, j).trim().replace(/\s+/g, ' '), body = text.slice(j + 1, k - 1), line = base + text.slice(0, j).split('\n').length;
    if (/^@(media|supports|container|layer)/.test(head)) rules(body, ctx + ' ' + head, line - 1, out);
    else if (!head.startsWith('@')) {
      const decl = []; for (const s of body.split(';')) { const m = s.match(/^\s*([\w-]+)\s*:([\s\S]*)$/); if (m) decl.push([m[1], m[2].trim()]); }
      out.push({ ctx, sels: split(head), decl, line });
    }
    i = k;
  }
  return out;
}
const all = styles.texts.flatMap((t, i) => rules(blank(t)).map((r) => ({ ...r, line: styles.files[i] + ':' + r.line })));

test('index.html links every css file once', () => {
  const there = fs.readdirSync(path.join(web, 'css')).filter((f) => f.endsWith('.css')).map((f) => 'css/' + f).sort();
  assert.deepEqual([...styles.files].sort(), there, 'a css file the page does not link (or links twice) is dead or seen twice');
  assert.equal(styles.files.at(-1), 'css/kit.css', 'the UI kit comes last: its shapes win over a tab\'s');
});

test('a selector does not set the same property twice in one place', () => {
  // A grouped rule giving many selectors a base that one of them then changes is the cascade at work; the same lone selector
  // setting a property, and again further down (same @media), is a value nobody sees.
  const bad = [], seen = new Map();
  for (const r of all) for (const [p, v] of r.decl) {
    if (r.decl.filter(([q, w]) => q === p && w === v).length > 1) bad.push(`${r.line} ${r.sels.join(', ')} { ${p}: ${v} } twice in one rule`);   // two values: a fallback, then the newer form
    if (r.sels.length !== 1) continue;
    const k = r.ctx + '|' + r.sels[0] + '|' + p, was = seen.get(k);
    if (was && was.r !== r && was.v !== v) bad.push(`${was.line} and ${r.line}: ${r.sels[0]} { ${p}: ${was.v} } then { ${p}: ${v} }${r.ctx ? ' in' + r.ctx : ''}`);
    seen.set(k, { v, line: r.line, r });
  }
  assert.deepEqual([...new Set(bad)], [], 'one rule per selector and property: change the first one, drop the second');
});

test('every class in css/*.css is used', () => {
  const src = [path.join(web, 'index.html'), ...fs.readdirSync(web).filter((f) => /\.(js|json)$/.test(f)).map((f) => path.join(web, f)),
    ...fs.readdirSync(path.join(web, 'js')).map((f) => path.join(web, 'js', f))].map((f) => fs.readFileSync(f, 'utf8')).join('\n');
  const made = { lD: 'apps.js: "l" + logLevel(line)', lE: '', lF: '', lV: '', lW: '' };   // names put together at run time
  const classes = new Map();
  for (const r of all) for (const s of r.sels) for (const m of s.matchAll(/\.([A-Za-z_][\w-]*)/g)) if (!classes.has(m[1])) classes.set(m[1], r.line);
  const unused = [...classes].filter(([c]) => !(c in made) && !new RegExp(`(^|[^\\w-])${c.replace(/-/g, '\\-')}(?![\\w-])`).test(src)).map(([c, l]) => `.${c} (${l})`);
  assert.deepEqual(unused, [], 'no page script, index.html or remote.json names it: remove the rule');
});

test('a running line never takes its own span', () => {
  const ui = fs.readFileSync(path.join(web, 'js', 'ui.js'), 'utf8');
  const list = ui.match(/const RUN = \[([^\]]*)\]/);
  assert.ok(list, 'RUN in js/ui.js is a list of selectors');
  assert.match(ui, /const RUN = \[[^\]]*\]\.map\(\(r\) => r \+ ':not\(\.rin\)'\)/, "each RUN selector ends in :not(.rin): '.x span' took the span runFit had just made");
  assert.match(ui, /function runFit\(t\) \{\s*if \(t\.classList\.contains\('rin'\)\) return;/, 'runFit refuses a .rin');
});
