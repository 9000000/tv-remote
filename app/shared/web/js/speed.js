// Health → Network: the TV's own internet speed (SpeedTest.kt), measured on the TV, since a phone's test says nothing of its Wi-Fi.
// It runs in the background there: this asks how far it got every half second, and shows the last result when the tab opens.
let speedRun = 0, speedSeen = false;                                // speedRun: the poll that is on (a newer one ends the older)

function speedDraw(s) {
  const going = !!(s && s.running);
  put('#speedgo', 'textContent', T(going ? 'speed.stop' : 'speed.go'));   // its emoji becomes the icon (iconize)
  $('#speedgo').classList.toggle('danger', going);
  const mbps = (v) => v == null ? '…' : T('speed.mbps', { n: v });
  const show = s && (s.phase || going);
  put('#hspeed', 'hidden', !show);
  if (!show) return;
  const verdict = s.phase !== 'done' ? '' : s.down >= 25 ? T('speed.ok_4k') : s.down >= 10 ? T('speed.ok_fhd') : s.down >= 5 ? T('speed.ok_hd') : T('speed.slow');
  $('#hspeed').replaceChildren(...kv([
    [T('speed.ping'), s.ping != null ? s.ping + ' ms' : '…'],
    [T('speed.down'), s.phase === 'ping' ? '…' : mbps(s.down)],
    [T('speed.up'), s.phase === 'ping' || s.phase === 'down' ? '…' : mbps(s.up)],
    [T('speed.verdict'), s.phase === 'error' ? T('speed.failed', { why: friendly(s.error || '') }) : verdict],
  ]));
}

async function speedPoll(run) {
  for (let empty = 0; run === speedRun; ) {
    await sleep(600);
    const s = await api('/api/speed', null, true);
    if (run !== speedRun) return;
    if (!s) continue;
    if (!s.running && !s.phase && ++empty < 8) continue;               // not started yet: app_process takes a moment
    speedDraw(!s.running && !s.phase ? { phase: 'error', error: T('speed.no_answer') } : s);
    if (!s.running) { speedRun++; return; }
  }
}

$('#speedgo').addEventListener('click', async () => {
  if ($('#speedgo').classList.contains('danger')) {                  // running: stop it
    speedRun++; await api('/api/speed', { stop: true }); speedDraw(null); return;
  }
  const run = ++speedRun;
  speedDraw({ running: true, phase: 'ping' });
  if (!await api('/api/speed', {})) { speedRun++; speedDraw(null); return; }
  speedPoll(run);
});

// the last result left on the TV (or one still running, from another remote), once per TV
async function speedLoad() {
  if (speedSeen) return;
  speedSeen = true;
  const s = await api('/api/speed', null, true);
  if (!s) return;
  speedDraw(s);
  if (s.running) speedPoll(++speedRun);
}
onTvChange(() => { speedSeen = false; speedRun++; speedDraw(null); });
