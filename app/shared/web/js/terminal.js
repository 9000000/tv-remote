// terminal tab: one shell command at a time on the TV, like `adb shell <command>`
const termHist = []; let termPos = 0;
// Earlier commands, newest first, kept on this device: a list to pick from, since a phone keyboard has no ↑ ↓.
function termRemember(cmd) {
  let h = []; try { h = JSON.parse(localStorage.getItem('termhist') || '[]'); } catch {}
  if (cmd) { h = [cmd, ...h.filter((c) => c !== cmd)].slice(0, 30); try { localStorage.setItem('termhist', JSON.stringify(h)); } catch {} }
  const sel = $('#termhist'); sel.hidden = !h.length;
  sel.replaceChildren(el('option', { value: '', textContent: T('terminal.earlier_commands') }), ...h.map((c) => el('option', { value: c, textContent: c })));
}
$('#termhist').addEventListener('change', (e) => { if (e.target.value) { $('#termin').value = e.target.value; $('#termin').focus(); } e.target.value = ''; });
function termPrint(text, cls) { const o = $('#termout'); o.append(el('span', { className: cls || '', textContent: text })); o.scrollTop = o.scrollHeight; }
$('#termf').addEventListener('submit', async (e) => {
  e.preventDefault();
  const cmd = $('#termin').value.trim(); if (!cmd) return;
  termHist.push(cmd); termPos = termHist.length; $('#termin').value = ''; termRemember(cmd);
  termPrint('$ ' + cmd + '\n', 'cmd');
  const r = await fetch('/api/shell', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ cmd }) }).then((x) => x.json()).catch(() => ({ error: T('terminal.server_unreachable') }));
  if (r.error) termPrint(r.error + '\n', 'err'); else termPrint((r.out || '') + (r.out && !r.out.endsWith('\n') ? '\n' : ''));
});
termRemember();
$('#termin').addEventListener('keydown', (e) => {
  if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
  e.preventDefault(); termPos = Math.max(0, Math.min(termHist.length, termPos + (e.key === 'ArrowUp' ? -1 : 1))); $('#termin').value = termHist[termPos] || '';
});
