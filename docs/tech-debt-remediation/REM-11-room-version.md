# ~~REM-11: Migrate InternalDb to Room (Version Table)~~ ⏪ REVERTED (2026-05-18)

**Addresses:** TD-02
**Module:** Storage — Versions subsystem
**BRICE:** B=3 R=2 I=3 C=3 E=5 → **3.2**
**Phase:** 2 — Architecture Improvements

**Status: merged 2026-05-13, then reverted 2026-05-18** together with the rest of the InternalDb → Room sweep (REM-10 / REM-27 / REM-28 / REM-29 / REM-30 / REM-31). None of it is in the shipped app.

**What was reverted.** The change had moved the `Version` table into a separate-file Room database (`AlkitabRoomDb` / `AppDatabase`), with `VersionEntity` + `VersionRoomDao`, an idempotent `VersionDataMigration`, and the `VersionDao` facade delegating to Room. All of that code, the schema JSONs, and the migration tests were removed in the revert; `VersionDao` now issues raw SQL against the legacy `Version` table in `AlkitabDb` (`InternalDbHelper`), which remains the source of truth.

**Why it was reverted** (applies to the whole REM-10/11/27–31 sweep; see the revert PR for the full rationale):

- The app's published Bible data (markers, sync state, etc.) is irreplaceable user-synced state — the risk/value tradeoff of a storage-engine swap was wrong.
- `AlkitabDb`'s dynamic build-time `user_version` (set to `App.getVersionCode()`) is fundamentally incompatible with Room's static `@Database(version = N)` contract, forcing a separate-file architecture with per-table row copies.
- Room replicating each legacy `createTable*` quirk created a global schema-drift bug surface (e.g. a lost `COLLATE NOCASE` index in [#197](https://github.com/yukuku/androidbible/pull/197), idempotency resurrection in [#195](https://github.com/yukuku/androidbible/issues/195)).

**What survived:** [REM-32](REM-32-room-song-db.md) (Room for the Songs subsystem) was kept — Songs is module-isolated in its own SQLite file and song books are re-downloadable, so the migration risk there is bounded. The Room dependency stays in the build for REM-32.

---

[← Back to remediation index](../tech-debt-remediation.md)
