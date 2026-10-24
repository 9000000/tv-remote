'use strict';
(function () {
  var K = new URLSearchParams(location.search).get('k') || '';
  var VI = (navigator.language || '').toLowerCase().indexOf('vi') === 0;
  var WORDS = {
    vi: { search: 'Tìm bài', queue: 'Hàng đợi', settings: 'Cài đặt', ph: 'Tên bài hát hoặc ca sĩ', go: 'Tìm', add: '+ Thêm', added: 'Đã thêm', who: 'Tên: ', noname: 'Đặt tên',
      searching: 'Đang tìm…', none: 'Không thấy bài nào.', fail: 'Không tìm được lúc này. Thử lại sau ít phút.', quota: 'Khóa API hết lượt hôm nay, đang dùng kết quả công khai.', badkey: 'Khóa API không dùng được, đang dùng kết quả công khai.', net: 'Không gọi được API, đang dùng kết quả công khai.',
      viaApi: 'Tìm bằng API chính thức', viaWeb: 'Tìm bằng kết quả công khai của YouTube',
      idle: 'Chưa phát bài nào. Tìm bài và bấm Thêm.', pause: '⏸', play: '▶', skip: '⏭', replay: '↺', voldn: '−', volup: '+', paused: 'Đang tạm dừng', by: 'Chọn bởi ', vol: 'Âm lượng ',
      qempty: 'Hàng đợi trống.', up: 'Lên', down: 'Xuống', del: 'Xóa', hist: 'Đã hát', again: 'Hát lại', full: 'Hàng đợi đầy (100 bài).',
      embed: 'Đã bỏ qua: chủ video không cho phát ở đây\n', gone: 'Đã bỏ qua: video không còn\n', err: 'Đã bỏ qua: video không phát được\n',
      nameH: 'Tên của bạn', save: 'Lưu', keyH: 'Khóa API YouTube (không bắt buộc)', keyNote: 'Có khóa thì tìm bằng API chính thức của YouTube (miễn phí khoảng 100 lượt tìm mỗi ngày). Không có thì dùng kết quả công khai. Khóa chỉ lưu trên TV, không lưu ở máy này.',
      keyPh: 'Dán khóa API', keyOn: 'Đã có khóa trên TV. Hết lượt hôm nay: ', keyOff: 'Chưa có khóa.', keyClear: 'Xóa khóa', keySaved: 'Đã lưu khóa.', times: ' lần',
      micH: 'Micro', micNote: 'App chưa thu giọng hát từ điện thoại: mic qua Wi-Fi bị trễ 100-300 ms nên hát lệch nhạc. Hãy cắm mic karaoke Bluetooth hoặc USB vào TV hoặc loa.', on: 'Đã nối với TV', off: 'Mất kết nối, đang nối lại…' },
    en: { search: 'Search', queue: 'Queue', settings: 'Settings', ph: 'Song or singer', go: 'Search', add: '+ Add', added: 'Added', who: 'Name: ', noname: 'Set a name',
      searching: 'Searching…', none: 'No songs found.', fail: 'Search is not working right now. Try again in a few minutes.', quota: 'The API key is out of quota for today, using public results.', badkey: 'The API key does not work, using public results.', net: 'Could not reach the API, using public results.',
      viaApi: 'Searched with the official API', viaWeb: 'Searched YouTube public results',
      idle: 'Nothing is playing. Search a song and tap Add.', pause: '⏸', play: '▶', skip: '⏭', replay: '↺', voldn: '−', volup: '+', paused: 'Paused', by: 'Added by ', vol: 'Volume ',
      qempty: 'The queue is empty.', up: 'Up', down: 'Down', del: 'Remove', hist: 'Sung', again: 'Sing again', full: 'The queue is full (100 songs).',
      embed: 'Skipped: the owner does not allow this video here\n', gone: 'Skipped: this video is not available\n', err: 'Skipped: this video would not play\n',
      nameH: 'Your name', save: 'Save', keyH: 'YouTube API key (optional)', keyNote: 'With a key, search uses YouTube\'s official API (about 100 searches a day free). Without it, public results are used. The key is kept on the TV, not on this phone.',
      keyPh: 'Paste the API key', keyOn: 'A key is set on the TV. Quota hits today: ', keyOff: 'No key set.', keyClear: 'Remove key', keySaved: 'Key saved.', times: '',
      micH: 'Microphone', micNote: 'The app does not take your voice from the phone: a mic over Wi-Fi lags 100-300 ms, so singing drifts off the music. Plug a Bluetooth or USB karaoke mic into the TV or the speaker.', on: 'Connected to the TV', off: 'Disconnected, reconnecting…' }
  };
  var W = VI ? WORDS.vi : WORDS.en;
  var $ = function (id) { return document.getElementById(id); };
  document.documentElement.lang = VI ? 'vi' : 'en';

  function store(k, v) { try { if (v === undefined) return localStorage.getItem(k); localStorage.setItem(k, v); } catch (e) { return null; } }
  var cid = store('cid') || (Math.random().toString(36).slice(2) + Date.now().toString(36)).slice(0, 20);
  store('cid', cid);
  var name = store('name') || '';
  var state = { queue: [], history: [], now: null, paused: false, vol: 0 };
  var ws = null, retry = 0, pingT = 0, results = [], added = {}, lastSource = '', quotaHits = 0;

  // ---- words ----
  document.querySelectorAll('#tabs button').forEach(function (b) { b.textContent = W[b.dataset.tab]; });
  $('q').placeholder = W.ph; $('sb').textContent = W.go; $('idle').textContent = W.idle; $('qempty').textContent = W.qempty;
  $('bSkip').textContent = W.skip; $('bReplay').textContent = W.replay; $('bVolDn').textContent = W.voldn; $('bVolUp').textContent = W.volup;
  $('bSkip').title = $('bSkip').ariaLabel = VI ? 'Bỏ qua bài' : 'Skip'; $('bReplay').title = $('bReplay').ariaLabel = VI ? 'Hát lại' : 'Sing again';
  $('bVolDn').title = $('bVolDn').ariaLabel = VI ? 'Giảm âm lượng' : 'Volume down'; $('bVolUp').title = $('bVolUp').ariaLabel = VI ? 'Tăng âm lượng' : 'Volume up';
  $('hh').textContent = W.hist;
  $('nameH').textContent = W.nameH; $('nameOk').textContent = W.save; $('nameIn').placeholder = W.noname;
  $('keyH').textContent = W.keyH; $('keyNote').textContent = W.keyNote; $('keyIn').placeholder = W.keyPh; $('keyOk').textContent = W.save; $('keyClear').textContent = W.keyClear;
  $('micH').textContent = W.micH; $('micNote').textContent = W.micNote;
  function showName() { $('who').textContent = name ? W.who + name : W.noname; $('nameIn').value = name; }
  showName();

  // ---- tabs ----
  function tab(t) {
    document.querySelectorAll('#tabs button').forEach(function (b) { b.setAttribute('aria-selected', b.dataset.tab === t ? 'true' : 'false'); });
    ['search', 'queue', 'settings'].forEach(function (n) { $('tab-' + n).hidden = n !== t; });
    if (t === 'settings') status();
  }
  $('tabs').addEventListener('click', function (e) { var b = e.target.closest('button'); if (b) tab(b.dataset.tab); });
  $('who').onclick = function () { tab('settings'); $('nameIn').focus(); };

  // ---- the TV ----
  function send(o) { if (ws && ws.readyState === 1) ws.send(JSON.stringify(o)); }
  function connect() {
    var p = location.protocol === 'https:' ? 'wss://' : 'ws://';
    ws = new WebSocket(p + location.host + '/ws?k=' + encodeURIComponent(K));
    ws.onopen = function () { retry = 0; $('conn').classList.add('on'); $('conn').title = W.on; send({ t: 'hello', cid: cid, name: name || (VI ? 'Khách' : 'Guest') });
      clearInterval(pingT); pingT = setInterval(function () { send({ t: 'ping' }); }, 20000); };
    ws.onclose = function () { $('conn').classList.remove('on'); $('conn').title = W.off; clearInterval(pingT); setTimeout(connect, Math.min(5000, 500 + retry++ * 700)); };
    ws.onmessage = function (e) {
      var m; try { m = JSON.parse(e.data); } catch (x) { return; }
      if (m.t === 'state') { state = m; draw(); }
      else if (m.t === 'notice') toast(m.code === 'full' ? W.full : (W[m.code] || W.err) + (m.title || ''));
    };
  }
  var toastT = 0;
  function toast(t) { var el = $('toast'); el.textContent = t; el.hidden = false; clearTimeout(toastT); toastT = setTimeout(function () { el.hidden = true; }, 7000); }

  function el(tag, cls, text) { var e = document.createElement(tag); if (cls) e.className = cls; if (text !== undefined) e.textContent = text; return e; }
  function btn(text, label, go, cls) { var b = el('button', cls || '', text); b.type = 'button'; if (label) { b.title = label; b.setAttribute('aria-label', label); } b.onclick = go; return b; }
  function thumb(src) { var i = el('img'); i.alt = ''; i.loading = 'lazy'; i.width = 96; i.height = 54; i.src = src; return i; }

  function draw() {
    var n = state.now;
    $('now').hidden = !n; $('idle').hidden = !!n;
    if (n) {
      if ($('nowImg').getAttribute('src') !== n.thumb) $('nowImg').src = n.thumb;
      $('nowTitle').textContent = n.title; $('nowBy').textContent = W.by + n.by + (state.paused ? '  ·  ' + W.paused : '') + '  ·  ' + W.vol + state.vol + '%';
      $('bPause').textContent = state.paused ? W.play : W.pause; $('bPause').classList.toggle('go', state.paused);
      $('bPause').title = $('bPause').ariaLabel = state.paused ? (VI ? 'Tiếp tục' : 'Resume') : (VI ? 'Tạm dừng' : 'Pause');
    }
    $('tabs').children[1].textContent = W.queue + (state.queue.length ? ' (' + state.queue.length + ')' : '');
    var ul = $('queue'); ul.textContent = '';
    state.queue.forEach(function (e, i) {
      var mine = e.cid === cid, li = el('li', 'item');
      li.appendChild(thumb(e.thumb));
      var t = el('div', 't'); t.appendChild(el('b', '', (i + 1) + '. ' + e.title)); t.appendChild(el('span', '', e.by)); li.appendChild(t);
      if (mine) {
        var a = el('div', 'acts');
        var up = btn('↑', W.up, function () { send({ t: 'move', qid: e.qid, d: -1 }); }); up.disabled = i === 0;
        var dn = btn('↓', W.down, function () { send({ t: 'move', qid: e.qid, d: 1 }); }); dn.disabled = i === state.queue.length - 1;
        a.appendChild(up); a.appendChild(dn); a.appendChild(btn('✕', W.del, function () { send({ t: 'remove', qid: e.qid }); }));
        li.appendChild(a);
      }
      ul.appendChild(li);
    });
    $('qempty').hidden = state.queue.length > 0;
    var hl = $('history'); hl.textContent = ''; $('hh').hidden = !state.history.length;
    state.history.forEach(function (e) {
      var li = el('li', 'item one'); li.appendChild(thumb(e.thumb));
      var t = el('div', 't'); t.appendChild(el('b', '', e.title)); t.appendChild(el('span', '', e.by)); li.appendChild(t);
      var a = el('div', 'acts'); a.appendChild(btn(W.again, '', function () { addSong(e); }, 'add')); li.appendChild(a); hl.appendChild(li);
    });
    drawResults();
  }

  function addSong(s) {
    send({ t: 'add', vid: s.vid, title: s.title, channel: s.channel || '', len: s.len || '' });
    added[s.vid] = Date.now(); drawResults(); setTimeout(drawResults, 2100);
  }

  function drawResults() {
    var ul = $('results'); ul.textContent = '';
    results.forEach(function (s) {
      var li = el('li', 'item one'); li.appendChild(thumb(s.thumb));
      var t = el('div', 't'); t.appendChild(el('b', '', s.title)); t.appendChild(el('span', '', (s.channel || '') + (s.len ? '  ·  ' + s.len : ''))); li.appendChild(t);
      var done = added[s.vid] && Date.now() - added[s.vid] < 2000;
      var a = el('div', 'acts'); a.appendChild(btn(done ? W.added + ' ✓' : W.add, '', function () { addSong(s); }, 'add' + (done ? ' done' : ''))); li.appendChild(a);
      ul.appendChild(li);
    });
  }

  // ---- search ----
  function say(t, bad) { var m = $('smsg'); m.textContent = t; m.hidden = !t; m.className = 'note' + (bad ? ' bad' : ''); }
  $('sf').onsubmit = function (e) {
    e.preventDefault();
    var q = $('q').value.trim(); if (!q) return;
    say(W.searching); $('results').textContent = ''; document.activeElement && document.activeElement.blur();
    fetch('/api/search?k=' + encodeURIComponent(K) + '&q=' + encodeURIComponent(q)).then(function (r) { return r.json(); }).then(function (j) {
      results = j.songs || []; lastSource = j.source; quotaHits = j.quota || 0; drawResults();
      if (!j.ok) say(W.fail, true);
      else if (!results.length) say(W.none);
      else say((j.note === 'quota' ? W.quota : j.note === 'key' ? W.badkey : j.note === 'net' ? W.net : (j.source === 'api' ? W.viaApi : W.viaWeb)), !!j.note);
    }).catch(function () { say(W.fail, true); });
  };

  // ---- controls ----
  $('bPause').onclick = function () { send({ t: 'pause', on: !state.paused }); };
  $('bSkip').onclick = function () { send({ t: 'skip' }); };
  $('bReplay').onclick = function () { send({ t: 'replay' }); };
  $('bVolDn').onclick = function () { send({ t: 'vol', d: -1 }); };
  $('bVolUp').onclick = function () { send({ t: 'vol', d: 1 }); };

  // ---- settings ----
  $('nf').onsubmit = function (e) { e.preventDefault(); name = $('nameIn').value.trim().slice(0, 16); store('name', name); showName(); send({ t: 'name', name: name }); toast0(VI ? 'Đã lưu tên.' : 'Name saved.'); };
  function toast0(t) { var m = $('keyState'); m.textContent = t; }
  function status() {
    fetch('/api/status?k=' + encodeURIComponent(K)).then(function (r) { return r.json(); }).then(function (j) {
      $('keyState').textContent = j.hasKey ? W.keyOn + j.quota + W.times : W.keyOff; $('keyClear').hidden = !j.hasKey;
    }).catch(function () { });
  }
  $('kf').onsubmit = function (e) {
    e.preventDefault();
    var v = $('keyIn').value.trim(); if (!v) return;
    fetch('/api/key?k=' + encodeURIComponent(K), { method: 'POST', body: v }).then(function () { $('keyIn').value = ''; status(); toast0(W.keySaved); });
  };
  $('keyClear').onclick = function () { fetch('/api/key?k=' + encodeURIComponent(K), { method: 'POST', body: '' }).then(status); };

  tab('search'); draw(); connect();
})();
