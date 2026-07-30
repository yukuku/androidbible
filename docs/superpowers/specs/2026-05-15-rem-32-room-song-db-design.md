# REM-32: Migrate SongDb to Room — Design

**Status:** Design
**Date:** 2026-05-15
**BRICE:** B=3 R=3 I=2 C=3 E=5 → **3.2**
**Phase:** 2 — Architecture Improvements
**Addresses:** TD-02 follow-through — the last hand-rolled `SQLiteOpenHelper` (`SongDbHelper`) in the app.

## Outcome

`SongDb` (a separate SQLite file `SongDb` managed by `SongDbHelper extends SQLiteOpenHelper`) becomes Room-backed. After this change the Songs subsystem no longer relies on a hand-rolled `SQLiteOpenHelper` for its production data; `SongDbHelper` is reduced to rollback-shadow status.

The public surface of the `SongDb` facade (`Alkitab/src/main/java/yuku/alkitab/base/storage/SongDb.java`) is preserved. Every call site in `SongListActivity`, `SongViewActivity`, `SongBookUtil`, and `SongFragment` keeps using `App.services.storage.songDb.…` with no signature changes.

## Approach: Option A — new `SongRoomDatabase`

Brainstormed against Option B (folding the two SongDb tables into `AppDatabase`). Decision: keep the two domains isolated, mirroring the existing separate-file split.

- The Songs module is genuinely self-contained (`docs/modules/songs.md`); it does not share rows, FKs, or transactions with Bible-reading tables.
- Bumping `AppDatabase` for Songs-only changes would force migration-test re-runs across unrelated subsystems and conflate audit trails.
- The cost of one extra `RoomDatabase` class file + a per-DB schema subdirectory is negligible.
- REM-21 (Song storage: Parcelable → JSON) is a future change to the *payload* in the `data` BLOB column. REM-32 is a strict storage-engine swap that round-trips bytes bit-for-bit, so the two efforts compose cleanly: REM-21 lands on top of REM-32 without re-touching the schema layer.

## Architecture

```
SongRoomDatabase  (NEW @Database(version = 1))
  ├── SongInfoEntity        ── table "song_info"
  ├── SongBookInfoEntity    ── table "song_book_info"
  └── SongRoomDao
        ├── (SongInfo primitives)
        ├── (SongBookInfo primitives)
        └── (test-only counts/list-all)

SongDbDataMigration  (NEW)
  └── copyFromLegacyDbIfNeeded(roomDb, legacyHelper)
        gated by Prefkey.song_db_data_migration_v1_done

SongDb.java  (REWRITTEN as facade-over-Room)
  └── public method signatures unchanged
        delegates to SongRoomDao via SongRoomDatabase.get(App.context)

S.kt
  └── songDb lazy: build helper + roomDb, run migration once, then construct SongDb
```

Five files for the table set — two entities, the Room DAO, the data migration, plus the rewritten facade. No surprises.

### Database location and KSP schemas

`SongRoomDatabase.kt` lives next to `AppDatabase.kt` in `Alkitab/src/main/java/yuku/alkitab/base/storage/room/`. KSP's `room.schemaLocation` arg already points at `Alkitab/schemas/`; the framework writes each `@Database` class's JSON to a subdir keyed by FQCN, so `Alkitab/schemas/yuku.alkitab.base.storage.room.SongRoomDatabase/1.json` lands automatically with no `build.gradle.kts` change.

The on-disk SQLite filename for the new Room DB is `AlkitabSongRoomDb`, distinct from the legacy `SongDb` filename so the two coexist during the rollback window.

### Schema mapping

Legacy `Table.SongInfo` enum → Room `song_info` table:

| Legacy column         | Type     | Room column            | Notes |
| --------------------- | -------- | ---------------------- | ----- |
| `_id`                 | integer  | `_id` (autoGenerate)   | Reassigned by Room on copy; legacy `_id` not preserved (no FKs reference it). |
| `bookName`            | text     | `bookName: String?`    | Used in keyed lookups but legacy schema permits NULL — kept nullable in Room. |
| `code`                | text     | `code: String?`        | Same. |
| `title`               | text     | `title: String?`       | Legacy `collate nocase` suffix is on the *column*; Room can't express that — facade callers already pass exact-case lookups, the affected paths are `db.query(... where bookName=?)` which doesn't use NOCASE. The collation only matters for the unused `ORDER BY title` path; if a future query needs it, switch to `COLLATE NOCASE` in the SQL. |
| `title_original`      | text     | `title_original: String?` | Same as `title`. |
| `ordering`            | integer  | `ordering: Int`        | Non-null in practice (every `storeSongs` call writes a value), keep non-null in Room. |
| `dataFormatVersion`   | integer  | `dataFormatVersion: Int` | Always written with a concrete value (1, 2, or 3) — non-null in Room. |
| `data`                | blob     | `data: ByteArray?`     | Parcelable-marshalled `Song`. Round-tripped byte-for-byte. Same `equals`/`hashCode` content-equality override as `SyncShadowEntity`. |
| `updateTime`          | integer  | `updateTime: Int`      | Always written via `Sqlitil.nowDateTime()` — non-null in Room. |

Indexes on `song_info` (mirroring `SongDbHelper.setupTableSongInfo`):
- `(bookName, code)` — non-unique, name `index_song_info_bookName_code`
- `(bookName, ordering)` — non-unique, name `index_song_info_bookName_ordering`

Legacy `Table.SongBookInfo` enum → Room `song_book_info` table:

| Legacy column | Type    | Room column          | Notes |
| ------------- | ------- | -------------------- | ----- |
| `_id`         | integer | `_id` (autoGenerate) | Reassigned on copy. |
| `name`        | text    | `name: String?`      | Effectively the lookup key. Legacy schema allows NULL; the migration drops rows with NULL `name` via `?: continue` (same defence-in-depth pattern as `SyncShadowDataMigration` for NULL `syncSetName`). |
| `title`       | text    | `title: String?`     | Kept nullable. |
| `copyright`   | text    | `copyright: String?` | Kept nullable. |

Index on `song_book_info`:
- `name` — non-unique, name `index_song_book_info_name`

### Data migration

`SongDbDataMigration.copyFromLegacyDbIfNeeded(roomDb: SongRoomDatabase, legacyHelper: SongDbHelper)`:

1. Bail if `Preferences.getBoolean(Prefkey.song_db_data_migration_v1_done, false)` is true.
2. Upgrade-path branch: if `dao.countAllSongInfos() > 0 || dao.countAllSongBookInfos() > 0`, set the flag and bail (handles a build that shipped without the flag).
3. Inside a single `roomDb.runInTransaction { … }`:
   - Stream every legacy `SongInfo` row by `_id ASC` cursor.
   - For each row, materialize a `SongInfoEntity(_id = 0L, …)` and call `dao.insertSongInfo`. Drop rows where `bookName` is NULL or `code` is NULL — those rows are unreachable from any facade path and would also create ambiguous lookup state in Room.
   - Then stream every legacy `SongBookInfo` row, drop rows with NULL `name`, insert.
4. Set the `song_db_data_migration_v1_done` flag.

Memory profile: row-by-row streaming. The `data` BLOB column is bounded — songs are small (~kB each), nowhere near the 2 MB CursorWindow cap that forced `SyncShadowDataMigration` to do extra work. No chunked reads needed.

### Facade rewrite

`SongDb.java` stays at the same path (`Alkitab/src/main/java/yuku/alkitab/base/storage/SongDb.java`) with the same constructor `public SongDb(SongDbHelper helper)` so `S.kt`'s lazy initializer doesn't need to change beyond inserting the one-time migration call. The helper parameter is retained for ABI parity but the helper is no longer used by any facade method. Every public method (`storeSongs`, `getSong`, `songExists`, `getFirstSongFromBook`, `getAnySong`, `listSongInfosByBookName`, `listSongInfosByBookNameAndDeepFilter`, `deleteSongBook`, `getSongBookInfo`, `listSongBookInfos`, `countSongBookInfos`, `insertSongBookInfo`, `getDataFormatVersionForSongs`, `getSongUpdateTime`) routes through the cached Room DAO (resolved once via `by lazy` from `SongRoomDatabase.get(App.context).songRoomDao()`).

`marshallSong` / `unmarshallSong` are unchanged: they operate on `byte[]` payloads, and the BLOB column stores those bytes the same way under Room as under SQLite.

Two static methods are referenced from `SongDbHelper`:

- `SongDb.getSongBookInfo(SQLiteDatabase db, String name)` — only called from `SongDbHelper.insertSongBookInfosFromSongInfos` (the 4.1-beta2 legacy upgrade path).
- `SongDb.insertSongBookInfo(SQLiteDatabase db, SongBookUtil.SongBookInfo info)` — same caller.

Both are legacy-only and never need to talk to Room. They stay on `SQLiteDatabase` and continue to operate against the legacy `AlkitabDb`-style SongDb file. `SongDbHelper.onUpgrade` keeps working: if a pre-4.1-beta2 install ever runs the new build, `SongDbHelper` will still populate its own `SongBookInfo` table from the legacy `SongInfo` data exactly like before, and the subsequent `SongDbDataMigration` run will copy the result into Room.

Deferred `vacuum`: the legacy `deleteSongBook` calls `db.execSQL("vacuum")` after deleting rows. Room cannot run `VACUUM` inside a transaction (SQLite restriction) and Room's transaction model wraps every write. The facade keeps the `VACUUM` semantics by running it via `roomDb.openHelper.writableDatabase.execSQL("vacuum")` outside any transaction, after the `dao.deleteSongBook(name)` returns. Skipping the vacuum would leak disk space on every song-book delete; preserving it is cheap.

### S.kt wiring

```kotlin
@JvmStatic
val songDb: SongDb by lazy {
    val helper = SongDbHelper()
    val roomDb = yuku.alkitab.base.storage.room.SongRoomDatabase.get(App.context)
    yuku.alkitab.base.storage.room.SongDbDataMigration.copyFromLegacyDbIfNeeded(roomDb, helper)
    SongDb(helper)
}
```

One extra block of init code. The lazy init runs on first `App.services.storage.songDb` access (typically `SongListActivity.onCreate` or the daily-verse widget, neither of which are in the Bible-reading hot path).

## Testing

Test layout:

- `Alkitab/src/test/java/yuku/alkitab/base/storage/room/SongRoomDaoTest.kt` — Robolectric. Tests every SongInfo primitive (insert, find-by-bookName-and-code, songExists, getFirstByBookName, getAnySong, listByBookName, listAllByBookName-null-arg, countSongBookInfos, deleteByBookName, getDataFormatVersionForBookName, getUpdateTimeByBookNameAndCode) and every SongBookInfo primitive (insertReplace, findByName, listAll, deleteByName, countAll). Verifies the `(bookName, ordering)` index is queried via `ORDER BY ordering` returning ordering-asc rows. Round-trips `data: ByteArray?` for null + empty + populated values.
- `Alkitab/src/test/java/yuku/alkitab/base/storage/room/SongDbDataMigrationTest.kt` — Robolectric. Cases mirroring `SyncShadowDataMigrationTest`:
  - copy is no-op on empty/empty
  - SongInfo rows copy with every field round-tripped (including the `data` BLOB)
  - SongBookInfo rows copy with every field round-tripped
  - legacy NULL `bookName` / NULL `code` SongInfo rows are dropped
  - legacy NULL `name` SongBookInfo rows are dropped
  - idempotency: running twice does not duplicate
  - fresh `_id` assignment
  - issue #195 resurrect-guard: clear Room after first migration, second run is a strict no-op
  - successful copy sets the done flag
  - fresh-install (no legacy rows) still sets the flag
  - upgrade-path: flag unset but Room SongInfo present → flag set, no re-copy
  - upgrade-path: flag unset but Room SongBookInfo present → flag set, no re-copy
- `Alkitab/src/test/java/yuku/alkitab/base/storage/SongDbTest.kt` (NEW) — Robolectric facade parity gate. Stands up an in-memory `SongRoomDatabase` via `setForTesting`, constructs a real `SongDb` instance, and exercises every public method. Most important case: round-trip a real `Song` (built via `KpriModel.Song` constructor with verses/lyrics) through `storeSongs` + `getSong`, asserting that the recovered `Song.equals(original)` — proving the Parcelable BLOB is byte-stable through Room. Also tests `deleteSongBook` count return + post-delete `VACUUM` did not corrupt remaining rows.
- `Alkitab/src/test/java/yuku/alkitab/base/storage/room/SongRoomDatabaseMigrationTest.kt` (NEW) — schema-validation scaffold mirroring `AppDatabaseMigrationTest`. For v1 only there is no `Migration` to run, but the test invokes `helper.createDatabase("song-migration-test", 1)` and then opens the DB via `Room.databaseBuilder` to validate that the entity-emitted schema matches the schema JSON Room wrote to `Alkitab/schemas/yuku.alkitab.base.storage.room.SongRoomDatabase/1.json`. This is the same insurance `AppDatabaseMigrationTest.v1 schema JSON is shippable…` provides — it catches accidental entity changes that would silently break a future migration.

## Risks & mitigations

| Risk | Mitigation |
| ---- | ---------- |
| Parcelable BLOB round-trip subtly differs through Room. | Facade parity test (`SongDbTest.kt`) round-trips a real `Song` through `storeSongs` + `getSong`. If the BLOB shape ever changes byte-for-byte, this test fails before users see broken songs. |
| `VACUUM` removed accidentally. | Facade preserves the explicit `roomDb.openHelper.writableDatabase.execSQL("vacuum")` outside Room's transaction, after `deleteSongBook`. Test asserts post-delete rows are still readable (a corrupted vacuum would surface as a missing row or SQLite open failure). |
| First-launch migration race with `App.services.storage.songDb`. | The `S.kt` lazy initializer is single-threaded by Kotlin's `LazyThreadSafetyMode.SYNCHRONIZED` default; only one thread runs the migration. |
| Existing schema export config does not write the new DB's JSON. | KSP writes per-FQCN subdirs automatically (verified — `Alkitab/schemas/yuku.alkitab.base.storage.room.AppDatabase/*.json` shows the layout). No `build.gradle.kts` change needed. The CI build will fail loudly if the schema JSON is missing or out of sync. |
| `SongDbHelper.onUpgrade` for pre-4.1-beta2 installs still runs. | Untouched. If a user on a very old build upgrades through this release, `SongDbHelper` will first run its `addColumnSongInfo` + `insertSongBookInfosFromSongInfos` upgrade against the legacy SQLite file, then the new `SongDbDataMigration` will copy the post-upgrade state into Room. The two run in sequence inside the same `songDb` lazy init. |

## Files changed (summary)

**New:**
- `Alkitab/src/main/java/yuku/alkitab/base/storage/room/SongInfoEntity.kt`
- `Alkitab/src/main/java/yuku/alkitab/base/storage/room/SongBookInfoEntity.kt`
- `Alkitab/src/main/java/yuku/alkitab/base/storage/room/SongRoomDao.kt`
- `Alkitab/src/main/java/yuku/alkitab/base/storage/room/SongRoomDatabase.kt`
- `Alkitab/src/main/java/yuku/alkitab/base/storage/room/SongDbDataMigration.kt`
- `Alkitab/schemas/yuku.alkitab.base.storage.room.SongRoomDatabase/1.json` (KSP-generated, checked in)
- `Alkitab/src/test/java/yuku/alkitab/base/storage/room/SongRoomDaoTest.kt`
- `Alkitab/src/test/java/yuku/alkitab/base/storage/room/SongDbDataMigrationTest.kt`
- `Alkitab/src/test/java/yuku/alkitab/base/storage/room/SongRoomDatabaseMigrationTest.kt`
- `Alkitab/src/test/java/yuku/alkitab/base/storage/SongDbTest.kt`
- `docs/tech-debt-remediation/REM-32-room-song-db.md`

**Modified:**
- `Alkitab/src/main/java/yuku/alkitab/base/storage/SongDb.java` — rewritten as facade-over-Room
- `Alkitab/src/main/java/yuku/alkitab/base/storage/Prefkey.kt` — add `song_db_data_migration_v1_done`
- `Alkitab/src/main/java/yuku/alkitab/base/S.kt` — wire migration into `songDb` lazy initializer
- `docs/tech-debt-remediation.md` — add REM-32 ✅ entry, update Sprint 5/6 lines
- `docs/storage.md` — split the SongDb section: Room-backed now, legacy as rollback shadow
- `docs/modules/songs.md` — note Room backing in the Storage paragraph
- `CLAUDE.md` — extend the SQLite-databases section to mention `AlkitabSongRoomDb` and demote `SongDbHelper` to rollback-shadow

**Untouched:**
- `Alkitab/src/main/java/yuku/alkitab/base/storage/SongDbHelper.java` — preserved as the rollback safety net for the migrated tables.
- `Alkitab/src/main/java/yuku/alkitab/base/storage/Table.java` — `SongInfo` and `SongBookInfo` enums are still referenced by the data-migration code reading the legacy DB; leave them alone.
- Every Songs-module activity / utility — all call sites are facade-only.

## Verification

`./gradlew :Alkitab:assemblePlainDebug :Alkitab:testPlainDebugUnitTest :Alkitab:testPlainReleaseUnitTest` — all green before commit / PR push.

## Relation to REM-21

REM-21 ("Migrate Song Storage from Parcelable to JSON", Phase 4, BRICE 2.8) is a payload-format change to the `data` column. REM-32 is a strict storage-engine change that round-trips the existing payload byte-for-byte. They compose:

- REM-32 ships with `data: ByteArray?` storing the existing Parcelable serialization.
- REM-21 later changes `dataFormatVersion` to a new value, writes a JSON-bytes payload to the same column, and teaches `marshallSong` / `unmarshallSong` to branch on `dataFormatVersion`. The Room storage layer doesn't notice.

REM-32 is a prerequisite for REM-21 only in the sense that REM-21 is easier to reason about against a Room-backed store with a typed entity and a tested data-migration scaffold. If REM-21 ever runs, it adds a `MIGRATION_1_2` to `SongRoomDatabase` and a row-by-row reformat — not in scope here.
