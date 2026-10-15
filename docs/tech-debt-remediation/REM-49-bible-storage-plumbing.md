# REM-49: Simplify Bible Database SQL and Storage Plumbing

**Module:** Bible storage (`InternalDb`, per-table DAOs, `SyncShadowDao`)
**Status:** Open. Existing DAO extraction and statement-lifetime fixes are complete; the work below remains.
**BRICE:** Not scored; assess the storage changes before scheduling.

## Problem

`InternalDbHelper` remains the active SQLite source of truth for Bible-related tables. `InternalDb` delegates many operations to `MarkerDao`, `LabelDao`, `VersionDao`, `DevotionDao`, `ReadingPlanDao`, `ProgressMarkDao`, `PerVersionDao`, `Marker_LabelDao`, and `SyncShadowDao`; sync application lives in `SyncApplier`. Song storage has a separate completed Room migration in [REM-32](REM-32-room-song-db.md), which does not migrate Bible data.

Remaining storage plumbing includes:

- Handwritten query concatenation in `InternalDb.listMarkers` and reordering helpers, without compile-time SQL verification. `getLabelById` already delegates to `LabelDao.getById`.
- Near-identical ordering updates in `reorderLabels` and `reorderVersions`, including paired arithmetic updates that must preserve ordering and transaction behavior.
- `SyncShadowDao` reads large serialized snapshots using SQLite `substr` chunks of `1_000_000` to stay below CursorWindow limits. This workaround needs explicit boundary coverage and a documented size/encoding contract.

## Completed foundations

`MarkerDao.countForAriRange` and `LabelDao.getMaxOrdering` close compiled statements with `use`; shared compiled-statement caching is resolved. The inclusive verse-255 bounds fix and its regression tests are part of [REM-18](REM-18-test-coverage.md), completed on 2026-04-16.

## Remaining work

1. Keep SQL close to the per-table DAOs, bind data values, and review query/table dependencies explicitly. Assess a typed query approach only with a migration plan for existing user data.
2. Share ordering logic where doing so preserves table-specific constraints. Verify upward/downward moves, unchanged order, and rollback on failure.
3. Characterize large SyncShadow reads, including multibyte content and chunk boundaries, before changing the workaround or storage representation.
4. Preserve transaction boundaries, sync notifications, and compatibility with installed databases. Coordinate Kotlin conversion with [REM-16](REM-16-java-to-kotlin.md); changing the implementation language alone does not resolve this work.

Import transaction safety remains in [REM-33](REM-33-import-safety.md), and defensive database/verse guards remain in [REM-41](REM-41-defensive-guards.md).

---

[← Back to remediation index](../tech-debt-remediation.md)
