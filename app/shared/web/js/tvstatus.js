// 📺 The saved TVs at a glance (computer app; the phone app's list is native, TvStatus.kt): under each name whether the TV is on, in
// standby or not answering, its model and the app on screen, read in the background while the list shows (FleetStatus.kt, one light
// shell per TV). "Xem màn hình" adds a small picture of each TV that is on, every 5 s: off until asked, it costs the TVs' CPU.
const TVSTAT_EVERY = 10000, TVSHOT_EVERY = 5000;
const tvstat = { rows: {}, timer: 0, shotTimer: 0, shots: false, busy: false, shooting: {}, urls: {}, known: {} };   // known: each TV's last reading, kept when the list is drawn again
try { tvstat.shots = localStorage.getItem('tvshots') === '1'; } catch {}

// called by loadTvs after it drew the list: one status slot (and picture slot) per saved TV, then the reading starts
function tvstatMount(tvs, rows) {
  if (CORE !== 'pc') return;
  Object.values(tvstat.urls).forEach((u) => URL.revokeObjectURL(u)); tvstat.urls = {}; tvstat.rows = {};
  rows.forEach((row, i) => {
    const t = tvs[i], st = el('div', { className: 'tvstat' }), shot = el('div', { className: 'tvshot', textContent: T('tvstat.no_picture') });
    row.prepend(shot); row.querySelector('.name').append(st);
    tvstat.rows[t.host] = { row, st, shot, img: null, last: tvstat.known[t.host] || null };
    tvstatDraw(t.host);
  });
  tvstatShow();
  tvstatGo();
}
function tvstatShow() {
  $('#tvlist').classList.toggle('shots', tvstat.shots);
  $('#tvshots').classList.toggle('on', tvstat.shots); $('#tvshots').setAttribute('aria-pressed', String(tvstat.shots));
}
function tvstatDraw(host) {
  const r = tvstat.rows[host]; if (!r) return;
  const s = r.last, state = s ? s.state : 'unknown';
  const word = { on: 'tvstat.on', standby: 'tvstat.standby', up: 'tvstat.up', off: 'tvstat.off', unknown: 'tvstat.checking' }[state];
  const app = s && s.app ? (s.home ? T('tvstat.home') : T('tvstat.showing', { app: s.app_name })) : '';
  const facts = s ? [s.model, s.android ? 'Android ' + s.android : ''].filter(Boolean).join(' · ') : '';
  const more = s && s.uptime ? [T('tvstat.uptime', { t: fmtUptime(s.uptime) }), s.mem_free >= 0 ? T('tvstat.mem_free', { p: s.mem_free }) : ''].filter(Boolean).join(' · ') : '';
  r.st.className = 'tvstat ' + state + (s && s.age > 45000 ? ' old' : '');
  r.st.title = state === 'off' ? T('tvstat.off_tip') : more;
  r.st.replaceChildren(...[el('span', { className: 'badge' + (state === 'on' ? ' ok' : ''), textContent: T(word) }),
    facts && el('small', { textContent: facts }), app && el('small', { textContent: app })].filter(Boolean));
}
async function tvstatPoll() {
  if (tvstat.busy) return;
  tvstat.busy = true;
  try {
    const r = await api('/api/fleet/status', null, true); if (!r) return;
    for (const s of r.tvs) { const row = tvstat.rows[s.host]; if (row) { row.last = tvstat.known[s.host] = s; tvstatDraw(s.host); } }
    if (tvstat.shots) tvstatShots();
  } finally { tvstat.busy = false; }
}
// a picture of each TV that is on; a TV that answers "nothing now" keeps its last one only while it is on
async function tvstatShots() {
  for (const [host, r] of Object.entries(tvstat.rows)) {
    if (tvstat.shooting[host]) continue;
    if (!r.last || r.last.state !== 'on') { tvstatClear(host); continue; }
    tvstat.shooting[host] = true;
    (async () => {
      try {
        const res = await fetch('/api/fleet/shot?host=' + encodeURIComponent(host)); if (res.status !== 200) return;
        const url = URL.createObjectURL(await res.blob());
        if (!r.img) { r.img = el('img', { alt: '' }); r.shot.replaceChildren(r.img); }
        const old = tvstat.urls[host]; r.img.src = url; tvstat.urls[host] = url; if (old) URL.revokeObjectURL(old);
      } catch {} finally { tvstat.shooting[host] = false; }
    })();
  }
}
function tvstatClear(host) {
  const r = tvstat.rows[host]; if (!r || !r.img) return;
  r.img = null; r.shot.textContent = T('tvstat.no_picture');
  if (tvstat.urls[host]) { URL.revokeObjectURL(tvstat.urls[host]); delete tvstat.urls[host]; }
}
// reads while the list is on screen and the page is in view, nothing otherwise
const tvstatWatching = () => CORE === 'pc' && picking && !document.hidden && Object.keys(tvstat.rows).length > 0;
function tvstatGo() {
  tvstatStop();
  if (!tvstatWatching()) return;
  tvstatPoll();
  tvstat.timer = setInterval(() => { if (tvstatWatching()) tvstatPoll(); else tvstatStop(); }, TVSTAT_EVERY);
  if (tvstat.shots) tvstat.shotTimer = setInterval(() => { if (tvstatWatching()) tvstatShots(); }, TVSHOT_EVERY);
}
function tvstatStop() { clearInterval(tvstat.timer); clearInterval(tvstat.shotTimer); tvstat.timer = tvstat.shotTimer = 0; }
$('#tvshots').addEventListener('click', () => {
  tvstat.shots = !tvstat.shots;
  try { localStorage.setItem('tvshots', tvstat.shots ? '1' : '0'); } catch {}
  if (!tvstat.shots) Object.keys(tvstat.rows).forEach(tvstatClear);
  tvstatShow(); tvstatGo();
});
document.addEventListener('visibilitychange', () => { if (document.hidden) tvstatStop(); else tvstatGo(); });
