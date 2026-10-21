# User-data backup and import

Data transfer exports and imports JSON snapshots of bookmarks, notes, highlights,
labels, reading-plan progress, progress pins, and reading history. It supports
backup/restore and moving user content between devices without signing in.
It does not package installed Bible versions, song books, or every app setting.

## Import safety

A simulation previews changes inside a rolled-back database transaction and
leaves reading history untouched. Actual import commits database changes before
replacing the staged history. Database import failures roll back database writes
and leave history unchanged.

Marker kinds and marker-label references are validated. References may resolve
against either the imported file or existing local data, regardless of file
ordering. Invalid rows reject the import rather than silently dropping content.
Entity identity and provenance are preserved across export and import.

Completed readings survive even if the plan has been deleted. Such progress can
be restored without reinstalling the plan; the export uses `0` when the missing
plan has no start time.

## Ownership

The [data-transfer package](../../Alkitab/src/main/java/yuku/alkitab/datatransfer)
owns the UI, snapshot model, validation, and transactional import/export. The
serializable models and compatibility tests define the JSON contract. Storage
interfaces separate snapshot operations from database access.

See [Storage](../storage.md) for persistence and [Sync](sync.md) for cross-device
synchronization. A successful import or export does not imply a successful sync.
