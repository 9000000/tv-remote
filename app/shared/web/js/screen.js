// TV screen (live / screenshots) and tabs
// ---------- screen: live MJPEG stream, or one screenshot at a time ----------
let screenOn = false, screenUrl = null, screenGen = 0, streamCtl = null, frames = 0;
const SCREEN_HINT = 'Bấm “Xem” để xem màn hình TV. Khi đang xem: bấm chuột lên hình để chạm, cuộn để cuộn, chuột phải = Back.';
if (CORE !== 'mac') document.querySelectorAll('#screenmode [data-for=mac]').forEach((o) => o.remove());   // an <option> cannot be hidden by CSS everywhere
function setScreenPlaceholder(msg) { $('#screenph').textContent = msg || SCREEN_HINT; $('#screenph').hidden = false; $('#screen').hidden = $('#screenv').hidden = true; }
function setScreenInfo(text, live) { $('#screeninfo').textContent = text; $('#screeninfo').classList.toggle('live', !!live); }
function showFrame(blob) {
  const u = URL.createObjectURL(blob); $('#screen').src = u; $('#screen').hidden = false; $('#screenph').hidden = $('#screenv').hidden = true;
  if (screenUrl) URL.revokeObjectURL(screenUrl); screenUrl = u; frames++;
}
function stopScreen(msg) {
  if (typeof drawing !== 'undefined' && drawing) drawStop();         // no picture to draw on
  screenOn = false; screenGen++; if (streamCtl) streamCtl.abort(); if (muxer) { try { muxer.destroy(); } catch {} muxer = null; }
  $('#screencard').classList.remove('on'); if (msg && matchMedia('(max-width:899px)').matches) toast(msg);   // on a phone the box is folded away: say it where it is seen
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
    const mode = $('#screenmode').value;
    const r = await fetch('/api/stream?' + (mode === 'hd' ? 'q=hd' : 'q=eco&size=' + mode), { signal: streamCtl.signal });
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
// The TV core sends its own H.264 (no computer in between to turn it into pictures); JMuxer makes it playable by a <video>.
// Packets: 1 byte kind (0 size, 1 codec config, 2 key frame, 3 frame, 4 nothing new), 4 bytes length, then the data.
const H264_Q = { 480: 0, 720: 1, 960: 2, 360: 3, 240: 4, hd: 'hd' }, H264_FPS = { 480: 8, 720: 10, 960: 10, 360: 8, 240: 8, hd: 30 };
const H264_END = new Uint8Array([0, 0, 0, 1, 9, 0xf0]);                // access unit delimiter
let muxer = null;
function loadScript(src) { return new Promise((ok, no) => document.head.append(el('script', { src, onload: ok, onerror: no }))); }
async function h264Loop(gen) {
  setScreenInfo('Đang mở luồng trực tiếp...', true);
  const size = $('#screenmode').value, video = $('#screenv'), step = Math.round(1000 / (H264_FPS[size] || 10));
  try { if (!window.JMuxer) await loadScript('jmuxer.min.js'); } catch { if (gen === screenGen) stopScreen('Không tải được phần phát hình.'); return; }
  if (gen !== screenGen) return;
  streamCtl = new AbortController();
  const mux = muxer = new JMuxer({ node: video, mode: 'video', flushingTime: 0, fps: H264_FPS[size] || 10, clearBuffer: true, onError: () => {} });
  const edge = setInterval(() => {                                    // stay on the newest frame instead of playing a growing backlog
    if (gen !== screenGen) { clearInterval(edge); return; }
    const b = video.buffered; if (!b.length) return;
    const end = b.end(b.length - 1); if (end - video.currentTime > 0.3) video.currentTime = end - 0.05;
    if (video.paused) video.play().catch(() => {});
  }, 250);
  let why = 'TV đã dừng gửi hình.';
  try {
    const r = await fetch('/api/video?q=' + (H264_Q[size] ?? 1), { signal: streamCtl.signal }); if (!r.ok) throw new Error('http');
    const rd = r.body.getReader(); let buf = new Uint8Array(0), config = null;
    for (;;) {
      const { done, value } = await rd.read(); if (done || gen !== screenGen) break;
      const nb = new Uint8Array(buf.length + value.length); nb.set(buf); nb.set(value, buf.length); buf = nb;
      while (buf.length >= 5) {
        const len = new DataView(buf.buffer, buf.byteOffset + 1, 4).getUint32(0); if (buf.length < 5 + len) break;
        const kind = buf[0], data = buf.slice(5, 5 + len); buf = buf.slice(5 + len);
        if (kind === 0) { const d = new DataView(data.buffer); $('#screenbox').style.aspectRatio = d.getUint32(0) + ' / ' + d.getUint32(4); }
        else if (kind === 1) config = data;                           // goes in front of the next frame
        else if (kind === 2 || kind === 3) {
          // JMuxer shows a frame only once it sees the start of the next one, and a still picture has no next one:
          // an empty delimiter after each frame makes it show the frame now.
          const frame = new Uint8Array((config ? config.length : 0) + data.length + H264_END.length);
          if (config) frame.set(config); frame.set(data, config ? config.length : 0); frame.set(H264_END, frame.length - H264_END.length); config = null;
          mux.feed({ video: frame, duration: step }); frames++;
          if (video.hidden) { video.hidden = false; $('#screen').hidden = $('#screenph').hidden = true; }
        }
      }
    }
  } catch (e) { why = 'Không lấy được hình từ TV.'; }
  clearInterval(edge);
  if (screenOn && gen === screenGen) stopScreen(why);                 // back to the plain remote: no dead picture left on the page
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
  if (muxer) { try { muxer.destroy(); } catch {} muxer = null; }
  $('#screencard').classList.add('on'); $('#screenbox').style.aspectRatio = '';
  setLabel($('#screenbtn'), '⏹', 'Dừng'); setScreenPlaceholder('Đang lấy hình...');
  ($('#screenmode').value === 'snap' ? snapLoop : CORE === 'mac' && STATE.video !== 'h264' ? liveLoop : h264Loop)(gen);   // the computer's Kotlin server sends H.264 too
}
setInterval(() => {
  if (screenOn && $('#screenmode').value !== 'snap' && streamCtl && !streamCtl.signal.aborted) {
    streamFps = frames;
    const shown = !$('#screen').hidden || !$('#screenv').hidden;           // the TV sends a frame only when the picture changes
    setScreenInfo((frames ? `Trực tiếp · ${frames} khung/giây` : shown ? 'Trực tiếp · hình đứng yên' : 'Trực tiếp · đang chờ hình') + ($('#screenmode').value === 'hd' ? ' · chế độ nét nhất làm TV chậm hơn rõ rệt' : ' · ' + $('#screenmode').value + 'p'), true);
  }
  frames = 0;
}, 1000);
$('#screenbtn').addEventListener('click', () => { if (screenOn) stopScreen(); else startScreen(); });
try { const q = localStorage.getItem('quality'); if (q && [...$('#screenmode').options].some((o) => o.value === q)) $('#screenmode').value = q; } catch {}   // the level picked last time
$('#screenmode').addEventListener('change', () => { try { localStorage.setItem('quality', $('#screenmode').value); } catch {} if (screenOn) startScreen(); });
$('#screenfull').addEventListener('click', () => { const b = $('#screenbox'); if (document.fullscreenElement) document.exitFullscreen(); else if (b.requestFullscreen) b.requestFullscreen(); });
document.addEventListener('visibilitychange', () => { if (document.hidden && screenOn) stopScreen(); });
setScreenPlaceholder();

// ---------- tabs ----------
let currentTab = 'remote', streamFps = 0;
function showTab(t) {
  if (CORE === 'phone' && (t === 'remote' || !document.querySelector('[data-tab="' + t + '"]'))) t = 'apps';   // the phone app has its own remote
  currentTab = t; document.querySelector('main').classList.toggle('wide', t === 'remote' && !picking);
  document.querySelectorAll('[data-tab]').forEach((b) => b.setAttribute('aria-selected', b.dataset.tab === t));
  document.querySelectorAll('[data-panel]').forEach((p) => { p.hidden = p.dataset.panel !== t; });
  dockScreen();
  if (ADB.state === 'ready') { if (t === 'apps' && !appsLoaded) loadApps(); if (t === 'files' && !filesLoaded) loadFiles(cwd); }
  if (t === 'tv' && CORE === 'mac') loadSteps();
  if (t === 'health') loadHealth();
  if (t === 'auto') autoTab();
  if (t === 'tv') { tsState(); loadTvset(); }
}
document.querySelectorAll('[data-tab]').forEach((b) => b.addEventListener('click', () => showTab(b.dataset.tab)));

