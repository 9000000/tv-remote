// The page's CSS as the browser reads it: css/*.css in the order index.html links them (a later file wins a tie).
const fs = require('node:fs');
const path = require('node:path');

const web = path.join(__dirname, '../web');
const files = [...fs.readFileSync(path.join(web, 'index.html'), 'utf8').matchAll(/<link rel="stylesheet" href="(css\/[^"]+)">/g)].map((m) => m[1]);
const texts = files.map((f) => fs.readFileSync(path.join(web, f), 'utf8'));
module.exports = { files, texts, css: texts.join('\n') };
