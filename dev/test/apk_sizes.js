#!/usr/bin/env node
// APK jump alarm (not a cap): compares each app's built release APK with dev/test/apk-sizes.json and fails when one grew by more
// than 10% AND more than 256 KB, or by more than 1 MB, over its baseline. Why: size itself does not slow a weak TV box (code not run
// costs no CPU / RAM), but a sudden jump is usually a library or asset pulled in by mistake: look at what came in, then decide.
//   node dev/test/apk_sizes.js            check (exit 1 on a jump; an APK not built here is skipped, said so)
//   node dev/test/apk_sizes.js --update   or TVR_APK_UPDATE=1: record the sizes now as the new baseline (say why in the commit)
// APKs: app/<x>/build/<name>.apk (app/<x>/build.sh); TVR_APK_ROOT=<repo> reads them from another checkout.
// The baseline is committed (sizes are the build's, not the machine's); a shrink is recorded only with --update.
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const FILE = path.join(__dirname, 'apk-sizes.json');
const APKS = { home: 'tv-home', tv: 'tv-setup', android: 'tv-remote', channels: 'tv-channels', games: 'tv-games' };
const PCT = 0.10, PCT_FLOOR = 256 * 1024, ABS = 1024 * 1024;

// Pure: -> 'ok' | 'jump'. Over 10% AND 256 KB (a 100 KB app doubling is still small), or over 1 MB whatever the percent.
function verdict(base, now) {
  const grew = now - base;
  if (grew > ABS) return 'jump';
  return grew > base * PCT && grew > PCT_FLOOR ? 'jump' : 'ok';
}

function sizes(root) {
  const out = {};
  for (const [app, name] of Object.entries(APKS)) {
    try { out[app] = fs.statSync(path.join(root, 'app', app, 'build', name + '.apk')).size; } catch (e) { /* not built here */ }
  }
  return out;
}

function main(argv, env) {
  const root = env.TVR_APK_ROOT || ROOT;
  const now = sizes(root);
  const base = fs.existsSync(FILE) ? JSON.parse(fs.readFileSync(FILE, 'utf8')) : {};
  if (argv.includes('--update') || env.TVR_APK_UPDATE === '1') {
    fs.writeFileSync(FILE, JSON.stringify({ ...base, ...now }, null, 2) + '\n');
    console.log('baseline recorded:', Object.entries(now).map(([a, n]) => `${a} ${(n / 1048576).toFixed(2)} MB`).join(', ') || 'no APK found');
    return 0;
  }
  let bad = 0;
  for (const app of Object.keys(APKS)) {
    if (now[app] == null) { console.log(`${app}: not built here, skipped`); continue; }
    if (base[app] == null) { console.log(`${app}: ${now[app]} B, no baseline yet (run with --update)`); continue; }
    const d = now[app] - base[app], v = verdict(base[app], now[app]);
    console.log(`${app}: ${now[app]} B (${d >= 0 ? '+' : ''}${d} B, ${(d / base[app] * 100).toFixed(1)}%) ${v === 'jump' ? 'JUMP' : 'ok'}`);
    if (v === 'jump') bad++;
  }
  if (bad) console.error('APK grew a lot: find what came in (unzip -l, R8 usage) and drop it; if it is wanted, node dev/test/apk_sizes.js --update and say why in the commit.');
  return bad ? 1 : 0;
}

module.exports = { verdict, PCT, ABS };
if (require.main === module) process.exit(main(process.argv.slice(2), process.env));
