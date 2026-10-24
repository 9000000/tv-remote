#!/usr/bin/env node
// The whole web UI looked over by a script: every tab, and every dialog, menu and chip reached by pressing the page's own
// buttons, each checked for what a person would notice. See AGENTS.md "Testing".
//
//   node dev/test/audit.js [--all] [--core=pc|tv|phone] [--only=apps,tv] [--depth=3] [--budget=120 s per tab] [--out=/tmp/tvr-audit]
//
// No TV and no server: the page is the one in app/shared/web (as it is now, not a release), its /api calls are answered by
// the live demo's made-up TV (docs/demo/mock.js), so pressing everything changes nothing real. Each button is pressed once
// per kind (the ⋯ of 30 app rows once); what opens (a dialog, a menu, a chip's list) is a screen, checked, then its own
// buttons are pressed in turn, down to --depth. Runs: 1100 px Vietnamese light and 400 px English dark (--all: the two others
// too). Checks on each screen:
//   layout      the page scrolling sideways; words cut; a button label on two lines; chips wrapping; out of its card; overlap
//   contrast    text under 4.5:1 (3:1 large), icons under 3:1 (WCAG, as dev/test/contrast.py)
//   rules       a control height that is none of --ctl / --ctl-sm / --chip; two filled (primary) buttons side by side; an emoji
//               left in a title or button (no line icon in ui.js ICON); a button with no name; text under 11 px
//   language    Vietnamese words on the English page; a word id (apps.xxx) shown instead of its words
//   touch       something to press smaller than 32 px on a phone-wide page
//   same        across all screens, a look used by few of its kind (a button 34 px high where all others are 36): the odd one
//   errors      a script error on the page
// Out: <out>/index.html, one screenshot per screen with each fault boxed in red and listed under it, worst screens first.
// Exit 1 if any fault of the kinds above but "same" (which is advice).
const fs = require('fs'), path = require('path');
let pw;
try { pw = require(path.join(__dirname, '..', 'node_modules', 'playwright')); } catch { try { pw = require('playwright'); } catch {
  console.error('needs Playwright: npm install --prefix dev playwright'); process.exit(2); } }

const arg = (k, d) => { const a = process.argv.find((x) => x.startsWith('--' + k)); return a ? (a.includes('=') ? a.split('=')[1] : true) : d; };
const ROOT = path.join(__dirname, '..', '..'), WEB = path.join(ROOT, 'app/shared/web'), DOCS = path.join(ROOT, 'docs');
const OUT = arg('out', '/tmp/tvr-audit'), CORE = arg('core', 'pc'), DEPTH = +arg('depth', 3);
const ONLY = arg('only', '') ? String(arg('only')).split(',') : null;
const HOST = 'http://audit.test';
const BUDGET = +arg('budget', 120) * 1000;                            // per tab and run: a screen that keeps reloading cannot hold it up
const TABS = ['header', 'remote', 'apps', 'files', 'health', 'auto', 'tv'];
const RUNS = [{ w: 1100, lang: 'vi', theme: 'light' }, { w: 400, lang: 'en', theme: 'dark' }]
  .concat(arg('all') ? [{ w: 1100, lang: 'en', theme: 'dark' }, { w: 400, lang: 'vi', theme: 'light' }] : []);
const IDS = Object.keys(JSON.parse(fs.readFileSync(path.join(WEB, 'i18n/en.json'), 'utf8')));
const TYPES = { html: 'text/html; charset=utf-8', js: 'text/javascript', css: 'text/css', json: 'application/json', png: 'image/png',
  webp: 'image/webp', jpg: 'image/jpeg', svg: 'image/svg+xml', woff2: 'font/woff2', sig: 'text/plain' };

// the files the page asks for, from the working tree; the made-up TV's host page; /api to the made-up TV
function serve(host) {
  return async (route) => {
    const u = new URL(route.request().url());
    const file = (f) => fs.existsSync(f) && fs.statSync(f).isFile() ? route.fulfill({ body: fs.readFileSync(f), contentType: TYPES[f.split('.').pop()] || 'application/octet-stream' }) : route.fulfill({ status: 404, body: 'not found' });
    if (u.origin !== HOST) return route.abort();                       // nothing leaves the computer (fonts, links, pictures)
    if (u.pathname.startsWith('/api/')) {
      const r = await host().evaluate((q) => window.demoApi(q), { method: route.request().method(), path: u.pathname, search: u.search, body: route.request().postData() || '' })
        .catch((e) => ({ status: 500, type: 'application/json', body: JSON.stringify({ error: String(e) }) }));
      return route.fulfill({ status: r.status, contentType: r.type, body: r.body });
    }
    if (u.pathname === '/demo/host.html') return route.fulfill({ contentType: TYPES.html, body: '<!doctype html><script src="mock.js"></script>' });
    if (u.pathname === '/app/' || u.pathname === '/app/index.html') {
      const html = fs.readFileSync(path.join(WEB, 'index.html'), 'utf8').replace('<html ', `<html data-core="${CORE}" `);   // as WebPage.kt
      return route.fulfill({ contentType: TYPES.html, body: html });
    }
    if (u.pathname.startsWith('/app/')) return file(path.join(WEB, decodeURIComponent(u.pathname.slice(5))));
    return file(path.join(DOCS, decodeURIComponent(u.pathname)));
  };
}

// ---------- in the page: the faults under one screen ----------
// [root]: the screen's element (a dialog, a menu, a tab's panel). Each fault: {kind, msg, el (a short path), r (its box)}
function check({ root: sel, ids, phone, lang }) {
  const root = sel === 'body' ? document.body : document.querySelector(sel);
  if (!root) return { faults: [], looks: [] };
  const out = [], seen = new Set();
  const css = (e) => getComputedStyle(e);
  const shown = (e) => { const r = e.getBoundingClientRect(); if (r.width < 1 || r.height < 1) return false;
    const d = e.closest('details:not([open])'); if (d && !e.closest('summary')) return false;   // laid out but not drawn: Chrome keeps the boxes
    for (let x = e; x; x = x.parentElement) { const s = css(x); if (s.display === 'none' || s.visibility === 'hidden' || +s.opacity < 0.05) return false; } return true; };
  const name = (e) => { const o = []; for (let i = 0, x = e; x && i < 3 && x !== document.body; i++, x = x.parentElement)
    o.unshift(x.tagName.toLowerCase() + (x.id ? '#' + x.id : '') + (typeof x.className === 'string' && x.className.trim() ? '.' + x.className.trim().split(/\s+/).slice(0, 2).join('.') : ''));
    return o.join(' > '); };
  const words = (e) => (e.innerText || e.value || e.getAttribute('aria-label') || e.title || '').trim().replace(/\s+/g, ' ').slice(0, 50);
  const box = (e) => { const r = e.getBoundingClientRect(); return { x: r.left, y: r.top, w: r.width, h: r.height }; };
  const add = (kind, e, msg) => { const k = kind + name(e) + words(e); if (seen.has(k)) return; seen.add(k); out.push({ kind, msg, el: name(e), text: words(e), r: box(e) }); };
  // the parts the screen does not own: the remote's keys, app art and the TV's picture keep their own looks
  const content = (e) => e.closest('.rc, canvas, video, .tiles, #scenetpl, #sceneicon, .shot, .homegal, .launchgal, .appicon, svg');
  const all = [...root.querySelectorAll('*')].filter((e) => shown(e));

  // layout (as dev/test/layout.js)
  if (document.documentElement.scrollWidth > innerWidth + 1) add('layout', document.documentElement, `page scrolls sideways (${document.documentElement.scrollWidth}px > ${innerWidth}px)`);
  for (const e of all) {
    if (!e.matches('button, .chip, summary, h1, h2, h3, label, .tab, select, .item .name > *, .item b, .nameline > span, small, p') || e.closest('.rc, canvas, video, .pop .sub')) continue;
    const s = css(e);
    if (e.scrollWidth > e.clientWidth + 1 && (s.overflowX !== 'visible' || s.textOverflow === 'ellipsis') && !e.closest('.runs')) add('layout', e, 'words cut');
    if (e.tagName === 'BUTTON' && words(e) && !/\n/.test(e.innerText.trim())) {
      const lines = new Set(); const w = document.createTreeWalker(e, NodeFilter.SHOW_TEXT);
      for (let t = w.nextNode(); t; t = w.nextNode()) { if (!t.data.trim()) continue; const rg = document.createRange(); rg.selectNodeContents(t);
        for (const q of rg.getClientRects()) if (q.width > 1) lines.add(Math.round(q.top + q.height / 2)); }
      const ys = [...lines].sort((p, q) => p - q); if (ys.length > 1 && ys[ys.length - 1] - ys[0] > parseFloat(s.fontSize) * 0.8) add('layout', e, 'label on two lines');
    }
  }
  for (const row of all.filter((e) => e.matches('.places, .fchips, .chips'))) {
    const kids = [...row.children].filter(shown);
    if (kids.length > 1 && css(row).flexWrap !== 'nowrap' && new Set(kids.map((k) => Math.round(k.getBoundingClientRect().top))).size > 1) add('layout', row, 'chips wrap to a second line');
  }
  for (const card of all.filter((e) => e.matches('.card, dialog[open] > *, .pop'))) {
    const c = card.getBoundingClientRect();
    for (const e of card.querySelectorAll('button, input, select, .chip, img, .item, .line, h2, h3, p')) {
      if (!shown(e) || e.closest('.scroll, .table, pre')) continue;
      let clipped = false; for (let x = e.parentElement; x && x !== card; x = x.parentElement) if (css(x).overflowX !== 'visible') { clipped = true; break; }
      if (clipped) continue;
      const r = e.getBoundingClientRect();
      if (r.right > c.right + 1 || r.left < c.left - 1) add('layout', e, `sticks out of ${card.matches('.pop') ? 'its menu' : 'its card'}`);
    }
  }
  // two cards one right under another with under 8 px between: a slot holding several cards needs the gap (css/kit.css)
  const cardsAll = all.filter((e) => e.matches('.panel .card') && !e.parentElement.closest('.card') && shown(e));
  for (const a of cardsAll) for (const b of cardsAll) {
    if (a === b) continue;
    const ra = a.getBoundingClientRect(), rb = b.getBoundingClientRect(), gap = rb.top - ra.bottom;
    if (Math.min(ra.right, rb.right) - Math.max(ra.left, rb.left) > 4 && gap > -4 && gap < 8) add('layout', a, `glued to the card under it (${Math.round(gap)} px)`);
  }
  // two things on top of each other: controls and lines of words, neither inside the other
  const solid = all.filter((e) => !content(e) && (e.matches('button, input, select, a, .chip, .tag') || ([...e.childNodes].some((n) => n.nodeType === 3 && n.data.trim()) && !e.matches('button *, a *'))))
    .filter((e) => !e.closest('.pop') || root.matches('.pop') || root.contains(e.closest('.pop')));
  for (let i = 0; i < solid.length; i++) for (let j = i + 1; j < solid.length; j++) {
    const a = solid[i], b = solid[j]; if (a.contains(b) || b.contains(a)) continue;
    const p = a.getBoundingClientRect(), q = b.getBoundingClientRect();
    const dx = Math.min(p.right, q.right) - Math.max(p.left, q.left), dy = Math.min(p.bottom, q.bottom) - Math.max(p.top, q.top);
    if (dx > 3 && dy > 3) add('layout', a, 'on top of ' + name(b) + ' "' + words(b) + '"');
  }

  // contrast (as dev/test/contrast.py)
  const P = (s) => { const m = s.match(/[\d.]+/g).map(Number); const k = s.startsWith('color(') ? 255 : 1; return [m[0] * k, m[1] * k, m[2] * k, m.length > 3 ? m[3] : 1]; };
  const over = (f, b) => [0, 1, 2].map((i) => f[i] * f[3] + b[i] * (1 - f[3])).concat(1);
  const L = (c) => { const x = c.slice(0, 3).map((v) => (v /= 255) <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4); return .2126 * x[0] + .7152 * x[1] + .0722 * x[2]; };
  const hex = (c) => '#' + c.slice(0, 3).map((v) => Math.round(v).toString(16).padStart(2, '0')).join('');
  const bgOf = (e) => { const layers = []; let unknown = false;
    for (let a = e; a && a.nodeType === 1; a = a.parentElement) { const s = css(a); if (s.backgroundImage !== 'none') { unknown = true; break; }
      const c = P(s.backgroundColor); if (c[3] > 0) { layers.push(c); if (c[3] === 1) break; } }
    let b = P(css(document.documentElement).backgroundColor); if (b[3] < 1) b = [255, 255, 255, 1];
    for (const l of layers.reverse()) b = over(l, b); return [b, unknown]; };
  const faint = (e) => { let op = 1; for (let a = e; a; a = a.parentElement) { op *= +css(a).opacity; if (op < .5) return true;
    if (a.matches('[disabled], [aria-disabled=true], [data-kind-off]')) return true; } return false; };
  for (const e of all) {
    const icon = e.matches('svg.ic, .ui-ic');
    if (!icon && (e.closest('svg') || content(e) || !e.childNodes.length || ![...e.childNodes].some((n) => n.nodeType === 3 && n.data.trim()))) continue;
    if (faint(e)) continue;
    const s = css(e), [bg, unknown] = bgOf(e); if (unknown) continue;
    const fg = over(P(s.color), bg), a = L(fg), b = L(bg), ratio = (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    const size = parseFloat(s.fontSize), need = icon || size >= 24 || (+s.fontWeight >= 700 && size >= 18.66) ? 3 : 4.5;
    if (ratio < need) add('contrast', e, `${Math.round(ratio * 100) / 100}:1 (${hex(fg)} on ${hex(bg)}, needs ${need}:1)`);
  }

  // rules (AGENTS.md "The tabs are made of a few shapes")
  const root_ = css(document.documentElement), px = (v) => parseFloat(root_.getPropertyValue(v));
  const sizes = [px('--ctl'), px('--ctl-sm'), px('--chip')].filter((v) => v > 0);
  for (const e of all) {
    if (content(e)) continue;
    if (e.matches('button:not(.mitem):not(.item):not(.pick):not([role=tab]):not(.tabs *):not(.pop *):not(.fchips *):not(.places *):not(.seg *), input:not([type=checkbox]):not([type=radio]):not([type=range]):not([type=file]):not([type=color]), select')) {
      const h = e.getBoundingClientRect().height;
      if (sizes.length && !sizes.some((v) => Math.abs(v - h) <= 1.5) && !e.matches('textarea')) add('rules', e, `height ${Math.round(h)}px is none of the control sizes (${sizes.join(' / ')})`);
    }
    if (e.matches('button, a.linkbtn, label.filebtn') && !words(e) && !e.querySelector('img[alt]:not([alt=""])')) add('rules', e, 'a button with no name (no words, aria-label or title)');
    const fs_ = parseFloat(css(e).fontSize);
    if (fs_ < 11 && [...e.childNodes].some((n) => n.nodeType === 3 && n.data.trim())) add('rules', e, `text ${fs_}px, under 11px`);
    if (e.matches('h2, h3, summary, button, .tabs .ico') && !e.closest('.rc, .tiles, #scenetpl, #sceneicon, .item .ic0, .pop, .mitem')) {
      const t = [...e.childNodes].find((n) => n.nodeType === 3 && n.data.trim());
      if (t && /^\s*\p{Extended_Pictographic}/u.test(t.data)) add('rules', e, 'an emoji left as it is: no line icon for it in ui.js ICON');
    }
  }
  for (const grp of all.filter((e) => e.matches('.actions, .acts, .card-head, .line, .fleetgo'))) {
    const fills = [...grp.querySelectorAll(':scope > button.primary, :scope > * > button.primary')].filter(shown);
    if (fills.length > 1) add('rules', grp, `${fills.length} filled buttons side by side: one is the action, the others plain`);
  }

  // language
  const VI = /[ăâđêôơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ]/i, idSet = new Set(ids);
  for (const e of all) {
    const t = [...e.childNodes].filter((n) => n.nodeType === 3).map((n) => n.data.trim()).join(' ').trim();
    if (!t) continue;
    if (idSet.has(t)) add('language', e, 'shows the word id, not its words');
    else if (lang === 'en' && VI.test(t) && !e.closest('.rc, input, textarea, [data-user]')) add('language', e, 'Vietnamese on the English page');
  }

  // touch: phone-wide
  if (phone) for (const e of all) {
    if (!e.matches('button, a[href], input, select, summary, label.check, .chip') || e.matches('input[type=checkbox], input[type=radio]') || e.closest('p, .rc .num')) continue;
    const r = e.getBoundingClientRect();
    if (Math.min(r.width, r.height) < 32) add('touch', e, `${Math.round(r.width)}×${Math.round(r.height)}px to press, under 32px`);
  }

  // looks, for "same": compared across all screens afterwards
  const looks = [];
  const kind = (e) => e.matches('button') ? 'button' + (e.matches('.primary') ? '.primary' : e.matches('.danger') ? '.danger' : '') + (e.matches('.small') ? '.small' : '') + (e.matches('.iconbtn') ? '.icon' : '')
    : e.matches('h2, h3') ? e.tagName.toLowerCase() : e.matches('p.note') ? 'p.note' : e.matches('.item') ? 'list row' : e.matches('.tag') ? 'tag' : e.matches('input[type=text], input[type=search], input:not([type])') ? 'field' : null;
  for (const e of all) {
    if (content(e) || e.closest('.pop')) continue;
    const k = kind(e); if (!k) continue;
    const s = css(e), r = e.getBoundingClientRect();
    const look = k.startsWith('button') || k === 'field' ? `${Math.round(r.height)}px high, radius ${s.borderRadius}, ${s.fontSize} ${s.fontWeight}`
      : k === 'list row' ? `padding ${s.paddingTop} ${s.paddingLeft}` : `${s.fontSize} ${s.fontWeight} ${s.textTransform} ${s.letterSpacing}`;
    looks.push({ kind: k, look, el: name(e), text: words(e), r: box(e) });
  }
  return { faults: out, looks };
}

// the screen's state, to tell a new one from one seen: what is open on top, its title, what is chosen in it
function topState(tab) {
  const vis = (e) => e && e.getClientRects().length && getComputedStyle(e).visibility !== 'hidden';
  const pops = [...document.querySelectorAll('.pop')].filter(vis);
  const dlgs = [...document.querySelectorAll('dialog[open]')];
  const head = (e) => (e.querySelector('h1, h2, h3, summary, b')?.innerText || '').trim().replace(/\d+/g, '#').slice(0, 40);
  const on = (e) => [...e.querySelectorAll('.on, [aria-selected=true], [aria-pressed=true], details[open] > summary')].filter(vis).map((x) => (x.innerText || '').trim().replace(/\d+/g, '#').slice(0, 20)).join('|');
  if (pops.length) { const p = pops[pops.length - 1]; return { sel: 'pop', root: '.pop', name: 'menu: ' + [...p.querySelectorAll('button')].map((b) => b.innerText.trim()).slice(0, 3).join(' / ').slice(0, 50), sig: 'pop:' + p.innerText.replace(/\d+/g, '#').slice(0, 80) }; }
  if (dlgs.length) { const d = dlgs[dlgs.length - 1]; d.dataset.auditTop = '1'; dlgs.slice(0, -1).forEach((x) => delete x.dataset.auditTop);
    return { sel: 'dialog', root: 'dialog[open][data-audit-top]', name: (d.id ? '#' + d.id + ' ' : '') + head(d), sig: 'dlg:' + (d.id || '') + head(d) + ':' + on(d) }; }
  const panel = tab === 'header' ? document.querySelector('header.top') : [...document.querySelectorAll(`.panel[data-panel="${tab}"]`)].find((p) => !p.hidden && vis(p));
  if (!panel) return { sel: 'none', root: 'body', name: tab, sig: 'none:' + tab };
  panel.dataset.auditTop = '1';
  const pick = document.querySelector('#tvlist, .picker');
  return { sel: 'panel', root: tab === 'header' ? 'header.top' : `.panel[data-panel="${tab}"][data-audit-top]`, name: tab + (on(panel) ? ' · ' + on(panel).slice(0, 50) : ''), sig: 'panel:' + tab + ':' + on(panel) + (vis(pick) ? ':pick' : '') };
}

// the things to press on the screen on top, numbered in order; their kind, so 30 rows' ⋯ count as one
function controls(rootSel) {
  const root = document.querySelector(rootSel); if (!root) return [];
  const vis = (e) => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0 && getComputedStyle(e).visibility !== 'hidden' && !e.closest('[hidden]') && !(e.closest('details:not([open])') && !e.closest('summary')); };
  const list = [...root.querySelectorAll('button, summary, [role=tab], label.filebtn, a.linkbtn, label.check, .chip')]
    .filter((e) => vis(e) && !e.disabled && !e.closest('.rc .num') && !e.matches('[data-audit-skip]'));
  return list.map((e, i) => {
    e.dataset.auditN = i;
    const t = (e.innerText || e.getAttribute('aria-label') || e.title || '').trim().replace(/\d+/g, '#').replace(/\s+/g, ' ').slice(0, 30);
    const inRow = e.closest('.item, .tile, .list > *');
    return { n: i, key: (inRow ? 'row:' : '') + e.tagName + '.' + [...e.classList].filter((c) => c !== 'on').join('.') + ':' + (inRow && e.closest('.list')?.children.length > 3 ? (e.closest('.acts') ? 'act:' + t : 'row') : t) };
  });
}

(async () => {
  fs.rmSync(OUT, { recursive: true, force: true }); fs.mkdirSync(OUT, { recursive: true });
  const browser = await pw.chromium.launch({ channel: 'chrome' }).catch(() => pw.chromium.launch());
  const screens = [], looks = [];
  let shot = 0;
  for (const run of RUNS) {
    const tag = `${run.w}-${run.lang}-${run.theme}`;
    const ctx = await browser.newContext({ viewport: { width: run.w, height: 900 }, acceptDownloads: false });
    let hostPage = null;
    await ctx.route('**/*', serve(() => hostPage));
    hostPage = await ctx.newPage();
    await hostPage.goto(HOST + '/demo/host.html');
    await hostPage.evaluate((l) => localStorage.setItem('lang', l), run.lang);   // the made-up TV names its scenes in the page's language
    await hostPage.reload();
    await hostPage.waitForFunction(() => typeof window.demoApi === 'function');
    await ctx.addInitScript(({ lang, theme }) => {
      if (location.pathname.startsWith('/demo/')) return;
      try { localStorage.setItem('lang', lang); localStorage.setItem('theme', theme); localStorage.setItem('quality', 'snap');
        sessionStorage.setItem('langKeep', JSON.stringify({ picking: false, tab: 'remote', y: 0 })); } catch {}
      window.open = () => null;                                       // links out: not followed
      HTMLInputElement.prototype.showPicker = () => {};
      const click = HTMLInputElement.prototype.click;
      HTMLInputElement.prototype.click = function () { if (this.type !== 'file') click.call(this); };   // no file chooser
    }, run);
    const page = await ctx.newPage();
    const errors = new Set();
    page.on('pageerror', (e) => errors.add(String(e.message || e).slice(0, 200) + ' · ' + String(e.stack || '').split('\n').find((l) => l.includes('/app/'))?.trim().replace(HOST, '')));
    page.on('dialog', (d) => d.dismiss().catch(() => {}));
    ctx.on('page', (p) => { if (p !== page && p !== hostPage) p.close().catch(() => {}); });
    const load = async () => {
      await page.goto(HOST + '/app/');
      await page.waitForFunction(() => typeof ADB === 'object' && ADB.state === 'ready', null, { timeout: 8000 }).catch(() => {});
      await page.evaluate(() => { if (window.pickLeave) pickLeave(); });
      await page.waitForTimeout(800);
    };
    await load();
    const visited = new Set(), pressed = new Set();
    let clicks = 0, until = 0;
    const reset = async (tab) => {
      for (let i = 0; i < 4; i++) {
        const open = await page.evaluate(() => { document.querySelectorAll('.pop').forEach((p) => p.remove());
          const d = [...document.querySelectorAll('dialog[open]')]; d.forEach((x) => x.close()); return d.length; });
        if (!open) break;
      }
      const ok = await page.evaluate((t) => { if (t !== 'header') showTab(t); scrollTo(0, 0); return !document.querySelector('dialog[open]'); }, tab).catch(() => false);
      if (!ok) await load();
    };
    // press [steps] from a fresh tab: each step the n-th control of the screen then on top
    const replay = async (tab, steps) => {
      await reset(tab); await page.waitForTimeout(250);
      for (const n of steps) {
        const st = await page.evaluate(topState, tab);
        await page.evaluate(controls, st.root);
        const h = await page.$(`[data-audit-n="${n}"]`);
        if (!h) return false;
        await h.scrollIntoViewIfNeeded().catch(() => {});
        await press(h);
      }
      return true;
    };
    // a click, then a short wait for what it opens (a dialog animates in 150 ms; the made-up TV answers at once)
    const press = async (h) => {
      await h.click({ timeout: 800, noWaitAfter: true }).catch(() => h.evaluate((e) => e.click()).catch(() => {}));
      await page.waitForTimeout(350);
    };
    const look = async (tab, st, trail) => {
      const res = await page.evaluate(check, { root: st.root, ids: IDS, phone: run.w < 500, lang: run.lang }).catch((e) => ({ faults: [{ kind: 'errors', msg: 'check failed: ' + e.message, el: '', r: null }], looks: [] }));
      const file = `s${++shot}.png`;
      // the faults boxed in red on the picture: drawn in the screen itself (a dialog is above everything else)
      await page.evaluate(({ root, faults }) => {
        const host = document.querySelector(root)?.closest('dialog') || document.body;
        faults.forEach((f, i) => { if (!f.r) return; const d = document.createElement('div'); d.className = 'audit-box';
          Object.assign(d.style, { position: 'fixed', left: f.r.x - 2 + 'px', top: f.r.y - 2 + 'px', width: f.r.w + 4 + 'px', height: f.r.h + 4 + 'px', border: '2px solid #f00', zIndex: 2147483647, pointerEvents: 'none', font: 'bold 11px sans-serif', color: '#fff' });
          const n = document.createElement('span'); n.textContent = i + 1; Object.assign(n.style, { background: '#f00', padding: '0 3px', position: 'absolute', left: '-2px', top: '-14px' }); d.append(n);
          host.append(d); });
      }, { root: st.root, faults: res.faults });
      await page.screenshot({ path: path.join(OUT, file) });
      await page.evaluate(() => document.querySelectorAll('.audit-box').forEach((d) => d.remove()));
      screens.push({ run: tag, name: st.name, tab, trail, file, faults: res.faults });
      res.looks.forEach((l) => looks.push({ ...l, run: tag, screen: screens.length - 1 }));
      process.stdout.write(`${tag} ${st.name.padEnd(50).slice(0, 50)} ${res.faults.length} fault(s) · ${clicks} clicks\n`);
    };
    const crawl = async (tab, steps, trailNames, depth) => {
      const st = await page.evaluate(topState, tab);
      if (visited.has(st.sig)) return;
      visited.add(st.sig);
      await look(tab, st, trailNames);
      if (depth >= DEPTH || st.sig.startsWith('dlg:connecting')) return;          // switching TV: looked at, nothing more in it
      const list = await page.evaluate(controls, st.root);
      let here = true;                                                // still on this screen as crawl found it: no replay needed
      for (const c of list) {
        if (Date.now() > until) { process.stdout.write(`${tag} ${tab}: out of time, the rest not pressed\n`); return; }
        const key = st.sig.split(':').slice(0, 2).join(':') + '>' + c.key;
        if (pressed.has(key)) continue;
        pressed.add(key);
        if (!here && !await replay(tab, steps)) continue;
        here = false;
        const now = await page.evaluate(controls, st.root);
        const h = await page.$(`[data-audit-n="${c.n}"]`);
        if (!h || !now[c.n] || now[c.n].key !== c.key) continue;
        const label = await h.evaluate((e) => (e.innerText || e.getAttribute('aria-label') || e.title || '').trim().slice(0, 30));
        await h.scrollIntoViewIfNeeded().catch(() => {});
        await press(h);
        clicks++;
        const after = await page.evaluate(topState, tab).catch(() => null);
        if (after && after.sig === st.sig && page.url().startsWith(HOST + '/app/')) { here = true; continue; }
        if (after && !visited.has(after.sig)) await crawl(tab, steps.concat(c.n), trailNames.concat(label), depth + 1);
      }
    };
    for (const tab of TABS) {
      if (ONLY && !ONLY.includes(tab)) continue;
      await reset(tab); await page.waitForTimeout(tab === 'health' ? 3000 : 1200);
      until = Date.now() + BUDGET;
      await crawl(tab, [], [tab], 0);
    }
    for (const e of errors) screens.push({ run: tag, name: 'script error', tab: '', trail: [], file: '', faults: [{ kind: 'errors', msg: e, el: '', r: null }] });
    await ctx.close();
  }
  await browser.close();

  // "same": per kind, the looks used by few against the usual one
  const same = [];
  const byKind = new Map(); looks.forEach((l) => { if (!byKind.has(l.kind)) byKind.set(l.kind, []); byKind.get(l.kind).push(l); });
  for (const [k, list] of byKind) {
    const count = new Map(); list.forEach((l) => count.set(l.look, (count.get(l.look) || 0) + 1));
    const [usual, most] = [...count].sort((a, b) => b[1] - a[1])[0];
    for (const [lk, n] of count) if (lk !== usual && n / list.length < 0.05 && n <= 3) {
      const ex = list.find((l) => l.look === lk);
      same.push({ kind: k, look: lk, usual, n, most, ex });
      const sc = screens[ex.screen]; if (sc) sc.faults.push({ kind: 'same', msg: `${k}: ${lk}, where ${most} others are ${usual}`, el: ex.el, text: ex.text, r: ex.r });
    }
  }

  // the report
  const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  const KINDS = ['errors', 'layout', 'contrast', 'rules', 'language', 'touch', 'same'];
  const total = Object.fromEntries(KINDS.map((k) => [k, screens.reduce((n, s) => n + s.faults.filter((f) => f.kind === k).length, 0)]));
  const order = [...screens].sort((a, b) => b.faults.filter((f) => f.kind !== 'same').length - a.faults.filter((f) => f.kind !== 'same').length);
  const html = `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>UI audit</title>
<style>body{font:14px system-ui,sans-serif;margin:0;padding:16px;background:#f4f5f7;color:#16181d}h1{font-size:20px;margin:0 0 6px}
.sum{display:flex;gap:8px;flex-wrap:wrap;margin:10px 0 18px}.sum span{background:#fff;border:1px solid #dde;border-radius:8px;padding:4px 10px}
.sc{background:#fff;border:1px solid #dde;border-radius:12px;padding:12px;margin:0 0 16px;display:grid;grid-template-columns:minmax(0,560px) 1fr;gap:14px}
.sc.ok{display:none}body.all .sc.ok{display:grid}@media(max-width:900px){.sc{grid-template-columns:1fr}}
.sc img{width:100%;border:1px solid #ccd;border-radius:6px;cursor:zoom-in}.sc h2{font-size:15px;margin:0 0 2px}.trail{color:#667;font-size:12px;margin-bottom:8px}
ol{margin:0;padding-left:22px}li{margin:3px 0}b.k{font-size:11px;text-transform:uppercase;border-radius:4px;padding:0 4px;margin-right:4px;color:#fff}
.k.layout{background:#c2410c}.k.contrast{background:#7c3aed}.k.rules{background:#0369a1}.k.language{background:#047857}.k.touch{background:#a16207}.k.same{background:#64748b}.k.errors{background:#b91c1c}
code{font-size:11px;color:#556;word-break:break-all}label{cursor:pointer}</style>
<h1>UI audit · ${new Date().toISOString().slice(0, 16).replace('T', ' ')} · core ${esc(CORE)}</h1>
<div>${screens.length} screens across ${RUNS.map((r) => `${r.w}px ${r.lang} ${r.theme}`).join(', ')} · <label><input type="checkbox" onchange="document.body.classList.toggle('all',this.checked)"> show screens with no fault</label></div>
<div class="sum">${KINDS.map((k) => `<span><b class="k ${k}">${k}</b>${total[k]}</span>`).join('')}</div>
${order.map((s) => `<div class="sc${s.faults.length ? '' : ' ok'}">${s.file ? `<a href="${s.file}" target="_blank"><img loading="lazy" src="${s.file}"></a>` : '<div></div>'}<div><h2>${esc(s.name)}</h2>
<div class="trail">${esc(s.run)} · ${esc(s.trail.join(' → '))}</div><ol>${s.faults.map((f) => `<li><b class="k ${f.kind}">${f.kind}</b>${esc(f.msg)}${f.text ? ` · “${esc(f.text)}”` : ''}<br><code>${esc(f.el)}</code></li>`).join('')}</ol></div></div>`).join('\n')}`;
  fs.writeFileSync(path.join(OUT, 'index.html'), html);
  console.log(KINDS.map((k) => `${k} ${total[k]}`).join(' · '));
  console.log(`${screens.length} screens; report: ${path.join(OUT, 'index.html')}`);
  process.exit(KINDS.filter((k) => k !== 'same').some((k) => total[k]) ? 1 : 0);
})();
