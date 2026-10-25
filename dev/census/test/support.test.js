import { test } from "node:test";
import assert from "node:assert/strict";
import { askable } from "../src/support.js";

test("askable: only the TV's own /api routes, short, no tricks", () => {
  for (const p of ["/api/state", "/api/diagnose", "/api/setup/version", "/api/apps?system=1", "/api/wirelog"]) assert.ok(askable(p), p);
  for (const p of ["/etc/passwd", "/api/../x", "api/state", "/api/state#x", "/api/a b", "/" + "a".repeat(400), 3, null])
    assert.ok(!askable(p), String(p));
});
