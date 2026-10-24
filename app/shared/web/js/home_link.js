// 🏠 Where TV Home's settings page talks to: the one seam between the page (home.js) and the TV Home on the TV.
//   host    the app that serves the page (TV Setup, the phone, the computer) asks TV Home over ADB: its /api/home... routes
//   direct  the page is TV Home's own (app/home, "Manage from phone": home.html): TV Home answers /h/... itself, no ADB, no TV Setup
// The page asks the same questions either way; HomeConfig's rules (clean / merge) run on the TV in both. A route a side does not
// have is null here and the page hides what needs it (an update through ADB; the wallpaper gallery, which the hosts download).
const HOME_PATHS = {
  host: { read: '/api/home', drop: '/api/home/photo/drop', qr: '/api/home/photo/qr', update: '/api/home/update', walls: '/api/home/walls', wall: '/api/home/wall',
    photo: '/api/home/photo', check: '/api/home/web/check?u=', app: { meta: '/api/app/meta?pkg=', icon: '/api/app/icon?pkg=' }, need: '/api/setup/need?f=' },
  direct: { read: '/h/config', drop: '/h/photo/drop', qr: null, update: null, walls: null, wall: null,
    photo: '/h/photo', check: '/h/web/check?u=', app: { meta: '/h/app/meta?pkg=', icon: '/h/app/icon?pkg=' }, need: null },
};

/**
 * [call](path, data, quiet) is the page's api(); [send](path, blob) puts a picture (fetch). A call to a route this side does not have
 * answers null, as a failed call does.
 */
function homeLinkFor(direct, call, send) {
  const p = direct ? HOME_PATHS.direct : HOME_PATHS.host;
  const via = (path, data, quiet) => path ? call(path, data, quiet) : Promise.resolve(null);
  return {
    direct,
    app: p.app,
    canUpdate: !!p.update, canGallery: !!p.walls, canQr: !!p.qr,
    read: (thumbs, quiet) => call(p.read + (thumbs ? '?thumbs=1' : ''), null, quiet),
    write: (change) => call(p.read, change),
    drop: (id) => call(p.drop, { id }),
    qr: (data) => via(p.qr, data || {}),
    update: () => via(p.update, {}),
    walls: (quiet) => via(p.walls, null, quiet),
    wall: (id) => via(p.wall, { id }),
    photo: (blob) => send(p.photo, blob),
    check: (url) => call(p.check + encodeURIComponent(url), null, true),   // can this page be the background: {ok, why, code, title} (WebCheck.kt)
    // TV Setup needs: only a host can ask; TV Home's own page has no TV Setup to ask, and its cards say what they lack
    setup: async (feature) => direct ? true : !!await call(p.need + feature),
  };
}

if (typeof module !== 'undefined') module.exports = { HOME_PATHS, homeLinkFor };
