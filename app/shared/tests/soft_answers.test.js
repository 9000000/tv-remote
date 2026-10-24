// node --test app/shared/tests/   Some routes answer 200 with another shape when TV Setup cannot answer: /api/rules gives
// {setup: "missing" | "old" | "busy"} (RulesClient.call) or {error} (RulesReceiver), /api/welcome {setup} (Welcome.call).
// call() turns only HTTP errors into null, so a script that reads `r.bedtime.on` from such an answer throws (a user's report,
// 2026-10-24: saveBeds while TV Setup restarted). This finds every such read: the answer of a SOFT route, or of a function
// that returns one (loadRules, saveBeds…, followed through any number of wrappers), read two levels deep (`r.a.b`) with
// nothing before it that checks `r.a`, `r.setup` or `r.error`.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const SOFT = ['/api/rules', '/api/welcome', '/api/telegram'];         // /api/telegram is answered by TV Setup through the rules receiver
const dir = path.join(__dirname, '../web/js');

/** The text from [open] (an opening brace) to its closing brace; strings and comments are rare enough here to ignore. */
function block(s, open) {
  let depth = 0;
  for (let i = open; i < s.length; i++) {
    if (s[i] === '{') depth++;
    else if (s[i] === '}' && --depth === 0) return s.slice(open, i + 1);
  }
  return s.slice(open);
}

/** Every named function of the page: `function f(`, `async function f(`, `const f = (…) =>` / `async (…) => {`. */
function functions(files) {
  const out = [];
  for (const [f, s] of files) {
    for (const m of s.matchAll(/(?:async\s+)?function\s+(\w+)\s*\([^)]*\)\s*\{|(?:const|let)\s+(\w+)\s*=\s*(?:async\s*)?\([^)]*\)\s*=>\s*\{/g)) {
      const open = m.index + m[0].length - 1;
      out.push({ name: m[1] || m[2], file: f, at: open, body: block(s, open) });
    }
  }
  return out;
}

const softCall = (names) => new RegExp(`(?:api\\(\\s*'(?:${SOFT.map((r) => r.replace(/\//g, '\\/')).join('|')})[^']*'|\\b(${[...names.keys()].join('|') || '$^'})\\()`);

/**
 * The functions whose result is a soft answer: one that returns what api(SOFT) or another such function gave. Each maps to
 * the fields it checked itself before returning (`if (!r || !r.bedtime) return null`): reading those from its result is safe.
 */
function softFunctions(fns) {
  const soft = new Map();
  for (let changed = true; changed;) {
    changed = false;
    const call = softCall(soft);
    for (const fn of fns) {
      if (soft.has(fn.name)) continue;
      const direct = new RegExp(`return\\s+(?:await\\s+)?${call.source}`).test(fn.body);
      const v = [...fn.body.matchAll(new RegExp(`(?:const|let)\\s+(\\w+)\\s*=\\s*await\\s+${call.source}`, 'g'))]
        .map((m) => m[1]).find((x) => new RegExp(`return\\s+${x}\\s*[;}\\n]`).test(fn.body));
      if (!direct && !v) continue;
      const checked = v ? [...fn.body.matchAll(new RegExp(`!\\s*${v}\\.(\\w+)\\s*\\)\\s*return\\b`, 'g'))].map((m) => m[1]) : [];
      soft.set(fn.name, new Set(checked)); changed = true;
    }
  }
  return soft;
}

function unguardedReads(files, soft) {
  const call = softCall(soft), bad = [];
  for (const [f, s] of files) {
    for (const m of s.matchAll(new RegExp(`(?:const|let)\\s+(\\w+)\\s*=\\s*await\\s+${call.source}`, 'g'))) {
      const v = m[1], start = m.index, safe = (m[2] && soft.get(m[2])) || new Set();
      let depth = 0, end = s.length;                                  // the rest of the block the answer was given in
      for (let i = start; i < s.length; i++) { if (s[i] === '{') depth++; else if (s[i] === '}' && depth-- === 0) { end = i; break; } }
      const rest = s.slice(start, end);
      for (const d of rest.matchAll(new RegExp(`\\b${v}\\.(\\w+)\\.\\w+`, 'g'))) {
        const k = d[1], before = rest.slice(0, d.index);
        if (safe.has(k)) continue;
        const guarded = new RegExp(`\\b${v}\\.(?:setup|error)\\b|!\\s*${v}\\.${k}\\b|\\b${v}\\.${k}\\s*(?:&&|\\?(?!\\.)|\\|\\|)|\\b${v}\\.${k}\\?\\.`).test(before + rest.slice(d.index, d.index + d[0].length + 4).replace(d[0], ''))
          || new RegExp(`\\b${v}\\.${k}\\s*&&[^;]*$`).test(before.split('\n').pop());
        if (!guarded) bad.push(`${f}:${s.slice(0, start + d.index).split('\n').length} ${d[0]}`);
      }
    }
  }
  return bad;
}

const read = (d) => fs.readdirSync(d).filter((f) => f.endsWith('.js')).map((f) => [f, fs.readFileSync(path.join(d, f), 'utf8')]);

test('a soft answer (setup / error instead of the data) is never read two levels deep unchecked', () => {
  const files = read(dir);
  const soft = softFunctions(functions(files));
  assert.ok(soft.has('loadRules'), 'found the wrappers of /api/rules');
  assert.deepEqual(unguardedReads(files, soft), [], 'check the field first (`if (!r || !r.x) return`), or r.setup / r.error');
});

test('the check finds the bug it was written for', () => {
  const bug = `async function loadRules(c) { const r = await api('/api/rules', c); if (!r) return null; return r; }
async function saveBeds(l) { const r = await loadRules({}); if (r && !r.bedtime.on) toast(1); return r; }
async function onSwitch() { const r = await saveBeds([]); if (r && r.bedtime.on) toast(2); }`;
  const fixed = `async function loadRules(c) { const r = await api('/api/rules', c); if (!r) return null; return r; }
async function saveBeds(l) { const r = await loadRules({}); if (!r || !r.bedtime) return null; if (!r.bedtime.on) toast(1); return r; }`;
  const files = [['bug.js', bug]], soft = softFunctions(functions(files));
  assert.deepEqual([...soft.keys()].sort(), ['loadRules', 'saveBeds']);
  assert.deepEqual(unguardedReads(files, soft), ['bug.js:2 r.bedtime.on', 'bug.js:3 r.bedtime.on']);
  const ok = [['ok.js', fixed]];
  assert.deepEqual(unguardedReads(ok, softFunctions(functions(ok))), []);
});
