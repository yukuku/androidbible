# REM-40: Version Download Robustness (Stream Leaks, Orphaned Work)

**Addresses:** PB-25, PB-26
**Module:** Versions (`VersionDownloadCompleteReceiver`, `DownloadMapper`, `VersionDownloadWorker`)
**BRICE:** B=3 R=2 I=4 C=4 E=4 → **3.4**
**Phase:** 3 — Modernization (2026-07 audit)

**Status:** Not started.

**Steps:**
1. **PB-25:** Rewrite `VersionDownloadCompleteReceiver`'s finalization with try-with-resources for `fis`/`ogis`/the `AtomicFile` stream, calling `af.failWrite(fos)` on any failure so the `AtomicFile` contract is honored and no fds/temp files leak.
2. **PB-26:** Reconcile persisted WorkManager state on startup: either cancel all `WORK_TAG` work whose `DownloadMapper` row is missing (simplest — the user just re-taps download), or persist enough of the row (`attrs`) to let `VersionDownloadCompleteReceiver` finalize an orphaned SUCCEEDED download. Also delete the temp file when work lands in FAILED/CANCELLED, not only in `consumeAndRemove`.
3. Tests: extend `DownloadMapperTest` with an orphaned-work reconciliation case; a finalization test with a corrupt gzip fixture.

**Difficulty:** Medium (~1 day).

---

[← Back to remediation index](../tech-debt-remediation.md)
