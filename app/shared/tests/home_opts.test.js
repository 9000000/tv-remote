// node --test app/shared/tests/   A card's settings dialog is drawn from the schema TV Home reports (HomeConfig.options → "wschema"):
// homeOpt reads a value (else the default), homeCardPrev draws the card at the chosen options, face and theme independent of each other.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const src = fs.readFileSync(path.join(__dirname, '../web/js/home.js'), 'utf8');
const sizes = src.slice(src.indexOf('const HOME_BIG_KINDS'), src.indexOf('// HomeConfig.pack, the same rule'));
const from = src.indexOf('const homeOpt'), to = src.indexOf('// One dialog for every card');
assert.ok(from > 0 && to > from);
const sb = { T: (k) => k, HOME_WIDGET_WORDS: { weather: 'home.w_weather' }, el: (tag, o = {}, ...kids) => ({ tag, ...o, kids }) };
vm.createContext(sb);
vm.runInContext((sizes + src.slice(from, to)).replace(/^(let|const) /gm, 'var ') + '\nthis.homeOpt = homeOpt; this.prev = homeCardPrev;', sb);

const v = (...ids) => ids.map((id) => ({ id, w: 'home.ov_' + id }));
const schema = {
  clock: [{ id: 'size', store: 'wsize', default: 'small', values: v('big', 'small') }, { id: 'face', default: 'classic', values: v('classic', 'minimal', 'roman') }, { id: 'theme', default: 'dark', values: v('dark', 'light') }],
  weather: [{ id: 'size', store: 'wsize', default: 'big', values: v('big', 'small') }, { id: 'look', default: 'glass', values: v('glass', 'tonal') }],
};

test('a value is the chosen one, else the schema\'s default; the size comes from wsize', () => {
  const c = { wschema: schema, wopt: { clock: { face: 'minimal', theme: 'light' } }, wsize: { clock: 'big' } };
  assert.equal(sb.homeOpt(c, 'clock', 'face'), 'minimal');
  assert.equal(sb.homeOpt(c, 'clock', 'theme'), 'light');
  assert.equal(sb.homeOpt(c, 'clock', 'size'), 'big');
  assert.equal(sb.homeOpt(c, 'weather', 'look'), 'glass');
  assert.equal(sb.homeOpt({ wschema: schema, wopt: { clock: { face: 'neon' } } }, 'clock', 'face'), 'classic');   // an unknown value: the default
  assert.equal(sb.homeOpt({ wschema: schema }, 'calendar', 'look'), '');
});

test('the clock\'s preview keeps face and theme apart: minimal can be light', () => {
  const p = sb.prev('clock', { wschema: schema, wopt: { clock: { face: 'minimal', theme: 'light' } } });
  assert.match(p.className, /wclock minimal light/);
  assert.doesNotMatch(p.innerHTML, /class="num"/);                 // minimal has no digits
  const r = sb.prev('clock', { wschema: schema, wopt: { clock: { face: 'roman' } } });
  assert.match(r.className, /roman dark/);
  assert.match(r.innerHTML, /XII/);
});

test('another card is drawn glass or tonal, big or small', () => {
  assert.match(sb.prev('weather', { wschema: schema, wopt: { weather: { look: 'tonal' } } }).className, /wcard tonal big/);
  assert.match(sb.prev('weather', { wschema: schema, wsize: { weather: 'small' } }).className, /wcard glass$/);
});
