# Game pictures

One 640x360 JPEG per catalog game (`img` of its entry in `../games.json`, path relative to `docs/games/`), shown on its tile in TV Games
(Suggestions and, once downloaded, the library). The app reads them from the site, else from GitHub raw, like `games.json`, and keeps them on the TV.

Each is a screenshot of the game's own title screen, taken from the game running in TV Games on the Android TV emulator (the 4:3 frame
on a dark 16:9 backdrop; the "Scan to join" chip painted out). The picture is the game's output, so it falls under the game's licence:

| File | Game | Licence | Source |
|---|---|---|---|
| roborun.jpg | Robo Run (Haseeb) | GPL-3.0 | https://github.com/heaven-hm/roborun-nes |
| nova.jpg | Nova the Squirrel (NovaSquirrel) | GPL-3.0 | https://github.com/NovaSquirrel/NovaTheSquirrel |
| witchnwiz.jpg | Witch n' Wiz (Matt Hughson) | MIT | https://github.com/mhughson/mbh-A53-witchnwiz |
| stallar.jpg | Stallar (Wendel Scardua) | MIT | https://github.com/wendelscardua/stallar |
| filefixers.jpg | File Fixers (Wendel Scardua) | MIT | https://github.com/wendelscardua/file-fixers |
| tabletennis.jpg | 8-Bit Table Tennis (Mike Michel) | MIT | https://github.com/mike42/8bit-table-tennis |
| opennes-snake.jpg, opennes-breakout.jpg, opennes-pong.jpg | openNES Snake / Breakout / Pong (Sebastian Dine) | Zlib | https://github.com/sebastiandine/openNES-Snake (-Breakout, -Pong) |
| croom.jpg, rhde.jpg, thwaite.jpg | Concentration Room, RHDE: Furniture Fight, Thwaite (Damian Yerrick) | GPL-3.0+ / GNU All-Permissive / GPL-3.0+ | https://github.com/pinobatch/croom-nes (rhde-nes, thwaite-nes) |
| coco.jpg | Coco Battle Royale II (Rodrigo Dornelles) | GPL-3.0 | https://github.com/psywave-games/coco-battle-royale-2 |
| runner.jpg | Runner (zorchenhimer) | MIT | https://github.com/zorchenhimer/nes-runner |
| ghostsgraves.jpg | Ghosts and Graves (Anthony Bongers) | Unlicense | https://github.com/AnthonyBongers/GhostsAndGraves |
| hockey.jpg, tanks.jpg, cycles.jpg, sumo.jpg, tienlen.jpg, caro.jpg, bombers.jpg, colors.jpg, cangua.jpg, typhu.jpg, bida.jpg, meono.jpg | Air Hockey, Tank Arena, Light Cycles, Sumo Bump, Tiến lên, Gomoku, Bombers, Color Cards, Ludo, Tycoon, Pool, Boom Cats (our own, `docs/games/web/`) | same as the repo | in-game capture of each game running (CPU seats), no overlay |

A new catalog game: run it, screencap its title screen, save it here as `<id>.jpg`, add `"img": "img/<id>.jpg"` to its entry and re-sign (`dev/sign/sign_games.py`).
