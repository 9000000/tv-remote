#!/usr/bin/env node
// The home page's TV Games picture, per language: compose.html at 1280x720 -> docs/img/tv-games-<lang>.webp (1280) and
// -640.webp. The frames beside it (tv.png, lib-<lang>.png, pad1/2.png) are screenshots of the emulators; replace them to
// show a newer build. Needs Playwright (npm install --prefix dev playwright) and Pillow for the WebP.
//   node dev/shots/games/compose.js
const path = require('path'), { execFileSync } = require('child_process');
const { chromium } = require(path.join(__dirname, '..', '..', 'node_modules', 'playwright'));
const ROOT = path.join(__dirname, '..', '..', '..'), IMG = path.join(ROOT, 'docs', 'img');
(async () => {
  const b = await chromium.launch({ channel: 'chrome' });
  const p = await b.newPage({ viewport: { width: 1280, height: 720 } });
  for (const lang of ['vi', 'en']) {
    await p.goto('file://' + path.join(__dirname, 'compose.html') + '?lang=' + lang, { waitUntil: 'load' });
    const png = path.join(require('os').tmpdir(), `tv-games-${lang}.png`);
    await p.locator('#c').screenshot({ path: png });
    execFileSync('python3', ['-c', `import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert('RGB')
im.save(sys.argv[2], 'WEBP', quality=82, method=6)
im.resize((640, 360), Image.LANCZOS).save(sys.argv[3], 'WEBP', quality=82, method=6)`,
      png, path.join(IMG, `tv-games-${lang}.webp`), path.join(IMG, `tv-games-${lang}-640.webp`)]);
    console.log('docs/img/tv-games-' + lang + '.webp');
  }
  await b.close();
})();
