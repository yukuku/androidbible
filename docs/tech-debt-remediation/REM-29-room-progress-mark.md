# REM-29: Migrate InternalDb to Room (ProgressMark Tables) ✅ COMPLETED (2026-05-14)

**Addresses:** TD-02 (continuation of [REM-10](REM-10-room-markers.md) / [REM-11](REM-11-room-version.md) / [REM-27](REM-27-room-devotion.md) / [REM-28](REM-28-room-per-version.md))
**Module:** Storage — Reading-progress pins
**BRICE:** B=3 R=3 I=2 C=3 E=5 → **3.2**
**Phase:** 2 — Architecture Improvements

**Outcome.** The legacy hand-rolled `ProgressMark` and `ProgressMarkHistory` SQLite tables are now backed by Room in `AlkitabRoomDb` (the same separate-file Room database introduced by REM-11 and extended by REM-10/REM-27/REM-28). `AppDatabase` bumped `version = 4 → 5`; the SQL inside `MIGRATION_4_5` mirrors what Room's entity definitions emit (verified by `MigrationTestHelper.runMigrationsAndValidate` with `validateDroppedTables = true`).

Two tables, one DAO: the legacy facade's `insertOrUpdate` writes a history row then upserts the matching mark inside a single transaction. The Room DAO preserves that atomicity with a `@Transaction`-annotated `insertHistoryAndUpsertMark` that runs the existence check + insert/update inside Room's transaction primitive, matching the legacy `SQLiteDatabase.beginTransactionNonExclusive` semantics exactly.

**Shipped:**
- **Entities:** `ProgressMarkEntity.kt` mirrors the legacy mark columns (`preset_id`, `caption`, `ari`, `modifyTime`); `ProgressMarkHistoryEntity.kt` mirrors the history columns (`progress_mark_preset_id`, `progress_mark_caption`, `ari`, `createTime`). `preset_id` / `progress_mark_preset_id` / `ari` are non-nullable in Room even though the legacy schema permitted NULL; `ProgressMarkDataMigration` drops any legacy rows where those columns are NULL (they are unreachable by the facade's keyed lookups, and coalescing a NULL `preset_id` to 0 would collide with the legitimate `preset_id = 0` placeholder row that the legacy schema seeds). `modifyTime` / `createTime` stay nullable to round-trip the placeholder rows that `InternalDbHelper.insertDefaultProgressMarks` inserts without those values. The `progress_mark_caption` column was declared `INTEGER` in legacy SQL but written as `String` by the DAO (SQLite type affinity); Room normalises this to `TEXT`. Non-unique indexes `index_progress_mark_preset_id` and `index_progress_mark_history_preset_id_createTime` mirror the legacy `index_601` / `index_701`.
- **Room DAO:** `ProgressMarkRoomDao.kt` exposes `findByPresetId`, `listAllWithNonZeroAri`, `countAllWithNonZeroAri`, `listHistoryByPresetId`, plain `insert` / `insertHistory` / `update` used by the data migration, and the transactional `insertHistoryAndUpsertMark` that mirrors the legacy facade's write path.
- **Schema bump:** `AppDatabase.kt` now `@Database(version = 5)` with `@JvmField val MIGRATION_4_5`. `Alkitab/schemas/yuku.alkitab.base.storage.room.AppDatabase/5.json` is checked in alongside `1.json`, `2.json`, `3.json`, and `4.json`.
- **Data migration:** `ProgressMarkDataMigration.kt` — idempotent one-time copy from the legacy `ProgressMark` and `ProgressMarkHistory` tables into Room. Idempotency uses the persistent `Prefkey.progress_mark_data_migration_v1_done` flag (same approach as REM-10/REM-11/REM-27/REM-28 post-#198), so a user who overwrites their pins cannot resurrect the deleted ari on the next launch. Streams rows from each legacy cursor into Room inside a single `runInTransaction` to bound memory — the history table grows monotonically with every pin update. Wired into `S.kt`'s `db` lazy initializer next to `MarkerDataMigration`, `VersionDataMigration`, `DevotionDataMigration`, and `PerVersionDataMigration`.
- **Facade preservation:** `ProgressMarkDao.kt` rewritten to delegate to the Room DAO, mapping between the new entities and the public `ProgressMark` / `ProgressMarkHistory` models. Public method signatures unchanged — call sites in `InternalDb` (`listAllProgressMarks`, `countAllProgressMarks`, `getProgressMarkByPresetId`, `insertOrUpdateProgressMark`, `listProgressMarkHistoryByPresetId`) did not need to be touched.
- **Legacy tables left in place.** `ProgressMark` and `ProgressMarkHistory` are still created by `InternalDbHelper.onCreate` (and still seeded with the five placeholder rows via `insertDefaultProgressMarks`) so the rollback safety net is preserved; a future release can drop them once the Room path has baked. The legacy seed doubles as the fresh-install seed source for Room — `ProgressMarkDataMigration` copies the five placeholder rows out of the legacy table on first launch, so callers like `IsiActivity.onPinDropped` that rely on a row already existing for each preset slot continue to work unchanged.

**Tests (2 new files, 1 existing extended, 1 existing rewritten):**
- `ProgressMarkRoomDaoTest.kt` (11 tests) — Room-level primitives: `preset_id` lookup, `ari != 0` filter, history ordering, transactional history-plus-mark write, null-column round-trip.
- `ProgressMarkDataMigrationTest.kt` (13 tests) — empty/full copies, history copy, placeholder-seed round-trip with null `modifyTime`/`caption`, drop-on-NULL-preset_id, drop-on-NULL-ari, multi-NULL-preset_id no-crash regression guard, idempotency, no-stomp-on-existing-Room-rows, fresh `_id` assignment, and the issue #195 regression guard against resurrecting cleared rows.
- `AppDatabaseMigrationTest.kt` — added `migrates4To5` test plus an end-to-end `v1 → v5` open-via-Room round-trip; updated the existing `Room.databaseBuilder` calls to register `MIGRATION_4_5` now that the entity-driven schema is at v5.
- `ProgressMarkDaoTest.kt` — `@Before` now installs an in-memory `AppDatabase` via `AppDatabase.setForTesting` and pre-seeds the five placeholder rows the way `InternalDbHelper.insertDefaultProgressMarks` used to, same harness change as `DevotionDaoTest` / `VersionDaoTest` / `PerVersionDaoTest`.

**Verification.** `./gradlew :Alkitab:assemblePlainDebug :Alkitab:testPlainDebugUnitTest :Alkitab:testPlainReleaseUnitTest` — all green.

**Difficulty:** Easy. Took ~half a day with the REM-10 / REM-11 / REM-27 / REM-28 pattern already established. The new wrinkle vs. REM-28 was handling two coupled tables that the legacy facade writes atomically — `@Transaction` covers that cleanly. Remaining tables (`ReadingPlan`, `SyncShadow`, `SyncLog`) can follow the same pattern.

---

[← Back to remediation index](../tech-debt-remediation.md)
