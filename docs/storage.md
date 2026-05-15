# Storage & Database

## SQLite Databases

The app stores data in two separate SQLite files:

- **`AlkitabRoomDb`** — Room database (`AppDatabase` at `@Database(version = 7)`). Owns every InternalDb table after the REM-10/REM-11/REM-27/REM-28/REM-29/REM-30/REM-31 migration sweep.
- **`AlkitabDb`** — legacy hand-rolled `SQLiteOpenHelper` (`InternalDbHelper`). After REM-31 every table here is a rollback safety net only — no production code writes to it.

`InternalDb` plus per-table facade DAOs (`MarkerDao`, `LabelDao`, `Marker_LabelDao`, `VersionDao`, …, `SyncShadowDao`) preserve the legacy public surface — callers don't need to know that Room backs every table now.

### AlkitabRoomDb (Room)

`yuku.alkitab.base.storage.room.AppDatabase`. Schema JSONs are exported under `Alkitab/schemas/`. Migration history:

- v1 (REM-11) — introduced the `version` table
- v2 (REM-10) — added `marker`, `label`, `marker_label` tables and their indexes via `MIGRATION_1_2`
- v3 (REM-27) — added the `devotion` table via `MIGRATION_2_3`
- v4 (REM-28) — added the `per_version` table via `MIGRATION_3_4`
- v5 (REM-29) — added `progress_mark` + `progress_mark_history` via `MIGRATION_4_5`
- v6 (REM-30) — added `reading_plan` + `reading_plan_progress` via `MIGRATION_5_6`
- v7 (REM-31) — added `sync_shadow` + `sync_log` via `MIGRATION_6_7`

The legacy tables in `AlkitabDb` are still created by `InternalDbHelper` as a rollback safety net; a one-time idempotent copy (one `*DataMigration` object per table set, wired from `S.db`'s lazy initializer) moves their rows into Room on first launch with the migrated code. After every flag is set, the legacy DB is only read for migration probes.

#### Room Tables

**marker** — bookmarks, notes, highlights
```sql
_id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
gid          TEXT              -- globally unique ID for sync
ari          INTEGER NOT NULL  -- verse reference (ARI encoding)
kind         INTEGER NOT NULL  -- 0=bookmark, 1=note, 2=highlight
caption      TEXT              -- title/content/highlight JSON
verseCount   INTEGER NOT NULL
createTime   INTEGER NOT NULL  -- epoch seconds
modifyTime   INTEGER NOT NULL  -- epoch seconds
-- Indices: ari, (kind, ari), (kind, modifyTime), (kind, createTime), gid
```

**label** — bookmark categories
```sql
_id              INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
gid              TEXT
title            TEXT
ordering         INTEGER NOT NULL
backgroundColor  TEXT
```

**marker_label** — junction table
```sql
_id        INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
gid        TEXT
marker_gid TEXT
label_gid  TEXT
-- Unique index on gid; indices on marker_gid and label_gid
```

**version** — downloaded Bible versions
```sql
_id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
locale       TEXT
shortName    TEXT
longName     TEXT
description  TEXT
filename     TEXT
preset_name  TEXT
modifyTime   INTEGER NOT NULL
active       INTEGER NOT NULL  -- 0 or 1
ordering     INTEGER NOT NULL
```

### Tables now in `AlkitabRoomDb` (rollback shadow still in `AlkitabDb`)

The columns below describe both the legacy `AlkitabDb` schema (managed by `InternalDbHelper`, kept around as a rollback safety net) and the Room-backed `AlkitabRoomDb` equivalent (managed by `AppDatabase`, the active source of truth). Production code reads and writes the Room tables; the legacy tables remain frozen at the schema below.

**ProgressMark** (Room: `progress_mark`) — 5 reading progress pins
```sql
preset_id    INTEGER PRIMARY KEY  -- 0-4
caption      TEXT
ari          INTEGER
modifyTime   TEXT
```

**ReadingPlan** — reading plan data
```sql
_id          INTEGER PRIMARY KEY
version      INTEGER
name         TEXT
title        TEXT
description  TEXT
duration     INTEGER          -- total days
startTime    INTEGER
data         BLOB             -- RPB binary data
```

**ReadingPlanProgress** — completion tracking
```sql
_id              INTEGER PRIMARY KEY
reading_plan_id  INTEGER REFERENCES ReadingPlan
reading_code     INTEGER          -- (day << 8) | sequence
checkTime        INTEGER
```

**Devotion** — cached devotional articles
```sql
name              TEXT
date              TEXT             -- yyyymmdd
readyToUse        INTEGER
body              TEXT
touchTime         INTEGER
dataFormatVersion INTEGER
```

**SyncShadow** (Room: `sync_shadow`) — last-synced entity state
```sql
_id        INTEGER PRIMARY KEY
syncSetName TEXT NOT NULL
revno      INTEGER NOT NULL
data       BLOB
-- Index: syncSetName (non-unique, mirroring legacy `index_SyncShadow_01`)
```
A SyncShadow row's `data` column can exceed the Android CursorWindow 2 MB cap (full Mabel snapshots routinely cross that mark); `SyncShadowDao.getBySyncSetName` reads the blob in 1 MB chunks via SQLite's `substr()`. Room can't express chunked cursors natively, so that single path drops down to raw SQL against `roomDb.openHelper.readableDatabase`.

**SyncLog** (Room: `sync_log`) — sync audit log
```sql
_id        INTEGER PRIMARY KEY
createTime INTEGER NOT NULL
kind       INTEGER NOT NULL  -- SyncRecorder.EventKind.code
syncSetName TEXT
params     TEXT              -- JSON of the kvpairs passed to SyncRecorder.log
-- Index: createTime (non-unique, mirroring legacy `index_SyncLog_01`)
```

**PerVersion** (Room: `per_version`) — per-version settings
```sql
versionId TEXT PRIMARY KEY
settings  TEXT              -- JSON settings blob
```

### SongDb (Songs Database)

Separate database for song book storage. Each song book's songs are stored as serialized Java objects (via `ObjectInputStream` from `Parcelable`-based `Song` class).

## SharedPreferences

Accessed via `Afw.Preferences` wrapper with caching and batch commits. Keys defined in `Prefkey.kt` enum (100+ entries).

### Key Preference Categories

- **UI**: `fontSize`, `typeface`, `lineSpacing`, `backgroundColor`, `textColor`, `verseNumberColor`, `nightMode`
- **Navigation**: `lastBookId`, `lastChapter`, `lastVerse`, `lastSplitVersion`, `lastSplitOrientation`
- **Sync**: `sync_simpleToken`, `fcm_registrationId`, `installation_id`
- **Reading**: `activeReadingPlanId`, `currentReadingPlanProgress`
- **Search**: `searchHistory`
- **Devotion**: `devotion_lastDate`, `devotion_kind`
- **Widget**: per-widget settings keyed by widget ID

## File Storage

### App Internal Storage

- `files/version_config.json` — cached version catalog update from server
- `files/*.yes` — downloaded Bible version files
- `files/pdb-*.yes` — converted PDB Bible versions
- `files/songs/` — cached MIDI files for song playback

### Assets (Bundled)

- `assets/internal/` — placeholder internal Bible version data (73 files)
- `assets/version_config.json` — bundled version catalog (fallback)
- `assets/help/` — help documentation HTML
- `assets/templates/` — template files

### External Storage (Legacy)

The app previously supported reading `.yes` files from external storage (`READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`). These permissions are capped at max SDK 32, reflecting the move to scoped storage.

## Database Access Patterns

All database access goes through `InternalDb` (accessed via `App.services.storage.db`, or the legacy `S.db`). Common patterns:

- **Markers**: `insertOrUpdateMarker()`, `deleteMarkerById()`, `listMarkersForAriKind()` — route through `MarkerDao` → Room
- **Highlights**: `updateOrInsertHighlights()` — manages per-verse highlight data; routes through Room
- **Attributes**: `putAttributes()` — loads bookmark/note/highlight counts for verse display; routes through Room
- **Versions**: `listAllVersions()` — retrieves all versions sorted by ordering; routes through `VersionDao` → Room
- **Devotions**: `storeArticleToDevotions()` — caches downloaded articles; still routes through `InternalDb`'s raw-SQL path (legacy `AlkitabDb`)

Database operations are generally synchronous on the calling thread; the Room database explicitly enables `allowMainThreadQueries()` so the migration is a drop-in replacement for the existing synchronous facade. Migrating individual operations to coroutines/`Flow` is tracked under REM-15 in `docs/tech-debt-remediation.md`.
