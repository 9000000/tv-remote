// terminal tab: one shell command at a time on the TV, like `adb shell <command>`
const termHist = []; let termPos = 0;
// what was run and what came back, for the copy buttons: { cmd, out } per run, out null while it runs
const termRuns = [];
// Earlier commands, newest first, kept on this device: a list to pick from, since a phone keyboard has no ↑ ↓.
function termSaved() { try { return JSON.parse(localStorage.getItem('termhist') || '[]'); } catch { return []; } }
function termRemember(cmd) {
  let h = termSaved();
  if (cmd) { h = [cmd, ...h.filter((c) => c !== cmd)].slice(0, 30); try { localStorage.setItem('termhist', JSON.stringify(h)); } catch {} }
  put('#termhistbtn', 'hidden', !h.length);
}
// a popup rather than a <select>: a long command stays on one line there (its whole text in the title), and it can be searched
function termPick() {
  return new Promise((done) => {
    let picked = null;
    const h = termSaved();
    const q = el('input', { type: 'search', className: 'mono', placeholder: T('terminal.find'), spellcheck: false, autocomplete: 'off', autocapitalize: 'off' });
    const none = el('p', { className: 'empty', textContent: T('terminal.none_found'), hidden: true });
    const rows = h.map((c) => el('button', { type: 'button', className: 'item pick', title: c, onclick: () => { picked = c; d.close(); } },
      el('span', { className: 'mono', textContent: c })));
    const filter = () => { const s = q.value.trim().toLowerCase(); let n = 0;
      rows.forEach((r, i) => { r.hidden = !!s && !h[i].toLowerCase().includes(s); if (!r.hidden) n++; }); none.hidden = n > 0; };
    q.addEventListener('input', filter);
    q.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); rows.find((r) => !r.hidden)?.click(); } });
    const d = el('dialog', { className: 'ask termpick' }, el('div', { className: 'card' },
      UI.head(T('terminal.history'), el('button', { type: 'button', className: 'small iconbtn', textContent: '✕', title: T('common.close'),
        'aria-label': T('common.close'), onclick: () => d.close() })),
      q, el('div', { className: 'list termrows' }, ...rows), none));
    d.addEventListener('close', () => { d.remove(); done(picked); });
    document.body.append(d); d.showModal();
    if (matchMedia('(pointer: fine)').matches) q.focus(); else rows[0]?.focus();
  });
}
$('#termhistbtn').addEventListener('click', async () => {
  const c = await termPick(); if (c == null) return;
  $('#termin').value = c; $('#termin').focus();
});
const termText = (r) => '$ ' + r.cmd + '\n' + (r.out || '');
const termCopyBtn = (title, text) => el('button', { type: 'button', className: 'small iconbtn tcopy', textContent: '📋', title, 'aria-label': title,
  onclick: () => copyText(text()) });
$('#termcopy').addEventListener('click', () => {
  const done = termRuns.filter((r) => r.out != null);
  if (!done.length) { toast(T('terminal.nothing')); return; }
  copyText(done.map(termText).join(''));
});
$('#termclear').addEventListener('click', () => { termRuns.length = 0; $('#termout').replaceChildren(); });
$('#termf').addEventListener('submit', async (e) => {
  e.preventDefault();
  const cmd = $('#termin').value.trim(); if (!cmd) return;
  termHist.push(cmd); termPos = termHist.length; $('#termin').value = ''; termRemember(cmd);
  const run = { cmd, out: null };
  termRuns.push(run);
  const out = el('div', { className: 'tout wait', textContent: T('terminal.running') });
  const blk = el('div', { className: 'tblk' },
    el('div', { className: 'tcmd' }, el('span', { textContent: '$ ' + cmd }), termCopyBtn(T('terminal.copy_cmd'), () => cmd)), out);
  const o = $('#termout'); o.append(blk); o.scrollTop = o.scrollHeight;
  const r = await fetch('/api/shell', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ cmd }) }).then((x) => x.json()).catch(() => ({ error: T('terminal.server_unreachable') }));
  const text = r.error ? r.error : (r.out || '');
  run.out = text + (text && !text.endsWith('\n') ? '\n' : '');
  out.className = 'tout' + (r.error ? ' err' : '') + (text ? '' : ' none');
  out.replaceChildren(text ? termCopyBtn(T('terminal.copy_out'), () => run.out) : '', text || T('terminal.no_output'));
  o.scrollTop = o.scrollHeight;
});
termRemember();
$('#termin').addEventListener('keydown', (e) => {
  if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
  e.preventDefault(); termPos = Math.max(0, Math.min(termHist.length, termPos + (e.key === 'ArrowUp' ? -1 : 1))); $('#termin').value = termHist[termPos] || '';
});
