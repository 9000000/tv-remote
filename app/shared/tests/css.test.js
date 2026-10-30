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

test('on / chosen is a tint, never the full accent fill', () => {
  // the full blue is a button that does something (.primary); a tab, a choice or a switch that is on draws var(--sel) through
  // the one rule in css/kit.css. Both filled, a screen had a dozen blue buttons and nobody could tell which ones act.
  const bad = [];
  for (const r of all) for (const s of r.sels) if (/\.on\b|aria-selected=true|:checked/.test(s) && !/\.primary/.test(s))
    for (const [p, v] of r.decl) if (/^background/.test(p) && /var\(--accent\)/.test(v)) bad.push(`${r.line} ${s} { ${p}: ${v} }`);
  assert.deepEqual(bad, [], 'draw it with the selected rule in css/kit.css (var(--sel)), not var(--accent)');
});

test('every var(--x) is a token, and none carries a colour fallback', () => {
  // DESIGN.md section 7: a name nobody defines draws nothing (the picker's chosen card used var(--text), so its marker was invisible)
  // and var(--x, #hex) hides that. Defined = declared in some css/*.css, or set by a script (grep setProperty in js/) or the phone app.
  const jsSet = ['--c', '--h', '--w', '--cols', '--gap', '--pip-h', '--cover-b'], ar = ['--ar'];   // --ar: set inline by the widget preview
  const css = styles.texts.map(blank).join('\n');
  const defined = new Set([...css.matchAll(/(--[\w-]+)\s*:/g)].map((m) => m[1]).concat(jsSet, ar));
  const bad = [];
  for (const m of css.matchAll(/var\((--[\w-]+)\s*(,\s*(#|rgb|hsl)[^)]*)?/g)) {
    const line = css.slice(0, m.index).split('\n').length;
    if (!defined.has(m[1])) bad.push(`line ${line}: ${m[1]} is not defined`);
    if (m[2]) bad.push(`line ${line}: ${m[0]}) has a colour fallback`);
  }
  assert.deepEqual(bad, [], 'use a token from css/base.css (or color-mix of one); a look it lacks is added there first');
});

test('the log warning colour is the --warn token, not a hex', () => {
  assert.ok(all.some((r) => r.sels.includes('.logpre .lW') && r.decl.some(([p, v]) => p === 'color' && v === 'var(--warn)')));
});

test('small controls keep a 44px hit area', () => {
  const has = (sel) => all.some((r) => r.sels.some((s) => s.endsWith(sel + '::after')) && r.decl.some(([p, v]) => p === 'inset' && /^-(8|12)px$/.test(v)));
  assert.ok(has('.wallx'), '.wallx (20px) needs ::after { inset:-12px }');
  assert.ok(has('.chstar'), '.chstar (28px) needs ::after { inset:-8px }');
  const mini = all.find((r) => r.sels.includes('.seg.mini button'));
  assert.equal(mini.decl.find(([p]) => p === 'min-height')[1], 'var(--ctl-sm)');
});

test('color-mix on a logo plate and a hover has a plain colour before it (WebView < Chrome 111, Android 5)', () => {
  // a declaration the old engine cannot parse is dropped; the line before it is what that engine shows
  const bad = [];
  for (const r of all) if (r.sels.some((x) => /chlogo$|^#picker \.item:hover$/.test(x)))
    r.decl.forEach(([p, v], i) => { if (/color-mix\(/.test(v) && !r.decl.slice(0, i).some(([q, w]) => q === p && !/color-mix\(/.test(w))) bad.push(r.line + ' ' + r.sels.join(', '));
    });
  assert.deepEqual(bad, []);
  assert.ok(all.some((r) => r.sels.some((x) => /chlogo$/.test(x)) && r.decl.some(([, v]) => /color-mix/.test(v))), 'the rule is still found');
});
