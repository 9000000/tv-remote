// Every script the project ships parses: the .js files and the inline <script>s of the page, the TV apps' assets and the site.
// A line comment once swallowed the rest of its line in docs/demo/games.html (`// ... if (k) TVKit.handler(k); }`): the demo's
// whole script died with "Unexpected token 'else'" and nothing else failed, since no test loaded that page.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const ROOT = path.join(__dirname, '..', '..', '..');
const DIRS = ['app/shared/web', 'app/tvgames/src/main/assets', 'app/tvhome/src/main/assets', 'app/tvsetup/src/main/assets', 'app/tvchannels/src/main/assets', 'docs'];
const SKIP = /(^|\/)(node_modules|vendor|lib)\/|\.min\.js$/;   // others' code, kept as they ship it

function files(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) files(p, out);
    else if (/\.(js|mjs|html)$/.test(e.name) && !SKIP.test(path.relative(ROOT, p))) out.push(p);
  }
  return out;
}

/** Each script as its own file for `node --check` (a module as .mjs: import / export are errors in a plain script). */
function scripts(tmp) {
  const out = [];
  for (const f of DIRS.flatMap((d) => files(path.join(ROOT, d)))) {
    const rel = path.relative(ROOT, f);
    if (!f.endsWith('.html')) { out.push({ rel, file: f }); continue; }
    const html = fs.readFileSync(f, 'utf8');
    let i = 0;
    for (const m of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
      const attrs = m[1];
      if (/\bsrc\s*=/.test(attrs) || !m[2].trim()) continue;
      const type = (attrs.match(/\btype\s*=\s*["']?([^"'\s>]+)/i) || [])[1] || '';
      if (type && !/^(text\/javascript|module)$/i.test(type)) continue;   // JSON-LD, templates: not scripts
      const line = html.slice(0, m.index).split('\n').length;
      const file = path.join(tmp, rel.replace(/[\/\\]/g, '_') + '_' + i++ + (type === 'module' ? '.mjs' : '.js'));
      fs.writeFileSync(file, '\n'.repeat(line - 1) + m[2]);           // the error's line is the page's own
      out.push({ rel, file });
    }
  }
  return out;
}

test('every shipped script parses (node --check)', () => {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'tvr-syntax-'));
  try {
    const all = scripts(tmp);
    assert.ok(all.length > 50, 'found only ' + all.length + ' scripts: the folders moved?');
    const bad = [];
    for (const s of all) {
      const r = spawnSync(process.execPath, ['--check', s.file], { encoding: 'utf8' });
      if (r.status !== 0) {
        const at = (r.stderr.match(/:(\d+)\n/) || [])[1];
        bad.push(s.rel + (at ? ':' + at : '') + '  ' + ((r.stderr.match(/SyntaxError[^\n]*/) || [r.stderr.trim()])[0]));
      }
    }
    assert.deepStrictEqual(bad, []);
  } finally { fs.rmSync(tmp, { recursive: true, force: true }); }
});
