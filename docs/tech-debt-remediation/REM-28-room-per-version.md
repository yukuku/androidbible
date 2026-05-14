# REM-28: Migrate InternalDb to Room (PerVersion Table) ✅ COMPLETED (2026-05-14)

**Addresses:** TD-02 (continuation of [REM-10](REM-10-room-markers.md) / [REM-11](REM-11-room-version.md) / [REM-27](REM-27-room-devotion.md))
**Module:** Storage — PerVersion settings
**BRICE:** B=3 R=3 I=2 C=3 E=5 → **3.2**
**Phase:** 2 — Architecture Improvements

**Outcome.** The legacy hand-rolled `PerVersion` SQLite table is now backed by Room in `AlkitabRoomDb` (the same separate-file Room database introduced by REM-11 and extended by REM-10/REM-27). `AppDatabase` bumped `version = 3 → 4`; the SQL inside `MIGRATION_3_4` mirrors what Room's entity definition emits (verified by `MigrationTestHelper.runMigrationsAndValidate` with `validateDroppedTables = true`).

**Shipped:**
- **Entity:** `PerVersionEntity.kt` mirrors the legacy two columns (`versionId`, `settings`). `versionId` is non-nullable in Room even though the legacy schema permitted NULL; `PerVersionDataMigration` coalesces legacy NULLs to the empty string. The unique index `index_per_version_versionId` mirrors the legacy `index_PerVersion_01`.
- **Room DAO:** `PerVersionRoomDao.kt` exposes `findByVersionId`, `count`, `listAll`, an `OnConflictStrategy.REPLACE` `upsert` (matches the legacy `SQLiteDatabase.replace` semantics on the unique index), and a plain `insert` used by the data migration.
- **Schema bump:** `AppDatabase.kt` now `@Database(version = 4)` with `@JvmField val MIGRATION_3_4`. `Alkitab/schemas/yuku.alkitab.base.storage.room.AppDatabase/4.json` is checked in alongside `1.json`, `2.json`, and `3.json`.
- **Data migration:** `PerVersionDataMigration.kt` — idempotent one-time copy from the legacy `PerVersion` table into Room. Idempotency uses the persistent `Prefkey.per_version_data_migration_v1_done` flag (same approach as REM-10/REM-11/REM-27 post-#198), so a user who clears their per-version settings cannot resurrect deleted rows on the next launch. Streams rows from the legacy cursor into Room inside a single `runInTransaction` to bound memory — `settings` is user-controlled JSON, free to grow as new fields are added. Wired into `S.kt`'s `db` lazy initializer next to `MarkerDataMigration`, `VersionDataMigration`, and `DevotionDataMigration`.
- **Facade preservation:** `PerVersionDao.kt` rewritten to delegate to the Room DAO, mapping between `PerVersionEntity` and the `PerVersionSettings` JSON-blob model. Public method signatures unchanged — call sites in `InternalDb` (`getPerVersionSettings`, `storePerVersionSettings`) did not need to be touched.
- **Legacy table left in place.** `PerVersion` is still created by `InternalDbHelper.onCreate` so the rollback safety net is preserved; a future release can drop it once the Room path has baked.

**Tests (2 new files, 1 existing extended, 1 existing rewritten):**
- `PerVersionRoomDaoTest.kt` (8 tests) — Room-level primitives: `versionId` lookup, `upsert` replace/insert/isolation, null-settings round-trip, count.
- `PerVersionDataMigrationTest.kt` (10 tests) — empty/full copies, null-settings preservation, NULL-versionId coalesce, idempotency, no-stomp-on-existing-Room-rows, fresh `_id` assignment, and the issue #195 regression guard against resurrecting cleared rows.
- `AppDatabaseMigrationTest.kt` — added `migrates3To4` test plus an end-to-end `v1 → v4` open-via-Room round-trip; updated the existing `Room.databaseBuilder` calls to register `MIGRATION_3_4` now that the entity-driven schema is at v4.
- `PerVersionDaoTest.kt` — `@Before` now installs an in-memory `AppDatabase` via `AppDatabase.setForTesting` (necessary harness change because the facade DAO routes through Room), same as `DevotionDaoTest` / `VersionDaoTest`.

**Verification.** `./gradlew :Alkitab:assemblePlainDebug :Alkitab:testPlainDebugUnitTest :Alkitab:testPlainReleaseUnitTest` — all green.

**Difficulty:** Easy. Took ~half a day with the REM-10 / REM-11 / REM-27 pattern already established. Remaining tables (`ReadingPlan`, `SyncShadow`, `SyncLog`, `ProgressMark`) can follow the same pattern.

---

[← Back to remediation index](../tech-debt-remediation.md)
