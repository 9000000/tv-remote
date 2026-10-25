# Send a theme

Make a look in TV Home, export it (Export theme), then open a pull request that adds one folder:

```
dev/themes/submissions/<slug>/
  theme.tvtheme.json   the exported file; add  "by": "your name"  (1-24 characters) and optionally "lang": "vi" | "en"
  preview.jpg          TV screenshot, 1280x720, at most 150 KB
  wall.jpg             optional, only your own photo, at most 1 MB
```
Rules: only theme settings in `conf`, no web addresses (except tvremote.pages.dev), no faces of other people, nudity, violence, logos or ads.
Do not edit `docs/home/themes.json`: it is signed; the owner adds the theme with `dev/sign/review_themes.py --local`.
You can also send it from TV Home itself (Share).
