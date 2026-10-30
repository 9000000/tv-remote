# Reviewing community themes: guide for agents

Themes come from TV Home (worker queue) or a pull request (`dev/themes/submissions/<slug>/`, see `THEMES.md`). Tool: `dev/sign/review_themes.py` (token read by the script, never print it).

```
python3 dev/sign/review_themes.py --list --json     # pending + flags + preview_path / wall_path (open them as images)
python3 dev/sign/review_themes.py --approve ID      # pictures, docs/home/themes.json, signature; prints the git add line
python3 dev/sign/review_themes.py --reject ID
python3 dev/sign/review_themes.py --local DIR [--dry]   # a PR folder: same checks (a flag = refused), approve, then the folder is removed
```
Nothing here commits or pushes. `free_slots` = room left under `READY_MAX` (120).

## Approve only when all are true
- The preview looks good: readable, nothing cut, matches the settings in `conf`.
- Name and `by` are decent (no insult, no ad, no personal data).
- No flags, or each flag is explained and harmless (an address on tvremote.pages.dev is not flagged).
- Pictures: no faces of private people, no nudity or violence, no logos, ads or text overlays. A wall from the sender's own photo is fine.

## Reject when any is true
- A foreign URL in `conf`, or a web wall not on our site.
- Offensive name or `by`; a broken or unreadable look; a copy of an existing theme (compare `docs/home/themes.json`).

## After approving
1. Look at the new `docs/img/themes/<id>.webp` once.
2. `node --test app/shared/tests/` and `./gradlew :tvhome:testDebugUnitTest` (HomeThemesTest keeps the packed copy equal; bump `app/tvhome/VERSION` if the packed copy changed).
3. Commit exactly the `git add` paths printed (for a PR folder also `git rm -r` it).
4. Push follows AGENTS.md ("Push without asking once tested"; `docs/` deploys the site at once; the site-push memory says the owner tests first).
5. Tell the owner what you approved / rejected, one line each with the reason.
