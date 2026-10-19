# Sync

Sync shares bookmarks, notes, highlights, reading progress, and history across
signed-in devices. Each device exchanges changes with the server over REST;
Firebase Cloud Messaging prompts other devices to sync when data changes.

## Architecture

The [sync package](../../Alkitab/src/main/java/yuku/alkitab/base/sync) contains
account management, scheduling, workers, and the per-data-type sync handlers.
WorkManager queues requests serially. Local changes are compared with a stored
shadow of the last synchronized state, and server changes are applied through
the storage layer. Conflict and concurrent-edit handling belongs to that code;
this document does not duplicate the protocol or merge rules.

Work delivery and sync success are separate: a worker can finish a handled,
unsuccessful attempt while allowing queued requests to continue. Use the sync
result in the log to determine whether data synchronized, rather than relying
only on WorkManager's completion state.

## Support logs

In **Sync settings**, press **Sync Now**, then **View log**. The log refreshes
while open. Use **Copy** to paste it into a support message, or take a screenshot.
UI labels follow the selected app language; technical diagnostics use English.
Logs are never uploaded automatically.

Events describe scheduling, prerequisites, progress, failures, and completion.
Timestamps, app/OS details, HTTP status, and exception categories and cause types
help locate where an attempt stopped. A queued request may still be waiting for
connectivity, earlier work, or Android scheduling. A process kill may leave no
final event or explanation.

Account emails are included. Log fields and set names already on the device are
trusted and displayed without an allowlist. New diagnostic call sites avoid
passwords, tokens, payloads, and raw exception messages; server URLs omit
credentials and request-specific parts. Logcat retains exception stack traces
for developer debugging and is separate from the copied support log.

Diagnostic storage is bounded and pruned in batches, without deleting synced
user data. If storage fails, a small in-memory fallback lasts until the process
ends. These logs help investigate failures; adding diagnostics alone does not
establish a root cause or fix a sync bug.
