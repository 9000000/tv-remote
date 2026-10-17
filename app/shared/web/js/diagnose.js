// "Why can't I reach the TV?" When the TV stays out of reach for a few polls, one quick self-check (Diagnose.kt, /api/diagnose:
// this device's network, the TV's ports, a VPN, the app's ADB link, the TV under a new address) answers with ONE conclusion and
// what to do, in the banner at the top, instead of leaving the person with a red chip and a raw error. The same check backs
// the phone's native screens. Cancel stops waiting; Retry connects again, and a TV that is still out of reach is checked again.
const DIAG_TONE = { up: 'ok', adb_off: 'warn', remote_off: 'warn', allow: 'warn', closed: 'warn', moved: 'warn' };   // the others are 'bad'
let diagKey = '', diagBad = 0, diagCtl = null;

// The TV is not answering as a remote and ADB is not up either (a TV that is asking, or not paired yet, has its own words).
function diagNeeded(s) {
  if (CORE === 'tv' || picking || !s.host || s.connected || s.asking || s.paired === false) return false;
  return !(s.adb && s.adb.state === 'ready');
}

function diagHide() {
  if (diagCtl) { diagCtl.abort(); diagCtl = null; }
  $('#diag').hidden = true; $('#diag').replaceChildren();
}

function diagWatch(s) {
  if (!diagNeeded(s)) { diagBad = 0; if (diagKey) { diagKey = ''; diagHide(); } return; }
  const key = s.host + '|' + (s.kind || 'android');
  if (diagKey === key || ++diagBad < 2) return;                      // one miss is often the TV or this device waking
  diagKey = key; diagRun(s);
}

async function diagRun(s) {
  if (diagCtl) diagCtl.abort();
  const ctl = diagCtl = new AbortController(), box = $('#diag');
  box.hidden = false; box.className = 'banner wait';
  box.replaceChildren(el('span', { className: 'minispin' }), el('span', { textContent: 'Đang kiểm tra mạng và TV…' }),
    el('button', { className: 'small', textContent: 'Hủy', onclick: () => { diagCtl = null; ctl.abort(); box.hidden = true; box.replaceChildren(); } }));
  let r = null;
  try {
    const q = new URLSearchParams({ ip: s.host, kind: s.kind || 'android', name: s.name || '', lang: LANG, err: s.error || '' });
    const x = await fetch('/api/diagnose?' + q, { signal: ctl.signal });
    if (x.ok) r = await x.json();
  } catch {}
  if (ctl.signal.aborted) return;
  diagCtl = null;
  if (!r) { box.hidden = true; box.replaceChildren(); return; }       // the check itself failed: the chips and banners say what they can
  diagShow(r, s);
}

function diagShow(r, s) {
  const box = $('#diag'), pc = CORE === 'pc';
  const again = async () => { diagKey = ''; diagBad = 0; box.hidden = true; box.replaceChildren(); };
  const ACT = {
    retry: () => el('button', { className: 'small primary', textContent: '🔄 Thử lại', onclick: async () => {
      await again();
      if (pc) selectTv(s.host, s.name, s.kind); else { await api('/api/adb/connect', {}, true); refresh(); } } }),
    scan: () => pc ? el('button', { className: 'small', textContent: '🔍 Quét mạng', onclick: () => { again(); wantPick = true; cameFromTv = true; refresh(); $('#scan').click(); } }) : null,
    adbhelp: () => pc ? el('button', { className: 'small', textContent: '🧭 Bật ADB giúp tôi', onclick: () => adbHelp() }) : null,
    use: () => pc && r.found ? el('button', { className: 'small primary', textContent: '📍 Dùng địa chỉ ' + r.found.ip, onclick: () => { again(); selectTv(r.found.ip, r.found.name || s.name, r.kind); } }) : null,
  };
  const acts = r.actions.map((a) => ACT[a] && ACT[a]()).filter(Boolean);
  box.className = 'banner ' + (DIAG_TONE[r.code] || 'bad');
  box.replaceChildren(el('b', { textContent: r.title }), el('p', { textContent: r.fix }),
    el('details', { className: 'checks' }, el('summary', { textContent: 'Đã kiểm tra' }),
      el('ul', {}, ...r.checks.map((c) => el('li', { className: c.ok === true ? 'ok' : c.ok === false ? 'bad' : '', textContent: (c.ok === true ? '✓ ' : c.ok === false ? '✗ ' : '· ') + c.note })))),
    el('div', { className: 'actions' }, ...acts));
  box.hidden = false;
}
