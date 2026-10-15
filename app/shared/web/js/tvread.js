// Reading what the TV printed for the Apps tab and the TV card (the recipes are app/web/tvshell.py and TvShell.kt). Kept apart
// from the page so node can test it: node --test app/web/tests/
((root) => {
  const STAMP = /^\d\d-\d\d \d\d:\d\d:\d\d\.\d{3}$/;

  // 🧹 "free blocks, block size" before and after: the bytes that came back (never below zero: apps keep writing meanwhile)
  function cleanFreed(text) {
    const n = String(text || '').trim().split(/\s+/).map(Number);
    if (n.length < 4 || n.some((x) => !Number.isFinite(x))) return null;
    return Math.max(0, n[2] * n[3] - n[0] * n[1]);
  }

  // 📜 one poll of an app's log: "pid <n>" then threadtime lines. The next poll asks from the last line's stamp on, so lines
  // with that stamp come again; `st` ({ last, seen }) remembers them so each line is shown once.
  function logNext(st, text) {
    const rows = String(text || '').replace(/\r/g, '').split('\n');
    const pid = (rows.shift() || '').replace(/^pid\s*/, '').trim().split(/\s+/)[0] || '';
    const lines = [];
    for (const l of rows) {
      if (!l.trim() || l.startsWith('---------')) continue;            // "--------- beginning of main"
      const at = l.slice(0, 18);
      if (STAMP.test(at)) {
        const newYear = st.last.startsWith('12-') && at.startsWith('01-');   // the stamps have no year
        if ((at < st.last && !newYear) || (at === st.last && st.seen.has(l))) continue;
        if (at !== st.last) { st.last = at; st.seen = new Set(); }
        st.seen.add(l);
      }
      lines.push(l);
    }
    return { pid, lines };
  }
  const logLevel = (l) => (/^\S+ \S+\s+\d+\s+\d+ ([VDIWEF]) /.exec(l) || [])[1] || '';

  // ⚙️ the TV card's "name value" lines
  function readTvset(text) {
    const o = { sdk: 0, dnsMode: '', dnsHost: '', sleep: null, saver: false, saverAfter: null, physical: 0, override: 0, home: '', launchers: [] };
    const val = (v) => (v === 'null' || v === undefined ? '' : v);
    for (const raw of String(text || '').replace(/\r/g, '').split('\n')) {
      const l = raw.trim(), sp = l.indexOf(' '), k = sp < 0 ? l : l.slice(0, sp), v = sp < 0 ? '' : l.slice(sp + 1).trim();
      if (k === 'sdk') o.sdk = parseInt(v, 10) || 0;
      else if (k === 'dns_mode') o.dnsMode = val(v);
      else if (k === 'dns_host') o.dnsHost = val(v);
      else if (k === 'sleep') o.sleep = /^-?\d+$/.test(v) ? Number(v) : null;
      else if (k === 'saver') o.saver = v === '1';
      else if (k === 'saver_after') o.saverAfter = /^\d+$/.test(v) ? Number(v) : null;
      else if (/^Physical density:/.test(l)) o.physical = parseInt(l.split(':')[1], 10) || 0;
      else if (/^Override density:/.test(l)) o.override = parseInt(l.split(':')[1], 10) || 0;
      else if (k === 'home') o.home = v.includes('/') ? v : '';
      else if (k === 'launcher' && v.includes('/') && !/FallbackHome/.test(v)) o.launchers.push(v);
    }
    if (o.home && !o.launchers.includes(o.home)) o.launchers.unshift(o.home);
    return o;
  }

  // 🔠 the sizes offered, from the screen's own density: bigger density = bigger everything
  function densityChoices(physical) {
    if (!physical) return [];
    return [['Nhỏ', Math.round(physical * 0.9)], ['Vừa', 'reset'], ['Lớn', Math.round(physical * 1.1)], ['Rất lớn', Math.round(physical * 1.2)]];
  }

  // 🎞️ the Capabilities card (Caps.kt): for each format the best decoder (hardware first, then the biggest picture)
  const VIDEO = [['video/avc', 'H.264'], ['video/hevc', 'H.265 (HEVC)'], ['video/x-vnd.on2.vp9', 'VP9'], ['video/av01', 'AV1'],
    ['video/dolby-vision', 'Dolby Vision'], ['video/mpeg2', 'MPEG-2'], ['video/x-vnd.on2.vp8', 'VP8'], ['video/mp4v-es', 'MPEG-4']];
  const AUDIO = [['audio/mp4a-latm', 'AAC'], ['audio/ac3', 'Dolby Digital'], ['audio/eac3', 'Dolby Digital Plus'], ['audio/eac3-joc', 'Dolby Atmos'],
    ['audio/ac4', 'Dolby AC-4'], ['audio/vnd.dts', 'DTS'], ['audio/vnd.dts.hd', 'DTS-HD'], ['audio/true-hd', 'Dolby TrueHD'],
    ['audio/opus', 'Opus'], ['audio/flac', 'FLAC'], ['audio/mpeg', 'MP3'], ['audio/vorbis', 'Vorbis']];
  const OUT_ENC = { pcm: 'PCM', ac3: 'Dolby Digital', eac3: 'Dolby Digital Plus', eac3joc: 'Dolby Atmos', ac4: 'AC-4', dts: 'DTS', dtshd: 'DTS-HD', truehd: 'TrueHD', iec61937: 'bitstream' };
  function capsRead(text) {
    const o = { sdk: 0, release: '', video: [], audio: [], hdr: [], modes: [], outputs: [], features: [] };
    const best = {};
    for (const raw of String(text || '').replace(/\r/g, '').split('\n')) {
      const p = raw.trim().split(/\s+/);
      if (p[0] === 'sdk') { o.sdk = parseInt(p[1], 10) || 0; o.release = p[2] || ''; }
      else if (p[0] === 'codec' && p.length >= 7 && p[1] === 'dec') {
        const mime = p[4].toLowerCase(), wh = (p[5].match(/^(\d+)x(\d+)$/) || []).slice(1).map(Number);
        const c = { hw: p[2] === 'hw', codec: p[3], w: wh[0] || 0, h: wh[1] || 0, flags: p[6] === '-' ? [] : p[6].split(','), secure: false };
        c.secure = c.flags.includes('secure');
        const b = best[mime];
        if (!b || (c.hw && !b.hw) || (c.hw === b.hw && c.w * c.h > b.w * b.h)) best[mime] = { ...c, flags: [...new Set([...(b && b.hw === c.hw ? b.flags : []), ...c.flags])] };
        else if (b.hw === c.hw) b.flags = [...new Set([...b.flags, ...c.flags])];
      } else if (p[0] === 'hdr') o.hdr = p.slice(1);
      else if (p[0] === 'mode' && p[1]) { if (!o.modes.includes(p[1])) o.modes.push(p[1]); }
      else if (p[0] === 'audio' && p[1]) o.outputs.push({ dev: p[1], enc: p.slice(2).map((e) => OUT_ENC[e] || e) });
      else if (p[0] === 'feature' && p[1]) o.features.push(p[1]);
    }
    for (const [mime, name] of VIDEO) if (best[mime]) o.video.push({ mime, name, ...best[mime] });
    for (const [mime, name] of AUDIO) if (best[mime]) o.audio.push({ mime, name, hw: best[mime].hw });
    return o;
  }
  // "4K", "1080p"... from a decoder's biggest picture (either way round: some list 2160x3840)
  const sizeName = (w, h) => { const [a, b] = [Math.max(w, h), Math.min(w, h)]; return a >= 7680 ? '8K' : a >= 3840 && b >= 2160 ? '4K' : b >= 1440 ? '1440p' : b >= 1080 ? '1080p' : b >= 720 ? '720p' : b ? b + 'p' : ''; };

  // ℹ️ one app (tvshell.app_info): the first "Package [" block only, the one in use (an updated system app also lists its
  // hidden factory copy after it). Runtime permissions are the ones a person can give or take back.
  const DANGEROUS = /^android\.permission\.(READ_CALENDAR|WRITE_CALENDAR|CAMERA|READ_CONTACTS|WRITE_CONTACTS|GET_ACCOUNTS|ACCESS_(FINE|COARSE|BACKGROUND|MEDIA)_LOCATION|RECORD_AUDIO|READ_PHONE_STATE|READ_PHONE_NUMBERS|CALL_PHONE|(READ|WRITE)_CALL_LOG|BODY_SENSORS|(SEND|RECEIVE|READ)_SMS|(READ|WRITE)_EXTERNAL_STORAGE|READ_MEDIA_(IMAGES|VIDEO|AUDIO|VISUAL_USER_SELECTED)|POST_NOTIFICATIONS|NEARBY_WIFI_DEVICES|BLUETOOTH_(CONNECT|SCAN|ADVERTISE)|ACTIVITY_RECOGNITION)$/;
  function appInfoRead(text) {
    const parts = String(text || '').replace(/\r/g, '').split('#~#\n');
    const lines = (parts[0] || '').split('\n');
    const start = lines.findIndex((l) => /^ {2}Package \[/.test(l));
    const o = { found: start >= 0, version: '', code: '', minSdk: 0, targetSdk: 0, installed: '', updated: '', system: false, updatedSystem: false,
      installer: '', pids: [], ramKb: 0, boot: false, perms: [] };
    if (start < 0) return o;
    const block = [];
    for (let i = start + 1; i < lines.length && /^ {3,}/.test(lines[i]); i++) block.push(lines[i]);
    const runtime = new Map(), install = new Set(), requested = [];
    let sect = '';
    for (const l of block) {
      const t = l.trim(), ind = l.length - l.trimStart().length;
      let m;
      if ((m = /versionCode=(\d+)(?: minSdk=(\d+))?(?: targetSdk=(\d+))?/.exec(t)) && !o.code) { o.code = m[1]; o.minSdk = +m[2] || 0; o.targetSdk = +m[3] || 0; }
      if ((m = /^versionName=(.*)$/.exec(t))) o.version = m[1];
      else if ((m = /^firstInstallTime=(.*)$/.exec(t))) o.installed = m[1];
      else if ((m = /^lastUpdateTime=(.*)$/.exec(t))) o.updated = m[1];
      else if ((m = /^installerPackageName=(.*)$/.exec(t))) o.installer = m[1] === 'null' ? '' : m[1];
      else if (/^(pkg)?[fF]lags=\[/.test(t) && /\bSYSTEM\b/.test(t)) { o.system = true; o.updatedSystem = o.updatedSystem || /UPDATED_SYSTEM_APP/.test(t); }
      if (/:$/.test(t) && !t.includes(' ')) sect = t;                 // "requested permissions:" has a space: see below
      else if (/^(requested|install|runtime) permissions:$/.test(t)) sect = t.split(' ')[0];
      else if (/^User \d+:/.test(t)) sect = 'user';
      else if (ind >= 6 && sect === 'requested' && /^[\w.]+$/.test(t.split(':')[0])) requested.push(t.split(/[:,\s]/)[0]);
      else if (ind >= 6 && sect === 'install' && (m = /^([\w.]+): granted=/.exec(t))) install.add(m[1]);
      else if (ind >= 8 && sect === 'runtime' && (m = /^([\w.]+): granted=(true|false)(.*)$/.exec(t)))
        runtime.set(m[1], { name: m[1], granted: m[2] === 'true', locked: /SYSTEM_FIXED|POLICY_FIXED/.test(m[3]) });
    }
    // Android 9 lists only the runtime permissions given so far: the others an app asked for show as not given
    for (const n of requested) if (!runtime.has(n) && !install.has(n) && DANGEROUS.test(n)) runtime.set(n, { name: n, granted: false, locked: false });
    o.perms = [...runtime.values()].filter((p) => DANGEROUS.test(p.name) || p.name.startsWith('android.permission.'))
      .sort((a, b) => a.name.localeCompare(b.name));
    o.pids = (parts[1] || '').trim().split(/\s+/).filter((x) => /^\d+$/.test(x));
    o.ramKb = parseInt(((parts[2] || '').match(/TOTAL\s+(?:PSS:\s*)?(\d+)/) || [])[1], 10) || 0;
    o.boot = /\//.test(parts[3] || '');
    return o;
  }
  // a permission in words, by the group a person knows it as (Settings → Apps → Permissions says the same)
  const PERM_NAMES = [[/RECORD_AUDIO/, 'Micro'], [/CAMERA/, 'Máy ảnh'], [/LOCATION/, 'Vị trí'], [/STORAGE|READ_MEDIA_/, 'Tệp, ảnh và video'],
    [/POST_NOTIFICATIONS/, 'Thông báo'], [/CONTACTS|GET_ACCOUNTS/, 'Danh bạ và tài khoản'], [/PHONE|CALL/, 'Điện thoại'], [/SMS/, 'Tin nhắn'],
    [/CALENDAR/, 'Lịch'], [/BLUETOOTH_|NEARBY_WIFI/, 'Thiết bị ở gần'], [/BODY_SENSORS|ACTIVITY_RECOGNITION/, 'Cảm biến và hoạt động']];
  const permName = (n) => (PERM_NAMES.find(([re]) => re.test(n)) || [0, n.replace(/^.*\./, '')])[1];

  // 🔁 "package/receiver" lines: the packages that start when the TV boots
  const bootRead = (text) => new Set(String(text || '').split('\n').map((l) => l.trim().split('/')[0]).filter((p) => /^[\w.]+$/.test(p) && p.includes('.')));

  // ⚙️ "<table> <name>=<value>" lines of tvshell.SETTINGS_READ
  function settingsRead(text) {
    const out = [];
    for (const l of String(text || '').replace(/\r/g, '').split('\n')) {
      const m = /^(global|secure|system) ([^=\s]+)=(.*)$/.exec(l);
      if (m) out.push({ table: m[1], name: m[2], value: m[3] });
    }
    return out;
  }

  root.capsRead = capsRead; root.sizeName = sizeName; root.appInfoRead = appInfoRead; root.permName = permName; root.bootRead = bootRead; root.settingsRead = settingsRead;
  root.cleanFreed = cleanFreed; root.logNext = logNext; root.logLevel = logLevel; root.readTvset = readTvset; root.densityChoices = densityChoices;
  if (typeof module !== 'undefined') module.exports = { cleanFreed, logNext, logLevel, readTvset, densityChoices, capsRead, sizeName, appInfoRead, permName, bootRead, settingsRead };
})(typeof window !== 'undefined' ? window : globalThis);
