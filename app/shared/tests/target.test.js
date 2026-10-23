// node --test app/shared/tests/   js/target.js (Target) gives what tests/target_cases.json says, the table TargetTest.kt reads too.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const sandbox = {};
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../web/js/target.js'), 'utf8').replace(/^(let|const) /gm, 'var ') + '\nthis.Target = Target;', sandbox);
const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'target_cases.json'), 'utf8')).cases;

for (const c of cases) test(`target ${JSON.stringify(c.t)}`, () => {
  assert.equal(sandbox.Target.kind(c.t), c.kind);
  assert.equal(sandbox.Target.pkg(c.t), c.pkg);
  assert.equal(sandbox.Target.id(c.t), c.id);
});
