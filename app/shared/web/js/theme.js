// Light or dark: the system's choice unless the viewer picked one (Settings), or the phone app says which its own screens
// use (?theme=, like ?lang=). Loaded in <head>, before the page is drawn, so it never flashes the other theme first.
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
