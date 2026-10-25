// node --test app/shared/tests/   js/scrub.js (what an error report must not carry) gives what tests/scrub_cases.json says;
// core/Scrub.kt is held to the same table (ScrubTest.kt).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ctx = vm.createContext({});
vm.runInContext(fs.readFileSync(path.join(__dirname, '../web/js/scrub.js'), 'utf8') + '\nthis.Scrub = Scrub;', ctx);
const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'scrub_cases.json'), 'utf8')).cases;

test('every case of scrub_cases.json', () => {
  for (const c of cases) assert.equal(ctx.Scrub.text(c.in), c.out, c.name);
});
test('scrubbing twice changes nothing more', () => {
  for (const c of cases) if (c.again !== false) assert.equal(ctx.Scrub.text(c.out), c.out, c.name);
});
test('not a string: empty', () => {
  assert.equal(ctx.Scrub.text(undefined), '');
  assert.equal(ctx.Scrub.text(null), '');
});
test('scrub.js uses no lookbehind (older WebKit)', () => {
  assert.doesNotMatch(fs.readFileSync(path.join(__dirname, '../web/js/scrub.js'), 'utf8').replace(/\/\/.*$/gm, ''), /\(\?<[=!]/);
});
