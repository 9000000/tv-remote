-- One row per (install, model). h = first 16 hex of sha256(id): the id itself is never stored.
CREATE TABLE IF NOT EXISTS tvs (
  h        TEXT NOT NULL,
  brand    TEXT NOT NULL,
  model    TEXT NOT NULL,
  device   TEXT, board TEXT, hw TEXT, soc TEXT,
  android  TEXT, sdk INTEGER,
  leanback INTEGER,            -- 1 / 0
  app      TEXT, v TEXT,
  adb      INTEGER, video INTEGER, optimize INTEGER,   -- 1 worked, 0 failed, NULL not tried
  first    TEXT NOT NULL,      -- YYYY-MM-DD (UTC)
  last     TEXT NOT NULL,
  PRIMARY KEY (h, brand, model)
) WITHOUT ROWID;
CREATE INDEX IF NOT EXISTS tvs_model ON tvs (brand, model);

-- One row per (UTC day, app): installs that pinged that day (`active`) and of those, first seen that day (`new`).
-- Fed by the ping's upsert (it returns a row only for the first counted ping of the day), for the growth chart.
CREATE TABLE IF NOT EXISTS days (
  day     TEXT NOT NULL,       -- YYYY-MM-DD (UTC)
  app     TEXT NOT NULL,       -- setup | phone | pc
  active  INTEGER,             -- NULL on days before the table existed (only `new` could be rebuilt from tvs.first)
  new     INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (day, app)
) WITHOUT ROWID;
