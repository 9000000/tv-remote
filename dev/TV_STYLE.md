# TV apps: one family look

TV Home, TV Channels, TV Setup and TV Games share no code (no `:shared` in channels / home / games), so the values below are
copied into each app as `res/values/tv_style.xml`. Change a value here and in every copy. TV Home's tile focus is the reference.

| Part | Value |
|---|---|
| Overscan margin | 48 dp left / right, 27 dp top / bottom (5 % of 960x540 dp) |
| Header (top-left of the main screen) | the app's icon 44 dp square, corner 10 dp, then 14 dp gap, then the app's name |
| App name | 24 sp, bold, `tv_ink` (#E8EAED), one line, no accent colour |
| Background | `tv_bg` #0E1116 |
| Card / panel | `tv_card` #1B2028, radius 16 dp (pill / field 22 dp) |
| Poster / tile / logo | radius 10 dp (TV Home's tile) |
| Muted text | `tv_dim` #9AA0A6 |
| Focus (cards, tiles, buttons) | TV Home's ring: 3 dp `tv_ring` (#2F7DF6) outside, 1 dp white inside, lift 1.06x (120 ms). A row of a plain list: a light fill only |

Apps today: Channels and Setup follow it (BrowseActivity `buildRail`, `Ui.states`; Setup MainActivity header, `ring()`).
TV Home has no logo header (clock and date at 48 dp, tiles radius 10, the ring above): it is the reference for the rest.
TV Games: LibraryActivity follows it (header, margins, ring, tile radius, `values/tv_style.xml`); LobbyView and GameCards still to do.
