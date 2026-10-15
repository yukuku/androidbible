# REM-40: Version Download Robustness (Stream Leaks, Orphaned Work)

**Source:** 2026-07 code audit
**Module:** Versions (`VersionDownloadCompleteReceiver`, `DownloadMapper`, `VersionDownloadWorker`)
**BRICE:** B=3 R=2 I=4 C=4 E=4 → **3.4**
**Phase:** 2 — Architecture Improvements (2026-07 audit)

**Status:** Complete (2026-10-15).

**Implementation:**
- `VersionDownloadCompleteReceiver.finalizeDownload` scopes both input streams and pairs every started atomic write with `finishWrite` or `failWrite`. Decompression failures preserve the existing destination.
- Application startup reconciles persisted tagged work with the in-memory mapper, cancels orphaned unfinished work, and removes untracked temporary files. Interrupted downloads can be requested again by the user.
- Each attempt has a unique temporary path. Mapper failure/cancellation and worker failure/cancellation clean up temporary files without deleting a newer attempt's output.
- Regression coverage verifies orphan cancellation, preservation of tracked files, removal cleanup, corrupt-gzip rollback, successful plain/gzip finalization, and partial HTTP transfer cleanup.

---

[← Back to remediation index](../tech-debt-remediation.md)
