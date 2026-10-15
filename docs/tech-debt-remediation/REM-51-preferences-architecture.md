# REM-51: Make Preference Types and Cache Invalidation Explicit

**Module:** Preferences (`yuku.afw.storage.Preferences`, `Prefkey`)
**Status:** Open. Transaction hold/unhold safety is complete in REM-02; type and cache work remains.
**BRICE:** Not scored; choose an incremental compatibility strategy before scheduling.

## Problem

`Preferences` has a manual cache with a `dirty` flag and external `invalidate()` calls. Correct refresh behavior depends on callers knowing when to invalidate it. `Prefkey` is a flat enum whose accessors (`getString`, `getInt`, `getBoolean`, and others) do not encode each key's value type, so incorrect accessor/default combinations are not checked at compile time.

## Remaining work

1. Define typed preference access, defaults, and logical groups while preserving persisted key names and values.
2. Make invalidation ownership explicit so callers do not need hidden knowledge of the cache. Verify updates made through all supported write paths.
3. Evaluate an incremental typed wrapper or DataStore/proto migration. Account for synchronous Java callers, startup, backup/restore, and existing transaction semantics before choosing a storage change.
4. Verify existing-install migration, default values, type mismatches, and refresh behavior.

The transaction cleanup fix is recorded in [REM-02](REM-02-preferences-hold-unhold.md). The editor concurrency race belongs to [REM-34](REM-34-sync-delivery.md) and should be fixed independently of a broader preference-storage migration.

---

[← Back to remediation index](../tech-debt-remediation.md)
