// TV screen (live / screenshots) and tabs
// ---------- screen: live MJPEG stream, or one screenshot at a time ----------
let screenOn = false, screenUrl = null, screenGen = 0, streamCtl = null, frames = 0;
const SCREEN_HINT = 'Bấm “Xem” để xem màn hình TV. Khi đang xem: bấm chuột lên hình để chạm, cuộn để cuộn, chuột phải = Back.';
function setScreenPlaceholder(msg) { $('#screenph').textContent = msg || SCREEN_HINT; $('#screenph').hidden = false; $('#screen').hidden = true; }
function setScreenInfo(text, live) { $('#screeninfo').textContent = text; $('#screeninfo').classList.toggle('live', !!live); }
function showFrame(blob) {
  const u = URL.createObjectURL(blob); $('#screen').src = u; $('#screen').hidden = false; $('#screenph').hidden = true;
  if (screenUrl) URL.revokeObjectURL(screenUrl); screenUrl = u; frames++;
}
function stopScreen(msg) {
  screenOn = false; screenGen++; if (streamCtl) streamCtl.abort();
  setLabel($('#screenbtn'), '▶', 'Xem'); setScreenPlaceholder(msg); setScreenInfo(''); dockScreen();
}
async function snapOnce() {
  try {
    const r = await fetch('/api/screen');
    if (r.ok) { const b = await r.blob(); return b; }
    let e = r.statusText; try { e = (await r.json()).error || e; } catch {} return e;
  } catch { return 'Không gọi được server'; }
}
async function snapLoop(gen) {
  setScreenInfo('Ảnh chụp, làm mới khoảng 1,5 giây/ảnh. Video có bản quyền (Netflix...) hiện màu đen.');
  while (screenOn && gen === screenGen) {
    const b = await snapOnce(); if (gen !== screenGen) return;
    if (b instanceof Blob) { showFrame(b); await sleep(150); } else { setScreenPlaceholder(b); await sleep(3000); }
  }
}
function findSeq(buf, seq, from) {
  outer: for (let i = from; i <= buf.length - seq.length; i++) { for (let j = 0; j < seq.length; j++) if (buf[i + j] !== seq[j]) continue outer; return i; }
  return -1;
}
async function liveLoop(gen) {
  setScreenInfo('Đang mở luồng trực tiếp...', true);
  const first = await snapOnce(); if (gen !== screenGen) return;       // the stream only sends a frame when the picture changes
  if (first instanceof Blob) showFrame(first);
  streamCtl = new AbortController();
  try {
    const r = await fetch('/api/stream?q=' + ($('#screenmode').value === 'hd' ? 'hd' : 'eco'), { signal: streamCtl.signal });
    if (!r.ok) throw new Error('stream');
    const rd = r.body.getReader(); let buf = new Uint8Array(0);
    for (;;) {
      const { done, value } = await rd.read(); if (done || gen !== screenGen) break;
      const nb = new Uint8Array(buf.length + value.length); nb.set(buf); nb.set(value, buf.length); buf = nb;
      for (;;) {                                                      // multipart: headers, blank line, Content-length bytes of JPEG
        const h = findSeq(buf, [13, 10, 13, 10], 0); if (h < 0) break;
        const m = /content-length:\s*(\d+)/i.exec(new TextDecoder().decode(buf.subarray(0, h)));
        if (!m) { buf = buf.slice(h + 4); continue; }
        const start = h + 4, n = +m[1]; if (buf.length < start + n) break;
        showFrame(new Blob([buf.slice(start, start + n)], { type: 'image/jpeg' }));
        buf = buf.slice(start + n);
      }
    }
  } catch (e) { if (gen !== screenGen) return; }
  if (screenOn && gen === screenGen) {                                 // recorder not available: keep going with screenshots
    toast('Luồng trực tiếp bị ngắt, chuyển sang ảnh chụp'); $('#screenmode').value = 'snap'; snapLoop(gen);
  }
}
// Keep watching on the other tabs: move the picture card into a floating corner slot, and back on the Remote tab.
const screenHome = $('#screencard').parentElement;
function dockScreen() {
  const float = currentTab !== 'remote' && screenOn, card = $('#screencard');
  if (float && card.parentElement !== $('#pipslot')) $('#pipslot').append(card);
  if (!float && card.parentElement !== screenHome) screenHome.prepend(card);
  $('#pipback').hidden = !float; document.body.classList.toggle('pip', float);
}
$('#pipback').addEventListener('click', () => showTab('remote'));
function startScreen() {
  screenOn = true; const gen = ++screenGen; if (streamCtl) streamCtl.abort();
  dockScreen();
  setLabel($('#screenbtn'), '⏹', 'Dừng'); $('#screenph').textContent = 'Đang lấy hình...';
  ($('#screenmode').value === 'snap' ? snapLoop : liveLoop)(gen);
}
setInterval(() => {
  if (screenOn && $('#screenmode').value !== 'snap' && streamCtl && !streamCtl.signal.aborted)
    setScreenInfo((frames ? `Trực tiếp · ${frames} khung/giây` : 'Trực tiếp · đang chờ hình') + ($('#screenmode').value === 'hd' ? ' · chế độ nét làm TV chậm hơn rõ rệt' : ' · chế độ nhẹ cho TV'), true);
  frames = 0;
}, 1000);
$('#screenbtn').addEventListener('click', () => { if (screenOn) stopScreen(); else startScreen(); });
$('#screenmode').addEventListener('change', () => { if (screenOn) startScreen(); });
$('#screenfull').addEventListener('click', () => { const b = $('#screenbox'); if (document.fullscreenElement) document.exitFullscreen(); else if (b.requestFullscreen) b.requestFullscreen(); });
document.addEventListener('visibilitychange', () => { if (document.hidden && screenOn) stopScreen(); });
setScreenPlaceholder();

// ---------- tabs ----------
let currentTab = 'remote';
function showTab(t) {
  currentTab = t; document.querySelector('main').classList.toggle('wide', t === 'remote');
  document.querySelectorAll('[data-tab]').forEach((b) => b.setAttribute('aria-selected', b.dataset.tab === t));
  document.querySelectorAll('[data-panel]').forEach((p) => { p.hidden = p.dataset.panel !== t; });
  dockScreen();
  if (ADB.state === 'ready') { if (t === 'apps' && !appsLoaded) loadApps(); if (t === 'files' && !filesLoaded) loadFiles(cwd); }
  if (t === 'tv') { loadTvs(); loadSteps(); }
}
document.querySelectorAll('[data-tab]').forEach((b) => b.addEventListener('click', () => showTab(b.dataset.tab)));

