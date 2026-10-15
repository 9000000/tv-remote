// 📺 Several TVs (computer only, app/web/fleet.py): every saved TV in one list to compare, the ones ticked get the same APK,
// lose the same app, or restart, each with its own progress line. The card sits under the saved TVs on the picker, once there are two.
let fleetTvs = [], fleetPick = new Set(), fleetRow = {};
const FLEET_STEP = { waiting: 'đang chờ lượt', connecting: 'đang kết nối', 'installing on TV': 'đang cài', 'checking the TV kept it': 'đang kiểm tra TV giữ app',
  installed: '✓ đã cài', uninstalled: '✓ đã gỡ', restarting: '✓ đang khởi động lại', done: '✓ xong' };
const FLEET_ADB = { closed: 'TV tắt, khác mạng hoặc chưa bật ADB', unauthorized: 'TV đang hỏi “Cho phép gỡ lỗi USB?”: bấm Cho phép trên TV',
  offline: 'ADB của TV đang offline: tắt bật lại TV', error: 'không kết nối được ADB' };

function fleetCheck(n) {                                              // called by loadTvs with the number of saved TVs
  $('#fleet').hidden = n < 2; $('#fleetcount').textContent = n ? '· ' + n + ' TV' : '';
  if (n >= 2 && $('#fleet').open) fleetLoad();
}
async function fleetLoad() {
  $('#fleetlist').replaceChildren(el('div', { className: 'muted', textContent: 'Đang hỏi từng TV...' }));
  const r = await api('/api/fleet'); if (!r) return;
  fleetTvs = r.tvs;
  if (!fleetPick.size) fleetTvs.forEach((t) => { if (t.state === 'ready') fleetPick.add(t.host); });   // the ones that can do it, to start with
  fleetDraw();
}
function fleetDraw() {
  fleetRow = {};
  $('#fleetlist').replaceChildren(...fleetTvs.map((t) => {
    const box = el('input', { type: 'checkbox', checked: fleetPick.has(t.host), onchange: (e) => { if (e.target.checked) fleetPick.add(t.host); else fleetPick.delete(t.host); } });
    const facts = t.state !== 'ready' ? FLEET_ADB[t.state] || t.state
      : [t.model, t.android ? 'Android ' + t.android : '', t.disk_total ? 'trống ' + fmtSize(t.disk_free) + ' / ' + fmtSize(t.disk_total) : '',
        t.ram ? 'RAM ' + fmtSize(t.ram) : '', t.setup ? 'TV Setup ' + t.setup : ''].filter(Boolean).join(' · ');
    const step = el('small', { className: 'fleetstep' });
    fleetRow[t.host] = step;
    return el('label', { className: 'fleetrow' + (t.state === 'ready' ? '' : ' off') }, box,
      el('div', { className: 'name' }, el('b', { textContent: (t.name || 'TV') + (t.host === STATE.host ? ' · đang dùng' : '') }),
        el('small', { textContent: t.host + ' · ' + facts }), step));
  }));
}
$('#fleet').addEventListener('toggle', (e) => { if (e.target.open) fleetLoad(); });

const fleetChosen = () => fleetTvs.filter((t) => fleetPick.has(t.host));
async function fleetAsk(what) {
  const tvs = fleetChosen();
  if (!tvs.length) { toast('Chọn ít nhất một TV'); return null; }
  return await ask(tr(what) + '\n\n' + tvs.map((t) => '• ' + (t.name || t.host)).join('\n')) ? tvs : null;
}
// one line per TV under its name, until every TV is done
async function fleetFollow(j) {
  while (j && j.tvs) {
    for (const [host, p] of Object.entries(j.tvs)) {
      const s = fleetRow[host]; if (!s) continue;
      s.className = 'fleetstep ' + p.status;
      s.textContent = p.status === 'error' ? '✗ ' + (/^adb:/.test(p.msg) ? FLEET_ADB[p.msg.slice(4)] || p.msg : friendly(p.msg))
        : (FLEET_STEP[p.msg] || p.msg) + (p.status === 'running' && p.pct ? ' ' + p.pct + '%' : '');
    }
    if (j.status === 'done') {
      const parts = Object.values(j.tvs), bad = parts.filter((p) => p.status === 'error').length;
      toast(bad ? 'Xong: ' + (parts.length - bad) + ' TV ✓, ' + bad + ' TV ✗' : 'Xong trên cả ' + parts.length + ' TV');
      return;
    }
    await sleep(1000);
    j = await api('/api/fleet/job?id=' + j.id, null, true);
  }
}

$('#fleetapk input').addEventListener('change', async (e) => {
  const f = e.target.files[0]; e.target.value = ''; if (!f) return;
  const tvs = await fleetAsk('Cài ' + f.name + ' lên các TV này?'); if (!tvs) return;
  const lbl = $('#fleetapk'), xhr = new XMLHttpRequest();
  xhr.open('POST', '/api/fleet/install?name=' + encodeURIComponent(f.name) + '&hosts=' + encodeURIComponent(tvs.map((t) => t.host).join(',')));
  xhr.setRequestHeader('Content-Type', 'application/octet-stream');
  xhr.upload.onprogress = (ev) => { if (ev.lengthComputable) lbl.dataset.busy = '⏳ ' + Math.round(ev.loaded * 100 / ev.total) + '%'; };
  xhr.onloadend = () => {
    delete lbl.dataset.busy;
    let r = {}; try { r = JSON.parse(xhr.responseText); } catch {}
    if (xhr.status !== 200) { toast('Lỗi: ' + friendly(r.error || xhr.statusText || 'không gọi được server')); return; }
    fleetFollow(r);
  };
  lbl.dataset.busy = '⏳'; xhr.send(f);
});
async function fleetDo(action, pkg, what) {
  const tvs = await fleetAsk(what); if (!tvs) return;
  const r = await api('/api/fleet/do', { action, pkg, hosts: tvs.map((t) => t.host) });
  if (r) fleetFollow(r);
}
$('#fleetmore').addEventListener('click', (e) => popover(e.currentTarget, [
  ['🗑', 'Gỡ một app...', () => fleetUninstall()],
  ['🔁', 'Khởi động lại', () => fleetDo('reboot', '', 'Khởi động lại các TV này?')],
  ['☑️', 'Chọn tất cả', () => { fleetTvs.forEach((t) => fleetPick.add(t.host)); fleetDraw(); }],
  ['↻', 'Đọc lại', () => fleetLoad()],
]));
// the apps the user installed on the TVs ticked, most common first, to pick the one to remove everywhere
function fleetUninstall() {
  const tvs = fleetChosen().filter((t) => t.apps);
  if (!tvs.length) { toast('Chọn ít nhất một TV'); return; }
  const count = new Map();
  tvs.forEach((t) => t.apps.forEach((p) => count.set(p, (count.get(p) || 0) + 1)));
  const apps = [...count].filter(([p]) => !/^(dev\.tvremote\.setup|com\.tailscale\.ipn)$/.test(p)).sort((a, b) => b[1] - a[1] || appName(a[0]).localeCompare(appName(b[0])));
  $('#infotitle').textContent = '🗑 Gỡ app nào?';
  $('#infobody').replaceChildren(apps.length ? el('div', { className: 'fleetapps' }, ...apps.map(([p, n]) => el('button', { onclick: () => { $('#infodlg').close(); fleetDo('uninstall', p, 'Gỡ ' + appName(p) + ' khỏi các TV này?'); } },
    el('span', { textContent: appName(p) }), el('small', { className: 'muted', textContent: p + ' · ' + n + '/' + tvs.length + ' TV' }))))
    : el('p', { className: 'muted', textContent: 'Các TV đã chọn không có app tự cài nào.' }));
  $('#infodlg').showModal();
}
