-- A submitted community theme, until the owner reviews it (dev/sign/review_themes.py). Pictures live here as BLOBs and are
-- deleted when the theme is rejected, or once the script confirms it saved the files (done).
CREATE TABLE IF NOT EXISTS themes (
  id      TEXT PRIMARY KEY,    -- 8 hex
  at      INTEGER NOT NULL,    -- unix seconds
  status  TEXT NOT NULL,       -- pending | approved | rejected | done
  name    TEXT NOT NULL,
  by      TEXT NOT NULL,
  lang    TEXT NOT NULL,       -- vi | en
  conf    TEXT NOT NULL,       -- JSON, THEME_KEYS only
  preview BLOB,
  wall    BLOB,
  bytes   INTEGER NOT NULL DEFAULT 0   -- size of the pictures while they are kept (the storage cap sums it)
) WITHOUT ROWID;
CREATE INDEX IF NOT EXISTS themes_status ON themes (status, at, bytes);

-- The photos of a theme's rotation (its own wall photos, at most 10, each re-encoded by the app to <= 350 KB): one row each, deleted with the
-- theme's pictures (reject / done) and when the theme row is purged. Counted in themes.bytes.
CREATE TABLE IF NOT EXISTS wallpics (
  theme TEXT NOT NULL,
  key   TEXT NOT NULL,         -- photo, photo2 .. photo10: the wall key used in conf
  pic   BLOB NOT NULL,
  PRIMARY KEY (theme, key)
) WITHOUT ROWID;

-- Submissions per sender per UTC day. h = sha256(IP + day + salt); rows of earlier days are deleted on every POST, the IP is never stored.
CREATE TABLE IF NOT EXISTS sends (
  day TEXT NOT NULL,
  h   TEXT NOT NULL,
  n   INTEGER NOT NULL,
  PRIMARY KEY (day, h)
) WITHOUT ROWID;
