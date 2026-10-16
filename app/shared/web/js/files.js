// file explorer, uploads and jobs
// ---------- files ----------
let cwd = '/sdcard/Download', filesLoaded = false, fileItems = [], volumes = [];
const KINDS = [
  ['🎬', 'video', /\.(mp4|mkv|avi|mov|webm|ts|m4v|wmv|flv)$/i], ['🎵', 'audio', /\.(mp3|flac|wav|m4a|ogg|aac|opus)$/i],
  ['🖼️', 'image', /\.(jpe?g|png|gif|webp)$/i], ['📦', 'apk', /\.apk$/i], ['🗜️', 'zip', /\.(zip|rar|7z|tar|gz|xz)$/i],
];
const kindOf = (i) => i.dir ? ['📁', 'dir'] : (KINDS.find((k) => k[2].test(i.name)) || ['📄', 'file']);
const join = (name) => cwd.replace(/\/$/, '') + '/' + name;
async function fileAct(path, data, okMsg) { const r = await api(path, data); if (r) { if (okMsg) toast(okMsg); loadFiles(cwd); } return r; }

async function loadFiles(path) {
  const r = await api('/api/files?path=' + encodeURIComponent(path));
  if (!r) { if (!filesLoaded && path !== '/sdcard') loadFiles('/sdcard'); return; }
  filesLoaded = true; cwd = r.path; fileItems = r.items; volumes = r.volumes || [];
  if (path !== cwd || $('#filesq').dataset.dir !== cwd) { $('#filesq').value = ''; $('#filesq').dataset.dir = cwd; }
  $('#space').textContent = r.space ? `Còn trống ${fmtSize(r.space.free)} / ${fmtSize(r.space.total)} · ${r.items.length} mục` : `${r.items.length} mục`;
  renderPlaces(); renderFiles();
}
function renderPlaces() {
  const places = [['Bộ nhớ TV', '/sdcard'], ['Download', '/sdcard/Download'], ['Phim', '/sdcard/Movies'], ['Ảnh', '/sdcard/Pictures'], ['Nhạc', '/sdcard/Music'],
    ...volumes.map((v) => ['USB ' + v, '/storage/' + v])];
  const best = places.filter((p) => cwd === p[1] || cwd.startsWith(p[1] + '/')).sort((a, b) => b[1].length - a[1].length)[0];
  $('#places').replaceChildren(...places.map(([label, p]) => el('button', { textContent: label, className: best && best[1] === p ? 'on' : '', onclick: () => loadFiles(p) })));
  const parts = cwd.split('/').filter(Boolean), kids = [];
  parts.forEach((part, i) => {
    if (i) kids.push(el('i', { textContent: '›' }));
    const p = '/' + parts.slice(0, i + 1).join('/');
    kids.push(el('button', { textContent: i === 0 && part === 'sdcard' ? 'Bộ nhớ TV' : part, onclick: () => loadFiles(p) }));
  });
  $('#crumbs').replaceChildren(...kids);
}
function renderFiles() {
  const q = $('#filesq').value.trim().toLowerCase(), sort = $('#filessort').value;
  const cmp = sort === 'new' ? (a, b) => b.mtime.localeCompare(a.mtime) : sort === 'big' ? (a, b) => b.size - a.size : (a, b) => a.name.localeCompare(b.name, undefined, { numeric: true });
  const rows = fileItems.filter((i) => !q || i.name.toLowerCase().includes(q)).sort((a, b) => (b.dir - a.dir) || cmp(a, b)).map((i) => {
    const full = join(i.name), [icon, kind] = kindOf(i);
    const btn = (text, title, fn, cls = '') => el('button', { textContent: text, title, ariaLabel: title, className: 'small ' + cls, onclick: (e) => { e.stopPropagation(); fn(e); } });
    const acts = el('div', { className: 'acts' });
    if (kind === 'apk') acts.append(setLabel(btn('', 'Cài APK này lên TV', async (e) => { const b = e.currentTarget; if (await ask('Cài ' + i.name + ' lên TV?', { ok: 'Cài' })) startJob('/api/file/install', { path: full }, 'Cài ' + i.name, b); }, 'primary'), '📦', 'Cài', true));
    if (['video', 'audio', 'image'].includes(kind)) acts.append(btn('▶ TV', 'Mở trên TV', () => api('/api/file/open', { path: full }).then((r) => r && toast('Đã mở trên TV: ' + i.name))));
    acts.append(btn('⋯', 'Thêm thao tác', (e) => popover(e.currentTarget, [   // one row on a phone too: the rest behind ⋯, as on the Apps tab
      i.dir ? null : ['⬇', 'Tải về máy này', () => el('a', { href: '/api/file?path=' + encodeURIComponent(full), download: i.name }).click()],
      ['✎', 'Đổi tên', async () => { const n = await ask('Tên mới:', { value: i.name }); if (n && n !== i.name) fileAct('/api/file/rename', { path: full, name: n }, 'Đã đổi tên'); }],
      ['🗑', 'Xóa', async () => { if (await ask('Xóa ' + (i.dir ? 'thư mục (và mọi thứ bên trong) ' : '') + full + '?', { ok: 'Xóa', danger: true })) fileAct('/api/file/delete', { path: full }, 'Đã xóa'); }, 'danger'],
    ]), 'iconbtn'));
    const open = i.dir ? () => loadFiles(full) : kind === 'image' ? () => { $('#preview img').src = '/api/file?inline=1&path=' + encodeURIComponent(full); $('#preview').showModal(); } : null;
    const row = el('div', { className: 'item' + (open ? ' click' : ''), onclick: open },
      el('div', { className: 'ficon' + (i.dir ? ' dir' : ''), textContent: icon }),
      el('div', { className: 'name' }, el('span', { textContent: i.name }), el('small', { textContent: (i.dir ? 'Thư mục' : fmtSize(i.size)) + ' · ' + i.mtime })), acts);
    if (open) { row.tabIndex = 0; row.addEventListener('keydown', (e) => { if (e.key === 'Enter' && e.target === row) open(); }); }
    return row;
  });
  $('#fileslist').replaceChildren(...(rows.length ? rows : [el('div', { className: 'muted', style: 'padding:14px 0', textContent: q ? 'Không có mục nào khớp.' : 'Thư mục trống.' })]));
}
$('#filesq').addEventListener('input', renderFiles);
$('#filessort').addEventListener('change', renderFiles);
$('#filesreload').addEventListener('click', () => loadFiles(cwd));
$('#mkdir').addEventListener('click', async () => { const n = await ask('Tên thư mục mới:', { value: '' }); if (n) fileAct('/api/file/mkdir', { path: join(n.trim()) }, 'Đã tạo thư mục'); });
$('#upfile').addEventListener('change', (e) => { [...e.target.files].forEach((f) => upload(f, 'file', cwd)); e.target.value = ''; });
$('#preview').addEventListener('click', () => { $('#preview').close(); $('#preview img').removeAttribute('src'); });
const fc = $('#filescard');
['dragenter', 'dragover'].forEach((ev) => fc.addEventListener(ev, (e) => { if (e.dataTransfer && [...e.dataTransfer.types].includes('Files')) { e.preventDefault(); fc.classList.add('dragging'); } }));
fc.addEventListener('dragleave', (e) => { if (!fc.contains(e.relatedTarget)) fc.classList.remove('dragging'); });
fc.addEventListener('drop', (e) => { e.preventDefault(); fc.classList.remove('dragging'); [...e.dataTransfer.files].forEach((f) => upload(f, 'file', cwd)); });

// ---------- jobs / upload ----------
const jobEls = {};
// The control that started a job shows its progress too: the jobs card sits in a corner, far from where the eyes are.
function mark(src, j) {
  if (!src) return;
  if (j.status === 'running') src.dataset.busy = '⏳ ' + (j.pct || 0) + '%'; else delete src.dataset.busy;
}
function showJob(j, label) {
  let row = jobEls[j.id];
  if (!row) { $('#jobscard').hidden = false; $('#jobscard').open = true; row = jobEls[j.id] = { root: el('div', { className: 'job' }), msg: el('div', { className: 'msg' }), bar: el('i') }; row.root.append(el('div', { textContent: label || j.title }), row.msg, el('div', { className: 'bar' }, row.bar)); $('#jobs').prepend(row.root); }
  row.root.className = 'job ' + j.status; row.msg.textContent = j.msg; row.bar.style.width = j.pct + '%';
  row.bar.parentElement.classList.toggle('busy', j.status === 'running' && !(j.pct >= 0));   // size unknown: moving, not stuck at 0
  row.status = j.status; row.pct = j.pct;
  // folded away, the title still tells how far things are
  const run = Object.values(jobEls).filter((r) => r.status === 'running');
  $('#jobsum').textContent = run.length ? '· ' + Math.round(run.reduce((s, r) => s + (r.pct > 0 ? r.pct : 0), 0) / run.length) + '%' : '';
}
// The phone app saves a TV file into its own Download folder itself (Managing.kt) and tells its progress here, in the same list.
window.nativeJob = (j) => {
  showJob(j, j.label);
  if (j.status !== 'running') toast(j.msg);
};
async function trackJob(j, label, after, src) {
  showJob(j, label); mark(src, j);
  let misses = 0;                                                     // a blip in the network is not the end of the job; ten in a row are
  while (j.status === 'running') {
    await sleep(1000); const r = await api('/api/job?id=' + j.id, null, true);
    if (r) { misses = 0; j = r; showJob(j, label); mark(src, j); continue; }
    if (++misses >= 10) { j = { ...j, status: 'error', msg: 'mất kết nối, không rõ đã xong chưa' }; showJob(j, label); break; }
  }
  mark(src, j);
  if (j.status === 'error') { j.msg = friendly(j.msg); showJob(j, label); }
  toast(j.status === 'done' ? 'Xong: ' + j.msg : 'Lỗi: ' + j.msg);
  if (j.status === 'done' && after) after(j);
  if (j.status === 'done' && filesLoaded) loadFiles(cwd);
  if (j.status === 'done') { appsLoaded = false; if (/install/.test(j.title)) loadQuick(); }
}
function upload(file, kind, dest, after, src) {
  const row = { id: 'up' + Math.random().toString(36).slice(2), title: 'Gửi ' + file.name, status: 'running', msg: 'đang gửi lên server...', pct: 0 };
  showJob(row); mark(src, row);
  const xhr = new XMLHttpRequest();
  xhr.open('POST', '/api/upload?kind=' + kind + '&name=' + encodeURIComponent(file.name) + '&dest=' + encodeURIComponent(dest || ''));
  xhr.setRequestHeader('Content-Type', 'application/octet-stream');
  xhr.upload.onprogress = (e) => { if (e.lengthComputable) { row.pct = Math.round(e.loaded * (CORE === 'pc' ? 45 : 95) / e.total); row.msg = 'đang gửi ' + fmtSize(e.loaded) + ' / ' + fmtSize(e.total); showJob(row); mark(src, row); } };
  xhr.onerror = () => { row.status = 'error'; row.msg = 'Không gọi được server'; showJob(row); mark(src, row); };
  xhr.onload = () => {
    let r = {}; try { r = JSON.parse(xhr.responseText); } catch {}
    if (xhr.status !== 200) { row.status = 'error'; row.msg = r.error || xhr.statusText; showJob(row); mark(src, row); return; }
    jobEls[row.id].root.remove(); delete jobEls[row.id]; trackJob(r, file.name, after, src);
  };
  xhr.send(file);
}
// "Cast" a file: copy it to the TV's Download folder, then have the TV open it with its own viewer or player.
$('#castfile').addEventListener('change', (e) => {
  const f = e.target.files[0]; e.target.value = ''; if (!f) return;
  upload(f, 'file', '/sdcard/Download', async (j) => { const p = /^saved (.+)$/.exec(j.msg || ''); if (await api('/api/file/open', { path: p ? p[1] : '/sdcard/Download/' + f.name })) toast('Đã mở trên TV: ' + f.name); });
});
async function startJob(path, data, label, src) { if (src) src.dataset.busy = '⏳'; const r = await api(path, data); if (r) trackJob(r, label, undefined, src); else mark(src, {}); }
$('#fetchf').addEventListener('submit', (e) => { e.preventDefault(); const u = $('#fetchurl').value.trim(); if (u) { startJob('/api/fetch', { url: u, dest: '/sdcard/Download' }); $('#fetchurl').value = ''; } });
$('#instf').addEventListener('submit', async (e) => { e.preventDefault(); const u = $('#insturl').value.trim(); if (u && await ask('Cài APK từ link này lên TV?\n' + u, { ok: 'Cài' })) { startJob('/api/install', { url: u }, undefined, $('#instf button')); $('#insturl').value = ''; } });
$('#apkfile').addEventListener('change', async (e) => { const f = e.target.files[0]; e.target.value = ''; if (f && await ask('Cài ' + f.name + ' lên TV?', { ok: 'Cài' })) upload(f, 'install', '', undefined, e.target.closest('label')); });


$('#jobsclose').addEventListener('click', (e) => { e.preventDefault(); $('#jobscard').hidden = true; });   // the next upload or install brings it back
