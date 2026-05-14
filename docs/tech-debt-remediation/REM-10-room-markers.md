# REM-10: Migrate InternalDb to Room (Markers Table) ✅ COMPLETED (2026-05-14)

**Addresses:** TD-02
**Module:** Storage — Markers subsystem
**BRICE:** B=4 R=3 I=2 C=3 E=5 → **3.4**
**Phase:** 2 — Architecture Improvements

**Outcome.** All three legacy hand-rolled SQLite tables — `Marker`, `Label`, `Marker_Label` — are now backed by Room in `AlkitabRoomDb` (the same separate-file Room database [REM-11](REM-11-room-version.md) introduced). `AppDatabase` bumped `version = 1 → 2`; the SQL inside `MIGRATION_1_2` mirrors what Room's entity definitions emit (verified by `MigrationTestHelper.runMigrationsAndValidate` with `validateDroppedTables = true`).

**Shipped:**
- **Entities:** `MarkerEntity.kt` / `LabelEntity.kt` / `MarkerLabelEntity.kt` mirror the legacy columns. Indexes match the legacy ones with one exception — the legacy `index_Marker_05 (kind, caption COLLATE NOCASE)` is dropped because Room's `@Index` does not support per-column collation; the only caller (`InternalDb.listMarkers` caption sort) falls back to SQLite's in-memory sort, which is negligible at realistic marker volumes (hundreds, max).
- **Room DAOs:** `MarkerRoomDao.kt` / `LabelRoomDao.kt` / `MarkerLabelRoomDao.kt`. `LabelRoomDao` carries the pair-wise `reorderById` shift inside a `@Transaction`. `MarkerRoomDao` exposes a `@RawQuery` escape hatch (`listMarkersRaw`) for `InternalDb.listMarkers`'s dynamic `ORDER BY` + optional join; the facade whitelists the four valid sort columns (`createTime` / `modifyTime` / `ari` / `caption`) before composing the SQL.
- **Schema bump:** `AppDatabase.kt` now `@Database(version = 2)` with `@JvmField val MIGRATION_1_2`. `Alkitab/schemas/yuku.alkitab.base.storage.room.AppDatabase/2.json` is checked in alongside the existing `1.json`.
- **Data migration:** `MarkerDataMigration.kt` — idempotent one-time copy from the legacy three tables into Room, running inside a single `roomDb.runInTransaction { ... }` so the three tables move atomically (Gemini-correct on REM-11). Idempotency uses a combined `marker + label + marker_label` count check so that a legacy state with labels-but-no-markers (or vice versa) doesn't double-copy on retry. Translates the legacy Indonesian column names (`judul` / `urutan` / `warnaLatar`) to the Room schema's English equivalents (`title` / `ordering` / `backgroundColor`). Wired into `S.kt`'s `db` lazy initializer next to `VersionDataMigration`.
- **Facade preservation:** `MarkerDao.kt` / `LabelDao.kt` / `Marker_LabelDao.kt` rewritten to delegate to the Room DAOs, mapping between Room entities and the `Marker` / `Label` / `Marker_Label` model classes. Public method signatures unchanged — call sites in `IsiActivity`, `MarkerListActivity`, sync code, etc. did not need to be touched.
- **InternalDb rewrites:** Seven raw-SQL methods rewritten to use Room: `listMarkers`, `deleteMarkerById`, `putAttributes`, `updateOrInsertPartialHighlight`, `updateOrInsertHighlights`, `getHighlightColorRgb` (both overloads), `updateLabels`, `deleteLabelAndMarker_LabelsByLabelId`, `sortLabelsAlphabetically`, `reorderLabels`. Transactional flows that previously used `helper.getWritableDatabase().beginTransactionNonExclusive()` now use `AppDatabase.get(App.context).runInTransaction(...)`. Removed unused imports (`Cursor`, `SQLiteDatabase`, `DatabaseUtils`, `ContentValues`, `Sqlitil`, `Literals.Array`, `Literals.ToStringArray`).
- **Legacy tables left in place.** `Marker` / `Label` / `Marker_Label` are still created by `InternalDbHelper.onCreate` so the rollback safety net is preserved; a future release can drop them once the Room path has baked.

**Tests (4 new files, 1 updated, 1 InternalDbTest extended):**
- `MarkerRoomDaoTest.kt` (13 tests) — Room-level primitives: range queries inclusive/exclusive bounds, ordering, `@RawQuery` bridge with and without `marker_label` joins.
- `LabelRoomDaoTest.kt` (10 tests) — `reorderById` move-up / move-down / no-op, `listByMarkerGid` cross-join order.
- `MarkerLabelRoomDaoTest.kt` (10 tests) — cascade-helper deletes by marker_gid / label_gid, count-by-label, gid uniqueness.
- `MarkerDataMigrationTest.kt` (8 tests) — empty/full copies, idempotency, no-stomp-on-existing-Room-rows, fresh `_id` assignment, atomic copy of all three tables.
- `AppDatabaseMigrationTest.kt` — added the real `migrates1To2` test (replacing the scaffold) plus an end-to-end open-via-Room round-trip after migrating from v1; the existing v1-schema sanity test now passes `MIGRATION_1_2` to the builder so the v1 → current open succeeds.
- `InternalDbTest.kt` — `@Before` now installs an in-memory `AppDatabase` via `AppDatabase.setForTesting` (necessary harness change because the facade DAOs route through Room), and 4 new tests cover the `listMarkers` `@RawQuery` branches: `label_id == 0`, `label_id == LABELID_noLabel`, label-filtered, and the case-insensitive caption sort.

**Verification.** `./gradlew :Alkitab:testPlainDebugUnitTest :Alkitab:testPlainReleaseUnitTest` — 494 tests, all green. `./gradlew :Alkitab:assemblePlainDebug` produces a working APK. On-device smoke (Pixel emulator API 35, pre-existing dataset of 1456 markers / 18 labels / 152 marker_label associations from a v1 Room install): installing the new APK ran `MarkerDataMigration` once on first launch (logged `Copied 1456 marker(s), 18 label(s), 152 marker_label row(s) from legacy tables to Room`), counts match the legacy table exactly, second launch is idempotent (no row growth), spot-check confirms `gid` / `ari` / `caption` round-trip verbatim.

**Difficulty:** Hard. Took ~1 day with the REM-11 pattern already established. Subsequent tables can follow the same pattern — `Devotion` shipped as [REM-27](REM-27-room-devotion.md), `PerVersion` as [REM-28](REM-28-room-per-version.md), `ProgressMark` / `ProgressMarkHistory` as [REM-29](REM-29-room-progress-mark.md); `ReadingPlan`, `SyncShadow`, `SyncLog` remain.

---

[← Back to remediation index](../tech-debt-remediation.md)
