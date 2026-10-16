// Light or dark: the system's choice unless the viewer picked one (Settings), or the phone app says which its own screens
// use (?theme=, like ?lang=). Loaded in <head>, before the page is drawn, so it never flashes the other theme first.
// TV Setup's Manage screen on the TV itself (ManageActivity) opens the page with ?ontv=1: no Remote tab, D-pad focus (js/ontv.js)
window.ONTV = /[?&]ontv=1(&|$)/.test(location.search);
if (window.ONTV) document.documentElement.dataset.ontv = '1';
// the tabs stay unseen until setup.js has picked the one to open (style.css): the page reloads to switch language, and the
// Remote tab, open in the markup, flashed up before going back to the tab the viewer was on; a stuck script still shows them
setTimeout(() => document.documentElement.classList.add('tabbed'), 3000);
(() => {
  let stored = null;
  try { stored = localStorage.getItem('theme'); } catch {}
  let asked = null;
  try { asked = new URLSearchParams(location.search).get('theme'); } catch {}
  const pick = (v) => v === 'light' || v === 'dark' ? v : null;
  window.THEME = pick(asked) || pick(stored) || 'auto';
  const dark = matchMedia('(prefers-color-scheme: dark)');
  const apply = () => {
    const t = window.THEME === 'auto' ? (dark.matches ? 'dark' : 'light') : window.THEME;
    document.documentElement.dataset.theme = t;                    // style.css reads only this, not the media query
    const m = document.querySelector('meta[name=theme-color]');
    if (m) m.content = t === 'dark' ? '#111418' : '#f3f4f7';
  };
  window.setTheme = (v) => {
    window.THEME = pick(v) || 'auto';
    try { window.THEME === 'auto' ? localStorage.removeItem('theme') : localStorage.setItem('theme', window.THEME); } catch {}
    apply();
  };
  dark.addEventListener?.('change', () => { if (window.THEME === 'auto') apply(); });
  apply();
})();
