# Storage and persistence

## Ownership

| Store | Owns | Schema and access |
| --- | --- | --- |
| `AlkitabDb` | Markers and labels, installed Bible metadata, devotional cache, per-version settings, progress pins, reading plans, sync shadows and logs | `InternalDbHelper`, accessed through `InternalDb` and its DAO facades |
| `AlkitabSongRoomDb` | Installed song books and song documents | `SongRoomDatabase`, accessed through the `SongDb` facade |
| Legacy `SongDb` | Source for the one-time migration into Room; retained legacy data | `SongDbHelper` and `SongDbDataMigration` |
| Preferences | Appearance, navigation, account state, and feature settings | `Preferences` wrapper and `Prefkey` |
| App files and cache | Bible files, catalogs, downloaded content, and temporary network data | The owning feature's import/download and cleanup code |

The active Bible database uses handwritten SQLite, not Room. Songs have a
separate Room database because they share no transactions or foreign keys with
Bible data. The legacy `SongDb` file is not an active Bible-data store.

Use `App.services.storage` for application access. Exact columns, indexes, and
migration steps belong in the
[storage sources](../Alkitab/src/main/java/yuku/alkitab/base/storage) and
[exported Room schemas](../Alkitab/schemas), rather than a second SQL schema in
this guide. Some access remains synchronous, including the song facade; use an
appropriate dispatcher for work that would block the UI.

## Compatibility and migrations

`InternalDbHelper` uses the app version code as its database version and applies
upgrade migrations. Database downgrades are unsupported. Preserve existing
column and identity conventions when changing storage.

Local row IDs and sync identities have different roles. Syncable entities use
globally unique IDs; references between them must survive import and device
changes. Reading-plan progress can outlive an installed plan. Sync shadows
represent the last synchronized state, while sync logs are diagnostic records.
Large shadow payloads need the chunked read path to avoid CursorWindow limits.

Song migration has two separate boundaries:

- `SongDbDataMigration` copies the legacy database into Room once. Its completion
  flag prevents cleared song books from being resurrected on later launches.
- Song payloads use portable JSON `SongDocument`s. Older Parcel payloads are
  decoded and converted lazily on successful read. Preserve legacy model class
  names and decoder compatibility until that stored data is no longer supported.

The payload version discriminator is defined by `SongDocumentJson`; Room's
schema version is independent of it. See the [portable-song design](features/portable-songs/design.md)
for format and migration rationale.

## Preferences and appearance

The `Preferences` wrapper provides cached access, batched writes, and change
notifications over SharedPreferences. `Prefkey` and resource defaults define
settings; avoid maintaining another key/default list in documentation.
Appearance consumers use an applied dimensions snapshot. Changes that bypass
normal writes or invalidation can leave caches stale; see
[REM-51](tech-debt-remediation/REM-51-preferences-architecture.md).

Widget settings belong to individual widget instances. Account credentials and
settings are distinct from the user-content snapshots handled by
[data transfer](modules/data-transfer.md).

## Files and packaged assets

Installed Bible files and cached catalog updates live in app-managed storage.
Temporary downloads and HTTP caches have separate lifetimes and are not a backup
of user data. File import must handle content URIs and scoped storage; do not
assume a selected document has a freely accessible filesystem path.

Bundled assets provide the internal Bible, a fallback Bible-version catalog,
help, and rendering templates. The open-source `plain` flavor packages placeholder
Bible text; production flavors use the proprietary overlay described in
[Build system](build-system.md). Bible-audio recording availability comes from
the backend and has no bundled catalog fallback.

Use [data transfer](modules/data-transfer.md) for supported user-content backups
and [sync](modules/sync.md) for cross-device changes. Neither guide promises a
full copy of preferences, installed content, or internal database files.
