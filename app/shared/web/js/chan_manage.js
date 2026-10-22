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
const chmCopy = (c) => JSON.parse(JSON.stringify({ favs: c.favs, groups: c.groups }));
const chmFold = (t) => String(t || '').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[đĐ]/g, 'd').toLowerCase();

async function chanManage() {
  const r = await api('/api/channels/manage?countries=1');
  if (!r) return;
  if (!r.receiver) {                                // a TV Channels from before this: updating keeps its favourites and groups
    if (r.latest && await ask(T('chm.old'), { ok: T('channels.update', { v: r.latest }) })) { await chanInstall(); chanManage(); }
    else if (!r.latest) toast(T('chm.old_none'));
    return;
  }
  chm = { favs: r.favs, groups: r.groups, view: 'find', cc: r.country, countries: r.countries || [], lists: {}, q: '', cat: '', saving: false, dlg: null, sync: null };
  chm.base = chmCopy(chm);
  chmDialog();
  chmLoad(chm.cc, false);
}

// the country's channels (kept for the dialog's life); fresh: read from the network again
async function chmLoad(cc, fresh) {
  const c = chm; if (!c) return;
  if (!fresh && c.lists[cc]) return chmDraw();
  c.loading = cc; chmDraw();
  const r = await api('/api/channels/manage?cc=' + cc + (fresh ? '&fresh=1' : ''), null, true);
  if (chm !== c) return;
  c.loading = '';
  c.lists[cc] = r && r.list ? r.list : { channels: [], empty: true };
  chmDraw();
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
  return n + b.groups.filter((o) => !c.groups.some((g) => g.id === o.id)).length;
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
  c.favs = b.favs; c.groups = b.groups; c.failed = false;
  if (c.view !== 'find' && c.view !== 'fav' && !chmGroup(c.view)) c.view = 'fav';
  chmDraw();
}
async function chmSave(c) {                         // true once the TV holds it; what it answers (it leaves out a channel it cannot find) becomes the state
  if (c.saving) return false;
  c.saving = true; c.failed = false; chmBar();
  const sent = [...c.favs, ...c.groups.flatMap((g) => g.ch)];
  // "from": the countries the channels were picked from (a feed's id can name another country than the list it is in)
  const r = await api('/api/channels/manage', { favs: c.favs.map((x) => x.id), groups: c.groups.map((g) => ({ id: g.id, name: g.name, ch: g.ch.map((x) => x.id) })), from: Object.keys(c.lists) });
  c.saving = false;
  if (!r || !r.receiver) { c.failed = true; chmBar(); return false; }   // the error is shown already (friendly()); the edits stay
  c.favs = r.favs; c.groups = r.groups; c.base = chmCopy(c);
  const kept = new Set([...c.favs, ...c.groups.flatMap((g) => g.ch)].map((x) => x.id));   // what the TV could not find is said, not lost silently
  const lost = [...new Map(sent.filter((x) => !kept.has(x.id)).map((x) => [x.id, x.n])).values()];
  c.sum = lost.length ? { text: T('chm.lost', { n: lost.length, names: lost.slice(0, 5).join(', ') + (lost.length > 5 ? '…' : '') }) } : null;
  if (c.view !== 'find' && c.view !== 'fav' && !chmGroup(c.view)) c.view = 'fav';
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
    { label: T('chm.find'), on: c.view === 'find', fn: () => { c.view = 'find'; chmDraw(); } },
    { label: T('chm.fav_chip', { n: c.favs.length }), on: c.view === 'fav', title: T('chm.favourites'), fn: () => { c.view = 'fav'; chmDraw(); } },
    ...c.groups.map((g) => ({ label: g.name + ' (' + g.ch.length + ')', on: c.view === g.id, fn: () => { c.view = g.id; chmDraw(); } })),
    { label: T('chm.new_group_btn'), fn: async () => { const g = await chmNewGroup(null); if (g) { c.view = g.id; chmDraw(); } } },
  ]);
}
// after an edit in the search list: only the tabs' counts, the bar and the buttons change; the list is not drawn again
function chmRefresh() {
  const c = chm; if (!c.tabs) return chmDraw();
  const t = chmTabs(); c.tabs.replaceWith(t); c.tabs = t;
  if (c.sync) c.sync();
  chmBar();
}
function chmDraw() {
  const c = chm; if (!c || !c.body) return;
  const top = c.card.scrollTop, moved = c.shown !== c.view; c.shown = c.view;
  c.sync = null;
  c.tabs = chmTabs();
  c.body.replaceChildren(c.tabs, ...(c.view === 'find' ? chmFind() : chmMine()), ...chmFile());
  const on = c.body.querySelector('.places .on'); if (on && on.scrollIntoView) on.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  c.card.scrollTop = moved ? 0 : top;
  chmBar();
}

const chmLogo = (ch) => el('span', { className: 'chlogo' }, ch.l ? el('img', { src: ch.l, alt: '', loading: 'lazy', referrerPolicy: 'no-referrer', onerror: (e) => e.target.remove() }) : null);

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
  const sel = el('select', { 'aria-label': T('chm.country'), onchange: () => { c.cc = sel.value; c.cat = ''; chmLoad(c.cc, false); } },
    ...(c.countries.length ? c.countries : [{ cc: c.cc, name: c.cc.toUpperCase(), flag: '' }]).map((k) => el('option', { value: k.cc, textContent: (k.flag ? k.flag + ' ' : '') + k.name, selected: k.cc === c.cc })));
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
  return [UI.line(sel, UI.btn('↻', () => chmLoad(c.cc, true), 'small iconbtn')), q, cats, results, note];
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
