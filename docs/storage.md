# Storage & Database

## SQLite Databases

### InternalDb (Main Database)

Managed by `InternalDbHelper` with version-based schema migrations (current version 50+).

#### Core Tables

**Marker** — bookmarks, notes, highlights
```sql
_id          INTEGER PRIMARY KEY
gid          TEXT UNIQUE       -- globally unique ID for sync
ari          INTEGER           -- verse reference (ARI encoding)
kind         INTEGER           -- 0=bookmark, 1=note, 2=highlight
caption      TEXT              -- title/content/highlight JSON
verseCount   INTEGER           -- number of verses covered
createTime   TEXT              -- ISO timestamp
modifyTime   TEXT              -- ISO timestamp
-- Indices on: ari, (kind, ari), (kind, modifyTime), gid
```

**Label** — bookmark categories
```sql
_id              INTEGER PRIMARY KEY
gid              TEXT UNIQUE
title            TEXT
ordering         INTEGER
backgroundColor  TEXT         -- hex color string
```

**Marker_Label** — junction table
```sql
_id        INTEGER PRIMARY KEY
gid        TEXT UNIQUE
marker_gid TEXT
label_gid  TEXT
```

**Version** — downloaded Bible versions
```sql
_id          INTEGER PRIMARY KEY
locale       TEXT
shortName    TEXT
longName     TEXT
description  TEXT
filename     TEXT
preset_name  TEXT
modifyTime   INTEGER
active       INTEGER          -- 0 or 1
ordering     INTEGER
```

**ProgressMark** — 5 reading progress pins
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

**SyncShadow** — last-synced entity state
```sql
_id       INTEGER PRIMARY KEY
syncSetName TEXT
revno      INTEGER
data       BLOB
```

**SyncLog** — sync audit log
```sql
_id       INTEGER PRIMARY KEY
createTime INTEGER
kind       INTEGER
entityType TEXT
message    TEXT
```

**PerVersion** — per-version settings
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

All database access goes through `InternalDb` (accessed via `S.db`). Common patterns:

- **Markers**: `insertOrUpdateMarker()`, `deleteMarkerById()`, `listMarkersForAriKind()`
- **Highlights**: `updateOrInsertHighlights()` — manages per-verse highlight data
- **Attributes**: `putAttributes()` — loads bookmark/note/highlight counts for verse display
- **Versions**: `listAllVersions()` — retrieves all versions sorted by ordering
- **Devotions**: `storeArticleToDevotions()` — caches downloaded articles

Database operations are generally synchronous on the calling thread. No Room, no DAO layer — direct SQLite via `SQLiteOpenHelper` and `Cursor` operations.
