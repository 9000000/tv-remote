// 📌 Pin a link: a pasted link (a YouTube channel / video / playlist / short, any https page, an app's deep link scheme://…) → a card, and what the TV
// opens it with (TV Home's /api route, LinkApps.handlers: app name + logo, "opens in the browser", or nothing) → "Pin to TV" puts it on TV Home's
// grid, "Try on the TV" opens it once. ytParse / linkParse are the rules (tests/home_pin.test.js; core/ontv LinkUrl.kt is the TV side's twin); the dialog is homePin().
// The same box is on TV Home's own page (home.html, hash #pin from its QR), where only Pin shows: that page is the phone's, not a remote.

/** {kind: 'channel'|'video'|'playlist'|'short', url: the canonical https link, id: what to show when YouTube names nothing} or null. */
function ytParse(text) {
  // the link starts the text or follows something no host or address ends with; no lookbehind: a TV box's old WebView (before Chrome 62) or Safari before 16.4 cannot parse one
  const m = String(text || '').match(/(?:^|[^\w.@-])((?:https?:\/\/)?(?:www\.|m\.|music\.)?(?:youtube\.com|youtu\.be)(?:[/?#][^\s]*)?)/i);
  if (!m) return null;
  let u; try { u = new URL(/^https?:/i.test(m[1]) ? m[1] : 'https://' + m[1]); } catch { return null; }
  const host = u.hostname.toLowerCase().replace(/^(www|m|music)\./, ''), path = u.pathname.replace(/\/+$/, ''), q = u.searchParams;
  const vid = (id) => /^[\w-]{6,20}$/.test(id || '') ? { kind: 'video', url: 'https://www.youtube.com/watch?v=' + id, id } : null;
  if (host === 'youtu.be') return vid(path.slice(1).split('/')[0]);
  if (host !== 'youtube.com') return null;
  let p;
  if (path === '/watch') return vid(q.get('v')) || (q.get('list') ? ytList(q.get('list')) : null);
  if (path === '/playlist') return ytList(q.get('list'));
  if ((p = path.match(/^\/shorts\/([\w-]{6,20})/))) return { kind: 'short', url: 'https://www.youtube.com/shorts/' + p[1], id: p[1] };
  if ((p = path.match(/^\/(?:live|embed|v)\/([\w-]{6,20})/))) return vid(p[1]);
  if ((p = path.match(/^\/(@[^/]+)/))) return { kind: 'channel', url: 'https://www.youtube.com/' + p[1], id: decodeURIComponent(p[1]) };
  if ((p = path.match(/^\/(channel\/UC[\w-]+|c\/[^/]+|user\/[^/]+)/))) return { kind: 'channel', url: 'https://www.youtube.com/' + p[1], id: p[1].split('/')[1] };
  return null;
}
function ytList(id) { return /^[\w-]{10,60}$/.test(id || '') ? { kind: 'playlist', url: 'https://www.youtube.com/playlist?list=' + id, id } : null; }

const LINK_BANNED = ['intent', 'file', 'content', 'javascript', 'data', 'about', 'blob', 'vbscript', 'filesystem', 'android-app', 'view-source', 'chrome'];
/**
 * The pasted text as a link TV Home may pin: {url, yt} (yt: ytParse's answer for a YouTube link), {bad: 'scheme'} for a scheme that must never be pinned
 * (intent:, file:, content:, javascript:...: it could reach an app's hidden screens), {bad: 'link'} for anything else. Never throws.
 */
function linkParse(text) {
  const t = String(text || '').trim();
  if (!t) return { bad: '' };
  const yt = ytParse(t);
  if (yt) return { url: yt.url, yt };
  const inText = t.match(/(?:^|\s)([a-z][a-z0-9+.-]{1,30}:\/\/\S+)/i);          // a link inside a sentence ("xem cái này https://… nhé")
  const tok = inText ? inText[1] : t.split(/\s+/)[0];
  const m = tok.match(/^([a-z][a-z0-9+.-]{1,30}):\/\//i);
  if (!m) {
    if (/^(?:[a-z0-9-]+\.)+[a-z]{2,}(?:[/?#]\S*)?$/i.test(tok) && tok === t) return { url: 'https://' + tok };
    return { bad: LINK_BANNED.includes(t.split(':')[0].toLowerCase()) && /^[a-z]+:/i.test(t) ? 'scheme' : 'link' };   // "javascript:alert(1)", "intent:…" have no // but are named
  }
  const scheme = m[1].toLowerCase();
  if (LINK_BANNED.includes(scheme)) return { bad: 'scheme' };
  if (tok.length > 500) return { bad: 'link' };
  if (scheme === 'http' || scheme === 'https') { const host = tok.slice(m[0].length).split(/[/?#]/)[0]; if (!host || host.includes('@')) return { bad: 'link' }; }
  return { url: scheme + tok.slice(scheme.length) };
}

/** What YouTube's oEmbed says about the link ({name, img}); channels have none, so their card shows the handle. Never throws. */
async function ytInfo(yt) {
  if (yt.kind === 'channel') return {};
  try {
    const r = await fetch('https://www.youtube.com/oembed?format=json&url=' + encodeURIComponent(yt.url));
    if (!r.ok) return {};
    const j = await r.json();
    return { name: String(j.title || ''), img: /^https:\/\//.test(j.thumbnail_url || '') ? j.thumbnail_url : '' };
  } catch { return {}; }
}

/** the words of a TV Home answer that is an error ({error: "pin_no_app"}), else '' */
const pinWords = (r) => r && r.error ? ({ pin_bad_link: T('errors.pin_bad_link'), pin_no_app: T('errors.pin_no_app'), pin_failed: T('errors.pin_failed') }[r.error] || T('errors.pin_failed')) : '';
const pinOkOn = (r) => !!r && (!!r.pinOk || !!(r.config && r.config.pinOk));

async function homePin(first) {
  const field = el('input', { type: 'url', inputMode: 'url', spellcheck: false, autocomplete: 'off', autocapitalize: 'off', placeholder: T('pin.placeholder'), 'aria-label': T('pin.placeholder') });
  const card = el('div', { className: 'item pinprev', hidden: true }), warn = el('p', { className: 'warnline', hidden: true });
  const opens = el('div', { className: 'pinopens', hidden: true });                   // what the TV opens it with: apps (a default to pick) / a browser / nothing
  const doPin = el('button', { type: 'button', className: 'primary', textContent: T('pin.pin'), disabled: true });
  const doOpen = el('button', { type: 'button', textContent: T('pin.open'), disabled: true, hidden: homeLink.direct });   // TV Home's own page is a phone: it cannot open anything on the TV
  const kinds = { channel: T('pin.kind_channel'), video: T('pin.kind_video'), playlist: T('pin.kind_playlist'), short: T('pin.kind_short') };
  let cur = null, seq = 0, pick = '';
  const drawOpens = (r) => {                                                             // [r]: TV Home's answer {kind, apps: [{pkg, label, logo}], choice}
    opens.replaceChildren(); opens.hidden = !r;
    if (!r) return;
    if (r.kind === 'none') { opens.append(el('p', { className: 'warnline', textContent: T('pin.none') })); return; }
    const apps = r.apps || [];
    pick = apps.some((a) => a.pkg === r.choice) ? r.choice : (apps[0] && apps[0].pkg) || '';
    const row = (a) => {
      const b = el('button', { type: 'button', className: 'pinapp' + (a.pkg === pick ? ' on' : ''), 'aria-pressed': String(a.pkg === pick), disabled: apps.length < 2 },
        a.logo ? el('img', { src: 'data:image/png;base64,' + a.logo, alt: '', width: 28, height: 28 }) : el('span', { className: 'pinlogo none' }), el('span', { textContent: a.label }));
      b.addEventListener('click', () => { pick = a.pkg; drawOpens(r); });
      return b;
    };
    opens.append(el('small', { textContent: r.kind === 'browser' ? T('pin.in_browser') : apps.length > 1 ? T('pin.pick') : T('pin.opens_with') }), ...apps.map(row));
  };
  const show = async () => {
    const n = ++seq, text = field.value.trim();
    cur = null; doPin.disabled = doOpen.disabled = true; card.hidden = true; drawOpens(null);
    const lk = linkParse(text);
    warn.hidden = !text || !!lk.url; warn.textContent = lk.bad === 'scheme' ? T('pin.bad_scheme') : T('pin.bad');
    if (!lk.url) return;
    const yt = lk.yt, host = (() => { try { return new URL(lk.url).hostname.replace(/^www\./, '') || lk.url; } catch { return lk.url; } })();
    const draw = (info) => {
      card.replaceChildren(info.img ? el('img', { className: 'pinimg', src: info.img, alt: '' }) : el('span', { className: 'pinimg none', textContent: yt ? '▶' : '↗' }),
        el('div', { className: 'name' }, el('span', { textContent: info.name || (yt ? yt.id : host) }), el('small', { textContent: yt ? kinds[yt.kind] : lk.url.length > 60 ? lk.url.slice(0, 57) + '…' : lk.url })));
      card.hidden = false;
    };
    cur = { url: lk.url, yt: !!yt, id: yt ? yt.id : host }; draw({});
    const pre = homeLink.link({ url: lk.url });                                          // asked at once, in step with the YouTube card's own fetch
    const info = yt ? await ytInfo(yt) : {};
    if (n !== seq) return;
    if (yt) { cur = { ...cur, ...info }; draw(info); }
    const r = await pre;
    if (n !== seq) return;
    const known = r && !r.error && r.kind ? r : null; drawOpens(known);
    doPin.disabled = !!known && known.kind === 'none';                                   // nothing on this TV opens it: no pin, no try
    doOpen.disabled = doPin.disabled;
  };
  field.addEventListener('input', show);
  const old = async () => {                                         // a TV Home from before pins: one question, the way the other new features ask
    const r = await homeLink.read(false, true);
    if (r && r.installed && pinOkOn(r)) return false;
    if (r && !r.installed) { toast(T('core.fr_home_missing')); return true; }
    if (homeLink.canUpdate && await ask(T('pin.old_q'), { ok: T('pin.old_ok') })) { const n = await homeLink.update(); if (n && n.installed) toast(T('home.updated', { v: n.version })); }
    else if (!homeLink.canUpdate) toast(T('pin.old_direct'));
    return true;
  };
  doPin.addEventListener('click', async () => {
    if (!cur) return;
    const done = UI.busy(doPin, T('pin.pinning'));
    try {
      if (await old()) return;
      const r = await homeLink.pin({ url: cur.url, name: cur.name || '', img: cur.img || '', pkg: pick });
      if (!r) return;
      const w = pinWords(r); if (w) { toast(w); return; }
      toast(T('pin.done', { name: cur.name || cur.id })); d.close();
    } finally { done(); }
  });
  doOpen.addEventListener('click', async () => {
    if (!cur) return;
    const done = UI.busy(doOpen, T('pin.opening'));
    try { if (await (cur.yt && !pick ? homeLink.open({ url: cur.url }) : homeLink.tryOpen({ url: cur.url, pkg: pick }))) toast(T('pin.opened')); } finally { done(); }
  });
  field.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !doPin.disabled) { e.preventDefault(); doPin.click(); } });
  const d = el('dialog', { className: 'ask pindlg' }, el('div', { className: 'card' },
    el('div', { className: 'card-head' }, el('h2', { textContent: T('pin.title') }),
      el('button', { type: 'button', className: 'small iconbtn', textContent: '✕', title: T('common.close'), 'aria-label': T('common.close'), onclick: () => d.close() })),
    el('p', { className: 'note', textContent: T('pin.note') }), field, warn, card, opens, el('div', { className: 'actions' }, doPin, doOpen)));
  d.addEventListener('close', () => d.remove());
  document.body.append(d); d.showModal();
  if (first) { field.value = first; show(); }
  field.focus();
}

if (typeof module !== 'undefined') module.exports = { ytParse, linkParse };
