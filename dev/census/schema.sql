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
