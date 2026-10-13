// History for the Health tab, shared by the three remotes: how long the TV was used and by which apps, day by day, and how
// memory, CPU and storage moved over time. The TV Setup app on the TV records it (see Stats.kt); this only draws it.
//
//   renderStats(root, data, vi, reload)
//     data    { usage: { "2026-10-13": { "pkg": seconds } }, samples: [[t, on, cpu, memUsed, memTotal, diskUsed, diskTotal, pkg], ...],
//               labels: { pkg: name }, collector: bool, days }
//     reload  (days) => void, called when the range buttons are used
(() => {
  const css = `
  .st-range { display:flex; gap:8px; margin:0 0 12px; flex-wrap:wrap; } .st-range button { min-height:40px; padding:0 14px; border-radius:12px; flex:none; flex-direction:row; }
  .st-range button.on { background:var(--accent); color:var(--accent-ink, #fff); }
  .st-sum { display:flex; gap:18px; flex-wrap:wrap; margin:0 0 12px; } .st-sum b { display:block; font-size:20px; } .st-sum span { color:var(--muted); font-size:12px; }
  .st-days { display:flex; align-items:flex-end; gap:4px; height:120px; } .st-day { flex:1; min-width:0; display:flex; flex-direction:column; align-items:center; justify-content:flex-end; height:100%; cursor:pointer; }
  .st-day i { display:block; width:100%; max-width:34px; min-height:2px; border-radius:6px 6px 2px 2px; background:var(--accent); opacity:.55; } .st-day.on i { opacity:1; }
  .st-day small { font-size:10px; color:var(--muted); margin-top:4px; white-space:nowrap; } .st-day.on small { color:var(--ink); font-weight:700; }
  .st-app { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:2px 10px; padding:7px 0; border-top:1px solid var(--line); font-size:14px; } .st-app:first-child { border-top:0; }
  .st-app span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; } .st-app b { font-variant-numeric:tabular-nums; } .st-app i { grid-column:1 / -1; height:6px; border-radius:3px; background:var(--accent); opacity:.7; }
  .st-chart { position:relative; margin-top:6px; } .st-chart svg { display:block; width:100%; height:150px; touch-action:pan-y; } .st-leg { display:flex; gap:14px; flex-wrap:wrap; font-size:12px; color:var(--muted); margin:8px 0 0; }
  .st-leg i { display:inline-block; width:10px; height:10px; border-radius:3px; margin-right:5px; vertical-align:-1px; } .st-tip { font-size:13px; min-height:38px; margin-top:8px; } .st-axis { display:flex; justify-content:space-between; font-size:11px; color:var(--muted); }
  .st-note { color:var(--muted); font-size:13px; margin:0; } .st-range ~ section.card { margin-top:12px; }`;
  const RAM = '#f59e0b', CPU = '#5b8cff', DISK = '#22c55e';
  const el = (tag, props = {}, ...kids) => { const e = Object.assign(document.createElement(tag), props); kids.forEach((k) => k != null && e.append(k)); return e; };
  const pad = (n) => String(n).padStart(2, '0');
  const dayKey = (d) => d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());

  window.renderStats = (root, data, vi, reload) => {
    const T = (v, e) => vi ? v : e;
    if (!document.getElementById('st-css')) document.head.append(el('style', { id: 'st-css', textContent: css }));
    const dur = (s) => { const m = Math.round(s / 60); return m < 60 ? m + T(' phút', ' min') : Math.floor(m / 60) + T(' giờ ', ' h ') + pad(m % 60); };
    const name = (p) => (data.labels && data.labels[p]) || p.split('.').pop();
    const days = [], n = data.days || 7, today = new Date();
    for (let i = n - 1; i >= 0; i--) { const d = new Date(today); d.setDate(d.getDate() - i); days.push(dayKey(d)); }
    const total = (k) => Object.values((data.usage || {})[k] || {}).reduce((a, b) => a + b, 0);
    let picked = null;                                              // a day, or null for the whole range

    const range = el('div', { className: 'st-range' }, ...[7, 30].map((d) => el('button', { type: 'button', className: d === n ? 'on' : '', textContent: d + T(' ngày', ' days'), onclick: () => reload(d) })));
    const sum = el('div', { className: 'st-sum' }), bars = el('div', { className: 'st-days' }), apps = el('div'), appsTitle = el('h2'), chartBox = el('div'), chartTitle = el('h2');
    root.replaceChildren(range);
    if (!data.collector) {
      root.append(el('section', { className: 'card' }, el('p', { className: 'st-note', textContent: T(
        'Chưa có dữ liệu. Phần ghi lịch sử chạy trong app TV Setup trên TV (bản 2.6.0 trở lên): cài và mở app đó một lần, vài phút sau quay lại đây.',
        'No data yet. The history is recorded by the TV Setup app on the TV (2.6.0 or newer): install and open it once, then come back in a few minutes.') })));
      return;
    }
    root.append(el('section', { className: 'card' }, el('h2', { textContent: T('Thời gian dùng TV', 'TV time') }), sum, bars),
      el('section', { className: 'card' }, appsTitle, apps), el('section', { className: 'card' }, chartTitle, chartBox));

    const draw = () => {
      const all = days.map(total), max = Math.max(1, ...all), used = all.filter((s) => s > 60).length;
      const whole = all.reduce((a, b) => a + b, 0);
      sum.replaceChildren(el('div', {}, el('b', { textContent: dur(picked ? total(picked) : whole) }), el('span', { textContent: picked ? picked.slice(8) + '/' + picked.slice(5, 7) : n + T(' ngày qua', ' days') })),
        el('div', {}, el('b', { textContent: dur(used ? whole / used : 0) }), el('span', { textContent: T('trung bình mỗi ngày có dùng', 'average per day used') })));
      bars.replaceChildren(...days.map((k, i) => el('div', { className: 'st-day' + (k === picked ? ' on' : ''), title: k + ' · ' + dur(all[i]), onclick: () => { picked = picked === k ? null : k; draw(); } },
        el('i', { style: 'height:' + Math.round(all[i] * 100 / max) + '%' }), el('small', { textContent: n > 10 && i % 5 ? '' : k.slice(8) + '/' + k.slice(5, 7) }))));

      const per = {};
      (picked ? [picked] : days).forEach((k) => Object.entries((data.usage || {})[k] || {}).forEach(([p, s]) => { per[p] = (per[p] || 0) + s; }));
      const list = Object.entries(per).filter(([, s]) => s >= 60).sort((a, b) => b[1] - a[1]).slice(0, 12), top = list.length ? list[0][1] : 1;
      appsTitle.textContent = T('App dùng nhiều', 'Most used apps') + ' · ' + (picked ? picked.slice(8) + '/' + picked.slice(5, 7) : n + T(' ngày', ' days'));
      apps.replaceChildren(...(list.length ? list.map(([p, s]) => el('div', { className: 'st-app' }, el('span', { textContent: name(p) }), el('b', { textContent: dur(s) }), el('i', { style: 'width:' + Math.max(2, Math.round(s * 100 / top)) + '%' })))
        : [el('p', { className: 'st-note', textContent: T('Không có app nào được dùng quá một phút.', 'No app was used for more than a minute.') })]));

      // memory, CPU and storage over the picked day, or the last 24 hours
      let from, to;
      if (picked) { const d = new Date(picked + 'T00:00:00'); from = d.getTime() / 1000; to = from + 86400; } else { to = Date.now() / 1000; from = to - 86400; }
      const rows = (data.samples || []).filter((r) => r[0] >= from && r[0] <= to);
      chartTitle.textContent = 'RAM · CPU · ' + T('Bộ nhớ', 'Storage') + ' · ' + (picked ? picked.slice(8) + '/' + picked.slice(5, 7) : T('24 giờ qua', 'last 24 hours'));
      if (rows.length < 2) { chartBox.replaceChildren(el('p', { className: 'st-note', textContent: T('Chưa đủ số liệu cho khoảng này (TV ghi 5 phút một lần khi đang bật).', 'Not enough samples for this period (the TV records every 5 minutes while it is on).') })); return; }
      const W = 600, H = 150, x = (t) => (t - from) * W / (to - from), y = (pct) => H - 4 - pct * (H - 8) / 100;
      const line = (pick, color) => {                                // broken where the TV was off or asleep for a while
        let d = '', last = 0;
        rows.forEach((r) => { const v = pick(r); if (v == null) return; d += (r[0] - last > 900 ? 'M' : 'L') + x(r[0]).toFixed(1) + ' ' + y(v).toFixed(1); last = r[0]; });
        return '<path d="' + d + '" fill="none" stroke="' + color + '" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>';
      };
      const grid = [0, 25, 50, 75, 100].map((p) => '<line x1="0" x2="' + W + '" y1="' + y(p) + '" y2="' + y(p) + '" stroke="currentColor" stroke-opacity=".12"/>').join('');
      const svg = el('div', { className: 'st-chart', innerHTML: '<svg viewBox="0 0 ' + W + ' ' + H + '" preserveAspectRatio="none">' + grid
        + line((r) => r[6] ? r[5] * 100 / r[6] : null, DISK) + line((r) => r[4] ? r[3] * 100 / r[4] : null, RAM) + line((r) => r[1] ? r[2] : null, CPU)
        + '<line id="st-cur" y1="0" y2="' + H + '" stroke="currentColor" stroke-opacity=".5" visibility="hidden"/></svg>' });
      const hm = (t) => { const d = new Date(t * 1000); return pad(d.getHours()) + ':' + pad(d.getMinutes()); };
      const tip = el('div', { className: 'st-tip', textContent: T('Chạm vào biểu đồ để xem số liệu tại thời điểm đó.', 'Touch the chart to read the figures at that moment.') });
      const show = (e) => {
        const b = svg.getBoundingClientRect(), t = from + (e.clientX - b.left) * (to - from) / b.width;
        const r = rows.reduce((a, c) => Math.abs(c[0] - t) < Math.abs(a[0] - t) ? c : a);
        const cur = svg.querySelector('#st-cur'); cur.setAttribute('x1', x(r[0])); cur.setAttribute('x2', x(r[0])); cur.setAttribute('visibility', 'visible');
        tip.textContent = hm(r[0]) + ' · RAM ' + (r[3] / 1024).toFixed(1) + ' / ' + (r[4] / 1024).toFixed(1) + ' GB · CPU ' + (r[1] && r[2] != null ? r[2] + '%' : '–')
          + ' · ' + T('bộ nhớ ', 'storage ') + (r[5] / 1024).toFixed(1) + ' GB' + (r[1] ? (r[7] ? ' · ' + name(r[7]) : '') : ' · ' + T('TV đang ngủ', 'TV asleep'));
      };
      svg.addEventListener('pointerdown', show); svg.addEventListener('pointermove', (e) => { if (e.buttons || e.pointerType === 'touch') show(e); });
      const ram = rows.map((r) => r[3]), cpu = rows.filter((r) => r[1] && r[2] != null).map((r) => r[2]);
      chartBox.replaceChildren(svg, el('div', { className: 'st-axis' }, el('span', { textContent: hm(from) }), el('span', { textContent: hm((from + to) / 2) }), el('span', { textContent: hm(to) })),
        el('div', { className: 'st-leg' }, el('span', { innerHTML: '<i style="background:' + RAM + '"></i>RAM ' + (Math.min(...ram) / 1024).toFixed(1) + '–' + (Math.max(...ram) / 1024).toFixed(1) + ' GB' }),
          el('span', { innerHTML: '<i style="background:' + CPU + '"></i>CPU ' + (cpu.length ? Math.min(...cpu) + '–' + Math.max(...cpu) + '%' : '–') }),
          el('span', { innerHTML: '<i style="background:' + DISK + '"></i>' + T('Bộ nhớ ', 'Storage ') + (rows[rows.length - 1][5] / 1024).toFixed(1) + ' / ' + (rows[rows.length - 1][6] / 1024).toFixed(1) + ' GB' })), tip);
    };
    draw();
  };
})();
