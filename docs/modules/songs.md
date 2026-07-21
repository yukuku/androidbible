# Songs Module

## Overview

The songs module provides hymn/worship song browsing, searching, and audio playback. Songs are organized into downloadable song books, each containing multiple songs with lyrics, metadata, and optional audio.

## Key Files

- `Alkitab/src/main/java/yuku/alkitab/songs/SongSearchSheet.kt` — Song search/browse as a Compose `ModalBottomSheet` (hosted by `SongViewActivity` via `ComposeBottomSheetHost`), with an activity-scoped ViewModel holding the search state
- `Alkitab/src/main/java/yuku/alkitab/songs/SongViewActivity.kt` — Individual song viewer
- `Alkitab/src/main/java/yuku/alkitab/songs/SongFragment.kt` — WebView-based song rendering with JavaScript (the default renderer)
- `Alkitab/src/main/java/yuku/alkitab/songs/SongComposeFragment.kt` + `SongComposeContent.kt` — native Jetpack Compose song renderer, an experimental drop-in replacement for `SongFragment` (see "Rendering" below)
- `Alkitab/src/main/java/yuku/alkitab/songs/SongBookUtil.kt` — Song book download, installation, metadata
- `Alkitab/src/main/java/yuku/alkitab/songs/SongFilter.java` — Search/filter with regex and tokenized queries
- `Alkitab/src/main/java/yuku/alkitab/songs/SongInfo.kt` — Lightweight song record (bookName, code, title, title_original)
- `Alkitab/src/main/java/yuku/alkitab/songs/newdoc/` — Portable-song document model, JSON (de)serialization, legacy decoder/converter, and renderers (REM-21)
- `KpriModel/` — Legacy song data model (`Song`, `Verse`, `Lyric`, `VerseKind`) — retained only as the on-device decoder's target type

## Data Model

The canonical in-memory/storage model is `yuku.alkitab.songs.newdoc.SongDocument` (REM-21): a `code`, a derived `meta` (`title`/`title_original`, recomputed from `blocks` on every encode/decode — never hand-authored), and an ordered list of layout `Block`s (`PBlock`, `RowBlock`, `LyricBlock`, `ScriptureBlock`, `YoutubeBlock`, `GapBlock`, plus a forward-compatible `UnknownBlock` fallback for unrecognised `type`s). `LyricBlock.verses` holds `Verse` objects (`kind`: `NORMAL`/`REFRAIN`/`TEXT`, `lines`: `VerseLine`s), and lines can carry inline `u`/`b`/`i` styling via `Span`s. See `docs/features/portable-songs/design.md` and `docs/features/portable-songs/android-implementation-plan.md` for the full schema and migration design.

`KpriModel.Song`/`Lyric`/`Verse`/`VerseKind` are retained only as the target type `yuku.alkitab.songs.newdoc.LegacyParcelDecoder` decodes into, and the class-name dispatch keys that decoder's Android-13-format auto-detection relies on. `LegacySongConverter` maps a decoded legacy `Song` to a `SongDocument`.

## Storage

Songs are stored in `SongRoomDatabase` (its own Room database in its own SQLite file — see [Storage & Database](../storage.md) for the rationale). Two tables: `song_info` (one row per song, with the JSON-encoded `SongDocument` in the `data` column, UTF-8 bytes) and `song_book_info` (one row per installed song book). The `SongDb.java` facade preserves the legacy public surface (now typed on `SongDocument`), routing through `SongRoomDao`.

`dataFormatVersion` on a `song_info` row marks the payload shape: `5` (`SongDocumentJson.DATA_FORMAT_VERSION`) is JSON; anything else is a legacy Android `Parcel.marshall()` byte buffer written before REM-21. `SongDb.readDocument` dispatches on that column: JSON rows parse directly; legacy rows are decoded by the pure-JVM `LegacyParcelDecoder` (falling back to the platform `Parcel.unmarshall()` if that throws), converted via `LegacySongConverter`, and the result is written back to the row (bumping `dataFormatVersion` to `5`) so each legacy row is converted **at most once**, lazily, on first read — there is no bulk migration pass. This exists because the marshalled `Parcel` byte layout changed in Android 13 (a length prefix was inserted after the `VAL_PARCELABLE` tag); a device that downloaded songs pre-13 and later upgraded could otherwise fail to read its own stored BLOBs.

Song books are downloaded as a gzipped JSON wrapper (`{ v, book, songs }`) via `SongBookUtil.deserializeSongs`, replacing the old gzipped Java-serialized `List<Song>` — REM-21 removed the `ObjectInputStream`/`SafeObjectInputStream` path entirely, closing off that deserialization-gadget surface. The response body is still capped at 50MB and wrapped in try-with-resources.

The legacy `SongDb` SQLite file (managed by `SongDbHelper`) is kept around as a rollback safety net; a one-time `SongDbDataMigration` copies its rows into Room on first launch with the migrated code — those rows are simply legacy-format and get converted on first read like any other. See [REM-32](../tech-debt-remediation/REM-32-room-song-db.md) for the storage-engine migration writeup and [REM-21](../tech-debt-remediation/REM-21-song-json-storage.md) for the payload migration.

## Search

Song search UI is a Compose Material 3 `ModalBottomSheet` (`SongSearchSheet`, shown via `ComposeBottomSheetHost` + `BibleAppTheme`, so light/dark and dynamic color are supported by default) hosted over `SongViewActivity` (it was a standalone `SongListActivity` until 2026-07-17). Selecting a result dismisses the sheet and displays the song in the host activity; reopening the sheet restores the previous query, book filter, deep-search flag, results, and scroll position from `SongSearchViewModel` (scoped to the activity), so back-navigation from a song no longer skips past the search results. Searches run in a `viewModelScope` coroutine on `Dispatchers.IO`; editing the query cancels the in-flight search and starts a new one.

`SongFilter` implements a sophisticated search with:
- Query tokenization (multi-term, quoted phrases via `QueryTokenizer`)
- Word-boundary and substring matching
- Regex pattern generation for highlighting
- Searches across title, title_original, and full lyric text

## Rendering

There are two interchangeable renderers, both fed the same `SongDocument` by `SongViewActivity.displaySong`:

- **WebView (default)** — `SongFragment` renders `SongDocumentRenderer.renderDocument` into `templates/song.html` + `song.css` and loads it in a `WebView`. Interactive links (`bible:`, `youtube:`, `patchtext:`) are dispatched through `shouldOverrideUrlLoading`.
- **Compose (experimental, off by default)** — `SongComposeFragment` hosts `SongDocumentComposable` (`SongComposeContent.kt`), which walks `SongDocument.blocks` and maps each block/role to native Compose widgets — no HTML or CSS. It reproduces the WebView feature set exactly: verse numbering, refrain styling, version captions, roles (title/tune/musical/authors/…), inline `u`/`b`/`i` spans, per-line size/alignment, clickable scripture references (via `ScriptureReferenceRenderer.renderParts`) and YouTube links, copyright, and the "send corrections" patch-text link. Colors, base font size, line spacing, and typeface come from the same `App.services.uiDimensions.applied()` dimensions the WebView path uses; two-finger pinch zoom is applied as a percentage.

Both fragments implement `SongTextZoomable` so `SongViewActivity`'s `TwofingerLinearLayout` pinch-to-zoom drives either one, and both route scripture/YouTube/patch-text taps to the shared `openScriptureReference` / `openYoutube` / `openPatchText` methods on the activity (via `SongFragment.ShouldOverrideUrlLoadingHandler` and `SongComposeFragment.Host` respectively).

The renderer is chosen by the `ExperimentalFlags.useComposeSong()` flag, exposed under **Settings → Experimental features → "Song lyrics (Compose)"** (`pref_useComposeSong_key`, default off) — the same pattern as the experimental Compose verse-item and navigation flags. Open a song after toggling to see the change.

## Audio Playback

Songs can have MP3 or MIDI audio attachments, both played through a single media3 `ExoPlayer` inside the foreground `SongAudioService` (MIDI via the experimental `media3-exoplayer-midi` decoder). `SongViewActivity` drives it through `SongAudioController`, which extends `MediaController.kt` with the shared state machine (reset → preparing → playing/paused → complete/error). Playback continues in the background with a media notification, and starting song audio stops Bible audio (and vice versa) via `AudioPlaybackCoordinator`. See [Audio Playback](audio-playback.md) for details.

## Song Book Management

`SongBookUtil` handles:
- Listing available song books from the server
- Downloading and installing new song books
- Managing user's default song book preference
- Song book metadata (name, title, copyright info)
