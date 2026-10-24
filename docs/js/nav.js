/* The header of every page of the site, from one list: <script src="js/nav.js" data-page="guide"></script> where the header goes.
   Plain (not deferred) on purpose: it writes the header while the page is read, so nothing jumps and the page's own script, further
   down, finds the language buttons. It also runs the narrow screens' menu (the same links in a sheet under the header). */
(function(){
  var me = document.currentScript, page = me.dataset.page || "", root = me.dataset.root || "";   // root: "../" on a page one folder down (demo/)
  var I = {
    install: '<path d="M12 3v12m0 0l-5-5m5 5l5-5M4 21h16"/>',
    guide: '<path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v16H6.5A2.5 2.5 0 0 0 4 21.5Z"/><path d="M4 21.5V5.5M8 7h8"/>',
    apps: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><path d="M14 17.5h7M17.5 14v7"/>',
    tvs: '<rect x="3" y="4" width="18" height="12" rx="2"/><path d="M8 20h8M12 16v4"/>',
    demo: '<circle cx="12" cy="12" r="9"/><path d="M10 8.5v7l6-3.5Z"/>'
  };
  // the same links on every page, in this order (a page's own sections are not here: the bar must look the same everywhere)
  var LINKS = [
    { id: "guide", href: "guide", vi: "Hướng dẫn", en: "Guide" },
    { id: "apps", href: "apps", vi: "Các app", en: "The apps" },
    { id: "tvs", href: "tvs", vi: "TV đã chạy", en: "TVs it ran on" },
    { id: "demo", href: "demo/", vi: "Dùng thử", en: "Live demo" }
  ];
  var own = page === "home" || page === "tour";                       // pages with their own Install part
  var install = own ? "#install" : root + "./#install";
  function icon(id){ return '<svg class="ni" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + I[id] + '</svg>'; }
  function words(l){ return '<span class="vi">' + l.vi + '</span><span class="en">' + l.en + '</span>'; }
  var links = LINKS;
  function link(l){ return '<a href="' + root + l.href + '"' + (l.id === page ? ' class="on" aria-current="page"' : "") + '>' + icon(l.id) + words(l) + '</a>'; }
  var gh = '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 .5a11.5 11.5 0 0 0-3.64 22.41c.58.1.79-.25.79-.56v-2c-3.2.7-3.88-1.37-3.88-1.37-.52-1.33-1.28-1.69-1.28-1.69-1.04-.71.08-.7.08-.7 1.15.08 1.76 1.19 1.76 1.19 1.03 1.76 2.69 1.25 3.35.96.1-.75.4-1.25.73-1.54-2.55-.29-5.24-1.28-5.24-5.68 0-1.25.45-2.28 1.18-3.08-.12-.29-.51-1.46.11-3.04 0 0 .97-.31 3.17 1.18a11 11 0 0 1 5.77 0c2.2-1.49 3.17-1.18 3.17-1.18.62 1.58.23 2.75.11 3.04.74.8 1.18 1.83 1.18 3.08 0 4.41-2.69 5.38-5.25 5.67.41.36.78 1.06.78 2.14v3.17c0 .31.21.67.8.56A11.5 11.5 0 0 0 12 .5Z"/></svg>';
  me.insertAdjacentHTML("beforebegin",
    '<nav><div class="wrap">' +
      '<a class="brand" href="' + (page === "home" ? "#top" : root + "./") + '"><img src="' + root + 'img/icon.png" width="32" height="32" alt="">TV Remote</a>' +
      '<div class="navlinks">' + links.map(link).join("") + '</div>' +
      '<div class="navtools">' +
        '<a class="btn btn-primary btn-sm navcta" href="' + install + '">' + icon("install") + words({ vi: "Cài đặt", en: "Install" }) + '</a>' +
        '<div class="lang" role="group" aria-label="Language"><button data-set="vi" aria-pressed="true">VI</button><button data-set="en" aria-pressed="false">EN</button></div>' +
        '<a class="btn btn-ghost btn-sm" href="https://github.com/HoangTran0410/tv-remote" target="_blank" rel="noopener" aria-label="GitHub">' + gh + '<span class="gh-label">GitHub</span></a>' +
        '<button class="menubtn" type="button" aria-expanded="false" aria-controls="navsheet" aria-label="Menu"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path class="m1" d="M4 7h16"/><path class="m2" d="M4 12h16"/><path class="m3" d="M4 17h16"/></svg></button>' +
      '</div>' +
    '</div>' +
    '<div class="navsheet" id="navsheet" hidden><div class="wrap">' +
      '<a href="' + install + '">' + icon("install") + words({ vi: "Cài đặt", en: "Install" }) + '</a>' + links.map(link).join("") +
    '</div></div>' +
    '<div class="navprog" aria-hidden="true"></div></nav>');

  // the menu closes on a tap on a link, outside, or Escape, and when the screen grows past the menu's width
  var btn = document.querySelector("nav .menubtn"), sheet = document.getElementById("navsheet");
  function set(open, back){ sheet.hidden = !open; btn.setAttribute("aria-expanded", open); if (!open && back) btn.focus(); }
  btn.onclick = function(){ set(sheet.hidden); if (!sheet.hidden) sheet.querySelector("a").focus({ preventScroll: true }); };
  sheet.addEventListener("click", function(e){ if (e.target.closest("a")) set(false); });
  document.addEventListener("click", function(e){ if (!sheet.hidden && !e.target.closest("nav")) set(false); });
  document.addEventListener("keydown", function(e){ if (e.key === "Escape" && !sheet.hidden) set(false, true); });
  matchMedia("(min-width:981px)").addEventListener("change", function(m){ if (m.matches) set(false); });
})();
