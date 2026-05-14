# REM-27: Migrate InternalDb to Room (Devotion Table) ✅ COMPLETED (2026-05-14)

**Addresses:** TD-02 (continuation of [REM-10](REM-10-room-markers.md) / [REM-11](REM-11-room-version.md))
**Module:** Storage — Devotion cache
**BRICE:** B=3 R=3 I=2 C=3 E=5 → **3.2**
**Phase:** 2 — Architecture Improvements

**Outcome.** The legacy hand-rolled `Devotion` SQLite table is now backed by Room in `AlkitabRoomDb` (the same separate-file Room database introduced by REM-11 and extended by REM-10). `AppDatabase` bumped `version = 2 → 3`; the SQL inside `MIGRATION_2_3` mirrors what Room's entity definition emits (verified by `MigrationTestHelper.runMigrationsAndValidate` with `validateDroppedTables = true`).

**Shipped:**
- **Entity:** `DevotionEntity.kt` mirrors the legacy columns (`name`, `date`, `body`, `readyToUse`, `touchTime`, `dataFormatVersion`). Indexes match the legacy ones (`(name, date, dataFormatVersion)` and `(touchTime)`). Numeric columns are non-nullable in Room even though the legacy schema allowed NULLs; `DevotionDataMigration` coalesces legacy NULLs to 0 — matches the REM-11 nullability handling.
- **Room DAO:** `DevotionRoomDao.kt` exposes `findByNameDateAndDataFormatVersion`, `deleteByNameAndDate`, `deleteWithTouchTimeBefore`, and an `@Transaction upsertByNameAndDate` that mirrors the legacy delete-then-insert semantics for the (name, date) identity key.
- **Schema bump:** `AppDatabase.kt` now `@Database(version = 3)` with `@JvmField val MIGRATION_2_3`. `Alkitab/schemas/yuku.alkitab.base.storage.room.AppDatabase/3.json` is checked in alongside `1.json` and `2.json`.
- **Data migration:** `DevotionDataMigration.kt` — idempotent one-time copy from the legacy `Devotion` table into Room. Idempotency uses the persistent `Prefkey.devotion_data_migration_v1_done` flag (same approach as the post-[#198](https://github.com/yukuku/androidbible/pull/198) fix for marker/version), so the user-driven cache eviction in `deleteDevotionsWithTouchTimeBefore` cannot resurrect deleted rows on the next launch. Wired into `S.kt`'s `db` lazy initializer next to `MarkerDataMigration` and `VersionDataMigration`.
- **Facade preservation:** `DevotionDao.kt` rewritten to delegate to the Room DAO, mapping between `DevotionEntity` and the `DevotionArticle` subclass hierarchy. Public method signatures unchanged — call sites in `InternalDb` (`storeArticleToDevotions`, `deleteDevotionsWithTouchTimeBefore`, `tryGetDevotion`) did not need to be touched.
- **Legacy table left in place.** `Devotion` is still created by `InternalDbHelper.onCreate` so the rollback safety net is preserved; a future release can drop it once the Room path has baked.

**Tests (2 new files, 1 existing extended, 1 existing updated):**
- `DevotionRoomDaoTest.kt` (10 tests) — Room-level primitives: `(name, date, dataFormatVersion)` lookup, `upsertByNameAndDate` replace/insert/isolation, `deleteWithTouchTimeBefore` cutoff semantics.
- `DevotionDataMigrationTest.kt` (8 tests) — empty/full copies, null-body preservation, idempotency, no-stomp-on-existing-Room-rows, fresh `_id` assignment, and the issue #195 regression guard against resurrecting cache-evicted rows.
- `AppDatabaseMigrationTest.kt` — added `migrates2To3` test plus an end-to-end `v1 → v3` open-via-Room round-trip; updated the two existing tests that open the DB via `Room.databaseBuilder` to register both `MIGRATION_1_2` and `MIGRATION_2_3` now that the entity-driven schema is at v3.
- `DevotionDaoTest.kt` — `@Before` now installs an in-memory `AppDatabase` via `AppDatabase.setForTesting` (necessary harness change because the facade DAO routes through Room), same as `VersionDaoTest`.

**Verification.** `./gradlew :Alkitab:testPlainDebugUnitTest :Alkitab:testPlainReleaseUnitTest` — all green. `./gradlew :Alkitab:assemblePlainDebug` produces a working APK.

**Difficulty:** Easy. Took ~half a day with the REM-10 / REM-11 pattern already established. `PerVersion` followed as [REM-28](REM-28-room-per-version.md); remaining tables (`ReadingPlan`, `SyncShadow`, `SyncLog`, `ProgressMark`) can follow the same pattern.

---

[← Back to remediation index](../tech-debt-remediation.md)
