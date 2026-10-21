# Song database Room migration: decision record

Original design: 2026-05-15. Implemented by
[REM-32](../../tech-debt-remediation/REM-32-room-song-db.md). That record owns
delivery and verification history; [Storage](../../storage.md) describes current
persistence. This document preserves the storage-engine decision referenced by
the build configuration.

## Keep song storage separate

Songs use their own Room database rather than sharing the Bible SQLite database.
They have no cross-domain rows, foreign keys, or transactions, and downloaded song
books can be replaced independently. Migrating song storage should not require
rewriting the Bible-data access layer.

Keep the `SongDb` facade to bound caller changes. The initial migration copied
payload bytes without interpreting them; changing a storage engine and changing
a payload format are separate operations. [REM-21](../../tech-debt-remediation/REM-21-song-json-storage.md)
later introduced JSON payloads without requiring a Room schema change.

## Migration constraints

- Preserve legacy upgrade paths before copying their resulting rows into Room.
- Copy songs and book metadata transactionally and mark migration complete with
  a persistent flag. Testing whether Room is empty would resurrect books a user
  deliberately cleared.
- Retain the legacy database as a migration source. Its retained contents are
  not a continuously synchronized mirror of subsequent Room writes.
- Keep post-delete space reclamation outside Room transactions because SQLite
  cannot run `VACUUM` inside a transaction.
- Export Room schemas and validate migrations against them. Schema versions
  are independent of song payload format versions.

The facade retains synchronous access for compatibility. Future coroutine or
threading work must address its callers rather than assume that using Room
alone moves operations off the main thread.
