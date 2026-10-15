# REM-33: Make Data-Transfer Import Safe (Transactions, History, Validation)

**Source:** 2026-07 code audit
**Module:** Data transfer (`datatransfer/`) + `InternalDbTxWrapper`
**BRICE:** B=4 R=4 I=4 C=4 E=3 → **3.8**
**Phase:** 1 — Quick Wins & Safety Fixes (2026-07 audit)

**Status:** Done (2026-10-15).

**Implemented:**
1. `InternalDbTxWrapper.transact` always ends its transaction in `finally`. Exceptions roll back writes, including exceptions after the action requests a commit.
2. `ImportProcess` stages history entries and replaces them only after a successful actual database import. Simulation and database failures leave live and saved history untouched. `ReadWriteStorageImpl.replaceHistory` replaces and saves history under the same lock, using `History.save` to persist and notify sync immediately.
3. Invalid marker kinds and dangling `Marker_Label` gids reject the whole import. References may resolve against the local database or entities anywhere in the incoming file.
4. `ReadonlyStorageImpl.rpps` includes progress for deleted plans. Their unavailable start time is exported as `0`, preserving the numeric JSON format. Plans with no completed readings are also retained.
5. `ImportProcessTest` exercises real SQLite rollback and subsequent writes, history preview/cancel, failed and successful imports, empty and deselected history, marker-label validation with forward and database references, and deleted-plan progress export/import.

**Reproduction:** Preview a backup containing different history, then cancel and save history: the preview must not replace local entries. Import a valid marker followed by an invalid marker kind: no imported rows may remain and subsequent database writes must succeed. Import an assignment with a missing marker or label: reject it without committing other rows.

---

[← Back to remediation index](../tech-debt-remediation.md)
