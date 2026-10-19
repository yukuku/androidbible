# Data Transfer Module

## Overview

JSON-based export/import of user data (markers, labels, reading plan progress, history, progress pins). Designed for backup/restore and migration between devices without cloud sync.

## Key Files

- `Alkitab/src/main/java/yuku/alkitab/datatransfer/process/ExportProcess.kt` — Exports user data to JSON
- `Alkitab/src/main/java/yuku/alkitab/datatransfer/process/ImportProcess.kt` — Imports from JSON with dry-run support
- `Alkitab/src/main/java/yuku/alkitab/datatransfer/model/JsonFileStructure.kt` — Kotlinx.Serializable data structures
- `Alkitab/src/main/java/yuku/alkitab/datatransfer/ui/DataTransferActivity.kt` — UI for export/import
- `Alkitab/src/main/java/yuku/alkitab/datatransfer/model/Gid.kt` — GID handling
- `Alkitab/src/main/java/yuku/alkitab/datatransfer/model/Rpp.kt` — Reading plan progress structures

## JSON Structure

```json
{
  "success": true,
  "snapshots": {
    "history": { "entities": [...] },
    "mabel": {
      "entities": {
        "markers": [...],
        "labels": [...],
        "marker_labels": [...]
      }
    },
    "pins": { "entities": [...] },
    "rp": { "entities": [...] }
  }
}
```

Uses Kotlinx Serialization for JSON encoding/decoding.

## Import Process

1. **Simulation mode** (dry run): Parses the file and validates changes inside a rolled-back database transaction. Reading history remains untouched in memory and preferences.
2. **Actual import**: Commits database changes, then replaces and saves the staged reading history. Exceptions during database import roll back all database writes and leave history untouched.
3. Per-entity `creator_id` tracking for provenance

Marker kinds must be recognized enum values. Marker-label assignments must reference markers and labels present either in the file or the local database; file ordering does not affect validation. Invalid rows reject the import rather than silently dropping data.

Exports retain completed readings for deleted reading plans, matching sync's preservation of that progress. Since the export format requires a numeric `startTime`, a missing plan's start time is represented as `0`. Import restores its completed readings without requiring the plan to be installed.

## Storage Interface

`ReadonlyStorageInterface` / `ReadWriteStorageInterface` abstract database access for testability. Implementations (`ReadonlyStorageImpl`, `ReadWriteStorageImpl`) wrap `InternalDb` operations.
