# dev/workflows: recipes for the usual changes

Short numbered steps with the real files, the test to run and how to verify. The rules themselves stay in the AGENTS.md
files (root [AGENTS.md](../../AGENTS.md) first); a recipe links to them instead of repeating them. A step that no longer
matches the code is fixed here in the same change.

| I want to | Recipe |
|---|---|
| add a TV Home card (widget) | [home-card.md](home-card.md) |
| add a key or a menu entry on the remote | [remote-key.md](remote-key.md) |
| add a `/api` route every core can answer | [api-route.md](api-route.md) |
| add a UI language | [language.md](language.md) |

Every recipe ends with **Done =**: the platforms to test, from the root AGENTS.md "Testing". Unit tests alone do not count.
