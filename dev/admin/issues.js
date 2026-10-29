#!/usr/bin/env node
// Which GitHub issues need a word from us, so nobody has to remind the agent:
//   - open, and the last word is not the owner's (a new issue, or an answer to us);
//   - labelled next-release ("fixed on main, waiting for a release") and a release came out after our last comment: say it is out,
//     then take the label off (and close the issue when the fix is all it asked for).
// Prints nothing when nothing is due. Run by the session hooks (.claude/settings.json; --every N: at most once per N minutes) and at
// the end of dev/release/release.sh. Needs gh; offline or no gh: silent.
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const os = require('os');

const OWNER = 'HoangTran0410';
const LABEL = 'next-release';
const gh = (...a) => JSON.parse(execFileSync('gh', a, { encoding: 'utf8', timeout: 15000, stdio: ['ignore', 'pipe', 'ignore'] }));

function due() {
  const issues = gh('issue', 'list', '--state', 'open', '--limit', '100', '--json', 'number,title,author,createdAt,comments,labels');
  const rel = gh('release', 'list', '--limit', '1', '--json', 'tagName,publishedAt')[0];
  const out = [];
  for (const i of issues) {
    const ours = i.comments.filter((c) => c.author.login === OWNER);
    const last = i.comments[i.comments.length - 1];
    const waiting = last ? last.author.login !== OWNER : i.author.login !== OWNER;
    if (waiting) out.push(`#${i.number} waits for a reply (${last ? 'last comment by ' + last.author.login : 'no answer yet'}): ${i.title}`);
    const lastOurs = ours.length ? ours[ours.length - 1].createdAt : i.createdAt;
    if (rel && i.labels.some((l) => l.name === LABEL) && rel.publishedAt > lastOurs)
      out.push(`#${i.number} is ${LABEL} and ${rel.tagName} is out since our last comment: say the fix is out, remove the label: ${i.title}`);
  }
  return out;
}

if (require.main === module) {
  const k = process.argv.indexOf('--every');
  if (k > 0) {                                                       // a hook on every prompt: ask GitHub at most once per N minutes
    const stamp = path.join(os.tmpdir(), 'tvr-issues-' + OWNER);
    try { if (Date.now() - fs.statSync(stamp).mtimeMs < +process.argv[k + 1] * 60000) process.exit(0); } catch (e) { }
    try { fs.writeFileSync(stamp, ''); } catch (e) { }
  }
  let lines;
  try { lines = due(); } catch (e) { process.exit(0); }
  if (lines.length) console.log('GitHub issues needing a reply (dev/admin/issues.js; replies are public: write them for the person, per AGENTS.md):\n- ' + lines.join('\n- '));
}
module.exports = { due };
