# Bible Versions Module

## Overview

The app supports 100+ downloadable Bible versions across many languages. Versions use the custom YES2 binary format. Users can also import PalmBible+ PDB files, which are converted to YES2.

## Key Files

- `Alkitab/src/main/java/yuku/alkitab/versionmanager/VersionsActivity.kt` — Version management UI
- `Alkitab/src/main/java/yuku/alkitab/versionmanager/VersionListFragment.kt` — Lists downloadable and installed versions; `RecyclerView` + `ItemTouchHelper` for the downloaded-tab reorder
- `Alkitab/src/main/java/yuku/alkitab/versionmanager/VersionFileImporter.kt` — Imports .yes, .pdb, .yes.gz, .pdb.gz files
- `Alkitab/src/main/java/yuku/alkitab/base/config/VersionConfig.java` — JSON catalog of available versions
- `Alkitab/src/main/java/yuku/alkitab/base/storage/YesReaderFactory.java` — Factory for loading YES1/YES2 readers
- `Alkitab/src/main/java/yuku/alkitab/base/util/VersionDownloadWorker.kt` — `CoroutineWorker` (OkHttp + Range-based resume) used to download `.yes` files
- `Alkitab/src/main/java/yuku/alkitab/base/util/DownloadMapper.kt` — In-process tracker that observes `WorkManager.getWorkInfoByIdFlow` and mirrors state into `DownloadManager.STATUS_*` constants for the list UI
- `Alkitab/src/main/java/yuku/alkitab/base/storage/VersionDao.kt` — Facade DAO routing through Room
- `Alkitab/src/main/java/yuku/alkitab/base/storage/room/VersionEntity.kt` / `VersionRoomDao.kt` — Room entity/DAO

## MVersion Hierarchy

```
MVersion (abstract)
├── MVersionInternal — Built-in version, always available, ID = "internal"
├── MVersionDb — Downloaded/user-added versions stored in database
└── MVersionPreset — Metadata for versions available to download (not yet installed)
```

`App.services.versions.getAvailableVersions()` (or the legacy `S.getAvailableVersions()`) merges internal + DB versions, filtering for active ones. Version instances (`VersionImpl`) are cached in a `ConcurrentHashMap` with `SoftReference` for memory-efficient GC.

## Version IDs

- `"internal"` — the built-in version
- `"preset/[name]"` — downloaded from the preset catalog
- `"file/[path]"` — user-imported from a file

## Version Config

`version_config.json` (44KB, bundled in assets) contains metadata for all downloadable versions: locale, short/long names, modify times, group ordering. This config is periodically updated from the server and cached locally at `files/version_config.json`.

## Import / Download Flow

For downloads from the preset catalog:
1. User picks a preset in `VersionListFragment`
2. `DownloadMapper.enqueue(...)` builds a `OneTimeWorkRequest` for `VersionDownloadWorker` and starts a `MainScope` observer on `WorkManager.getWorkInfoByIdFlow(uuid)`
3. The worker streams the file via OkHttp (`Accept-Encoding: identity`, `Range: bytes=N-` on resume) into a temp file under `cacheDir/DownloadMapper-tmp/`, reporting progress via `setProgress(Data)`
4. On `SUCCEEDED`, `VersionDownloadCompleteReceiver.onReceive(id)` finalizes the file (decompresses gzip via `OptionalGzipInputStream` if needed), moves it into app storage, and inserts a row into the `version` table

For local imports (`.yes` / `.pdb`):
1. User selects a `.yes` or `.pdb` file (via file manager or intent)
2. `VersionFileImporter` detects format and handles gzip decompression
3. PDB files are converted to YES2 via the `BiblePlus` module, saved as `pdb-{name}.yes`
4. YES2 files are copied to app storage
5. A row is inserted into the `version` table
6. Import size limit: 100MB

## YES2 Format Details

See [Binary Formats](../binary-formats.md) for the complete YES2 specification.

## Per-Version Settings

`PerVersion` table stores per-version settings like font size multiplier, allowing different text sizes for different Bible versions.
