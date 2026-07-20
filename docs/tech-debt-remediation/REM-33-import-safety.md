# REM-33: Make Data-Transfer Import Safe (Transactions, History, Validation)

**Source:** 2026-07 code audit
**Module:** Data transfer (`datatransfer/`) + `InternalDbTxWrapper`
**BRICE:** B=4 R=4 I=4 C=4 E=3 → **3.8**
**Phase:** 1 — Quick Wins & Safety Fixes (2026-07 audit)

**Status:** Not started.

**Steps:**
1. Wrap `InternalDbTxWrapper.transact`'s action in `try/finally { db.endTransaction() }` (mirror `SyncApplier`). Add a regression test that throws mid-import and asserts the DB is writable afterwards and no partial rows were committed.
2. Make `ImportProcess.history()` transactional-in-effect:
   - During a simulation run (`actualRun=false`), do not touch the live `History` singleton at all (dry-run against a copy, or defer `replaceAllEntries` until the commit callback).
   - On an actual run, call `History.save()` inside the commit path so a process kill can't drop the imported history, and so the sync notification fires deterministically.
3. Implement the missing gid validation for `Marker_Label` rows (skip or fail rows whose marker/label gid isn't present in the file or the DB) — the TODO comment already marks the spot.
4. Decide whether export should include progress for deleted reading plans (sync preserves it; export currently drops it). Aligning export with sync semantics is a one-line change in `ReadonlyStorageImpl.rpps()`.
5. Extend `ImportProcess` tests with: invalid `kind` enum row, dangling `Marker_Label` gid, simulation-then-cancel leaves history untouched.

**Failure modes today:** a *canceled* import simulation permanently replaces reading history on all synced devices; a *failed* import leaves an open write transaction (app-wide DB writes block, ANR) while telling the user everything was rolled back.

**Difficulty:** Easy-Medium (~1 day including tests).

---

[← Back to remediation index](../tech-debt-remediation.md)
