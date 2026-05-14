# ~~REM-11: Migrate InternalDb to Room (Version Table)~~ ✅ COMPLETED (2026-05-13)

**Addresses:** TD-02
**Module:** Storage — Versions subsystem
**BRICE:** B=3 R=2 I=3 C=3 E=5 → **3.2**
**Phase:** 2 — Architecture Improvements

**Outcome.** The legacy hand-rolled `Version` table is now backed by Room in a new, separate-file database (`AlkitabRoomDb`) at `yuku.alkitab.base.storage.room.AppDatabase` (`@Database(version = 1)` at landing; later bumped to v2 by [REM-10](REM-10-room-markers.md)). A separate file avoids the `user_version` collision with `InternalDbHelper`'s versionCode-driven schema — see `../superpowers/specs/2026-05-13-rem-11-room-version-table-design.md`.

**Shipped:**
- **Entities and DAO:** `VersionEntity.kt` mirrors the legacy columns; `VersionRoomDao.kt` exposes `listAllOrderedByOrdering()`, `getById()`, `insertOrUpdate()`, `deleteByPresetName()`, and the active-flag / ordering helpers.
- **Data migration:** `VersionDataMigration.kt` — idempotent one-time copy from the legacy `Version` table into Room, wired into `S.db`'s lazy initializer.
- **Facade preservation:** `VersionDao.kt` (under `storage/`) now delegates to Room, mapping `VersionEntity` ↔ `MVersionDb`. `InternalDb.listAllVersions()` and related methods continue to work unchanged.
- **Legacy table left in place** as a rollback safety net; a future release can drop it once the Room path has baked.

**Verification.** Migration test scaffolding via `MigrationTestHelper` (added separately) plus `VersionRoomDaoTest.kt` and `VersionDataMigrationTest.kt`. Schema JSON checked in at `Alkitab/schemas/yuku.alkitab.base.storage.room.AppDatabase/1.json`.

**Difficulty:** Medium (1-2 days). Lower risk than Markers since the Version table is simpler.

---

[← Back to remediation index](../tech-debt-remediation.md)
