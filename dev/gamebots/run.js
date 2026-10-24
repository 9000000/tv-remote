#!/usr/bin/env node
// Bots that PLAY the web games (docs/games/web/*) end to end in headless Chrome at 1920x1080, in real time (no virtual time:
// it fast-forwards timers and broke quiz's fetch timeout). Per game: load -> idle fps -> start -> play (random + simple
// heuristics, one player drops and rejoins mid-round) -> game over (3 min cap) -> rematch once -> heap after.
// Records page/console errors, game-over reached, stuck (no canvas/DOM/layout change for 20 s while the bots press), fps (rAF/s)
// in play and idle, JS heap across the rematch, screenshots lobby / play / over / rematch.
//
//   node dev/gamebots/run.js [game ...] [--players=2] [--cap=180] [--jobs=3] [--out=DIR]
//   needs puppeteer-core: installed OUTSIDE the repo, GAMEBOTS_NODE=<dir with node_modules/puppeteer-core> (default below)
//   Chrome: CHROME=<path> (default the macOS app). The page gets the REAL app/games/src/main/assets/tvpad.js on a fake bridge (page.js).
const fs = require('fs'), path = require('path'), http = require('http');
const REPO = path.resolve(__dirname, '../..'), WEB = path.join(REPO, 'docs/games/web');
const SCRATCH = '/private/tmp/claude-501/-Users-hoangtran-Desktop-Github/818eb7ba-5b31-444e-81f2-e599faff06df/scratchpad';
const NODE_DIR = process.env.GAMEBOTS_NODE || path.join(SCRATCH, 'gamebots_node');
const CHROME = process.env.CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const puppeteer = require(require.resolve('puppeteer-core', { paths: [NODE_DIR] }));

// kind: which brain (page.js); input: how a Kit game is steered; players: phones that join (2 unless the game is better with more/less)
const GAMES = {
  balloons: { kind: 'kit', input: 'ptr' }, bida: { kind: 'bida' }, bombers: { kind: 'kit', input: 'stick' }, cangua: { kind: 'tbl' },
  caro: { kind: 'caro' }, colors: { kind: 'tbl' }, cycles: { kind: 'kit', input: 'stick' }, hockey: { kind: 'kit', input: 'ptr', fire: false },
  meono: { kind: 'tbl' }, moles: { kind: 'kit', input: 'ptr' }, quiz: { kind: 'quiz' }, slice: { kind: 'kit', input: 'ptr', fire: false },
  sumo: { kind: 'kit', input: 'stick' }, tanks: { kind: 'kit', input: 'stick' }, tienlen: { kind: 'tbl' }, typhu: { kind: 'tbl' },
};
const args = process.argv.slice(2), opt = {};
args.filter((a) => a.startsWith('--')).forEach((a) => { const [k, v] = a.slice(2).split('='); opt[k] = v === undefined ? true : v; });
const want = args.filter((a) => !a.startsWith('--')), list = want.length ? want : Object.keys(GAMES);
const PLAYERS = +opt.players || 2, CAP = (+opt.cap || 180) * 1000, JOBS = +opt.jobs || 3, OUT = opt.out || path.join(SCRATCH, 'gamebots');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function serve() {                                                              // static server for docs/games/web, real files, no cache tricks
  const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml' };
  const s = http.createServer((q, r) => {
    let f = path.join(WEB, decodeURIComponent(q.url.split('?')[0])); if (!f.startsWith(WEB)) { r.writeHead(403).end(); return; }
    if (fs.existsSync(f) && fs.statSync(f).isDirectory()) f = path.join(f, 'index.html');
    fs.readFile(f, (e, b) => { if (e) { r.writeHead(404).end('no'); return; } r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream', 'cache-control': 'no-store' }); r.end(b); });
  });
  return new Promise((ok) => s.listen(0, '127.0.0.1', () => ok(s)));
}

async function launch(extra) {                                                  // Chrome on a busy machine sometimes fails to start: retry
  for (let i = 0; ; i++) try {
    return await puppeteer.launch({ executablePath: CHROME, headless: 'new', defaultViewport: null, timeout: 120000, userDataDir: fs.mkdtempSync(path.join(require('os').tmpdir(), 'gamebots-chrome-')),
      args: ['--window-size=1920,1080', '--no-first-run', ...extra] });
  } catch (e) { if (i >= 3) throw e; await sleep(3000); }
}

async function heap(cdp) {                                                      // JS heap after a forced GC, MB
  await cdp.send('HeapProfiler.collectGarbage'); const { metrics } = await cdp.send('Performance.getMetrics');
  return +(metrics.find((m) => m.name === 'JSHeapUsedSize').value / 1048576).toFixed(2);
}

async function bot(port, game) {
  // one Chrome per game: in a shared browser only the front tab gets requestAnimationFrame, the others freeze (measured: fps 0, a frozen match)
  const browser = await launch(['--autoplay-policy=no-user-gesture-required', '--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--disable-backgrounding-occluded-windows', '--enable-precise-memory-info', '--js-flags=--expose-gc']);
  try { return await bot1(browser, port, game); } finally { await browser.close().catch(() => { }); }
}
async function bot1(browser, port, game) {
  const spec = GAMES[game], dir = path.join(OUT, game); fs.rmSync(dir, { recursive: true, force: true }); fs.mkdirSync(dir, { recursive: true });
  const R = { game, players: PLAYERS, errors: [], over: false, rematch: false, stuck: [], fpsPlay: null, fpsIdle: null, fpsOver: null, heap: {}, notes: [] };
  const page = await browser.newPage(); await page.setViewport({ width: 1920, height: 1080, deviceScaleFactor: 1 });
  const cdp = await page.createCDPSession(); await cdp.send('Performance.enable');
  const err = (s) => { if (R.errors.length < 12 && !R.errors.includes(s)) R.errors.push(s); };
  page.on('pageerror', (e) => err('pageerror: ' + String(e.message).slice(0, 200)));
  page.on('console', (m) => { if (m.type() === 'error' && !/favicon|status of 404/.test(m.text())) err('console: ' + m.text().slice(0, 200)); });
  page.on('requestfailed', (r) => err('requestfailed: ' + r.url().slice(-60)));
  page.on('response', (r) => { if (r.status() >= 400 && !/favicon/.test(r.url())) err('http ' + r.status() + ': ' + r.url().slice(-60)); });
  const cfg = { ...spec, game, players: Array.from({ length: PLAYERS }, (_, i) => i + 1) };
  // page.js installs the fake TVPadNative first, then the real tvpad.js builds window.tvpad on it
  await page.evaluateOnNewDocument('window.__botCfg=' + JSON.stringify(cfg) + ';\n' + fs.readFileSync(path.join(__dirname, 'page.js'), 'utf8') + '\n' + fs.readFileSync(path.join(REPO, 'app/games/src/main/assets/tvpad.js'), 'utf8'));
  const ev = (f) => page.evaluate(f);
  const shot = async (n) => { try { await page.screenshot({ path: path.join(dir, n + '.png') }); } catch (e) { err('screenshot ' + n + ': ' + e.message); } };
  const rafRate = async (ms) => { const a = await ev(() => __bot.raf); await sleep(ms); const b = await ev(() => __bot.raf); return +((b - a) / (ms / 1000)).toFixed(1); };
  const state = () => ev(() => __bot.state()).catch(() => 'lobby');

  await page.goto(`http://127.0.0.1:${port}/${game}/`, { waitUntil: 'load', timeout: 30000 });
  await sleep(2500);
  R.fpsIdle = await rafRate(3000); await shot('1-lobby');
  R.heap.start = await heap(cdp);

  // start (retry: quiz waits for its bank, a Kit title needs a press)
  await ev(() => __bot.run(true));
  const t0 = Date.now(); let st = await state(), tries = 0;
  let reloaded = false;
  while (st === 'lobby' && Date.now() - t0 < 25000) {
    // quiz's bank fetch has a 3 s cap and the FIRST request of a cold headless Chrome can take 4-6 s: reload once (warm) so the bot tests the game, not the cold start
    if (game === 'quiz' && !reloaded && Date.now() - t0 > 8000 && await ev(() => typeof BANK !== 'undefined' && !BANK).catch(() => false)) { reloaded = true; R.notes.push('cold bank fetch timed out: reloaded'); await page.reload({ waitUntil: 'domcontentloaded' }); await sleep(2000); await ev(() => __bot.run(true)); }
 if (tries++ % 6 === 0) await ev(() => __bot.start()); await sleep(500); st = await state(); }
  if (st === 'lobby') R.notes.push('never left the lobby in 25 s');
  const play0 = Date.now(); let shotPlay = false, dropped = 0, sigAt = Date.now(), lastSig = '', maxStall = 0, lastSent = 0;
  const dropAt = 12000 + Math.random() * 6000, backAt = dropAt + 5000;
  while (Date.now() - play0 < CAP) {
    await sleep(1000); st = await state(); const el = Date.now() - play0;
    if (st === 'over') { R.over = true; break; }
    if (!shotPlay && el > 9000) { shotPlay = true; await shot('2-play'); }
    if (!dropped && el > dropAt && PLAYERS >= 2) { dropped = 1; await ev(() => __bot.leave(2)); R.notes.push('P2 dropped at ' + Math.round(el / 1000) + 's'); }
    else if (dropped === 1 && el > backAt) { dropped = 2; await ev(() => __bot.join(2)); R.notes.push('P2 rejoined at ' + Math.round(el / 1000) + 's'); }
    const sig = await ev(() => __bot.sig()).catch(() => ''), info = await ev(() => __bot.info()).catch(() => ({ sent: 0 }));
    if (sig !== lastSig) { lastSig = sig; sigAt = Date.now(); }
    const stall = (Date.now() - sigAt) / 1000;
    if (stall > maxStall && info.sent > lastSent) maxStall = stall;
    if (stall >= 20 && st === 'play' && info.sent - lastSent > 5) { R.stuck.push(Math.round(el / 1000) + 's'); await shot('stuck-' + Math.round(el / 1000)); sigAt = Date.now(); }
    lastSent = info.sent;
  }
  // fps in play: rAF delta over a 5 s window while the match runs (measured again if the game ended early)
  R.playSecs = Math.round((Date.now() - play0) / 1000);
  if (!R.over) { R.notes.push('no game over in ' + R.playSecs + 's'); await shot('3-cap'); }
  else {
    await sleep(1800); await shot('3-over'); R.fpsOver = await rafRate(2000);
    R.heap.over = await heap(cdp);
    await ev(() => __bot.rematch());
    const r0 = Date.now(); let s2 = 'over';
    while (Date.now() - r0 < 20000) { await sleep(500); s2 = await state(); if (s2 === 'play') break; if (s2 === 'over' && Date.now() - r0 > 3000 && (Date.now() - r0) % 3000 < 600) await ev(() => __bot.rematch()); }
    R.rematch = s2 === 'play';
    if (!R.rematch) { R.notes.push('rematch did not start'); await shot('4-rematch-fail'); }
    else { await sleep(9000); await shot('4-rematch-play'); }
    R.heap.rematch = await heap(cdp);
  }
  // play fps: a window in a fresh stretch of play (rematch, or the capped game)
  if (await state() === 'play') R.fpsPlay = await rafRate(5000);
  R.heap.growth = R.heap.rematch != null ? +(R.heap.rematch - R.heap.start).toFixed(2) : null;
  R.maxStall = +maxStall.toFixed(1); R.info = await ev(() => __bot.info()).catch(() => null);
  await page.close();
  fs.writeFileSync(path.join(dir, 'result.json'), JSON.stringify(R, null, 1));
  return R;
}

// --lobby[=before|after]: only screenshots of the lobby / first screen with 0, 1 and 4 players (lobby-<n>-<tag>.png)
async function lobbyShots(port, game, tag) {
  const browser = await launch([]);
  try {
    for (const n of [0, 1, 4]) for (let attempt = 0; attempt < 3; attempt++) try {
      const page = await browser.newPage(); await page.setViewport({ width: +(opt.size || '1920x1080').split('x')[0], height: +(opt.size || '1920x1080').split('x')[1] });
      const cfg = { ...GAMES[game], game, players: Array.from({ length: n }, (_, i) => i + 1) };
      await page.evaluateOnNewDocument('window.__botCfg=' + JSON.stringify(cfg) + ';\n' + fs.readFileSync(path.join(__dirname, 'page.js'), 'utf8') + '\n' + fs.readFileSync(path.join(REPO, 'app/games/src/main/assets/tvpad.js'), 'utf8'));
      await page.goto(`http://127.0.0.1:${port}/${game}/${opt.qr ? '?lobbyqr=1' : ''}`, { waitUntil: 'domcontentloaded', timeout: 60000 }).catch((e) => console.log(game, n, e.message)); await sleep(2500);
      fs.mkdirSync(path.join(OUT, game), { recursive: true });
      await page.screenshot({ path: path.join(OUT, game, `lobby-${n}-${tag}${opt.qr ? '-qr' : ''}${opt.size ? '-' + opt.size : ''}.png`) }); await page.close(); break;
    } catch (e) { console.log(game, n, 'retry', e.message.slice(0, 80)); }
  } finally { await browser.close(); }
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  if (opt.lobby) {
    const srv = await serve(), q = list.slice(), tag = opt.lobby === true ? 'shot' : opt.lobby;
    await Promise.all(Array.from({ length: Math.min(JOBS, q.length) }, async () => { while (q.length) await lobbyShots(srv.address().port, q.shift(), tag); }));
    srv.close(); return;
  }
  const srv = await serve(), port = srv.address().port;
  const q = list.slice(), results = [];
  await Promise.all(Array.from({ length: Math.min(JOBS, q.length) }, async () => {
    while (q.length) {
      const g = q.shift(); if (!GAMES[g]) { console.log('unknown game', g); continue; }
      try { const r = await bot(port, g); results.push(r); console.log(line(r)); }
      catch (e) { results.push({ game: g, fatal: String(e.stack || e).slice(0, 400) }); console.log(g, 'FATAL', String(e.message).slice(0, 200)); }
    }
  }));
  srv.close();
  results.sort((a, b) => a.game.localeCompare(b.game));
  fs.writeFileSync(path.join(OUT, 'summary.json'), JSON.stringify(results, null, 1));
  console.log('\n' + results.map(line).join('\n'));
})();

function line(r) {
  if (r.fatal) return `${r.game}: FATAL ${r.fatal}`;
  return `${r.game}: over=${r.over} rematch=${r.rematch} errors=${r.errors.length} stuck=${r.stuck.length}(max ${r.maxStall}s) fps play/idle/over=${r.fpsPlay}/${r.fpsIdle}/${r.fpsOver} heapMB ${r.heap.start}->${r.heap.over}->${r.heap.rematch} (+${r.heap.growth}) t=${r.playSecs}s${r.notes.length ? ' | ' + r.notes.filter((n) => !/^P2/.test(n)).join('; ') : ''}${r.errors.length ? ' | ' + r.errors.slice(0, 3).join(' || ') : ''}`;
}
