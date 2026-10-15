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
