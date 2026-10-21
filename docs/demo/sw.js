// Live demo: the real web UI of the app (app/shared/web) in the browser, with a made-up TV.
// This worker (scope /demo/) does two things for the page in /demo/app/:
//   1. serves the UI's own files, fetched from jsDelivr at the git tag below (nothing is copied into docs/);
//   2. answers every /api/... call by asking the demo page (/demo/remote.html, mock.js), which keeps the made-up TV in its memory
//      (a worker may be stopped by the browser at any time, a page is not).
// To show a newer UI: change REF to a newer release tag (docs/README.md, "Live demo"), commit, push.
const REF = 'v0.12.0';
const CDN = 'https://cdn.jsdelivr.net/gh/HoangTran0410/tv-remote@' + REF + '/app/shared/web/';
const CACHE = 'tvr-demo-' + REF;
const APP = '/demo/app/';
// the same list as WebPage.kt: what the real server hands out
const FILE = /^(index\.html|css\/[a-z0-9_-]+\.css|stats\.js|jmuxer\.min\.js|js\/[a-z0-9_-]+\.js|manifest\.json|remote\.json|i18n\/[a-z]{2,3}\.json|icon-[a-z0-9]+\.png)$/;

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => e.waitUntil((async () => {
  for (const k of await caches.keys()) if (k.startsWith('tvr-demo-') && k !== CACHE) await caches.delete(k);   // an older REF
  await self.clients.claim();
})()));

self.addEventListener('fetch', (e) => {
  const u = new URL(e.request.url);
  if (u.origin !== location.origin) return;
  if (u.pathname.startsWith('/api/')) { e.respondWith(mock(e, u)); return; }
  if (u.pathname === '/demo/app') { e.respondWith(Response.redirect(APP, 301)); return; }
  if (u.pathname.startsWith(APP)) e.respondWith(file(u.pathname.slice(APP.length) || 'index.html'));
});

async function file(name) {
  if (!FILE.test(name)) return new Response('not found', { status: 404 });
  const cache = await caches.open(CACHE);
  let r = await cache.match(CDN + name);
  if (!r) {
    try { r = await fetch(CDN + name); } catch { r = null; }
    if (!r || !r.ok) return new Response('the demo could not load ' + name + (r ? ' (' + r.status + ')' : ''), { status: 502 });
    await cache.put(CDN + name, r.clone());
  }
  if (name !== 'index.html') return r;
  // what WebPage.kt does to the page: it says which app it is talking to (the computer app: "pc")
  const html = (await r.text()).replace('<html ', '<html data-core="pc" ').replace('<head>', '<head>' + await syncShim());
  return new Response(html, { headers: { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' } });
}

// The page reads its words and remote.json with a synchronous XMLHttpRequest (i18n.js, remote_spec.js), and Chrome does not
// hand those to a worker: they would reach the static host and 404. So the page starts with these three files already in it,
// and a few lines that answer exactly those requests from them.
const SYNC = ['i18n/vi.json', 'i18n/en.json', 'remote.json'];
async function syncShim() {
  const text = {};
  for (const n of SYNC) text[n] = await (await file(n)).text();
  return '<script>(function(F){var o=XMLHttpRequest.prototype.open,s=XMLHttpRequest.prototype.send;' +
    'XMLHttpRequest.prototype.open=function(m,u,a){var p=new URL(u,location.href).pathname.slice(' + APP.length + ');this._d=a===false&&p in F?F[p]:null;return o.apply(this,arguments)};' +
    'XMLHttpRequest.prototype.send=function(){if(this._d===null)return s.apply(this,arguments);' +
    'for(var k of["status","responseText","response"])Object.defineProperty(this,k,{value:k==="status"?200:this._d})};' +
    '})(' + JSON.stringify(text).replace(/</g, '\\u003c') + ')</script>' + NOW;
}

// The page takes a screenshot each second (on a real TV a screencap is slow and heavy); the made-up TV draws at once, so
// after a key or an app the demo takes the next one right away instead of up to a second later
const NOW = '<script>(function(f){var R=/^\\/api\\/(key|app|app\\/launch|volume|scene\\/run|recents|draw)$/;window.fetch=function(u,o){var p=f.apply(this,arguments);' +
  'try{var path=new URL(typeof u==="string"?u:u.url,location.href).pathname;if(o&&o.method==="POST"&&R.test(path))p.then(function(){' +
  'if(typeof screenOn!=="undefined"&&screenOn&&typeof snapOnce==="function")snapOnce().then(function(b){if(b instanceof Blob)showFrame(b)})})}catch(e){}return p}})(window.fetch)</script>';

// the demo page answers: {method, path, search, body} in, {status, type, body} out
async function mock(e, u) {
  const body = e.request.method === 'POST' ? await e.request.text() : '';
  const pages = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });   // the demo page may not be controlled yet on a first visit
  const home = pages.find((c) => new URL(c.url).pathname === '/demo/remote.html');   // the hub (/demo/) holds the tabs; this page holds the made-up TV
  if (!home) return json(503, { error: 'open the demo from /demo/#remote' });
  const reply = await new Promise((done) => {
    const ch = new MessageChannel();
    const t = setTimeout(() => done(null), 20000);
    ch.port1.onmessage = (m) => { clearTimeout(t); done(m.data); };
    home.postMessage({ demo: 'api', method: e.request.method, path: u.pathname, search: u.search, body }, [ch.port2]);
  });
  if (!reply) return json(504, { error: 'the demo did not answer' });
  return new Response(reply.body, { status: reply.status, headers: { 'content-type': reply.type, 'cache-control': 'no-store' } });
}
const json = (status, o) => new Response(JSON.stringify(o), { status, headers: { 'content-type': 'application/json' } });
