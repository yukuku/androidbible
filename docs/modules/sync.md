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

Known limitation tracked in `docs/tech-debt.md` (PB-03): the client-side patch is last-write-wins for every Mabel entity and for progress pins, so concurrent edits to the same highlight color or pin position on two devices silently discard one side. Notes and bookmark captions are merged server-side before deltas are emitted.

## FCM Integration

When a sync completes on one device, the server sends an FCM message to other registered devices. `FcmMessagingService` receives the push and triggers a sync via `SyncAdapter`.

FCM configuration differs between debug (uses `RIBKA_FUNCTIONS_HOST_DEBUG` at `10.0.3.2:5001`) and release builds.

Registration retry: a failed FCM token send sets `Prefkey.fcm_registration_pending = true`. In-process, `Sync.sendFcmRegistrationId` retries up to three times on a daemon `ScheduledExecutorService` (`fcmRetryExecutor`) at 1 min / 5 min / 30 min, clearing the flag on success. Cross-launch, `App.staticInit()` calls `Sync.retryPendingFcmRegistrationIfNeeded(registrationId)` to re-enter the send when the flag is still set after a process death.

## Authentication

Simple token-based auth stored in `Prefkey.sync_simpleToken`. Login flow is handled by `SyncLoginActivity`.

## Database Tables

- **SyncShadow** — stores the last-synced state of each entity for conflict detection
- **SyncLog** — audit log of sync operations for debugging (viewable in `SyncLogActivity`)
