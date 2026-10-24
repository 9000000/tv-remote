// ---------- Telegram bot (Telegram.kt): TV Setup's switch, token and paired chats, in Settings, shown only to the owner ----------
// The token goes to the TV once and is never sent back, only masked. The phone and computer apps have no bot of their own: they say
// where it lives (the TV) and whether TV Setup there has it.
let tgNow = null, tgAt = 0, tgWant = false, tgPoll = 0;
const tgMin = (s) => Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');

function tgCodeLine() {
  const j = tgNow, line = $('#tgcodeline'); if (!line) return;
  const left = j && j.code ? Math.max(0, Math.round(j.code_left - (Date.now() - tgAt) / 1000)) : 0;
  const locked = j && j.locked ? Math.max(0, Math.round(j.locked - (Date.now() - tgAt) / 1000)) : 0;
  const say = locked ? T('telegram_ui.locked', { left: tgMin(locked) }) : left ? T('telegram_ui.code_line', { code: j.code, left: tgMin(left) }) : '';
  put(line, 'textContent', say); put(line, 'hidden', !say);
}

function tgShow(j) {
  tgNow = j; tgAt = Date.now();
  const on = !!(j && j.on), has = !!(j && j.has_token), want = on || tgWant;
  $('#tgpick').querySelectorAll('button').forEach((b) => b.classList.toggle('on', (b.dataset.v === 'on') === want));
  put('#tgbody', 'hidden', !want);
  put('#tgsetup', 'hidden', !(want && !has));
  put('#tgready', 'hidden', !(on && has));
  put('#tgkept', 'hidden', !has);
  if (!(on && has)) return;
  const st = { ok: T('telegram_ui.st_ok'), connecting: T('telegram_ui.st_connecting'), badtoken: T('telegram_ui.st_badtoken'),
    error: T('telegram_ui.st_error', { err: friendly(j.error || '') }) }[j.status] || T('telegram_ui.st_connecting');
  put('#tgstate', 'textContent', [j.bot, j.masked, st].filter(Boolean).join(' · '));
  tgCodeLine();
  $('#tglang').querySelectorAll('button').forEach((b) => b.classList.toggle('on', b.dataset.v === (j.lang || '')));
  const when = (t) => new Date(t).toLocaleString(LANG === 'vi' ? 'vi-VN' : 'en-GB', { dateStyle: 'short', timeStyle: 'short' });
  $('#tgchats').replaceChildren(UI.list(j.chats.map((c) => UI.item({
    name: c.name, sub: T('telegram_ui.chat_sub', { when: when(c.at) }),
    extra: [scopeChips(c.scope, (k) => k !== c.scope && tgDo({ do: 'scope', id: c.id, scope: k }))],      // what it may do: Scope.kt, as for a browser
    acts: [el('button', { className: 'small danger', textContent: T('telegram_ui.chat_remove'),
      onclick: async () => { if (await ask(T('telegram_ui.chat_remove_ask', { name: c.name }), { ok: T('telegram_ui.chat_remove'), danger: true })) tgDo({ do: 'forget', id: c.id }); } })],
  })), T('telegram_ui.chats_none')));
}

async function tgDo(body) { const j = await api('/api/telegram', body); if (j) tgShow(j); return j; }

// While Settings is open the page asks every few seconds: a chat that just paired, or Telegram answering, shows up by itself
function tgLoop() {
  clearTimeout(tgPoll);
  if (!$('#about').open || SCOPE !== 'full' || CORE !== 'tv') return;
  tgPoll = setTimeout(async () => { const j = await api('/api/telegram', null, true); if (j) tgShow(j); tgLoop(); }, 3000);
}

async function tgLoad() {
  if (CORE !== 'tv') return;
  put('#tggroup', 'hidden', SCOPE !== 'full');
  if (SCOPE !== 'full') return;
  tgWant = false;
  const j = await api('/api/telegram', null, true); if (j) tgShow(j);
  tgLoop();
}

if (CORE === 'tv') {
  $('#aboutbtn').addEventListener('click', tgLoad);
  setInterval(() => { if ($('#about').open) tgCodeLine(); }, 1000);
  $('#tgpick').addEventListener('click', async (e) => {
    const b = e.target.closest('button'); if (!b) return;
    const on = b.dataset.v === 'on';
    if (on === !!(tgNow && tgNow.on) && !tgWant) return;
    if (on && !(tgNow && tgNow.has_token)) { tgWant = true; tgShow(tgNow); $('#tgtoken').focus(); return; }   // no token yet: the steps first
    tgWant = false;
    await tgDo({ do: on ? 'on' : 'off' });
  });
  $('#tgform').addEventListener('submit', async (e) => {
    e.preventDefault();
    const t = $('#tgtoken').value.trim(); if (!t) return;
    const j = await tgDo({ do: 'token', token: t });
    if (j) { tgWant = false; $('#tgtoken').value = ''; toast(T('telegram_ui.saved')); }
  });
  $('#tgcode').addEventListener('click', () => tgDo({ do: 'code' }));
  $('#tglang').addEventListener('click', (e) => { const b = e.target.closest('button'); if (b && b.dataset.v !== (tgNow.lang || '')) tgDo({ do: 'lang', lang: b.dataset.v }); });
  $('#tgremove').addEventListener('click', async () => {
    if (!await ask(T('telegram_ui.remove_ask'), { ok: T('telegram_ui.remove_ok'), danger: true })) return;
    tgWant = false; await tgDo({ do: 'remove' });
  });
  $('#about').addEventListener('close', () => clearTimeout(tgPoll));
} else {
  // the phone and computer apps: the bot is the TV's own; this asks whether TV Setup there is new enough (an Install / Update question if not)
  $('#tgcheck').addEventListener('click', async () => { if (await setupFor('telegram')) toast(T('telegram_ui.setup_ok')); });
}
