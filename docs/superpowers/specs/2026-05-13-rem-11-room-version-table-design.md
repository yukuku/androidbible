# REM-11: Introduce Room and migrate the `Version` table

**Status:** Draft  
**Author:** Claude  
**Date:** 2026-05-13  
**Scope:** REM-11 only. REM-10 (Markers) is deferred to a follow-up PR after this lands and bakes.

## Goal

Introduce Room as a sanctioned ORM in the codebase, prove it can coexist with the
existing `SQLiteOpenHelper`-based `InternalDbHelper`, and migrate just the
`Version` table to it. Future tables can follow the same pattern.

This PR addresses **REM-11**. REM-10 (`Marker` table migration) is intentionally
out of scope — it has joins, FTS-adjacent index requirements, and ~10x the
surface area, so it merits its own PR and bake time.

## Constraints discovered during exploration

1. **The hand-rolled DAO layer already exists.** `MarkerDao`, `VersionDao`,
   `LabelDao`, `Marker_LabelDao`, etc. all live under
   `Alkitab/src/main/java/yuku/alkitab/base/storage/`. The "extract DAOs from
   `InternalDb`" work the doc envisioned for REM-10/11 is already done. What
   remains is the Room conversion.
2. **`InternalDbHelper` uses `App.getVersionCode()` as the SQLite `user_version`.**
   `versionCode` is computed at build time as
   `(2_000_000 + minutes-since-2026-01-01) * 10` — every build produces a
   different value. Room expects a static `@Database(version = N)` and explicit
   `Migration(from, to)` instances. **These two version schemes are fundamentally
   incompatible if Room tries to own the same DB file's `user_version`.**
3. **The `Version` table is well-isolated.** No joins from `Version` to other
   tables in the SQL code (only `reorderVersions` and the one-time
   `convertFromEdisiToVersion` migration touch it with raw SQL). This makes it
   safe to move to a separate file.
4. The user-critical data — markers, bookmarks, notes, highlights, sync state —
   lives in other tables. The `Version` table contains user preferences (which
   downloaded versions are active, their ordering) that can be reconstructed
   from filesystem state if lost.

## Approach: separate Room database file

Add Room as a separate database file (`AlkitabRoomDb`), independent from the
existing `AlkitabDb`. Migrate `Version` table data one-time on first launch, and
make Room the sole writer from then on.

```
Before:
  AlkitabDb (SQLiteOpenHelper, user_version = versionCode)
    ├── Marker, Label, Marker_Label
    ├── Version ◀── handwritten VersionDao reads/writes
    ├── ProgressMark, ReadingPlan, SyncShadow, PerVersion, Devotion, ...

After:
  AlkitabDb (SQLiteOpenHelper, user_version = versionCode) — UNCHANGED
    ├── Marker, Label, Marker_Label
    ├── Version (left in place as a rollback safety net, no longer written to)
    └── ...all other tables...

  AlkitabRoomDb (RoomDatabase, version = 1) — NEW
    └── VersionEntity ◀── Room-generated VersionDao reads/writes
```

### Why not share a single DB file?

I considered sharing `AlkitabDb` via `RoomDatabase.Builder.openHelperFactory()`,
but it collides on `user_version`:

- Room would see `user_version` = whatever `App.getVersionCode()` was when the
  user last upgraded — e.g. `14000600`. Room's `@Database` would need a literal
  `version = N` matching that, but `N` is dynamic per build.
- Hardcoding `@Database(version = 14000700)` (the current `versionCode`) means
  every device with an older `versionCode` triggers Room migration logic, which
  needs a `Migration(from, to)` for every (versionCode -> 14000700) pair —
  unbounded.
- Setting `fallbackToDestructiveMigration(true)` would wipe the existing
  Markers/Bookmarks tables on first run. **Not acceptable.**

Decoupling `InternalDbHelper` from `versionCode` (e.g., introducing an explicit
`DB_VERSION` constant) is a viable long-term direction but is itself a risky
refactor that affects every existing user's migration path. It's a separate
project from REM-11.

The separate-file approach sidesteps these issues entirely: Room owns its
file, `InternalDbHelper` owns its file, neither needs to know about the other.

### One-time data copy

On first launch with this code:

1. `AppDatabase` (Room) is opened — empty `VersionEntity` table.
2. `RoomDatabase.Callback.onCreate` triggers a migration helper that:
   - Opens `InternalDbHelper` (already initialized via `App.services.storage`)
   - Reads all rows from the old `Version` table via raw SQL on `InternalDbHelper`'s `SQLiteDatabase`
   - Inserts them into `VersionEntity` via Room
   - Logs success/failure to `AppLog`
3. If the copy fails partway, the user-visible effect is: empty version list →
   user re-downloads versions. No loss of bookmarks, notes, highlights, sync
   state. Re-running the migration on next launch is safe (idempotent: the
   migration only runs when `VersionEntity` is empty AND the old Version table
   has rows).

The old `Version` table in `AlkitabDb` is **not dropped** in this PR. It stays
as a safety net for at least one full release cycle. A follow-up PR will drop
it once Room-based version handling is proven stable.

## Components

### `gradle/libs.versions.toml`

Add Room and KSP entries (Room requires KSP for code generation):

```toml
[versions]
ksp = "2.2.0-2.0.2"   # must match the kotlin version
room = "2.7.0"

[libraries]
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

### `Alkitab/build.gradle.kts`

- Apply the KSP plugin.
- Add `androidx-room-runtime`, `androidx-room-ktx` to `implementation`.
- Add `androidx-room-compiler` to `ksp`.
- Add `androidx-room-testing` to `testImplementation` (used by `MigrationTestHelper`).
- Configure Room schema export to `$projectDir/schemas/` so we can diff schemas
  in code review.

### `Alkitab/src/main/java/yuku/alkitab/base/storage/room/`

New package. Contains:

- **`AppDatabase.kt`** — `@Database(entities = [VersionEntity::class], version = 1, exportSchema = true)`. Singleton via `Room.databaseBuilder(context, AppDatabase::class.java, "AlkitabRoomDb").build()`. Held by `Storage`/`App.services.storage`.
- **`VersionEntity.kt`** — `@Entity(tableName = "version", indices = [Index("ordering"), Index(value = ["active", "longName"]), Index("preset_name")])`. Columns mirror `MVersionDb` fields. Nullable types match the legacy schema (`locale: String?`, `shortName: String?`, etc.) because the legacy `createTableVersion` declares all columns without `NOT NULL`. Note: this entity uses a fresh table name (`version` lowercase) to clearly disambiguate from the legacy `Version` table — they live in different DB files, but a different name makes greps unambiguous.
- **`VersionRoomDao.kt`** — Room `@Dao` interface with: `listAll(): List<VersionEntity>`, `getMaxOrdering(): Int`, `getByFilename(filename: String): VersionEntity?`, `insert(entity: VersionEntity): Long`, `update(entity: VersionEntity)`, `deleteByPresetName(presetName: String): Int`, `deleteByFilename(filename: String): Int`, `setActiveByPresetName(presetName: String, active: Int)`, `setActiveByFilename(filename: String, active: Int)`, plus the two pair-wise ordering updates needed for `reorderVersions`.

### `Alkitab/src/main/java/yuku/alkitab/base/storage/VersionDao.kt`

Replace the hand-rolled SQLiteOpenHelper-based implementation with a wrapper
that delegates to `VersionRoomDao`. Public surface (method signatures) stays
identical so call sites in `InternalDb`, `S`, `IsiActivity`, etc. don't need to
change.

Internally, mapping between `MVersionDb` ⇄ `VersionEntity` happens here.

### `InternalDb.reorderVersions()`

Currently uses raw `db.execSQL` on `Db.TABLE_Version`. Rewrite to call the new
DAO's pair-wise update methods. The `Preferences.internal_version_ordering`
mutation stays in place — that's not a DB operation.

### Data-copy migration

`VersionDataMigration.kt` (new file in `storage/room/`):

```kotlin
fun copyFromLegacyDbIfNeeded(roomDb: AppDatabase, legacyHelper: InternalDbHelper) {
    val dao = roomDb.versionDao()
    if (dao.listAll().isNotEmpty()) return  // already migrated

    legacyHelper.readableDatabase.query(
        "Version", null, null, null, null, null, "ordering asc"
    ).use { c ->
        // for each row, build VersionEntity and dao.insert()
    }
}
```

Called from `Storage.kt` (or wherever `App.services.storage` is wired up) after
both helpers are constructed, before any consumer reads.

### Tests

1. **`VersionRoomDaoTest`** — new file. Uses
   `Room.inMemoryDatabaseBuilder(...).build()`. Covers the same scenarios as
   the existing `VersionDaoTest` (insert/upsert, list ordering, setActive
   variants, delete by preset_name / filename, getMaxOrdering, shared-preset_name
   edge case).
2. **`VersionDaoTest`** — keep it. The test exercises the public `VersionDao`
   interface, which still works after the swap (just backed by Room internally).
   This is our parity gate.
3. **`VersionDataMigrationTest`** — new file. Robolectric. Sets up
   `InternalDbHelper` with seeded rows in the legacy `Version` table, runs the
   migration, asserts Room DB now has the same rows. Also tests the idempotent
   case (no-op when Room DB already has rows).

## Out of scope

- REM-10 (Markers / Labels / Marker_Label tables). Markers are the
  user-critical data; rolling Room out there requires more conservative
  bake time.
- Decoupling `InternalDbHelper` from `App.getVersionCode()`. This would be
  required for a single-file Room approach but is its own significant refactor
  with its own migration risk.
- Dropping the old `Version` table from `AlkitabDb`. Deferred at least one
  release for rollback safety.
- Migrating any other tables to Room.
- Coroutine/Flow APIs on the Room DAO. The existing call sites are synchronous;
  no need to change calling conventions in this PR.

## Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Room schema validation fails on real device because legacy schema is subtly different from what Room expects | Separate file: Room creates the table from its own entity definition, so no validation against legacy schema |
| One-time copy migration fails for some users | Idempotent — runs again next launch. Worst case: user re-downloads versions, no loss of markers/bookmarks |
| Two DB files double the IO footprint | `Version` table is tiny (≤100 rows). Negligible. |
| KSP plugin slows the build | Acceptable — Room generation is fast; the plugin is industry-standard. |
| The `versionDao` field in `InternalDb` is `public final`, so call sites might bypass the facade | The wrapping keeps the same `VersionDao` class type, same public methods. Bypassers still work. |

## Open questions

None at this point; assumptions are stated above.
