// 📺 Manage TV Channels: favourites and groups edited here, not with the D-pad on the TV (/api/channels/manage → TV Channels'
// ChannelsReceiver over ADB, core/ontv/Channels.kt). The page holds the whole state by channel id and sends all of it after
// each change (debounced): the TV keeps the streams of what it has and finds the streams of what is new. The channels to pick
// from are one country's list at a time (a big one is ~200 KB: shown 200 at a time, the search narrows it).
let chm = null;                                     // the open dialog: { favs, groups, view, cc, countries, lists, q, cat, saving, dirty }
onTvChange(() => { chm = null; });

const CHM_SHOW = 200;                               // rows drawn at once: a country can have 2 000 channels
const chmFold = (t) => String(t || '').normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[đĐ]/g, 'd').toLowerCase();

async function chanManage() {
  const r = await api('/api/channels/manage?countries=1');
  if (!r) return;
  if (!r.receiver) {                                // a TV Channels from before this: updating keeps its favourites and groups
    if (r.latest && await ask(T('chm.old'), { ok: T('channels.update', { v: r.latest }) })) { await chanInstall(); chanManage(); }
    else if (!r.latest) toast(T('chm.old_none'));
    return;
  }
  chm = { favs: r.favs, groups: r.groups, view: 'find', cc: r.country, countries: r.countries || [], lists: {}, q: '', cat: '', status: '', dirty: 0, T: null, dlg: null };
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
  const d = el('dialog', { className: 'ask chmask' }, el('div', { className: 'card' },
    el('div', { className: 'card-head' }, el('h2', { textContent: T('chm.title') }),
      el('button', { type: 'button', className: 'small iconbtn', textContent: '✕', title: T('common.close'), 'aria-label': T('common.close'), onclick: () => d.close() })),
    body));
  d.addEventListener('close', () => { const c = chm; d.remove(); if (c && c.dirty) chmSend(c, true); chanCard(); });
  chm.dlg = d; chm.body = body;
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

// saved at once, in one go after a short pause; what the TV answers (it leaves out a channel it cannot find) becomes the state
// again, unless the person changed something meanwhile
function chmChanged() {
  const c = chm; c.dirty++; c.status = 'saving'; chmDraw();
  clearTimeout(c.T); c.T = setTimeout(() => chmSend(c, false), 600);
}
async function chmSend(c, closing) {
  clearTimeout(c.T);
  const n = c.dirty; c.dirty = 0;
  const r = await api('/api/channels/manage', { favs: c.favs.map((x) => x.id), groups: c.groups.map((g) => ({ id: g.id, name: g.name, ch: g.ch.map((x) => x.id) })) });
  if (closing) return;
  if (chm !== c) return;
  if (!r || !r.receiver) { c.dirty += n; c.status = 'failed'; chmDraw(); return; }
  if (!c.dirty) { c.favs = r.favs; c.groups = r.groups; if (c.view !== 'find' && c.view !== 'fav' && !chmGroup(c.view)) c.view = 'fav'; }
  c.status = c.dirty ? 'saving' : 'saved';
  chmDraw();
}

// ---------- drawing
function chmDraw() {
  const c = chm; if (!c || !c.body) return;
  const tabs = UI.chips([
    { label: T('chm.find'), on: c.view === 'find', fn: () => { c.view = 'find'; chmDraw(); } },
    { label: '⭐ ' + c.favs.length, on: c.view === 'fav', title: T('chm.favourites'), fn: () => { c.view = 'fav'; chmDraw(); } },
    ...c.groups.map((g) => ({ label: g.name + ' ' + g.ch.length, on: c.view === g.id, fn: () => { c.view = g.id; chmDraw(); } })),
    { label: T('chm.new_group_btn'), fn: async () => { const g = await chmNewGroup(null); if (g) { c.view = g.id; chmDraw(); } } },
  ]);
  const status = el('p', { className: 'note chmstate' + (c.status === 'failed' ? ' bad' : ''), textContent: T(c.status === 'saving' ? 'chm.saving' : c.status === 'failed' ? 'chm.failed' : c.status === 'saved' ? 'chm.saved' : 'chm.hint') });
  c.body.replaceChildren(tabs, ...(c.view === 'find' ? chmFind() : chmMine()), status);
  const on = c.body.querySelector('.places .on'); if (on && on.scrollIntoView) on.scrollIntoView({ block: 'nearest', inline: 'nearest' });
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
const chmSub = (ch) => [ch.g && ch.g !== 'Undefined' ? ch.g : '', (ch.id.split('@')[0].split('.').pop() || '').length === 2 ? ch.id.split('@')[0].split('.').pop().toUpperCase() : ''].filter(Boolean).join(' · ');

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
    onclick: () => { c.cat = k; drawCats(); drawRows(); } }, k || T('chm.all'), el('b', { textContent: String(n) }))));
  function drawRows() {
    const w = chmFold(c.q).split(/\s+/).filter(Boolean);
    const hit = all.filter((ch) => (!c.cat || ch.g === c.cat) && (!w.length || w.every((x) => (ch.f || (ch.f = chmFold(ch.n))).includes(x))));
    const rows = hit.slice(0, CHM_SHOW).map((ch) => {
      const fav = chmIsFav(ch.id), ing = c.groups.filter((g) => g.ch.some((x) => x.id === ch.id)).length;
      return UI.item({ icon: chmLogo(ch), name: ch.n, sub: ch.g && ch.g !== 'Undefined' ? ch.g : '',
        acts: [el('button', { type: 'button', className: 'small iconbtn' + (fav ? ' on' : ''), textContent: fav ? '★' : '☆', title: T('chm.favourite'), 'aria-label': T('chm.favourite'), 'aria-pressed': String(fav),
            onclick: (e) => { chmToggle('fav', ch); } }),
          el('button', { type: 'button', className: 'small iconbtn' + (ing ? ' on' : ''), textContent: '＋', title: T('chm.to_group'), 'aria-label': T('chm.to_group'), onclick: () => chmGroups(ch) })] });
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

$('#chanmanage').addEventListener('click', chanManage);
