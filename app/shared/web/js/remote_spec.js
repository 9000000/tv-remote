// The remote's keys and menu entries come from remote.json, the same file the Phone app's native remote reads (PhoneRemote.kt).
// This file reads it and holds REMOTE_DO: what each "do" name in the file does here. Kept free of the page's other scripts at
// load time (the arrows reach them only when run), so tests/remote.test.js can load it alone and check every name has a handler.
const REMOTE = (() => {
  try {                                                               // synchronous: the keys are drawn before remote.js binds them
    const x = new XMLHttpRequest(), v = new URL(document.currentScript.src).search;   // ?v=stamp of this script, so an update brings the new file too
    x.open('GET', 'remote.json' + v, false); x.send();
    return JSON.parse(x.responseText);
  } catch { return { rows: {}, menus: {} }; }
})();
// An entry as this page shows it: the "web" fields lay over the common ones.
const remoteEntry = (e) => ({ ...e, ...e.web, sub: (e.web && e.web.sub) || e.sub });
const remoteWords = (e) => (LANG === 'en' ? e.en : e.vi);
const remoteHere = (e) => !e.cores || e.cores.includes('web');   // "cores" says "phone" (the native remote) or "web" (the pages)

// A menu from remote.json as menuDlg wants it. ctx.when: the conditions an entry may name; ctx.expand: the lists that stand in for an
// "expand" entry (each already in menuDlg's form).
function remoteMenu(name, ctx = {}) {
  const out = [];
  for (const raw of REMOTE.menus[name]) {
    if (!remoteHere(raw) || (raw.when && !(ctx.when || {})[raw.when])) continue;
    if (raw.expand) { out.push(...ctx.expand[raw.expand]()); continue; }
    const e = remoteEntry(raw);
    out.push(['@' + e.icon + '|' + remoteWords(e), e.sub ? remoteWords(e.sub) : '', () => REMOTE_DO[e.do](e.arg)]);
  }
  return out;
}

const REMOTE_DO = {
  shot: () => screenshot(),
  open: () => quickOpen(),
  timer: () => timerMenu(),
  scenes: () => scenesMenu(),
  draw: () => { if (drawing) drawStop(); else if (!needAdb()) drawStart(); },
  search: () => askStoreSearch(),
  package: () => askStorePage(),
  link: () => askLink(),
  media: () => castPick('image/*,video/*'),
  file: () => castPick('audio/*,video/*,image/*'),
  store: (id) => storePage(id),
  timerPlus: () => timerPlus(),
  timerCancel: () => setSleep(0),
  timerCustom: () => timerCustom(),
  automation: () => showTab('auto'),
  clip: () => clipDialog(),
  screenOff: () => screenOff(),
};
