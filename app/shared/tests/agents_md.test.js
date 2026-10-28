// AGENTS.md files are followed to the letter by agents: a renamed file turns into a confident wrong instruction.
// Every path in a link or in backticks must exist, and the Downloader codes must match app/shared's signed list.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const ROOT = path.resolve(__dirname, '../../..');

// What a fresh clone has: tracked files + untracked ones git does not ignore (so .gitignore is honoured).
const files = execFileSync('git', ['ls-files', '-co', '--exclude-standard'], { cwd: ROOT, maxBuffer: 1 << 28 })
  .toString().split('\n').filter((f) => f && fs.existsSync(path.join(ROOT, f)) && !/(^|\/)node_modules\//.test(f));
const fileSet = new Set(files);
const dirSet = new Set();
const names = new Set();
for (const f of files) {
  names.add(path.basename(f));
  for (let d = path.dirname(f); d !== '.' && !dirSet.has(d); d = path.dirname(d)) dirSet.add(d);
}

const agents = files.filter((f) => path.basename(f) === 'AGENTS.md' && !f.startsWith('.claude/'));

// Mentioned on purpose although a clone lacks them (made by a build, a script or the user's machine).
const EXCEPTIONS = new Set([
  'tvs.json', 'cert.pem', 'key.pem',          // the user's own pairing data, never committed
  'local.properties', 'keys.json',            // per machine
  'signing.jks', 'versions.json',             // signing key (gitignored); a release asset
  'SHA256SUMS.sig', '.sig', 'strings.xml',    // release asset; "a signature"; "no strings.xml" (a rule)
  'tvs.xml', '.codegraph', 'media', '.local', // on the TV / per machine, gitignored
  'pc', 'facts',                              // data-src short names; `Launchers.parse/facts`
]);
// Prefixes of generated output (gitignored): mentioned as where a build or run puts things.
const GENERATED = ['app/', 'dev/node_modules', 'dev/.local', '/data/', '/sdcard/', '/tmp/', '~/', '/Users/', '/system/', '/vendor/', '/proc/', '/sys/'];

function exists(ref, dir) {
  const clean = ref.replace(/\/$/, '');
  const tries = [clean, path.posix.join(dir, clean)];
  if (tries.some((p) => fileSet.has(p) || dirSet.has(p))) return true;
  // a build output path under a gitignored build/ folder
  if (/(^|\/)build\//.test(clean)) return true;
  if (/\.apk$/.test(clean)) return true;                          // APKs are release assets, never committed
  return false;
}

function refsOf(text) {
  const out = [];
  const lines = text.split('\n');
  let fence = false;
  lines.forEach((line, i) => {
    if (/^\s*```/.test(line)) { fence = !fence; return; }
    if (fence) return;
    for (const m of line.matchAll(/\]\(([^)\s]+)\)/g)) out.push({ ref: m[1], link: true, line: i + 1 });
    for (const m of line.matchAll(/`([^`]+)`/g)) out.push({ ref: m[1], link: false, line: i + 1 });
  });
  return out;
}

function candidate(ref) {
  let r = ref.trim().replace(/#.*$/, '').replace(/:\d+(-\d+)?$/, '').replace(/^\.\//, '');
  if (!r || /[\s<>{}*$|()=,;"'\\[\]!?@]/.test(r)) return null;      // templates, globs, commands, prose
  if (/^[a-z]+:/i.test(r) || r.startsWith('//')) return null;       // URLs, tvchannels://
  if (r.startsWith('-') || r.startsWith('+')) return null;
  const hasSlash = r.includes('/');
  const ext = /\.[A-Za-z][A-Za-z0-9]{0,5}$/.test(r);
  if (!hasSlash && !ext) return null;
  if (!hasSlash && /^\d/.test(r)) return null;
  // version numbers, domains, JS member access (a.b.c): need a known file-like extension for bare names
  if (!hasSlash && !/\.(kt|kts|js|mjs|json|md|html|css|sh|py|xml|gradle|properties|png|svg|webp|jpg|txt|yml|yaml|apk|pem|jks|toml|ttf|woff2?|mp4|ico|java|cfg|conf|sig|sha256|tsv|csv)$/i.test(r)) return null;
  return r;
}

test('the AGENTS.md files are found', () => {
  assert.ok(agents.includes('AGENTS.md'));
  assert.ok(agents.length >= 10, `only ${agents.length} AGENTS.md`);
});

test('every path an AGENTS.md names exists', () => {
  const bad = [];
  for (const a of agents) {
    const dir = path.posix.dirname(a);
    for (const { ref, line } of refsOf(fs.readFileSync(path.join(ROOT, a), 'utf8'))) {
      const r = candidate(ref);
      if (!r || EXCEPTIONS.has(path.basename(r))) continue;
      if (r.startsWith('/') || r.startsWith('~') || GENERATED.some((g) => g !== 'app/' && r.startsWith(g))) continue;
      if (exists(r, dir)) continue;
      // a bare name counts when a file of that name exists anywhere
      if (!r.includes('/') && names.has(r)) continue;
      // `js/remote.js`, `res/drawable/x.xml`: a tail of a real path inside this folder's tree
      const tail = '/' + r.replace(/\/$/, '');
      if (files.some((f) => f.endsWith(tail)) || [...dirSet].some((d) => d.endsWith(tail))) continue;
      bad.push(`${a}:${line}  ${ref}`);
    }
  }
  assert.deepStrictEqual(bad, [], 'named in an AGENTS.md but not in the repo:\n' + bad.join('\n'));
});

test('the Downloader codes in AGENTS.md match docs/data/downloader.json', () => {
  const list = JSON.parse(fs.readFileSync(path.join(ROOT, 'docs/data/downloader.json'), 'utf8'));
  const names = { setup: 'tv-setup', remote: 'tv-remote', home: 'tv-home', channels: 'tv-channels', games: 'tv-games' };
  for (const f of ['AGENTS.md', 'app/shared/AGENTS.md']) {
    const text = fs.readFileSync(path.join(ROOT, f), 'utf8').replace(/\s+/g, ' ');
    for (const [key, name] of Object.entries(names)) {
      assert.ok(text.includes(`**${list[key]}**`) && new RegExp(`\\*\\*${list[key]}\\*\\* (→ \`)?${name}`).test(text),
        `${f}: the Downloader code of ${name} should be ${list[key]}`);
    }
  }
});
