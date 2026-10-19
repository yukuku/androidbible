# REM-34: Fix Sync Request Delivery and Shared-State Races

**Source:** 2026-07 code audit
**Module:** Sync (`Sync.java`, `SyncKotlin.kt`, `SyncApplier.kt`, `History.kt`) + `yuku.afw.storage.Preferences` in `Alkitab`
**BRICE:** B=4 R=4 I=3 C=3 E=3 → **3.4**
**Phase:** 1: Quick Wins & Safety Fixes (2026-07 audit)

**Status:** Implemented 2026-10-15.

## Findings and resolution

- `Sync.notifySyncNeeded` returned from the entire call when one set was being updated by sync. It skips that set and retains the other requested sets, with duplicates removed within the request.
- `SyncKotlin.syncNow` discarded requests while a worker was running. Its separate state check and `REPLACE` enqueue could also cancel a worker that started between those operations. Each nonempty request is persisted as WorkManager input and appended atomically to the unique `sync` chain using `APPEND_OR_REPLACE`. Requests execute serially, including follow-up requests for the same set and partial-sync continuations.
- A WorkManager failure cancels dependent work. `SyncAdapter` records attempt errors in the sync log and the `isSyncSuccessful` output flag, then completes the delivery with `Result.success` so queued requests can proceed. Unexpected runtime exceptions are recorded per set, and the remaining sets are still attempted. New requests can also start a fresh chain after cancellation or a terminal WorkManager failure.
- `History.listAllEntries` takes the same monitor as history mutators, so its copied snapshot cannot observe an intermediate replacement.
- Mabel additions and modifications reject missing content and missing or unknown marker subtypes with `unknown_kind`. The database transaction rolls back preceding operations and leaves the sync shadow unchanged. `SyncAdapter` counts this result as a parse error. Deletions can still omit content.
- Preference editor creation, each complete write or removal, batching release, and cache invalidation share the `Preferences` class monitor. Another writer cannot apply and detach an editor while a value is still being added to it. Nested `withTransaction` scopes retain their exception-safe batching behavior.

## Verification

On 2026-10-15, `./gradlew testPlainDebugUnitTest testPlainReleaseUnitTest assemblePlainDebug` passed: 5,333 tests in each variant, including 14 new regression tests, and a debug APK build. Controlled runs against the baseline reproduced dropped requests, a lost preference write, an intermediate empty history snapshot, and malformed-payload crashes. The tests use controlled workers and local SQLite; live account and multi-device end-to-end sync are outside this verification.

- `SyncDeliveryTest` uses the real WorkManager test runtime and a controlled adapter to exercise running and queued delivery, same-set reruns, mixed suppressed sets, handled and unexpected attempt failures, and empty requests.
- `PreferencesConcurrencyTest` pauses a preference write before adding its value, allowing a competing write to expose premature editor application. It also checks nested batching after an exception.
- `HistoryConcurrencyTest` pauses a replacement after clearing the history and verifies a reader receives the completed snapshot.
- `SyncApplierTest` exercises malformed payload rollback across all Mabel entity kinds, an unknown marker subtype, valid marker insertion, and contentless deletion against SQLite.

## Scope and tradeoffs

Each call persists a separate delivery request; bursts can cause redundant sync attempts. Normal requests require a connected network, and a forced request remains ordered behind earlier requests in the chain. Queue persistence starts when WorkManager commits its enqueue operation. Process interruption before that commit is outside this guarantee.

This task preserves error reporting and queued delivery. Automatic retries of transient API failures belong to [REM-50](REM-50-sync-api-boundary.md), and local-edit conflict handling belongs to [REM-43](REM-43-sync-conflict-handling.md).

---

[← Back to remediation index](../tech-debt-remediation.md)
