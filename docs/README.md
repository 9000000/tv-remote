# docs

The product page https://tvremote.pages.dev. Plain files, nothing is generated or built.

**Deploying.** Cloudflare Pages (project `tvremote`) deploys it on every push to main that touches `docs/`. Its build watch
path is `docs/*` (Cloudflare's `*` spans folders; `docs/**` matches nothing and skipped every build). A deployment stuck at
"queued" with `is_skipped` was filtered out, not waiting. Everything here is public, this file included.

**Files**

| | |
|---|---|
| `index.html` | the page, with its JSON-LD for search engines |
| `css/site.css`, `js/site.js` | its look and behaviour |
| `guide.html`, `css/guide.css`, `js/guide.js` | the user guide (served at `/guide`): written once as Vietnamese + English spans, a menu, offline accent-insensitive search built from the page |
| `dev/guide_stale.py` | which guide topics may be out of date: each topic's `data-v` (the release it was checked against, `next` = main, stamped by `release.sh`) and `data-src` (the app files it depends on); `release.sh` stops while any is listed |
| `js/guide-anim.js` | the guide's four animations (pairing, Allow USB debugging, game controller, Home Assistant), `.ga-*` markup in `guide.html`; the install topics' `.gst` figures take the home page's install stories (`data-from` = the panel's id in `index.html`) and play them over the topic's own steps, a step per part |
| `js/qr.js` | a copy of `app/shared/web/js/qr.js`, kept the same by `app/shared/tests/page.test.js` |
| `js/anim.js`, `lib/gsap.min.js` | the animation runtime and helpers; GSAP 3.13.0 as published |
| `js/demo.js`, `css/demo.css` | the hero demo |
| `js/stories.js`, `css/stories.css` | How it works and the install tabs |
| `llms.txt` | the app in plain words for AI agents: keep it true when features or download links change |
| `robots.txt`, `sitemap.xml` | for search engines |
| `img/` | screenshots (`dev/shots.py`) and the link previews `og-<lang>.jpg` (1200×630, from `tvapp-<lang>.webp`) |

**Animations** are GSAP timelines written by hand. A timeline reads as a list of "at second t, this changes": `show`,
`press`, `fill`, `type`, `toggle`, `focus`, `pointer`, `steps`, or `track` for anything else. To look at one moment, in the
browser console: `storySeek(document.querySelector(".st-b"), 6.05)`.

**Screenshots** (`img/`: the computer app, the Tailscale box, the TV web page, the phone app, TV Setup, the og previews):

```
dev/shots.py                      # all of them
dev/shots.py web ts tv phone tvapp   # or some
```

It needs the computer app built and, for `tv`, `phone` and `tvapp`, both emulators running (`dev/emu.sh`). The TV's picture
is the made-up home screen `dev/shots/home-<lang>.html`; the TV is named "TV phòng khách" / "Living room TV". Then check each
`<img>`'s width and height in `index.html`.

**Trying it locally:** `python3 -m http.server -d docs 8000`, then http://127.0.0.1:8000.
