# Song Document Revamp

## Current Observations

- Songs and kidung downloads currently begin in `SongViewActivity`, which opens a server-backed download page through `HelpActivity`.
- The selected download returns an `alkitab:///addon/download?...` URI and the current song-book path expects `type=ser`.
- `SongBookUtil.downloadSongBook()` fetches `/addon/songs/get_songs`, wraps the response with `OptionalGzipInputStream`, and deserializes a `List<Song>` with `ObjectInputStream`.
- Java `Serializable` is only required for that network payload path.
- Local song persistence does not use Java serialization; it stores each song as a marshalled `Parcel` blob in `SongInfo.data`.
- The local SQLite schema is hybrid: summary and index columns sit beside an opaque payload blob.
- `SongListActivity` summary search uses indexed columns, while deep search reopens the blob and scans the full `Song`.
- Rendering is WebView-based and hardcoded to the current `Song` field layout through `SongFragment` template string replacement.
- Copy, share, and patch-text flows each carry their own song-to-text or song-to-HTML logic instead of sharing one projection layer.
- `VerseKind.TEXT` exists but is not treated as a first-class rendering concept, and `Verse.ordering` is effectively ignored in the current renderer.

## Current Flow

1. `SongViewActivity` opens the download page in `HelpActivity`.
2. The download page returns an `alkitab:///addon/download?...type=ser...` URI.
3. `SongViewActivity.downloadByAlkitabUri()` validates the URI, extracts the song-book metadata, and calls `SongBookUtil.downloadSongBook()`.
4. `SongBookUtil.downloadSongBook()` fetches the song-book payload, optionally ungzips it, then deserializes a `List<Song>` through `ObjectInputStream`.
5. `SongDb.storeSongs()` writes the song-book metadata plus summary columns and a parcel-marshalled blob for each song row.
6. Summary list/search screens use `SongInfo` rows; deep search reparses full songs from blobs.
7. `SongViewActivity` loads one song, prepares template values such as scripture links and copyright, and hands the song to `SongFragment`.
8. `SongFragment` fills a fixed HTML template and renders the result in a WebView.

## Pain Points

- The transport format is tightly coupled to Java object serialization and the exact legacy model classes.
- Local persistence depends on parcel marshalling, which is not a stable or inspectable storage format.
- Rendering assumes a fixed field-based schema rather than a flexible document structure.
- Search has to deserialize full songs for deep matching instead of using document-derived search text.
- Text export paths duplicate structure knowledge and can drift from the main renderer.
- The existing schema makes it hard to support arbitrary song layouts or richer future content blocks.

## Chosen Migration Decisions

- Rollout: dual-format rollout.
- Scope: end-to-end plan including server and Android app changes.
- Storage: canonical JSON document plus indexed summary and search columns in SQLite.
- Legacy installed books: keep readable and migrate that book when it is updated or redownloaded.
- Schema style: typed song metadata plus ordered typed blocks.
- Renderer: AST-to-HTML/WebView for v1.

## Implementation Plan

- Add JSON transport for song-book download using `type=json&schemaVersion=1`.
- Keep legacy `type=ser` accepted during rollout.
- Introduce canonical models: `SongBookDocument`, `SongDocument`, `SongMeta`, and typed `SongBlock` nodes.
- Use a typed ordered block model for v1, including the minimum block families needed to represent the current catalog: section, stanza, refrain, paragraph, heading, and divider.
- Convert any legacy `Song` object to `SongDocument` immediately after parsing and never persist new parcel or blob rows for migrated content.
- Extend song storage to keep `documentJson`, `storageFormat`, `documentSchemaVersion`, and `searchText` while preserving fast index columns such as `bookName`, `code`, `title`, `title_original`, `ordering`, and `updateTime`.
- Make full-song reads return `SongDocument`; legacy rows are mapped in memory until that book is updated.
- Change deep search to use `searchText` for JSON rows and keep a legacy fallback only for old rows.
- Replace field-slot HTML generation with a document renderer that emits HTML from typed blocks.
- Keep the WebView template only as a themed shell around rendered document HTML instead of as the source of layout structure.
- Derive copy, share, and patch-text output from the same document projection layer used by the main renderer.
- Keep keypad navigation and audio URL lookup based on `bookName + code`.
- Add server-side JSON support for song-book download so the client can request canonical document payloads.
- Remove legacy `Serializable`, `ObjectInputStream`, and parcel persistence only in a later cleanup phase after rollout is complete.

## Test Plan

- Fresh JSON download and render flow.
- Existing legacy installed book remains readable before update.
- Updating a legacy book rewrites it to JSON storage.
- Search parity between legacy and JSON books.
- Copy, share, and patch-text parity from the new document source.
- Gzip and non-gzip response parsing.
- Graceful handling of malformed or unknown schema versions.
- Graceful handling of unknown document block types without crashing the feature.

## Assumptions

- No native Android renderer is included in this change.
- `SongBookInfo` schema remains unchanged in v1.
- Audio hosting and path conventions remain unchanged.
- The backend will provide the JSON endpoint and payload described by this plan.
