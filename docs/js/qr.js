// A copy of app/shared/web/js/qr.js (this page is published on its own, from docs/): keep the two the same.
// QR codes for short texts (links, a Wi-Fi), drawn here so that the page needs no library: byte mode, error correction level L,
// versions 1 to 10 (up to 271 bytes), fixed mask 0. The same code as Qr.kt in the Android apps.
function qrModules(text) {
  const DATA = [19, 34, 55, 80, 108, 136, 156, 194, 232, 274], BLOCKS = [1, 1, 1, 1, 1, 2, 2, 2, 2, 4], EC = [7, 10, 15, 20, 26, 18, 20, 24, 30, 18];   // per version, level L
  const ALIGN = [[], [6, 18], [6, 22], [6, 26], [6, 30], [6, 34], [6, 22, 38], [6, 24, 42], [6, 26, 46], [6, 28, 50]];   // centres of the alignment squares
  const bytes = [...new TextEncoder().encode(text)];
  const lenBits = (n) => (n < 10 ? 8 : 16);                            // the length field grows at version 10
  const v = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10].find((n) => (4 + lenBits(n) + bytes.length * 8 + 7) >> 3 <= DATA[n - 1]); if (!v) return null;
  const cap = DATA[v - 1], bits = [];
  const put = (value, n) => { for (let i = n - 1; i >= 0; i--) bits.push((value >>> i) & 1); };
  put(4, 4); put(bytes.length, lenBits(v)); bytes.forEach((b) => put(b, 8));
  put(0, Math.min(4, cap * 8 - bits.length));
  while (bits.length % 8) bits.push(0);
  for (let pad = 0xEC; bits.length < cap * 8; pad = pad === 0xEC ? 0x11 : 0xEC) put(pad, 8);
  const data = []; for (let i = 0; i < cap; i++) data.push(bits.slice(i * 8, i * 8 + 8).reduce((a, b) => (a << 1) | b, 0));
  // Reed-Solomon over GF(256), polynomial 0x11D
  const mul = (a, b) => { let z = 0; for (let i = 7; i >= 0; i--) { z = (z << 1) ^ ((z >>> 7) * 0x11D); z ^= ((b >>> i) & 1) * a; } return z; };
  const n = EC[v - 1], gen = new Array(n).fill(0); gen[n - 1] = 1;
  for (let i = 0, root = 1; i < n; i++, root = mul(root, 2)) for (let j = 0; j < n; j++) { gen[j] = mul(gen[j], root); if (j + 1 < n) gen[j] ^= gen[j + 1]; }
  // the data split in blocks (the last ones one longer when it does not divide), each with its own check codewords, then interleaved
  const nb = BLOCKS[v - 1], short = Math.floor(cap / nb), blocks = [], checks = [];
  for (let b = 0, at = 0; b < nb; b++) {
    const len = short + (b >= nb - (cap % nb) ? 1 : 0), d = data.slice(at, at + len); at += len;
    const ec = new Array(n).fill(0);
    d.forEach((x) => { const f = x ^ ec.shift(); ec.push(0); for (let j = 0; j < n; j++) ec[j] ^= mul(gen[j], f); });
    blocks.push(d); checks.push(ec);
  }
  const all = [];
  for (let i = 0; i <= short; i++) blocks.forEach((d) => { if (i < d.length) all.push(d[i]); });
  for (let i = 0; i < n; i++) checks.forEach((ec) => all.push(ec[i]));
  const size = 17 + 4 * v;
  const dark = Array.from({ length: size }, () => new Array(size).fill(false)), fixed = Array.from({ length: size }, () => new Array(size).fill(false));
  const set = (x, y, on) => { if (x >= 0 && x < size && y >= 0 && y < size) { dark[y][x] = on; fixed[y][x] = true; } };
  for (let i = 0; i < size; i++) { set(6, i, i % 2 === 0); set(i, 6, i % 2 === 0); }
  [[3, 3], [size - 4, 3], [3, size - 4]].forEach(([cx, cy]) => { for (let dy = -4; dy <= 4; dy++) for (let dx = -4; dx <= 4; dx++) { const d = Math.max(Math.abs(dx), Math.abs(dy)); set(cx + dx, cy + dy, d !== 2 && d !== 4); } });
  const cs = ALIGN[v - 1];
  cs.forEach((cy) => cs.forEach((cx) => {
    if ((cx === 6 && cy === 6) || (cx === 6 && cy === cs[cs.length - 1]) || (cy === 6 && cx === cs[cs.length - 1])) return;   // under a corner square
    for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) set(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
  }));
  const f = 1 << 3; let rem = f; for (let i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
  const format = ((f << 10) | rem) ^ 0x5412, bit = (i) => ((format >>> i) & 1) === 1;   // level L, mask 0
  for (let i = 0; i <= 5; i++) set(8, i, bit(i));
  set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8));
  for (let i = 9; i <= 14; i++) set(14 - i, 8, bit(i));
  for (let i = 0; i <= 7; i++) set(size - 1 - i, 8, bit(i));
  for (let i = 8; i <= 14; i++) set(8, size - 15 + i, bit(i));
  set(8, size - 8, true);
  if (v >= 7) {                                                        // the version, twice, protected by a BCH code
    let r = v; for (let i = 0; i < 12; i++) r = (r << 1) ^ ((r >>> 11) * 0x1F25);
    const info = (v << 12) | r;
    for (let i = 0; i < 18; i++) { const on = ((info >>> i) & 1) === 1, a = size - 11 + (i % 3), b = Math.floor(i / 3); set(a, b, on); set(b, a, on); }
  }
  for (let right = size - 1, i = 0; right >= 1; right -= 2) {          // zigzag from the bottom right, two columns at a time
    if (right === 6) right = 5;
    for (let vert = 0; vert < size; vert++) for (let j = 0; j < 2; j++) {
      const x = right - j, y = ((right + 1) & 2) === 0 ? size - 1 - vert : vert;
      if (!fixed[y][x] && i < all.length * 8) { dark[y][x] = ((all[i >>> 3] >>> (7 - (i & 7))) & 1) === 1; i++; }
    }
  }
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) if (!fixed[y][x] && (x + y) % 2 === 0) dark[y][x] = !dark[y][x];
  return dark;
}
// An <svg> of the code, black on white with the margin readers need; null when the text is too long.
function qrSvg(text, px = 180) {
  const m = qrModules(text); if (!m) return null;
  const n = m.length + 8; let d = '';
  m.forEach((r, y) => r.forEach((on, x) => { if (on) d += 'M' + (x + 4) + ' ' + (y + 4) + 'h1v1h-1z'; }));
  const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  s.setAttribute('viewBox', '0 0 ' + n + ' ' + n); s.setAttribute('width', px); s.setAttribute('height', px); s.setAttribute('shape-rendering', 'crispEdges');
  s.innerHTML = '<rect width="' + n + '" height="' + n + '" fill="#fff"/><path d="' + d + '" fill="#000"/>';
  return s;
}
if (typeof module !== 'undefined') module.exports = { qrModules };
