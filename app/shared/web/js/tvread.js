// Reading what the TV printed for the Apps tab and the TV card (the recipes are in TvShell.kt). Kept apart
// from the page so node can test it: node --test app/shared/tests/
((root) => {
  const T = (...a) => root.T(...a);                                  // i18n.js on the page; a test gives one
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
    const o = { sdk: 0, dnsMode: '', dnsHost: '', dnsOff: null, sleep: null, saver: false, saverAfter: null, physical: 0, override: 0, home: '', launchers: [] };
    const val = (v) => (v === 'null' || v === undefined ? '' : v);
    for (const raw of String(text || '').replace(/\r/g, '').split('\n')) {
      const l = raw.trim(), sp = l.indexOf(' '), k = sp < 0 ? l : l.slice(0, sp), v = sp < 0 ? '' : l.slice(sp + 1).trim();
      if (k === 'sdk') o.sdk = parseInt(v, 10) || 0;
      else if (k === 'dns_mode') o.dnsMode = val(v);
      else if (k === 'dns_host') o.dnsHost = val(v);
      else if (k === 'dns_off') { const m = /^(\S+) (\d+)$/.exec(val(v)); if (m) o.dnsOff = { host: m[1], at: Number(m[2]) * 1000 }; }   // TV Setup turned a filter off (DnsWatch.kt)
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
    return [[T('tvread.size_small'), Math.round(physical * 0.9)], [T('tvread.size_normal'), 'reset'], [T('tvread.size_large'), Math.round(physical * 1.1)], [T('tvread.size_larger'), Math.round(physical * 1.2)]];
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
    let sect = '', user = null, first = null;                         // a TV with a second profile lists each user's grants: the first (0) is the one pm changes
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
      else if ((m = /^User (\d+):/.exec(t))) { sect = 'user'; user = m[1]; if (first === null) first = user; }
      else if (ind >= 6 && sect === 'requested' && /^[\w.]+$/.test(t.split(':')[0])) requested.push(t.split(/[:,\s]/)[0]);
      else if (ind >= 6 && sect === 'install' && (m = /^([\w.]+): granted=/.exec(t))) install.add(m[1]);
      else if (ind >= 8 && sect === 'runtime' && user === first && (m = /^([\w.]+): granted=(true|false)(.*)$/.exec(t)))
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
  const PERM_NAMES = [[/RECORD_AUDIO/, 'tvread.perm_mic'], [/CAMERA/, 'tvread.perm_camera'], [/LOCATION/, 'tvread.perm_location'], [/STORAGE|READ_MEDIA_/, 'tvread.perm_files'],
    [/POST_NOTIFICATIONS/, 'tvread.perm_notif'], [/CONTACTS|GET_ACCOUNTS/, 'tvread.perm_contacts'], [/PHONE|CALL/, 'tvread.perm_phone'], [/SMS/, 'tvread.perm_sms'],
    [/CALENDAR/, 'tvread.perm_calendar'], [/BLUETOOTH_|NEARBY_WIFI/, 'tvread.perm_nearby'], [/BODY_SENSORS|ACTIVITY_RECOGNITION/, 'tvread.perm_sensors']];
  const permName = (n) => { const p = PERM_NAMES.find(([re]) => re.test(n)); return p ? T(p[1]) : n.replace(/^.*\./, ''); };

  // 🔁 "package/receiver" lines: the packages that start when the TV boots
  const bootRead = (text) => new Set(String(text || '').split('\n').map((l) => l.trim().split('/')[0]).filter((p) => /^[\w.]+$/.test(p) && p.includes('.')));

  // ⚙️ "<table> <name>=<value>" lines of tvshell.SETTINGS_READ
  function settingsRead(text) {
    const out = [];
    for (const l of String(text || '').replace(/\r/g, '').split('\n')) {
      const m = /^(global|secure|system) ([^=\s]+)=(.*)$/.exec(l);
      if (m) out.push({ table: m[1], name: m[2], value: m[3] });
      const v = /^vol (\d) .*volume is (\d+) in range \[(\d+)\.\.(\d+)\]/.exec(l);   // the live volume, over the saved key
      if (v) {
        const name = v[1] === '1' ? 'volume_system' : 'volume_music', live = { value: v[2], min: Number(v[3]), max: Number(v[4]) };
        const s = out.find((x) => x.table === 'system' && x.name === name);
        if (s) Object.assign(s, live); else out.push({ table: 'system', name, ...live });
      }
    }
    return out;
  }

  // 🗂️ The settings people look for, in words, by topic, their values read out (1 → On, 600000 → 10 min); the rest of Android's
  // hundreds of internal keys go under "Other", their names made readable. [group, id of its words, kind]: kind says how to read the value.
  const SET_GROUPS = { screen: 'tvread.grp_screen', net: 'tvread.grp_net', dev: 'tvread.grp_dev',
    apps: 'tvread.grp_apps', sound: 'tvread.grp_sound', hdmi: 'tvread.grp_hdmi', time: 'tvread.grp_time',
    a11y: 'tvread.grp_a11y', device: 'tvread.grp_device', other: 'tvread.grp_other' };
  const SET_INFO = {
    screen_off_timeout: ['screen', 'tvread.set_screen_off_timeout', 'ms'], sleep_timeout: ['screen', 'tvread.set_sleep_timeout', 'ms'],
    attentive_timeout: ['screen', 'tvread.set_attentive_timeout', 'ms'],
    screensaver_enabled: ['screen', 'tvread.set_screensaver_enabled', 'bool'], screensaver_components: ['screen', 'tvread.set_screensaver_components', 'comp'],
    screensaver_activate_on_sleep: ['screen', 'tvread.set_screensaver_activate_on_sleep', 'bool'],
    stay_on_while_plugged_in: ['screen', 'tvread.set_stay_on_while_plugged_in', 'bool'],
    screen_brightness: ['screen', 'tvread.set_screen_brightness', 'pct255'], screen_brightness_mode: ['screen', 'tvread.set_screen_brightness_mode', 'bool'],
    display_density_forced: ['screen', 'tvread.set_display_density_forced', 'dpi'],
    user_disabled_hdr_formats: ['screen', 'tvread.set_user_disabled_hdr_formats', 'text'],
    window_animation_scale: ['screen', 'tvread.set_window_animation_scale', 'scale'], transition_animation_scale: ['screen', 'tvread.set_transition_animation_scale', 'scale'],
    animator_duration_scale: ['screen', 'tvread.set_animator_duration_scale', 'scale'],
    wifi_on: ['net', 'tvread.set_wifi_on', 'bool'], bluetooth_on: ['net', 'tvread.set_bluetooth_on', 'bool'], airplane_mode_on: ['net', 'tvread.set_airplane_mode_on', 'bool'],
    private_dns_mode: ['net', 'tvread.set_private_dns_mode', 'dns'], private_dns_specifier: ['net', 'tvread.set_private_dns_specifier', 'text'],
    http_proxy: ['net', 'tvread.set_http_proxy', 'text'], wifi_scan_always_enabled: ['net', 'tvread.set_wifi_scan_always_enabled', 'bool'],
    adb_enabled: ['dev', 'tvread.set_adb_enabled', 'bool'], adb_wifi_enabled: ['dev', 'tvread.set_adb_wifi_enabled', 'bool'],
    development_settings_enabled: ['dev', 'tvread.set_development_settings_enabled', 'bool'],
    install_non_market_apps: ['dev', 'tvread.set_install_non_market_apps', 'bool'],
    package_verifier_enable: ['dev', 'tvread.set_package_verifier_enable', 'bool'],
    verifier_verify_adb_installs: ['dev', 'tvread.set_verifier_verify_adb_installs', 'bool'],
    mock_location: ['dev', 'tvread.set_mock_location', 'bool'], always_finish_activities: ['dev', 'tvread.set_always_finish_activities', 'bool'],
    default_input_method: ['apps', 'tvread.set_default_input_method', 'comp'], voice_interaction_service: ['apps', 'tvread.set_voice_interaction_service', 'comp'],
    voice_recognition_service: ['apps', 'tvread.set_voice_recognition_service', 'comp'], autofill_service: ['apps', 'tvread.set_autofill_service', 'comp'],
    backup_enabled: ['apps', 'tvread.set_backup_enabled', 'bool'], location_mode: ['apps', 'tvread.set_location_mode', 'loc'],
    sound_effects_enabled: ['sound', 'tvread.set_sound_effects_enabled', 'bool'], volume_music: ['sound', 'tvread.set_volume_music', 'num'],
    volume_system: ['sound', 'tvread.set_volume_system', 'num'], encoded_surround_output: ['sound', 'tvread.set_encoded_surround_output', 'surround'],
    encoded_surround_output_enabled_formats: ['sound', 'tvread.set_encoded_surround_output_enabled_formats', 'text'],
    auto_time: ['time', 'tvread.set_auto_time', 'bool'], auto_time_zone: ['time', 'tvread.set_auto_time_zone', 'bool'],
    time_12_24: ['time', 'tvread.set_time_12_24', 'h'], system_locales: ['time', 'tvread.set_system_locales', 'text'],
    accessibility_enabled: ['a11y', 'tvread.set_accessibility_enabled', 'bool'], touch_exploration_enabled: ['a11y', 'tvread.set_touch_exploration_enabled', 'bool'],
    enabled_accessibility_services: ['a11y', 'tvread.set_enabled_accessibility_services', 'comp'], font_scale: ['a11y', 'tvread.set_font_scale', 'scale'],
    accessibility_display_magnification_enabled: ['a11y', 'tvread.set_accessibility_display_magnification_enabled', 'bool'], high_text_contrast_enabled: ['a11y', 'tvread.set_high_text_contrast_enabled', 'bool'],
    accessibility_captioning_enabled: ['a11y', 'tvread.set_accessibility_captioning_enabled', 'bool'],
    device_name: ['device', 'tvread.set_device_name', 'text'], bluetooth_name: ['device', 'tvread.set_bluetooth_name', 'text'],
    bluetooth_address: ['device', 'tvread.set_bluetooth_address', 'text'], android_id: ['device', 'tvread.set_android_id', 'text'],
    hdmi_control_enabled: ['hdmi', 'tvread.set_hdmi_control_enabled', 'bool'],
    hdmi_control_volume_control_enabled: ['hdmi', 'tvread.set_hdmi_control_volume_control_enabled', 'bool'],
    hdmi_control_auto_wakeup_enabled: ['hdmi', 'tvread.set_hdmi_control_auto_wakeup_enabled', 'bool'],
    hdmi_control_auto_device_off_enabled: ['hdmi', 'tvread.set_hdmi_control_auto_device_off_enabled', 'bool'],
    earc_control_enabled: ['hdmi', 'tvread.set_earc_control_enabled', 'bool'], earc_status: ['hdmi', 'tvread.set_earc_status', 'num'],
    nrdp_external_surround_sound_enabled: ['sound', 'tvread.set_nrdp_external_surround_sound_enabled', 'bool'],
    long_press_timeout: ['a11y', 'tvread.set_long_press_timeout', 'press'], pointer_speed: ['a11y', 'tvread.set_pointer_speed', 'speed'],
    show_ime_with_hard_keyboard: ['apps', 'tvread.set_show_ime_with_hard_keyboard', 'bool'],
    heads_up_notifications_enabled: ['apps', 'tvread.set_heads_up_notifications_enabled', 'bool'], notification_badging: ['apps', 'tvread.set_notification_badging', 'bool'],
    tts_default_synth: ['apps', 'tvread.set_tts_default_synth', 'comp'], enabled_input_methods: ['apps', 'tvread.set_enabled_input_methods', 'comp'],
    always_on_vpn_app: ['net', 'tvread.set_always_on_vpn_app', 'comp'], always_on_vpn_lockdown: ['net', 'tvread.set_always_on_vpn_lockdown', 'bool'],
    screensaver_default_component: ['screen', 'tvread.set_screensaver_default_component', 'comp'],
    maintenance_window: ['device', 'tvread.set_maintenance_window', 'text'], tv_user_setup_complete: ['device', 'tvread.set_tv_user_setup_complete', 'bool'],
    boot_count: ['device', 'tvread.set_boot_count', 'num'], user_setup_complete: ['device', 'tvread.set_user_setup_complete', 'bool'],
  };
  const SET_KEYS = Object.keys(SET_INFO);
  function msText(ms) {
    if (ms < 0 || ms >= 2147483647) return T('tvread.never');
    const m = ms / 60000;
    if (m >= 60 && m % 60 === 0) return T('tvread.hours_n', { n: m / 60 });
    if (m >= 1 && Number.isInteger(m)) return T('tvread.minutes_n', { n: m });
    return T('tvread.seconds_n', { n: Math.round(ms / 1000) });
  }
  function settingValue(kind, v) {
    const on = T('tvread.on'), off = T('tvread.off'), auto = T('tvread.automatic');
    if (v === 'null' || v === '') return kind === 'text' || kind === 'comp' ? T('tvread.none') : T('tvread.default');
    const n = Number(v);
    switch (kind) {
      case 'bool': return /^-?\d+$/.test(v) ? (n !== 0 ? on : off) : v === 'true' ? on : v === 'false' ? off : v;
      case 'ms': return Number.isFinite(n) ? msText(n) : v;
      case 'scale': return n === 0 ? off : Number.isFinite(n) ? n + '×' : v;
      case 'pct255': return Number.isFinite(n) ? Math.round(n / 2.55) + '%' : v;
      case 'dpi': return v + ' dpi';
      case 'press': return Number.isFinite(n) ? T('tvread.seconds_n', { n: (n / 1000).toLocaleString(root.LANG || 'vi') }) : v;
      case 'speed': return Number.isFinite(n) ? (n > 0 ? '+' + n : String(n)) : v;
      case 'dns': return { off: off, opportunistic: auto, hostname: T('tvread.own_server') }[v] || v;
      case 'loc': return [off, T('tvread.device_only'), T('tvread.battery_saving'), T('tvread.high_accuracy')][n] || v;
      case 'surround': return [auto, T('tvread.never'), T('tvread.always'), T('tvread.manual')][n] || v;
      case 'h': return v === '24' ? T('tvread.h24') : v === '12' ? T('tvread.h12') : v;
      case 'comp': return v.split(':').map((c) => c.split('/')[0]).join(', ');   // "pkg/.Service": the app is what matters
      default: return v;
    }
  }
  // one row of settingsRead in words: { group, title, value } (value as read out; s.value stays the raw one)
  function settingInfo(s) {
    const i = SET_INFO[s.name];
    if (i) {
      const out = { group: i[0], groupName: T(SET_GROUPS[i[0]]), title: T(i[1]), value: settingValue(i[2], s.value), known: true, order: SET_KEYS.indexOf(s.name) };
      if (i[2] === 'comp' && s.value && s.value !== 'null') out.pkgs = out.value.split(', ');   // the page puts the apps' names in
      return out;
    }
    const words = s.name.replace(/[_.:]+/g, ' ').trim();
    return { group: 'other', groupName: T(SET_GROUPS.other), title: words.charAt(0).toUpperCase() + words.slice(1), value: s.value === 'null' ? '—' : s.value, known: false, order: 0 };
  }

  // ✎ What the list lets a person change: simple settings, by a choice or a toggle. SET_LOCKED is TvShell.SET_LOCKED (the
  // server checks it too): settings that could cut the TV off from the remotes or break it stay read-only, with a 🔒.
  const SET_LOCKED = /^.*(adb|develop|debug|provision|setup|verif|install|package|vpn|dns|proxy|wifi|airplane|bluetooth|mobile|tether|captive|ntp|lock|password|policy|service|input_method|accessibility_enabled|touch_exploration|location|android_id|_uri|component|hidden_api|database|locale|density|volume|boot|hdr|formats|user|fac|zram|usb_mass|netstats|standby|safe_volume|shop|tuner|tcl|miracast|always_on_vpn|maintenance|earc_status).*$/i;
  // ADB and Wireless debugging: on, never off (TvShell.ON_ONLY): turning them on only adds a way in for the remotes
  const ON_ONLY = { adb_enabled: '1', adb_wifi_enabled: '1' };
  const VOLUME = /^volume_(system|music)$/;                    // the others follow the media volume on a TV
  const NUMBER = /^-?\d+(\.\d+)?$/;
  // { locked } · { kind: 'bool', on, off } · { kind: 'pick', values } · { kind: 'text', tech } (tech: Android's own internal key) · {} nothing simple to set
  function settingEdit(s) {
    if (ON_ONLY[s.name]) return s.value === ON_ONLY[s.name] || s.table !== 'global' ? { locked: true } : { kind: 'turnon', on: ON_ONLY[s.name] };
    if (VOLUME.test(s.name) && s.table === 'system') return { kind: 'step', min: s.min ?? 0, max: s.max ?? 100 };   // through the volume command (TvShell.VOLUME)
    if (SET_LOCKED.test(s.name)) return { locked: true };
    const i = SET_INFO[s.name], kind = i ? i[2] : NUMBER.test(s.value) ? 'raw' : '';
    const words = s.value === 'true' || s.value === 'false';
    switch (kind) {
      case 'bool': return { kind: 'bool', on: words ? 'true' : s.name === 'stay_on_while_plugged_in' ? '7' : '1', off: words ? 'false' : '0' };   // 7: on every kind of power
      case 'ms': return { kind: 'pick', values: [5, 10, 15, 30, 60, 120, 240].map((m) => String(m * 60000)).concat(s.name === 'screen_off_timeout' ? '2147483647' : '-1') };
      case 'scale': return { kind: 'pick', values: s.name === 'font_scale' ? ['0.85', '1.0', '1.15', '1.3'] : ['0', '0.5', '1.0', '1.5', '2.0'] };
      case 'pct255': return { kind: 'range', min: 0, max: 255 };
      case 'h': return { kind: 'pick', values: ['12', '24'] };
      case 'press': return { kind: 'pick', values: ['300', '400', '600', '1000', '1500'] };
      case 'speed': return { kind: 'pick', values: ['-5', '-3', '0', '3', '5'] };
      case 'surround': return { kind: 'pick', values: ['0', '1', '2', '3'] };
      case 'text': return s.name === 'device_name' ? { kind: 'text' } : {};
      case 'raw': return { kind: 'text', tech: true };
    }
    return {};
  }
  const settingWord = (s, v) => settingValue((SET_INFO[s.name] || [])[2], v);
  root.SET_GROUPS = SET_GROUPS; root.settingInfo = settingInfo; root.settingEdit = settingEdit; root.settingWord = settingWord;

  root.capsRead = capsRead; root.sizeName = sizeName; root.appInfoRead = appInfoRead; root.permName = permName; root.bootRead = bootRead; root.settingsRead = settingsRead;
  root.cleanFreed = cleanFreed; root.logNext = logNext; root.logLevel = logLevel; root.readTvset = readTvset; root.densityChoices = densityChoices;
  if (typeof module !== 'undefined') module.exports = { cleanFreed, logNext, logLevel, readTvset, densityChoices, capsRead, sizeName, appInfoRead, permName, bootRead, settingsRead, settingInfo, SET_GROUPS, settingEdit };
})(typeof window !== 'undefined' ? window : globalThis);
