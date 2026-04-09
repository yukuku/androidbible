# Songs Module

## Overview

The songs module provides hymn/worship song browsing, searching, and audio playback. Songs are organized into downloadable song books, each containing multiple songs with lyrics, metadata, and optional audio.

## Key Files

- `Alkitab/src/main/java/yuku/alkitab/songs/SongListActivity.java` — Main song list with search and filtering
- `Alkitab/src/main/java/yuku/alkitab/songs/SongViewActivity.kt` — Individual song viewer
- `Alkitab/src/main/java/yuku/alkitab/songs/SongFragment.kt` — WebView-based song rendering with JavaScript
- `Alkitab/src/main/java/yuku/alkitab/songs/SongBookUtil.java` — Song book download, installation, metadata
- `Alkitab/src/main/java/yuku/alkitab/songs/SongFilter.java` — Search/filter with regex and tokenized queries
- `Alkitab/src/main/java/yuku/alkitab/songs/SongInfo.kt` — Lightweight song record (bookName, code, title, title_original)
- `KpriModel/` — Song data model (`Song`, `Verse`, `Lyric`, `VerseKind`)

## Data Model

`KpriModel.Song` is the core song entity with fields: `title`, `title_original`, `authors_lyric`, `authors_music`, `tune`, and a list of `Verse` objects. Each verse has a `VerseKind` (verse, chorus, bridge, etc.) and `Lyric` lines.

**Important caveat**: `Song` uses `Parcelable` serialization for persistent database storage. This is noted in the code as a bad design decision — changes to the `Song` class can break deserialization of stored data.

## Storage

Songs are stored in `SongDb` (separate SQLite database from the main `InternalDb`). Song books are downloaded as serialized `List<Song>` objects via `ObjectInputStream`, optionally gzip-compressed. Data format version is currently 3.

## Search

`SongFilter` implements a sophisticated search with:
- Query tokenization (multi-term, quoted phrases via `QueryTokenizer`)
- Word-boundary and substring matching
- Regex pattern generation for highlighting
- Searches across title, title_original, and full lyric text

## Audio Playback

Songs can have audio attachments played via two controller implementations:
- `ExoplayerController.kt` — ExoPlayer (media3) for MP3 with OkHttp streaming
- `MidiController.kt` — Android MediaPlayer for MIDI with local caching

Both extend `MediaController.kt` with a shared state machine (reset → preparing → playing/paused → complete/error). See [Audio Playback](audio-playback.md) for details.

## Song Book Management

`SongBookUtil` handles:
- Listing available song books from the server
- Downloading and installing new song books
- Managing user's default song book preference
- Song book metadata (name, title, copyright info)
