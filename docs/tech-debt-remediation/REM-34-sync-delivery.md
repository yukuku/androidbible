# REM-34: Fix Sync Request Delivery and Shared-State Races

**Addresses:** PB-16, PB-17, PB-18, PB-19, PB-20
**Module:** Sync (`Sync.java`, `SyncKotlin.kt`, `SyncApplier.kt`, `History.kt`) + Afw `Preferences`
**BRICE:** B=4 R=4 I=3 C=3 E=3 → **3.4**
**Phase:** 1 — Quick Wins & Safety Fixes (2026-07 audit)

**Status:** Not started.

**Steps:**
1. **PB-16:** In `Sync.notifySyncNeeded`, change the ongoing-counter `return` to `continue` (the log line already says "ignored"). One word; add a unit test with a mixed set-name array.
2. **PB-17:** Stop dropping requests when the sync worker is RUNNING. Options: (a) enqueue with `ExistingWorkPolicy.APPEND_OR_REPLACE` semantics via a persisted pending-sets record; (b) keep the requested names in `syncSetNameQueue` (don't drain before checking RUNNING) and re-trigger when the worker finishes. Also remove the RUNNING-check/REPLACE TOCTOU (a request enqueued as a worker starts should not cancel it mid-apply — `ExistingWorkPolicy.KEEP` + a rerun flag is one option).
3. **PB-19:** Add `@Synchronized` to `History.listAllEntries()` (every mutator already has it).
4. **PB-20:** Add the `o.content ?: return ...unknown_kind` guard to `applyMabelAppendDelta` (mirror the pins/rp appliers), plus a defensive `Marker.Kind.fromCode` null check, so malformed server data records a parse failure instead of crashing the worker and wedging the sync set.
5. **PB-18:** Fix the `Preferences` editor race in Afw: make `getEditor()`/`put*` synchronize on the same monitor as `commitIfNotHeld()` (they're all cheap in-memory operations; a single lock is fine). Consider this a prerequisite for trusting `fcm_registration_pending`, `sync_last_infos`, and the history JSON.

**Difficulty:** Medium (~1-2 days). Steps 1, 3, 4 are trivial; step 2 needs care around WorkManager semantics; step 5 touches a widely used class (run the full unit suite).

---

[← Back to remediation index](../tech-debt-remediation.md)
