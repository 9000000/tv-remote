import { test } from "node:test";
import assert from "node:assert/strict";
import { validate, str, hashId, aggregate, kindOf, MAX_BODY } from "../src/logic.js";

const ID = "3f2b8c1e-5a4d-4e6f-9a7b-1c2d3e4f5a6b";
const ping = (o = {}) => JSON.stringify({ id: ID, app: "setup", v: "0.21.1", tv: { brand: "Sony", model: "BRAVIA 4K", android: "14", sdk: 34, leanback: true }, ok: { adb: true, video: false }, ...o });

test("validate: good ping, flags tri-state, unknown fields dropped", () => {
  const r = validate(ping({ extra: 1, tv: { brand: " Sony ", model: "X", leanback: true, ip: "1.2.3.4" } }));
  assert.equal(r.brand, "Sony"); assert.equal(r.ip, undefined); assert.equal(r.adb, 1); assert.equal(r.video, 0); assert.equal(r.optimize, null);
  assert.equal(r.leanback, 1);
});
test("validate: rejects malformed", () => {
  for (const t of ["", "{", "[]", "null", ping({ id: "nope" }), ping({ app: "x" }), ping({ tv: null }), ping({ tv: { brand: "", model: "m" } }), ping({ tv: { brand: "a" } }), ping({ id: "3f2b8c1e-5a4d-1e6f-9a7b-1c2d3e4f5a6b" })])
    assert.equal(validate(t), null, t);
  assert.equal(validate(ping({ pad: "x".repeat(MAX_BODY) })), null);
});
test("str trims, cuts to 64, strips control chars", () => {
  assert.equal(str("  a\nb\t c  "), "a b c");
  assert.equal(str("x".repeat(100)).length, 64);
  assert.equal(str(5), "");
});
test("validate: sdk out of range -> null, numeric android ok", () => {
  assert.equal(validate(ping({ tv: { brand: "a", model: "b", sdk: 9999, android: 14 } })).sdk, null);
  assert.equal(validate(ping({ tv: { brand: "a", model: "b", android: 14 } })).android, "14");
});
test("hashId: 16 hex, stable, not the id", async () => {
  const h = await hashId(ID);
  assert.match(h, /^[0-9a-f]{16}$/); assert.equal(h, await hashId(ID)); assert.ok(!ID.includes(h));
});
test("kindOf", () => {
  assert.equal(kindOf("Sony", "BRAVIA", ""), "tv");
  assert.equal(kindOf("LG", "OLED55C1", ""), "tv");
  assert.equal(kindOf("Xiaomi", "MIBOX4", ""), "box");
  assert.equal(kindOf("Google", "Chromecast", ""), "box");
  assert.equal(kindOf("FPT", "FPT Play Box S", ""), "box");
  assert.equal(kindOf("XGIMI", "MoGo 2", ""), "projector");
  assert.equal(kindOf("Hisense", "HY300", ""), "projector");
});
test("aggregate: merges android rows, rates, sorts, no ids", () => {
  const rows = [
    { brand: "Sony", model: "A", device: "a1", android: "9", leanback: 1, n: 2, adb_y: 2, adb_n: 2, video_y: 0, video_n: 1, optimize_y: 0, optimize_n: 0, last: "2026-10-01" },
    { brand: "Sony", model: "A", android: "11", leanback: 1, n: 1, adb_y: 0, adb_n: 1, video_y: 1, video_n: 1, optimize_y: 0, optimize_n: 0, last: "2026-10-20" },
    { brand: "Xiaomi", model: "MIBOX4", android: "9", leanback: 1, n: 5, adb_y: 0, adb_n: 0, video_y: 0, video_n: 0, optimize_y: 0, optimize_n: 0, last: "2026-10-02" },
  ];
  const a = aggregate(rows, new Date("2026-10-25T00:00:00Z"));
  assert.equal(a.models[0].model, "MIBOX4"); assert.equal(a.models[0].kind, "box");
  const s = a.models[1];
  assert.equal(s.count, 3); assert.deepEqual(s.android, ["11", "9"]); assert.equal(s.last, "2026-10-20");
  assert.equal(s.ok.adb.rate, 0.67); assert.equal(s.ok.video.rate, 0.5); assert.equal(s.ok.optimize.rate, null);
  assert.deepEqual(s.devices, ["a1"]); assert.equal(a.installs, 8); assert.equal(a.updated, "2026-10-25");
  assert.ok(!JSON.stringify(a).includes('"h"'));
});
