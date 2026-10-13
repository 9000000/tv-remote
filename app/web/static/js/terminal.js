// terminal tab: one shell command at a time on the TV, like `adb shell <command>`
const termHist = []; let termPos = 0;
function termPrint(text, cls) { const o = $('#termout'); o.append(el('span', { className: cls || '', textContent: text })); o.scrollTop = o.scrollHeight; }
$('#termf').addEventListener('submit', async (e) => {
  e.preventDefault();
  const cmd = $('#termin').value.trim(); if (!cmd) return;
  termHist.push(cmd); termPos = termHist.length; $('#termin').value = '';
  termPrint('$ ' + cmd + '\n', 'cmd');
  const r = await fetch('/api/shell', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ cmd }) }).then((x) => x.json()).catch(() => ({ error: 'Không gọi được server' }));
  if (r.error) termPrint(tr(r.error) + '\n', 'err'); else termPrint((r.out || '') + (r.out && !r.out.endsWith('\n') ? '\n' : ''));
});
$('#termin').addEventListener('keydown', (e) => {
  if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
  e.preventDefault(); termPos = Math.max(0, Math.min(termHist.length, termPos + (e.key === 'ArrowUp' ? -1 : 1))); $('#termin').value = termHist[termPos] || '';
});
