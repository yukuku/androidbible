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

1. **Simulation mode** (dry run): Parses the file and reports what would change without modifying the database
2. **Actual import**: Applies changes transactionally with rollback capability
3. Per-entity `creator_id` tracking for provenance

## Storage Interface

`ReadonlyStorageInterface` / `ReadWriteStorageInterface` abstract database access for testability. Implementations (`ReadonlyStorageImpl`, `ReadWriteStorageImpl`) wrap `InternalDb` operations.
