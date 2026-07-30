# Storage & Database

## SQLite Databases

The app stores data in three separate SQLite files. The two legacy `SQLiteOpenHelper`-managed files (`AlkitabDb`, `SongDb`) remain the active sources of truth for everything Bible-related; the Songs subsystem moved to Room (`AlkitabSongRoomDb`) under REM-32.

- **`AlkitabDb`** — hand-rolled `SQLiteOpenHelper` (`InternalDbHelper`). Sets `user_version` to the build-time `App.getVersionCode()` and migrates through `onUpgrade` based on that. Holds every Bible-related table (markers, labels, versions, devotions, per-version settings, progress marks, reading plans, sync shadows, sync logs).
- **`SongDb`** — hand-rolled `SQLiteOpenHelper` (`SongDbHelper`). After REM-32, every table here is a rollback safety net only, plus the source of legacy rows for `SongDbDataMigration` on first launch with the migrated code.
- **`AlkitabSongRoomDb`** — Room database (`SongRoomDatabase` at `@Database(version = 1)`). Owns the `song_info` and `song_book_info` tables. Lives in a separate file from `AlkitabDb` because the Songs subsystem is module-isolated (see [Songs](modules/songs.md)) — it shares no rows, foreign keys, or transactions with the Bible-reading tables.

`InternalDb` plus per-table facade DAOs (`MarkerDao`, `LabelDao`, `Marker_LabelDao`, `VersionDao`, `DevotionDao`, `PerVersionDao`, `ProgressMarkDao`, `ReadingPlanDao`, `SyncShadowDao`) talk directly to `AlkitabDb` via raw SQL. The `SongDb` facade routes through `SongRoomDatabase`.

### AlkitabDb (legacy SQLite)

Schema is created in `InternalDbHelper.onCreate` and migrated in `InternalDbHelper.onUpgrade`. Tables:

**Marker** — bookmarks, notes, highlights
```sql
_id          INTEGER PRIMARY KEY AUTOINCREMENT
gid          TEXT              -- globally unique ID for sync
ari          INTEGER NOT NULL  -- verse reference (ARI encoding)
kind         INTEGER NOT NULL  -- 0=bookmark, 1=note, 2=highlight
caption      TEXT              -- title/content/highlight JSON
verseCount   INTEGER NOT NULL
createTime   INTEGER NOT NULL  -- epoch seconds
modifyTime   INTEGER NOT NULL  -- epoch seconds
-- Indices: ari, (kind, ari), (kind, modifyTime), (kind, createTime), gid,
--          (kind, caption COLLATE NOCASE)
```

**Label** — bookmark categories (Indonesian column names from the legacy schema)
```sql
_id          INTEGER PRIMARY KEY AUTOINCREMENT
gid          TEXT
judul        TEXT              -- title
urutan       INTEGER           -- ordering
warnaLatar   TEXT              -- backgroundColor
```

**Marker_Label** — junction table
```sql
_id        INTEGER PRIMARY KEY AUTOINCREMENT
gid        TEXT
marker_gid TEXT
label_gid  TEXT
```

**Version** — downloaded Bible versions
```sql
_id          INTEGER PRIMARY KEY AUTOINCREMENT
locale       TEXT
shortName    TEXT
longName     TEXT
description  TEXT
filename     TEXT
preset_name  TEXT
modifyTime   INTEGER
active       INTEGER           -- 0 or 1
ordering     INTEGER
```

**ProgressMark** — 5 reading progress pins
```sql
preset_id    INTEGER PRIMARY KEY  -- 0-4
caption      TEXT
ari          INTEGER
modifyTime   TEXT
```

**ProgressMarkHistory** — append-only history of every pin update
```sql
_id                       INTEGER PRIMARY KEY AUTOINCREMENT
progress_mark_preset_id   INTEGER
progress_mark_caption     TEXT
ari                       INTEGER
createTime                INTEGER
```

**ReadingPlan** — reading plan data
```sql
_id          INTEGER PRIMARY KEY AUTOINCREMENT
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
_id                           INTEGER PRIMARY KEY AUTOINCREMENT
reading_plan_progress_gid     TEXT NOT NULL
reading_code                  INTEGER NOT NULL  -- (day << 8) | sequence
checkTime                     INTEGER
-- Unique index: (reading_plan_progress_gid, reading_code)
```

**Devotion** — cached devotional articles
```sql
_id                INTEGER PRIMARY KEY AUTOINCREMENT
name               TEXT
date               TEXT             -- yyyymmdd
readyToUse         INTEGER
body               TEXT
touchTime          INTEGER
dataFormatVersion  INTEGER
```

**SyncShadow** — last-synced entity state
```sql
_id          INTEGER PRIMARY KEY AUTOINCREMENT
syncSetName  TEXT NOT NULL
revno        INTEGER NOT NULL
data         BLOB
-- Index: syncSetName (non-unique)
```
A SyncShadow row's `data` column can exceed the Android CursorWindow 2 MB cap (full Mabel snapshots routinely cross that mark); `SyncShadowDao.getBySyncSetName` reads the blob in 1 MB chunks via SQLite's `substr()`.

**SyncLog** — sync audit log
```sql
_id          INTEGER PRIMARY KEY AUTOINCREMENT
createTime   INTEGER NOT NULL
kind         INTEGER NOT NULL   -- SyncRecorder.EventKind.code
syncSetName  TEXT
params       TEXT               -- JSON of the kvpairs passed to SyncRecorder.log
-- Index: createTime (non-unique)
```

**PerVersion** — per-version settings
```sql
_id        INTEGER PRIMARY KEY AUTOINCREMENT
versionId  TEXT NOT NULL
settings   TEXT                 -- JSON settings blob
-- Unique index: versionId
```

### AlkitabSongRoomDb (Room) — Songs Database

`yuku.alkitab.base.storage.room.SongRoomDatabase` at `@Database(version = 1)`. Lives in its own SQLite file so the Songs subsystem stays module-isolated. Schema JSON is exported under `Alkitab/schemas/yuku.alkitab.base.storage.room.SongRoomDatabase/`.

**song_info** — every song in every installed song book
```sql
_id                INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
bookName           TEXT
code               TEXT
title              TEXT
title_original     TEXT
ordering           INTEGER NOT NULL  -- caller-supplied display order
dataFormatVersion  INTEGER NOT NULL  -- 5 = JSON (SongDocumentJson.DATA_FORMAT_VERSION); older = legacy Parcelable
data               BLOB              -- UTF-8 JSON-encoded SongDocument (dataFormatVersion 5), or a legacy
                                      -- Parcelable-marshalled yuku.kpri.model.Song (any other value)
updateTime         INTEGER NOT NULL  -- Sqlitil.nowDateTime()
-- Indices: (bookName, code) non-unique, (bookName, ordering) non-unique
```

The `data` BLOB stores the song's lyrics, verses, refrains, scripture references, etc. `SongDb.readDocument` dispatches on `dataFormatVersion`: JSON rows (`5`) parse directly into a `SongDocument`; legacy rows are decoded by the pure-JVM `LegacyParcelDecoder`, converted via `LegacySongConverter`, and written back as JSON (bumping `dataFormatVersion` to `5`) the first time they're read — a lazy, at-most-once, on-read migration with no bulk pass. This replaced the Parcelable-only storage that REM-21 flagged as a bad design choice (the marshalled `Parcel` byte layout changed in Android 13, so a device that stored songs pre-13 and upgraded could fail to read its own BLOBs with the platform `Parcel` alone). See `docs/features/portable-songs/design.md` and `docs/modules/songs.md`.

**song_book_info** — installed song book metadata
```sql
_id        INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
name       TEXT  -- the lookup key, e.g. "NKB" / "PKJ" / "KJ"
title      TEXT
copyright  TEXT
-- Index: name (non-unique, matching legacy `SongBookInfo_001_index`)
```

The legacy `SongDb` SQLite file (managed by `SongDbHelper`) is left intact as a rollback safety net; `SongDbDataMigration` performs a one-time idempotent copy of both tables into Room on first launch with the migrated code, gated by `Prefkey.song_db_data_migration_v1_done` (flag-based gate to avoid the #195 resurrect-on-clear footgun).

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

The `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` permissions (for reading `.yes` files from external storage) are declared with `maxSdkVersion="32"`; on newer Android the app relies on scoped storage.

## Database Access Patterns

All database access goes through `InternalDb` (accessed via `App.services.storage.db`, or the legacy `S.db`). Common patterns:

- **Markers**: `insertOrUpdateMarker()`, `deleteMarkerById()`, `listMarkersForAriKind()` — route through `MarkerDao` → raw SQL on `AlkitabDb`
- **Highlights**: `updateOrInsertHighlights()` — manages per-verse highlight data
- **Attributes**: `putAttributes()` — loads bookmark/note/highlight counts for verse display
- **Versions**: `listAllVersions()` — retrieves all versions sorted by ordering, via `VersionDao` → raw SQL on `AlkitabDb`
- **Devotions**: `storeArticleToDevotions()` — caches downloaded articles
- **Songs**: `SongDb.storeSongs()` / `SongDb.getSong()` — route through `SongRoomDao` → Room

Database operations are generally synchronous on the calling thread. The Songs Room database enables `allowMainThreadQueries()` to keep the existing synchronous facade contract.
