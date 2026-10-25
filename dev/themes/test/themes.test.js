import { test } from "node:test";
import assert from "node:assert/strict";
import { validate, flags, clean, senderHash, tokenOk, sizeOf, THEME_KEYS, MAX_BODY, MAX_PHOTO } from "../src/logic.js";

const JPG = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0, 0, 0, 0, 0, 0]).toString("base64");
const WEBP = Buffer.concat([Buffer.from("RIFF"), Buffer.alloc(4), Buffer.from("WEBPVP8 ")]).toString("base64");
const sub = (o = {}) => JSON.stringify({ tvtheme: 1, name: "Sapa", by: "Bro", lang: "vi", conf: { wall: "dawn", shade: 30, widgets: ["weather"] }, preview: JPG, ...o });

test("good submission, webp preview, optional wall", () => {
  assert.equal(validate(sub()).ok.name, "Sapa");
  assert.ok(validate(sub({ preview: WEBP })).ok);
  assert.equal(validate(sub({ wall: JPG })).ok.wall.length, 10);
});
test("strings trimmed, control chars stripped, cut to 24", () => {
  assert.equal(clean("  a\u0000\nb‮  c "), "a b c");
  assert.equal(clean("x".repeat(40)).length, 24);
  assert.equal(validate(sub({ name: " \n ", })).error, "name: 1-24 characters");
  assert.equal(validate(sub({ by: 5 })).error, "by: 1-24 characters");
});
test("rejects malformed", () => {
  for (const t of ["", "{", "[]", "null", sub({ tvtheme: 2 }), sub({ lang: "fr" }), sub({ conf: [] }), sub({ conf: null }), sub({ preview: "" }),
    sub({ preview: "!!!!" }), sub({ preview: Buffer.from("GIF89a....").toString("base64") }), sub({ wall: WEBP }), sub({ wall: "x" })])
    assert.ok(validate(t).error, t);
  assert.equal(validate("x".repeat(MAX_BODY + 1)).status, 413);
});
test("conf: only THEME_KEYS, plain, <= 8 KB", () => {
  assert.match(validate(sub({ conf: { wall: "dawn", evil: 1 } })).error, /evil/);
  assert.match(validate(sub({ conf: { widgets: ["x".repeat(500)].concat(Array(30).fill("y".repeat(500))) } })).error, /conf/);
  assert.match(validate(sub({ conf: { wsize: { a: { b: { c: { d: { e: { f: 1 } } } } } } } })).error, /plain/);
  for (const k of THEME_KEYS) assert.ok(validate(sub({ conf: { [k]: 1 } })).ok, k);
});
test("size limits of the pictures", () => {
  const big = (n) => Buffer.concat([Buffer.from([0xff, 0xd8, 0xff]), Buffer.alloc(n)]).toString("base64");
  assert.match(validate(sub({ preview: big(150_000) })).error, /150 KB/);
  assert.ok(validate(sub({ preview: big(149_000) })).ok);
  assert.match(validate(sub({ wall: big(1_000_000) })).error, /1 MB/);
});
test("flags: foreign web wall, any URL, long names", () => {
  assert.deepEqual(flags({ name: "a", by: "b", conf: { wall: "web", wurl: "https://tvremote.pages.dev/home/loop.html?i=" + encodeURIComponent("https://tvremote.pages.dev/home/a.jpg") } }), []);   // the project's own site, nested too: no red line
  assert.equal(flags({ name: "a", by: "b", conf: { wall: "web", wurl: "https://tvremote.pages.dev/home/loop.html?i=" + encodeURIComponent("https://evil.example/a.jpg") } }).length, 1);   // a foreign picture inside our page is
  const f = flags({ name: "a", by: "b", conf: { wall: "web", wurl: "https://evil.example/x" } });
  assert.ok(f.some((x) => /not on tvremote.pages.dev/.test(x)) && f.some((x) => /URL in conf/.test(x)));
  assert.ok(flags({ name: "a", by: "b", conf: { wall: "web", wurl: "http://tvremote.pages.dev/" } }).some((x) => /not on/.test(x)));
  assert.ok(flags({ name: "a", by: "b", conf: { walls: ["see https://x.y"] } }).some((x) => /URL/.test(x)));
  assert.ok(flags({ name: "n".repeat(22), by: "b", conf: {} }).some((x) => /long/.test(x)));
  assert.deepEqual(flags({ name: "a", by: "b", conf: { wall: "dawn" } }), []);
});
test("senderHash: stable per day, differs by day and salt, not the IP", async () => {
  const a = await senderHash("1.2.3.4", "2026-10-25", "s");
  assert.match(a, /^[0-9a-f]{24}$/);
  assert.equal(a, await senderHash("1.2.3.4", "2026-10-25", "s"));
  assert.notEqual(a, await senderHash("1.2.3.4", "2026-10-26", "s"));
  assert.notEqual(a, await senderHash("1.2.3.4", "2026-10-25", "t"));
});
test("tokenOk", () => {
  const t = "a".repeat(48);
  assert.ok(tokenOk("Bearer " + t, t));
  for (const h of [null, "", "Bearer x", "Bearer " + "b".repeat(48), t]) assert.ok(!tokenOk(h, t));
  assert.ok(!tokenOk("Bearer ", ""), "no token set = closed");
});
test("walls: up to ten photos by key, each a jpeg under 360 KB", () => {
  const big = (n) => Buffer.concat([Buffer.from([0xff, 0xd8, 0xff]), Buffer.alloc(n)]).toString("base64");
  const ten = Object.fromEntries(["photo", "photo2", "photo3", "photo4", "photo5", "photo6", "photo7", "photo8", "photo9", "photo10"].map((k) => [k, big(MAX_PHOTO - 3)]));
  const r = validate(sub({ walls: ten }));
  assert.equal(r.ok.photos.length, 10);
  assert.ok(JSON.stringify({ tvtheme: 1, name: "Sapa", by: "Bro", lang: "vi", conf: {}, preview: JPG, walls: ten }).length < MAX_BODY, "ten full photos fit the body limit");
  assert.equal(sizeOf(r.ok), r.ok.preview.length + 10 * (MAX_PHOTO - 3 + 3));
  assert.match(validate(sub({ walls: { ...ten, photo11: JPG } })).error, /at most 10|bad key/);
  assert.match(validate(sub({ walls: { photo: big(MAX_PHOTO) } })).error, /350 KB/);
  assert.match(validate(sub({ walls: { photo: WEBP } })).error, /not a jpeg/);
  assert.match(validate(sub({ walls: { "../x": JPG } })).error, /bad key/);
  assert.match(validate(sub({ walls: [JPG] })).error, /object/);
  assert.match(validate(sub({ walls: { photo: "x" } })).error, /base64/);
  assert.equal(validate(sub({ walls: {} })).ok.photos.length, 0);
});
