# Sync Module

## Overview

Cloud sync enables sharing bookmarks, notes, highlights, reading progress, and history across devices. Uses a delta-based protocol over REST with Firebase Cloud Messaging (FCM) for push notifications.

## Key Files

- `Alkitab/src/main/java/yuku/alkitab/base/sync/Sync.java` — Core sync engine with generic delta framework
- `Alkitab/src/main/java/yuku/alkitab/base/sync/SyncAdapter.java` — WorkManager-based worker executing sync
- `Alkitab/src/main/java/yuku/alkitab/base/sync/Sync_Mabel.java` — Markers, labels, marker-label sync
- `Alkitab/src/main/java/yuku/alkitab/base/sync/Sync_Pins.java` — Progress marks sync
- `Alkitab/src/main/java/yuku/alkitab/base/sync/Sync_Rp.java` — Reading plan progress sync
- `Alkitab/src/main/java/yuku/alkitab/base/sync/Sync_History.java` — Reading history sync
- `Alkitab/src/main/java/yuku/alkitab/base/sync/Fcm.java` — FCM token management
- `Alkitab/src/main/java/yuku/alkitab/base/sv/FcmMessagingService.kt` — FCM message handler
- `Alkitab/src/main/java/yuku/alkitab/base/sync/SyncRecorder.java` — Sync event logging
- `Alkitab/src/main/java/yuku/alkitab/base/sync/SyncKotlin.kt` — Kotlin wrapper for scheduling

## Request delivery

`Sync.notifySyncNeeded` filters disabled sets and sets whose local updates are caused by sync. It retains the remaining names in a mixed request. `SyncKotlin.syncNow` persists each nonempty request in the unique WorkManager `sync` chain with `APPEND_OR_REPLACE`. Work executes serially; a request received during an active attempt runs after it, including requests for the same set.

Normal requests require network connectivity. Forced requests omit that constraint but remain ordered behind earlier work. Sync errors are recorded in the sync log and in the worker's `isSyncSuccessful` output. The delivery worker returns success after a handled attempt failure so WorkManager does not cancel queued dependents. Unexpected runtime exceptions are isolated per set. This delivery policy does not add automatic network retries.

## Protocol

### Endpoint
`POST /sync/api/sync`

### Request
- `simpleToken` — authentication token
- `installation_id` — device identifier
- `fcm_token` — Firebase token for push
- Per entity type: `base_revno` (last known revision) + `delta` (local changes)

### Response
- Per entity type: `append_delta` (server changes since base_revno)
- New `revno` to use as next base

### Delta Format
Each delta entry is an operation tuple: `(kind, gid, content)` where:
- `kind` = entity type identifier
- `gid` = globally unique ID
- `content` = entity data (null for deletions)

Operations: `add`, `mod`, `del`

## Conflict Resolution

The sync uses a shadow table (`SyncShadow`) to track the last-synced state. When applying server deltas:
1. If the entity hasn't changed locally since last sync → apply server version
2. If the entity changed locally → server wins (last-write-wins for most fields)
3. Partial sync threshold: 100 operations per batch

## FCM Integration

When a sync completes on one device, the server sends an FCM message to other registered devices. `FcmMessagingService` receives the push and triggers a sync via `SyncAdapter`.

FCM configuration differs between debug (uses `RIBKA_FUNCTIONS_HOST_DEBUG` at `10.0.3.2:5001`) and release builds.

## Authentication

Simple token-based auth stored in `Prefkey.sync_simpleToken`. Login flow is handled by `SyncLoginActivity`.

## Database Tables

- **SyncShadow** — stores the last-synced state of each entity for conflict detection
- **SyncLog** — audit log of sync operations for debugging (viewable in `SyncLogActivity`)

## Support diagnostics

In Sync settings, press **Sync Now**, then **View log**. The log refreshes while
open. Use its **Copy** menu item to paste the log into a support message, or take
a screenshot. Copy uses the normal app clipboard utility; nothing is uploaded
automatically. Menu labels follow the in-app language; technical details are English.

The log includes timestamps with timezone offsets, event codes, app version,
Android release/API level, worker IDs, attempts, scheduling state, per-set stages,
HTTP status, exception categories and bounded cause class chains. No-work events
identify missing login, disabled sets, ongoing updates, unknown sets,
empty scheduling requests, or empty worker input.

Useful interpretations:

- `schedule_requested` without `schedule_accepted`: inspect a `schedule` error.
- `schedule_accepted` means WorkManager accepted the request, not that sync ran.
  `ENQUEUED` or `BLOCKED` can mean it is waiting on earlier work in the serial
  chain, Android scheduling, or network constraints. Automatic requests require
  a connected network; forced requests retain their unconstrained network policy.
- WorkManager `SUCCEEDED` means delivery completed. Check `is_sync_successful`
  and `worker_result_failed` to determine whether the sync attempt succeeded.
  Handled failures keep the delivery chain moving to subsequent requests.
- `worker_stopped` or `CANCELLED`: Android/WorkManager stopped the work. This is
  observational; diagnostics do not change cancellation or retry behavior.
- `DNS`, `TIMEOUT`, `TLS`, `CONNECTION`: check connectivity and try another network.
- HTTP 401/403 (`AUTH`): authentication was rejected. Check the signed-in account.
  HTTP 429 or 5xx: retain the status and timestamp for support.
- `JSON` at `parse`: the response could not be decoded. `DATABASE` at `gather` or
  `apply`: local storage failed. Send the event and cause classes to support;
  do not clear application data as a diagnostic step.
- `apply_result` values such as `dirty_entities` or `dirty_sync_account` identify
  existing conflict/account-change handling; they do not mean completion.

Only allowlisted metadata is recorded. Account email is retained in authentication
and account events and is included when the log is copied. `serverPrefix` retains
the server origin (scheme, host and port), with URL credentials, paths, queries
and fragments omitted. Passwords, tokens, headers, request/response bodies, notes
and other user content are omitted.
Exception messages are omitted entirely because even ordinary I/O and JSON
messages can contain credentials or user content. Legacy log parameters are also
filtered when displayed or copied. Raw FCM tokens and sync responses are not
sent to logcat by these sync paths.

Storage retains at most 500 diagnostic rows after each insert, with bounded
metadata fields. Incoming set names and parameter size are checked before insertion.
A primary-key range deletion enforces retention in the same transaction as insertion.
Legacy rows are filtered on reading and age out through retention; individual log
writes do not rewrite them. Only diagnostic log rows are pruned; sync shadows and user
data are untouched. If diagnostic storage fails, up to 50 events remain in
memory and can be copied during that process lifetime.

WorkManager state snapshots are captured while Sync settings or View log is open.
A process kill or failure before worker construction may leave only scheduling
state, without a cause. The serial delivery chain, expedited-work configuration,
retry policy and data conflict semantics are unchanged.

The reports involving Alkitab 4.11.x on Android 10/11, including the Sync Now
no-reaction report on Android 10 / One UI 2.1, are reproduction leads. Diagnostics
on the current develop architecture do not establish or fix that historical
root cause. Device checks should include offline Sync Now, queued sync,
all sets disabled, background restrictions, an authentication rejection,
and copy/screenshot review on Android 10/11 and a newer Android device.
