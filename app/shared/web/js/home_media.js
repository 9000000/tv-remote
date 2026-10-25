// 🖼️ A theme file's own photos and videos (`media` in <name>.tvtheme.json: {tvtheme:1, name, conf, media?}). The conf names them by the keys
// TV Home uses (photo, photo2 .., vid:xxxxxxxx), which mean nothing on another TV: the file carries each one (a data: URL, or an https
// address on a host the backgrounds already allow) and the import uploads them to TV Home's own photo / video paths, then rewrites the keys
// in `wall` / `walls` to the slots and ids the TV gave. The host side writes the same shape (core/ontv/ThemeFile.kt). No words here: the
// page (home.js) turns the {id, args} of a refusal and the progress objects into text. Pure, with the page's functions passed in, so
// tests/home_media.test.js runs it with no page.
const HOME_MEDIA_MAX = 60 * 1048576;                                // one photo or video in a file (ThemeFile.MEDIA_MAX)
const HOME_MEDIA_FILE_MAX = 130 * 1048576;                          // a whole file: one video at the cap + ten photos, in base64
const HOME_MEDIA_HOSTS = {                                          // NetPicture.HOSTS / WebVideo.HOSTS (app/home wall/), loop.html's lists
  photo: ['tvremote.pages.dev', 'upload.wikimedia.org', 'images.pexels.com', 'images.unsplash.com'],
  video: ['tvremote.pages.dev', 'upload.wikimedia.org', 'videos.pexels.com'],
};

const homeMediaKind = (k) => /^photo\d*$/.test(String(k)) ? 'photo' : /^vid:[0-9a-f]{8}$/.test(String(k)) ? 'video' : '';

// the theme's own photos and videos in order of use (the wall first, then its rotation), each once
function homeMediaKeys(conf) {
  const out = [];
  for (const k of [conf && conf.wall, ...((conf && Array.isArray(conf.walls)) ? conf.walls : [])]) if (homeMediaKind(k) && !out.includes(k)) out.push(k);
  return out;
}

// The small tags on a theme's tile: {id, args} each. Video (own vid:, or the site's loop.html?v=), a rotation of N backgrounds, a slideshow (loop.html?s=)
function homeMediaBadges(conf) {
  const out = [], walls = [...new Set([conf.wall, ...(Array.isArray(conf.walls) ? conf.walls : [])].filter(Boolean))];
  const u = String(conf.wurl || '').toLowerCase();
  if (walls.some((k) => /^vid:/.test(k)) || (conf.wall === 'web' && /loop\.html\?v=/.test(u))) out.push({ id: 'home.badge_video', args: {} });
  if (conf.wall === 'web' && /loop\.html\?s=/.test(u)) out.push({ id: 'home.badge_slides', args: {} });
  else if (walls.length > 1) out.push({ id: 'home.badge_photos', args: { n: walls.length } });
  return out;
}

// What the TV holds of them, for the export's checkbox: counts and the bytes known (a TV Home from before photo sizes: exact false)
function homeMediaWeigh(conf, snap) {
  let photos = 0, videos = 0, bytes = 0, exact = true;
  const vids = ((snap && snap.video) || {}).items || [], pics = (snap && snap.photos) || [];
  for (const k of homeMediaKeys(conf)) {
    if (homeMediaKind(k) === 'video') { videos++; const v = vids.find((x) => x.id === k); if (v && v.size) bytes += v.size; else exact = false; }
    else { photos++; const p = pics.find((x) => x.id === k); if (p && p.size) bytes += p.size; else exact = false; }
  }
  return { photos, videos, bytes, exact, over: vids.some((v) => homeMediaKeys(conf).includes(v.id) && v.size > HOME_MEDIA_MAX) };
}

// "data:image/jpeg;base64,...": {data:true, mime, bytes (decoded, from the length)}; an https address on an allowed host: {url}; else null
function homeMediaParse(v, kind) {
  if (typeof v !== 'string') return null;
  const m = /^data:([a-z]+\/[a-z0-9.+-]+);base64,/i.exec(v.slice(0, 80));
  if (m) {
    const mime = m[1].toLowerCase();
    if (mime.split('/')[0] !== (kind === 'video' ? 'video' : 'image')) return null;
    const n = v.length - m[0].length, pad = v.endsWith('==') ? 2 : v.endsWith('=') ? 1 : 0;
    return { data: true, mime, bytes: Math.max(0, Math.floor(n * 3 / 4) - pad) };
  }
  try {
    const u = new URL(v);
    if (u.protocol === 'https:' && HOME_MEDIA_HOSTS[kind].includes(u.hostname)) return { url: v };
  } catch (e) { /* not an address */ }
  return null;
}

/**
 * What an import will do with [media], or why it cannot: {items: [{key, kind, value, parsed}]} or {error: {id, args}}.
 * [room] = {photos, videos, videoMax} (TV Home's free places and the biggest video it takes). Keys the conf does not use are ignored;
 * a key the conf uses without an item is left for the caller to turn into a plain background.
 */
function homeMediaPlan(conf, media, room) {
  if (!media || typeof media !== 'object' || Array.isArray(media)) return { items: [] };
  const items = [];
  for (const key of homeMediaKeys(conf)) {
    if (!(key in media)) continue;
    const kind = homeMediaKind(key), parsed = homeMediaParse(media[key], kind);
    if (!parsed) return { error: { id: 'home.theme_media_bad', args: { key } } };
    if (parsed.data && parsed.bytes > HOME_MEDIA_MAX) return { error: { id: 'home.theme_media_big', args: { key, mb: Math.ceil(parsed.bytes / 1048576), max: HOME_MEDIA_MAX / 1048576 } } };
    if (kind === 'video' && parsed.data && room.videoMax && parsed.bytes > room.videoMax) return { error: { id: 'home.theme_media_big', args: { key, mb: Math.ceil(parsed.bytes / 1048576), max: Math.floor(room.videoMax / 1048576) } } };
    items.push({ key, kind, value: media[key], parsed });
  }
  const photos = items.filter((x) => x.kind === 'photo').length, videos = items.length - photos;
  if (photos > room.photos || videos > room.videos) return { error: { id: 'home.theme_media_room', args: { photos, videos, pr: Math.max(0, room.photos), vr: Math.max(0, room.videos) } } };
  return { items };
}

/**
 * [conf] with the keys of [map] (old key -> the key the TV gave) put in `wall` / `walls`. An own key without an entry in the map is
 * dropped (the theme falls back to a plain background, as an import without media always did); `lost` says it happened.
 */
function homeMediaRemap(conf, map, own) {
  const out = { ...conf };
  const gone = (k) => homeMediaKind(k) && !(k in map);
  let lost = false;
  const re = (k) => (k in map ? map[k] : k);
  if (gone(out.wall)) { lost = true; out.wall = 'night'; } else if (out.wall) out.wall = re(out.wall);
  if (Array.isArray(out.walls)) {
    const keep = out.walls.filter((k) => !gone(k));
    lost = lost || keep.length !== out.walls.length;
    out.walls = keep.length ? keep.map(re) : [out.wall || 'night'];
    if (!out.wall) out.wall = out.walls[0];
  }
  return { conf: out, lost };
}

const homeMediaMb = (n) => n >= 1048576 ? (n / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(n / 1024)) + ' KB';   // fmtSize is the hosts' page's; TV Home's own has none

// an error of this feature in words (T and friendly are the page's, TV Home's own page has no table for the host's codes)
const homeMediaWhy = (m) => /home:media_big/.test(m) ? T('home.theme_media_big_any', { max: HOME_MEDIA_MAX / 1048576 }) : /home:media_missing/.test(m) ? T('home.theme_media_missing')
  : /home:media_fetch/.test(m) ? T('home.theme_media_fetch') : friendly(m);

const homeMediaIds = (snap, kind) => kind === 'video' ? (((snap || {}).video || {}).items || []).map((v) => v.id) : ((snap || {}).photos || []).map((p) => p.id);

/**
 * Uploads the planned items one at a time and answers {map, lost-free}: old key -> new key. [d] brings the page's parts:
 *   blobOf(item) -> Blob     the bytes (a data: URL decoded, or the address fetched; refuses what is over the cap)
 *   photo(blob) -> snapshot  homeLink.photo (a jpeg the size of the screen)   video(blob, name) -> snapshot  upload + waiting for the TV to make it light
 *   toJpeg(blob) -> Blob     another picture type made a jpeg    drop(id)    take one back    say({i, n, key, kind})    cancelled() -> bool
 * Any failure or Cancel takes back what this import had added and throws.
 */
async function homeMediaImport(items, d) {
  const map = {}, added = [];
  try {
    for (let i = 0; i < items.length; i++) {
      if (d.cancelled()) throw new Error('cancelled');
      const it = items[i];
      d.say({ i: i + 1, n: items.length, key: it.key, kind: it.kind });
      let blob = await d.blobOf(it);
      if (blob.size > HOME_MEDIA_MAX) throw new Error('home:media_big');
      let snap;
      if (it.kind === 'photo') {
        if (!/^image\/jpeg$/i.test(blob.type)) blob = await d.toJpeg(blob);
        const before = homeMediaIds(await d.read(), 'photo');
        snap = await d.photo(blob);
        const now = homeMediaIds(snap, 'photo').find((id) => !before.includes(id));
        if (!now) throw new Error('home:photos_full');
        added.push(now); map[it.key] = now;
      } else {
        const before = homeMediaIds(await d.read(), 'video');
        snap = await d.video(blob, 'theme-' + it.key.slice(4) + (/webm/i.test(blob.type) ? '.webm' : '.mp4'));
        const now = homeMediaIds(snap, 'video').find((id) => !before.includes(id));
        if (!now) throw new Error('home:video_full');
        added.push(now); map[it.key] = now;
      }
    }
  } catch (e) {
    for (const id of added) { try { await d.drop(id); } catch (x) { /* best effort */ } }
    throw e;
  }
  return map;
}

/**
 * The theme's own photos and videos read back from the TV as {key: dataURL}; [d]: blobOf(key) -> Blob (homeLink.mediaUrl fetched),
 * dataUrl(blob) -> string, say({i, n, key}), cancelled(). Over the cap: throws 'home:media_big' before anything else is read.
 */
async function homeMediaExport(conf, sizes, d) {
  const keys = homeMediaKeys(conf), media = {};
  if (keys.some((k) => (sizes[k] || 0) > HOME_MEDIA_MAX)) throw new Error('home:media_big');
  for (let i = 0; i < keys.length; i++) {
    if (d.cancelled()) throw new Error('cancelled');
    d.say({ i: i + 1, n: keys.length, key: keys[i] });
    const b = await d.blobOf(keys[i]);
    if (b.size > HOME_MEDIA_MAX) throw new Error('home:media_big');
    media[keys[i]] = await d.dataUrl(new Blob([b], { type: homeMediaKind(keys[i]) === 'video' ? 'video/mp4' : 'image/jpeg' }));
  }
  return media;
}

// the file as Blob parts, so a video's 80 MB of text is never one string joined again
function homeMediaFile(name, conf, media) {
  const head = JSON.stringify({ tvtheme: 1, name, conf }, null, 2);
  const keys = Object.keys(media || {});
  if (!keys.length) return [head];
  const parts = [head.slice(0, head.lastIndexOf('}')).replace(/\s+$/, '') + ',\n  "media": {'];
  keys.forEach((k, i) => parts.push((i ? ',' : '') + '\n    ' + JSON.stringify(k) + ': "', media[k], '"'));
  parts.push('\n  }\n}\n');
  return parts;
}

if (typeof module !== 'undefined') module.exports = { HOME_MEDIA_MAX, HOME_MEDIA_FILE_MAX, HOME_MEDIA_HOSTS, homeMediaKind, homeMediaKeys, homeMediaBadges, homeMediaWeigh, homeMediaMb, homeMediaParse, homeMediaPlan, homeMediaRemap, homeMediaImport, homeMediaExport, homeMediaFile };
