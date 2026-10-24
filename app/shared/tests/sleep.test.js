// node --test app/shared/tests/   sleepMinutes (js/auto.js) reads each text as tests/sleep_cases.json says, the table SleepTest.kt (the Phone app's copy) reads too.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const src = fs.readFileSync(path.join(__dirname, '../web/js/auto.js'), 'utf8');
const from = src.indexOf('function sleepMinutes');
const to = src.indexOf('\n}\n', from) + 3;
assert.ok(from > 0 && to > from, 'sleepMinutes moved');
const box = {};
vm.createContext(box);
vm.runInContext(src.slice(from, to) + '\nthis.sleepMinutes = sleepMinutes;', box);
const table = JSON.parse(fs.readFileSync(path.join(__dirname, 'sleep_cases.json'), 'utf8'));
const [h, m] = table.now.split(':').map(Number);

for (const c of table.cases) test(`sleepMinutes ${JSON.stringify(c.in)} at ${table.now}`, () => {
  assert.equal(box.sleepMinutes(c.in, new Date(2026, 9, 24, h, m, 0, 0)), c.min);
});
