// dev/flows/*.md say every case of a feature; dev/flows/check.js keeps them true to the code (dev/flows/README.md).
const test = require('node:test');
const assert = require('node:assert');
const { check, checkFlow } = require('../../../dev/flows/check.js');

test('every flow names only files, symbols and tests that exist, and its Cases equal the code', () => {
  assert.deepStrictEqual(check(), []);
});

test('a flow that drifted from the code is caught', () => {
  const ok = '**Code:** `app/shared/src/main/kotlin/dev/tvremote/core/ontv/LauncherSwap.kt`\n\n**Tests:** `app/shared/src/test/kotlin/dev/tvremote/core/LauncherTest.kt`\n\n';
  assert.deepStrictEqual(checkFlow(ok + 'Uses `LauncherSwap.take`.\n'), []);
  const errs = checkFlow(ok + 'Uses `LauncherSwap.takeAll` and `app/nope.kt`.\n\n'
    + 'Cases (`app/shared/src/main/kotlin/dev/tvremote/core/ontv/LauncherSwap.kt:WHYS`): crash, home\n\n'
    + '| # | Test |\n|---|---|\n| 1 | `noSuchTest` |\n');
  assert.strictEqual(errs.length, 4, errs.join('\n'));
});
