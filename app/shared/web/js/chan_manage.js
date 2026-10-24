// 📺 Manage TV Channels: favourites and groups edited here, not with the D-pad on the TV (/api/channels/manage → TV Channels'
// ChannelsReceiver over ADB, core/ontv/Channels.kt). The page holds the whole state by channel id; edits are local (buttons
// toggle in place, nothing redrawn, the list keeps its scroll), counted in a bar, and Save sends ALL of it in one call: the TV
// keeps the streams of what it has and finds the streams of what is new. The channels to pick
// from are one country's list at a time (a big one is ~200 KB: shown 200 at a time, the search narrows it).
let chm = null;                                     // the open dialog: { favs, groups, base (as last saved), view, cc, countries, lists, q, cat, saving }
onTvChange(() => { chm = null; });

const CHM_SHOW = 200;                               // rows drawn at once: a country can have 2 000 channels
// iptv-org's categories, in the page's language (an unknown one stays as it came)
const CHM_CATS = { animation: 'chm.cat_animation', auto: 'chm.cat_auto', business: 'chm.cat_business', classic: 'chm.cat_classic',
  comedy: 'chm.cat_comedy', cooking: 'chm.cat_cooking', culture: 'chm.cat_culture', documentary: 'chm.cat_documentary',
  education: 'chm.cat_education', entertainment: 'chm.cat_entertainment', family: 'chm.cat_family', general: 'chm.cat_general',
  interactive: 'chm.cat_interactive', kids: 'chm.cat_kids', legislative: 'chm.cat_legislative', lifestyle: 'chm.cat_lifestyle',
  movies: 'chm.cat_movies', music: 'chm.cat_music', news: 'chm.cat_news', outdoor: 'chm.cat_outdoor', public: 'chm.cat_public',
  relax: 'chm.cat_relax', religious: 'chm.cat_religious', science: 'chm.cat_science', series: 'chm.cat_series', shop: 'chm.cat_shop',
  sports: 'chm.cat_sports', travel: 'chm.cat_travel', undefined: 'chm.cat_undefined', weather: 'chm.cat_weather', xxx: 'chm.cat_xxx' };
const chmCat = (g) => String(g || '').split(/\s*[;,]\s*/).filter(Boolean).map((k) => CHM_CATS[k.toLowerCase()] ? T(CHM_CATS[k.toLowerCase()]) : k).join(', ');
const chmCopy = (c) => JSON.parse(JSON.stringify({ favs: c.favs, groups: c.groups, pls: c.pls }));
const chmFold = (t) => String(t || '').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[đĐ]/g, 'd').toLowerCase();

async function chanManage() {
  const r = await api('/api/channels/manage?countries=1');
  if (!r) return;
  if (!r.receiver) {                                // a TV Channels from before this: updating keeps its favourites and groups
    if (r.latest && await ask(T('chm.old'), { ok: T('channels.update', { v: r.latest }) })) { await chanInstall(); chanManage(); }
    else if (!r.latest) toast(T('chm.old_none'));
    return;
  }
  chm = { favs: r.favs, groups: r.groups, pls: r.lists || [], plsOk: Array.isArray(r.lists), view: 'find', cc: r.country, countries: r.countries || [], lists: {}, q: '', cat: '', saving: false, dlg: null, sync: null, adv: false, cats: {}, browse: false };
  chm.base = chmCopy(chm);
  chmDialog();
  chmLoad(chm.cc, false);
  chmCounts(chm);
}

// the country's channels (kept for the dialog's life); fresh: read from the network again
async function chmLoad(cc, fresh) {
  const c = chm; if (!c) return;
  chmCatLoad(c, cc);
  if (!fresh && c.lists[cc]) return chmDraw();
  c.loading = cc; chmDraw();
  const r = await api('/api/channels/manage?cc=' + cc + (fresh ? '&fresh=1' : ''), null, true);
  if (chm !== c) return;
  c.loading = '';
  c.lists[cc] = r && r.list ? r.list : { channels: [], empty: true };
  chmDraw();
}

// A country with a categories file (docs/channels/categories/<cc>.json, js/chancat.js) shows ready-made categories. The page asks its own
// core first (/api/channels/categories?cc=: the site sends no CORS header, so a browser on the TV or phone cannot read it from there),
// then the site itself; no file: the plain list. Which countries have one is the site's data, never the page's.
async function chmCatLoad(c, cc) {
  if (cc in c.cats) return;
  c.cats[cc] = undefined;
  const ok = (d) => d && Array.isArray(d.categories) && d.categories.length ? d : null;
  let doc = ok(await api('/api/channels/categories?cc=' + encodeURIComponent(cc), null, true));
  if (!doc) { try { doc = ok(await (await fetch('https://tvremote.pages.dev/channels/categories/' + encodeURIComponent(cc) + '.json')).json()); } catch (e) { doc = null; } }
  c.cats[cc] = doc;
  if (chm === c) chmDraw();
}

function chmDialog() {
  const body = el('div', { className: 'chm' });
  const c = chm;
  const msg = el('span', { className: 'chmn' });
  const cancel = el('button', { type: 'button', className: 'small', textContent: T('chm.discard'), onclick: () => chmDiscard(c) });
  const save = el('button', { type: 'button', className: 'small primary', textContent: T('chm.save'), onclick: () => chmSave(c) });
  const bar = el('div', { className: 'chmbar', hidden: true }, msg, el('div', { className: 'tools' }, cancel, save));
  const card = el('div', { className: 'card' },
    el('div', { className: 'card-head' }, el('h2', { textContent: T('chm.title') }),
      el('button', { type: 'button', className: 'small iconbtn', textContent: '✕', title: T('common.close'), 'aria-label': T('common.close'), onclick: () => chmClose(c) })),
    body, bar);
  const d = el('dialog', { className: 'ask chmask' }, card);
  d.addEventListener('cancel', (e) => { e.preventDefault(); chmClose(c); });
  d.addEventListener('close', () => { d.remove(); if (chm === c) chm = null; chanCard(); });
  Object.assign(c, { dlg: d, body, card, bar, msg, cancel, save });
  document.body.append(d); d.showModal();
}

// ---------- the state, changed in one place each; every change draws and saves
const chmIsFav = (id) => chm.favs.some((c) => c.id === id);
const chmGroup = (gid) => chm.groups.find((g) => g.id === gid);
const chmList = (view) => view === 'fav' ? chm.favs : (chmGroup(view) || { ch: [] }).ch;
function chmToggle(view, ch) {
  const l = chmList(view), i = l.findIndex((c) => c.id === ch.id);
  if (i >= 0) l.splice(i, 1); else l.push(ch);
  chmChanged();
}
function chmMove(view, i, by) {
  const l = chmList(view), j = i + by;
  if (j < 0 || j >= l.length) return;
  [l[i], l[j]] = [l[j], l[i]];
  chmChanged();
}
async function chmNewGroup(first) {
  const name = await ask(T('chm.new_group'), { value: '' });
  if (!name || !name.trim()) return null;
  const g = { id: Math.random().toString(36).slice(2, 8) + Date.now().toString(36).slice(-3), name: name.trim().slice(0, 40), ch: first ? [first] : [] };
  chm.groups.push(g); chmChanged();
  return g;
}
async function chmRename(g) {
  const name = await ask(T('chm.rename'), { value: g.name });
  if (!name || !name.trim()) return;
  g.name = name.trim().slice(0, 40); chmChanged();
}
async function chmDelete(g) {
  if (!await ask(T('chm.delete_q', { name: g.name }), { ok: T('chm.delete'), danger: true })) return;
  chm.groups = chm.groups.filter((x) => x !== g); chm.view = 'fav'; chmChanged();
}

// ---------- unsaved edits: counted against what the TV holds, saved in one call
const chmSet = (a, b) => a.filter((x) => !b.includes(x)).length + b.filter((x) => !a.includes(x)).length;
const chmIds = (l) => l.map((x) => x.id);
function chmListDiff(a, b) {                       // channels put in or taken out; the same ones in another order count as one
  const x = chmIds(a), y = chmIds(b), n = chmSet(x, y);
  return n || x.some((id, i) => id !== y[i]) ? Math.max(n, 1) : 0;
}
function chmPending() {
  const c = chm, b = c.base;
  let n = chmListDiff(b.favs, c.favs);
  for (const g of c.groups) {
    const o = b.groups.find((x) => x.id === g.id);
    n += o ? chmListDiff(o.ch, g.ch) + (o.name !== g.name ? 1 : 0) : 1 + g.ch.length;
  }
  n += b.groups.filter((o) => !c.groups.some((g) => g.id === o.id)).length;
  const key = (l) => l.url + '\n' + l.name;           // a renamed link counts as one change
  return n + chmSet(b.pls.map(key), c.pls.map(key));
}
function chmChanged() { if (chm.view === 'find') chmRefresh(); else chmDraw(); }
function chmBar() {
  const c = chm, n = chmPending();
  c.bar.hidden = !n && !c.saving;
  put(c.msg, 'textContent', c.saving ? T('chm.saving') : c.failed ? T('chm.failed') : T('chm.changes', { n }));
  c.bar.classList.toggle('bad', !!c.failed && !c.saving);
  c.cancel.disabled = c.save.disabled = c.saving;
  c.body.inert = c.saving;
}
function chmDiscard(c) {
  const b = JSON.parse(JSON.stringify(c.base));
  c.favs = b.favs; c.groups = b.groups; c.pls = b.pls; c.failed = false;
  if (c.view !== 'find' && c.view !== 'fav' && c.view !== 'lists' && !chmGroup(c.view)) c.view = 'fav';
  chmDraw();
}
async function chmSave(c) {                         // true once the TV holds it; what it answers (it leaves out a channel it cannot find) becomes the state
  if (c.saving) return false;
  c.saving = true; c.failed = false; chmBar();
  const sent = [...c.favs, ...c.groups.flatMap((g) => g.ch)];
  // "from": the countries the channels were picked from (a feed's id can name another country than the list it is in)
  const r = await api('/api/channels/manage', { favs: c.favs.map((x) => x.id), groups: c.groups.map((g) => ({ id: g.id, name: g.name, ch: g.ch.map((x) => x.id) })), from: Object.keys(c.lists), lists: c.pls.map((x) => ({ name: x.name, url: x.url })) });
  c.saving = false;
  if (!r || !r.receiver) { c.failed = true; chmBar(); return false; }   // the error is shown already (friendly()); the edits stay
  c.favs = r.favs; c.groups = r.groups; if (Array.isArray(r.lists)) c.pls = r.lists; c.base = chmCopy(c);
  const kept = new Set([...c.favs, ...c.groups.flatMap((g) => g.ch)].map((x) => x.id));   // what the TV could not find is said, not lost silently
  const lost = [...new Map(sent.filter((x) => !kept.has(x.id)).map((x) => [x.id, x.n])).values()];
  c.sum = lost.length ? { text: T('chm.lost', { n: lost.length, names: lost.slice(0, 5).join(', ') + (lost.length > 5 ? '…' : '') }) } : null;
  if (c.view !== 'find' && c.view !== 'fav' && c.view !== 'lists' && !chmGroup(c.view)) c.view = 'fav';
  if (chm === c) { toast(T('chm.saved')); chmDraw(); }
  return true;
}
function chmClose(c) {
  if (c.saving) return;
  if (!chmPending()) { c.dlg.close(); return; }
  menuDlg(T('chm.keep_q'), 0, [
    [T('chm.keep_save'), '', async () => { if (await chmSave(c)) c.dlg.close(); }],
    [T('chm.keep_drop'), '', () => c.dlg.close()],
    [T('chm.keep_stay'), '', () => {}],
  ], -1, true);                                     // a question of its own, not a step after a menu: no Back
}

// ---------- drawing
function chmTabs() {
  const c = chm;
  return UI.chips([
    ...(c.cats[c.cc] ? [{ label: T('chm.sec_back'), fn: () => { c.adv = false; c.view = 'find'; chmDraw(); } }] : []),
    { label: T('chm.find'), on: c.view === 'find', fn: () => { c.view = 'find'; c.adv = true; chmDraw(); } },
    { label: T('chm.fav_chip', { n: c.favs.length }), on: c.view === 'fav', title: T('chm.favourites'), fn: () => { c.view = 'fav'; chmDraw(); } },
    ...c.groups.map((g) => ({ label: g.name + ' (' + g.ch.length + ')', on: c.view === g.id, fn: () => { c.view = g.id; chmDraw(); } })),
    ...(c.plsOk ? [{ label: T('chm.lists_chip', { n: c.pls.length }), on: c.view === 'lists', title: T('chm.lists'), fn: () => { c.view = 'lists'; chmDraw(); } }] : []),
    { label: T('chm.new_group_btn'), fn: async () => { const g = await chmNewGroup(null); if (g) { c.view = g.id; chmDraw(); } } },
  ]);
}
// after an edit in the search list: only the tabs' counts, the bar and the buttons change; the list is not drawn again
function chmRefresh() {
  const c = chm; if (c.browse) { if (c.sync) c.sync(); chmBar(); return; }
  if (!c.tabs) return chmDraw();
  const t = chmTabs(); c.tabs.replaceWith(t); c.tabs = t;
  if (c.sync) c.sync();
  chmBar();
}
function chmDraw() {
  const c = chm; if (!c || !c.body) return;
  const top = c.card.scrollTop, moved = c.shown !== c.view; c.shown = c.view;
  c.sync = null;
  const doc = c.cats[c.cc], home = c.view === 'find' && !c.adv && doc !== null;   // null: no file for this country: the plain list
  const wait = home && (doc === undefined || !c.lists[c.cc]);
  c.browse = home && !wait && !c.lists[c.cc].empty;
  c.tabs = c.browse || wait ? null : chmTabs();
  if (c.browse) c.body.replaceChildren(...chmSecs());
  else if (wait) c.body.replaceChildren(chmSecTop(), UI.state(T('chm.loading')));
  else c.body.replaceChildren(c.tabs, ...(c.view === 'find' ? chmFind() : c.view === 'lists' ? chmLists() : chmMine()), ...chmFile());
  const on = c.body.querySelector('.places .on'); if (on && on.scrollIntoView) on.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  c.card.scrollTop = moved ? 0 : top;
  chmBar();
}

const chmLogo = (ch) => el('span', { className: 'chlogo' }, ch.l ? el('img', { src: ch.l, alt: '', loading: 'lazy', referrerPolicy: 'no-referrer', onerror: (e) => e.target.remove() }) : null);

// the person's own m3u links (max 5): the TV reads each one itself and shows it as a row of its own; a link is shown by its host
// only (it may carry a token). Edits are local like the rest and go with Save.
const CHM_MAX_LISTS = 5;
const chmHost = (u) => { try { return new URL(u).host; } catch (e) { return ''; } };
const chmUrlOk = (u) => /^https?:\/\/[^\s]+$/i.test(u) && u.length <= 500 && !!chmHost(u);
function chmAddList(name, url) {
  const c = chm, u = url.trim();
  if (!chmUrlOk(u)) { toast(T('chm.list_bad')); return false; }
  if (c.pls.some((x) => x.url === u)) { toast(T('chm.list_dup')); return false; }
  if (c.pls.length >= CHM_MAX_LISTS) { toast(T('chm.list_max', { n: CHM_MAX_LISTS })); return false; }
  c.pls.push({ id: '', name: name.trim().slice(0, 40) || chmHost(u).replace(/^www\./, ''), url: u });
  chmDraw();
  return true;
}
function chmLists() {
  const c = chm;
  const name = el('input', { type: 'text', autocomplete: 'off', spellcheck: false, maxLength: 40, placeholder: T('chm.list_name') });
  const url = el('input', { type: 'url', autocomplete: 'off', spellcheck: false, placeholder: T('chm.list_url') });
  const add = () => chmAddList(name.value, url.value);
  url.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); add(); } });
  const rows = c.pls.map((l, i) => UI.item({ icon: el('span', { className: 'chlogo', textContent: '🔗' }), name: l.name, sub: chmHost(l.url),
    acts: [el('button', { type: 'button', className: 'small iconbtn', textContent: '✕', title: T('chm.remove'), 'aria-label': T('chm.remove'),
      onclick: () => { c.pls.splice(i, 1); chmDraw(); } })] }));
  return [el('div', { className: 'card-head' }, el('h3', { textContent: T('chm.lists') })), UI.note(T('chm.lists_note', { n: CHM_MAX_LISTS })),
    UI.list(rows, T('chm.lists_none')), name, UI.line(url, UI.btn(T('chm.list_add'), add, 'primary'))];
}

// a favourites list or one group: in order, with up / down / take out
function chmMine() {
  const c = chm, fav = c.view === 'fav', g = fav ? null : chmGroup(c.view), l = chmList(c.view);
  const add = el('button', { type: 'button', className: 'small', textContent: T('chm.add'), onclick: () => { c.view = 'find'; chmDraw(); } });
  const head = el('div', { className: 'card-head' }, el('h3', { textContent: fav ? T('chm.favourites') : g.name }),
    el('div', { className: 'tools' }, ...(fav ? [add] : [
      el('button', { type: 'button', className: 'small', textContent: '✎ ' + T('chm.rename_btn'), onclick: () => chmRename(g) }),
      el('button', { type: 'button', className: 'small danger', textContent: '🗑 ' + T('chm.delete'), onclick: () => chmDelete(g) }), add])));
  const rows = l.map((ch, i) => UI.item({ icon: chmLogo(ch), name: ch.n, sub: chmSub(ch),
    acts: [el('button', { type: 'button', className: 'small iconbtn', textContent: '↑', title: T('chm.up'), 'aria-label': T('chm.up'), disabled: i === 0, onclick: () => chmMove(c.view, i, -1) }),
      el('button', { type: 'button', className: 'small iconbtn', textContent: '↓', title: T('chm.down'), 'aria-label': T('chm.down'), disabled: i === l.length - 1, onclick: () => chmMove(c.view, i, 1) }),
      el('button', { type: 'button', className: 'small iconbtn', textContent: '✕', title: T('chm.remove'), 'aria-label': T('chm.remove'), onclick: () => chmToggle(c.view, ch) })] }));
  return [head, UI.list(rows, T(fav ? 'chm.no_favs' : 'chm.no_group'))];
}
const chmSub = (ch) => [ch.g && ch.g.toLowerCase() !== 'undefined' ? chmCat(ch.g) : '', (ch.id.split('@')[0].split('.').pop() || '').length === 2 ? ch.id.split('@')[0].split('.').pop().toUpperCase() : ''].filter(Boolean).join(' · ');

// all channels of a country: search, a category, ★ to favour, ＋ to put in a group
function chmFind() {
  const c = chm, list = c.lists[c.cc];
  const sel = chmCountryBtn(c);
  const q = el('input', { type: 'search', value: c.q, autocomplete: 'off', spellcheck: false, placeholder: T('chm.search'), oninput: () => { c.q = q.value; drawRows(); } });
  const cats = el('div', { className: 'fchips chmcats' });
  const results = el('div', {}), note = el('p', { className: 'note' });
  const all = list ? list.channels : [];
  const kinds = [...all.reduce((m, ch) => m.set(ch.g, (m.get(ch.g) || 0) + 1), new Map())].sort((a, b) => b[1] - a[1]);
  const drawCats = () => cats.replaceChildren(...[['', all.length], ...kinds].map(([k, n]) => el('button', { type: 'button', className: c.cat === k ? 'on' : '',
    onclick: () => { c.cat = k; drawCats(); drawRows(); } }, k ? chmCat(k) : T('chm.all'), el('b', { textContent: '(' + n + ')' }))));
  let live = [];                                    // the rows on screen: their buttons follow the state without drawing again
  const mark = (r) => {
    const fav = chmIsFav(r.ch.id), ing = c.groups.some((g) => g.ch.some((x) => x.id === r.ch.id));
    r.fav.textContent = fav ? '★' : '☆'; r.fav.classList.toggle('on', fav); r.fav.setAttribute('aria-pressed', String(fav));
    r.grp.classList.toggle('on', ing);
  };
  c.sync = () => live.forEach(mark);
  function drawRows() {
    const w = chmFold(c.q).split(/\s+/).filter(Boolean);
    const hit = all.filter((ch) => (!c.cat || ch.g === c.cat) && (!w.length || w.every((x) => (ch.f || (ch.f = chmFold(ch.n))).includes(x))));
    live = [];
    const rows = hit.slice(0, CHM_SHOW).map((ch) => {
      const fav = el('button', { type: 'button', className: 'small iconbtn', title: T('chm.favourite'), 'aria-label': T('chm.favourite'), onclick: () => chmToggle('fav', ch) });
      const grp = el('button', { type: 'button', className: 'small iconbtn', textContent: '＋', title: T('chm.to_group'), 'aria-label': T('chm.to_group'), onclick: () => chmGroups(ch) });
      const r = { ch, fav, grp }; mark(r); live.push(r);
      return UI.item({ icon: chmLogo(ch), name: ch.n, sub: ch.g && ch.g.toLowerCase() !== 'undefined' ? chmCat(ch.g) : '', acts: [fav, grp] });
    });
    results.replaceChildren(UI.list(rows, T('chm.none')));
    put(note, 'textContent', hit.length > CHM_SHOW ? T('chm.more', { shown: CHM_SHOW, n: hit.length }) : '');
    note.hidden = !note.textContent;
  }
  if (c.loading === c.cc || !list) return [UI.line(sel), UI.state(T('chm.loading'))];
  if (list.empty) return [UI.line(sel), UI.warn(T('chm.empty')), UI.actions(UI.btn(T('chm.reload'), () => chmLoad(c.cc, true), 'primary'))];
  drawCats(); drawRows();
  return [UI.line(sel, Object.assign(UI.btn('↻', () => chmLoad(c.cc, true), 'small iconbtn'), { title: T('chm.reload').replace(/^\S+\s*/, ''), 'aria-label': T('chm.reload').replace(/^\S+\s*/, '') })), q, cats, results, note];
}

// the country now, as a button that opens the picker (a <select> of 250 had no search)
function chmCountryBtn(c) {
  const k = c.countries.find((x) => x.cc === c.cc) || { cc: c.cc, name: c.cc.toUpperCase(), flag: '' };
  const n = chmN(c, k.cc);
  return c.ccBtn = el('button', { type: 'button', className: 'chmcc', title: T('chm.country'), 'aria-label': T('chm.country'),
    onclick: () => chmPickCountry(c) },
    el('span', { textContent: (k.flag ? k.flag + ' ' : '') + k.name }), n ? el('b', { textContent: String(n) }) : null, el('i', { textContent: '▾' }));
}

async function chmPickCountry(c) {
  const cc = await chmCountry(c);
  if (cc && cc !== c.cc && chm === c) { c.cc = cc; c.cat = ''; c.adv = false; if (c.view === 'find') chmLoad(cc, false); else { c.view = 'find'; chmLoad(cc, false); } }
}

// ---------- sections of channel cards, one swipe row each. Favourites, then the person's groups, then the file's categories,
// then the rest. A card plays the channel (the TV finds it by name); its star favours it in place (only the favourites row is drawn
// again). The tools of the plain view (search, groups, lists, country) are behind one ⋯.
const CHM_SEC_SHOW = 40;                             // cards drawn in a row at first; "+n" draws the rest of that row
function chmSecTop() {
  const c = chm, k = c.countries.find((x) => x.cc === c.cc) || { name: c.cc.toUpperCase(), flag: '' };
  return el('div', { className: 'chtop' }, el('span', { className: 'chcc', textContent: (k.flag ? k.flag + ' ' : '') + k.name }),
    el('button', { type: 'button', className: 'small iconbtn', textContent: '⋯', title: T('chm.tools'), 'aria-label': T('chm.tools'), onclick: () => chmSecMenu(c) }));
}
function chmSecMenu(c) {
  const adv = (view) => { c.adv = true; c.view = view; chmDraw(); };
  menuDlg(T('chm.tools'), -1, [
    [T('chm.sec_all'), '', () => adv('find')],
    [T('chm.sec_mine'), '', () => adv('fav')],
    [T('chm.new_group_btn'), '', async () => { const g = await chmNewGroup(null); if (g) adv(g.id); }],
    ...(c.plsOk ? [[T('chm.lists'), '', () => adv('lists')]] : []),
    [T('chm.country'), '', () => chmPickCountry(c)],
  ]);
}
function chmSecs() {
  const c = chm, list = c.lists[c.cc], doc = c.cats[c.cc];
  const cat = c.secs && c.secs.src === list ? c.secs : (c.secs = Object.assign(ChanCat.categorize(list.channels, doc), { src: list }));
  const marks = [];                                 // the stars of the rows that stay: they follow the favourites without drawing again
  const star = (ch, into) => {
    const b = el('button', { type: 'button', className: 'chstar', title: T('chm.favourite'), 'aria-label': T('chm.favourite'), onclick: () => chmToggle('fav', ch) });
    into.push({ ch, b }); return b;
  };
  const paint = (r) => { const f = chmIsFav(r.ch.id); put(r.b, 'textContent', f ? '★' : '☆'); r.b.classList.toggle('on', f); r.b.setAttribute('aria-pressed', String(f)); };
  const card = (ch, into) => { const b = star(ch, into); paint(into[into.length - 1]); return el('div', { className: 'chcard' },
    el('button', { type: 'button', className: 'chplay', title: ch.n, onclick: () => chanOpen(ch.n) }, chmLogo(ch), el('span', { className: 'chn', textContent: ch.n })), b); };
  const fill = (row, list, into, all) => {
    const upto = all ? list.length : CHM_SEC_SHOW, frag = document.createDocumentFragment();
    list.slice(0, upto).forEach((ch) => frag.append(card(ch, into)));
    if (list.length > upto) frag.append(el('button', { type: 'button', className: 'chcard chmore', textContent: T('chm.sec_more_n', { n: list.length - upto }),
      onclick: () => { row.replaceChildren(); fill(row, list, into, true); } }));
    row.replaceChildren(frag);
  };
  const section = (title, list, into, empty) => {
    const row = el('div', { className: 'chrow' });
    if (list.length) fill(row, list, into, false);
    return [el('section', { className: 'chsec' }, el('h3', {}, title, el('small', { textContent: String(list.length) })), list.length ? row : UI.note(empty))];
  };
  const out = [chmSecTop()];
  const favSec = () => section(T('chm.favourites'), c.favs, [], T('chm.sec_fav_empty'))[0];   // its stars are drawn from the state each time: no marks kept
  let favNode = favSec(); out.push(favNode);
  for (const g of c.groups) if (g.ch.length) out.push(section(g.name, g.ch, marks, '')[0]);
  for (const k of cat.cats) out.push(section(k[window.LANG] || k.en || k.id, k.list, marks, '')[0]);
  if (cat.other.length) out.push(section(T('chm.sec_others'), cat.other, marks, '')[0]);
  c.sync = () => {
    marks.forEach(paint);
    const n = favSec(); favNode.replaceWith(n); favNode = n;
  };
  return out;
}

// a country's number: its list once loaded (with the providers' channels too, e.g. VieON's for Vietnam), else iptv-org's count
const chmN = (c, cc) => (c.lists[cc] && !c.lists[cc].empty ? c.lists[cc].channels.length : c.counts && c.counts[cc]) || 0;

// the channels per country, asked once a dialog: the button gets its number in place (the list below keeps its scroll)
function chmCounts(c) {
  return c.counting || (c.counting = api('/api/channels/counts', null, true).then((r) => {
    if (!r || !r.counts || !Object.keys(r.counts).length) { c.counting = null; return; }
    c.counts = r.counts;
    if (c.ccBtn && c.ccBtn.isConnected) c.ccBtn.replaceWith(chmCountryBtn(c));
  }));
}

// every country, searched by name or code, sorted by name or by how many channels it has (counted from iptv-org's list by the
// server: /api/channels/counts; the numbers come in a moment after the list, and a country without one goes last)
function chmCountry(c) {
  return new Promise((done) => {
    let picked = null;
    const q = el('input', { type: 'search', placeholder: T('chm.find_country'), spellcheck: false, autocomplete: 'off' });
    const none = el('p', { className: 'empty', textContent: T('chm.no_country'), hidden: true });
    const box = el('div', { className: 'list chmccs' });
    const rows = (c.countries.length ? c.countries : [{ cc: c.cc, name: c.cc.toUpperCase(), flag: '' }]).map((k) => {
      const n = el('b');
      const r = el('button', { type: 'button', className: 'item pick' + (k.cc === c.cc ? ' on' : ''), onclick: () => { picked = k.cc; d.close(); } },
        el('span', { className: 'flag', textContent: k.flag || '' }), el('div', { className: 'name' }, el('span', { textContent: k.name }), el('small', { textContent: k.cc.toUpperCase() })), n);
      r.k = k; r.f = chmFold(k.name) + ' ' + k.cc; r.n = n;
      return r;
    });
    const sorts = el('div', { className: 'fchips' });
    const draw = () => {
      const by = c.ccSort === 'n' && c.counts ? 'n' : 'a';
      sorts.replaceChildren(...[['a', T('chm.sort_name')], ['n', T('chm.sort_count')]].map(([v, w]) => el('button', { type: 'button', className: by === v ? 'on' : '',
        'aria-pressed': String(by === v), disabled: v === 'n' && !c.counts, onclick: () => { c.ccSort = v; draw(); } }, w)));
      rows.forEach((r) => { const n = chmN(c, r.k.cc); put(r.n, 'textContent', n ? String(n) : ''); });
      const cnt = (r) => chmN(c, r.k.cc) || 0;
      rows.sort(by === 'n' ? (x, y) => cnt(y) - cnt(x) || x.k.name.localeCompare(y.k.name) : (x, y) => x.k.name.localeCompare(y.k.name));
      box.replaceChildren(...rows);
      filter();
    };
    const filter = () => {
      const w = chmFold(q.value.trim()).split(/\s+/).filter(Boolean); let n = 0;
      rows.forEach((r) => { r.hidden = !w.every((x) => r.f.includes(x)); if (!r.hidden) n++; });
      none.hidden = n > 0;
    };
    q.addEventListener('input', filter);
    q.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); const r = rows.find((x) => !x.hidden); if (r) r.click(); } });   // the first one left
    const d = el('dialog', { className: 'ask chmask chmpick' }, el('div', { className: 'card' },
      UI.head(T('chm.pick_country'), el('button', { type: 'button', className: 'small iconbtn', textContent: '✕', title: T('common.close'), 'aria-label': T('common.close'), onclick: () => d.close() })),
      q, sorts, box, none));
    d.addEventListener('close', () => { d.remove(); done(picked); });
    draw();
    document.body.append(d); d.showModal();
    const on = rows.find((r) => r.k.cc === c.cc); if (on) on.scrollIntoView({ block: 'center' });
    if (matchMedia('(pointer: fine)').matches) q.focus(); else (on || q).focus();   // a phone: no keyboard over half the list
    if (!c.counts) chmCounts(c).then(() => { if (d.open) draw(); });
  });
}

// the groups a channel is in: a tap puts it in or takes it out; a new group starts with it
function chmGroups(ch) {
  const c = chm;
  menuDlg(ch.n, -1, [
    ...c.groups.map((g) => [(g.ch.some((x) => x.id === ch.id) ? '✓ ' : '') + g.name, '', () => chmToggle(g.id, ch)]),
    [T('chm.new_group_btn'), T('chm.new_group_sub'), () => chmNewGroup(ch)],
  ]);
}

// ---------- a file of the favourites and groups: "Export" is a download from the server (like the scenes backup); "Import" reads the
// file here, asks merge or replace, and the TV checks it again (ids first, then names) and says what it found and what not
function chmFile() {
  const c = chm;
  const pick = el('input', { type: 'file', accept: '.json,application/json', hidden: true, onchange: () => { const f = pick.files[0]; pick.value = ''; if (f) chmImport(f); } });
  const out = [UI.actions(
    UI.btn(T('chm.export'), async () => {
      if (chmPending() && !await chmSave(c)) return;                // the file is what the TV holds: save first
      const a = el('a', { href: 'api/channels/export', download: '' }); document.body.append(a); a.click(); a.remove();
    }, 'small'),
    UI.btn(T('chm.import'), () => pick.click(), 'small'), pick)];
  if (c.sum) out.push(UI.note(c.sum.text), ...(c.sum.missing ? [UI.note(c.sum.missing)] : []));
  return out;
}
async function chmImport(f) {
  const c = chm; if (!c) return;
  let doc;
  try { doc = JSON.parse(await f.text()); } catch (e) { doc = null; }
  const n = doc && ((Array.isArray(doc.favourites) ? doc.favourites.length : 0) + (Array.isArray(doc.groups) ? doc.groups.length : 0));
  if (!doc || typeof doc !== 'object' || Array.isArray(doc) || !n) { toast(T('chm.imp_bad')); return; }
  menuDlg(T('chm.imp_title'), 0, [
    [T('chm.imp_merge'), T('chm.imp_merge_sub'), () => chmRun(c, doc, 'merge')],
    [T('chm.imp_replace'), T('chm.imp_replace_sub'), async () => { if (await ask(T('chm.imp_replace_q'), { ok: T('chm.imp_replace'), danger: true })) chmRun(c, doc, 'replace'); }],
  ]);
}
async function chmRun(c, doc, mode) {
  if (chmPending() && !await chmSave(c)) return;
  c.sum = { text: T('chm.imp_working') }; chmDraw();
  const r = await api('/api/channels/import', { mode, doc });
  if (chm !== c) return;
  const i = r && r.imported;
  if (!r || !r.receiver || !i || !i.ok) { c.sum = r && r.imported ? { text: T('chm.imp_bad') } : null; if (!r || !r.receiver) toast(T('chm.failed')); chmDraw(); return; }
  c.favs = r.favs; c.groups = r.groups; c.base = chmCopy(c); c.view = 'fav';
  c.sum = { text: T('chm.imp_done', { g: i.groups, n: i.added }) + (i.skipped ? ' ' + T('chm.imp_skipped', { n: i.skipped }) : ''),
    missing: i.missing_n ? T('chm.imp_missing', { n: i.missing_n, names: i.missing.join(', ') + (i.missing_n > i.missing.length ? '…' : '') }) : '' };
  chmDraw();
}

$('#chanmanage').addEventListener('click', chanManage);
