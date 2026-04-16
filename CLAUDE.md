# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Bible for Android** (Alkitab / Quick Bible) — a 100% free, open-source Bible reader app for Android. Published on Google Play as "Alkitab" (Indonesian) and "Quick Bible" (non-Indonesian). The codebase supports multiple product flavors, 100+ downloadable Bible versions, song books, devotions, reading plans, cloud sync, and a daily verse widget.

- Official site: https://alkitab.app
- Developer docs: https://alkitab.app/developer

## Build Commands

```bash
# Debug APK (open-source build, works out of the box)
./gradlew assemblePlainDebug

# Debug App Bundle
./gradlew bundlePlainDebug

# Unit tests (same as CI)
./gradlew testPlainDebugUnitTest testPlainReleaseUnitTest

# Run a single test class
./gradlew testPlainDebugUnitTest --tests "yuku.alkitab.base.util.QueryTokenizerTest"

# Run a single test method
./gradlew testPlainDebugUnitTest --tests "yuku.alkitab.base.util.QueryTokenizerTest.testQuotedPhrases"
```

**Requirements**: JDK 17 (Zulu recommended), Android SDK with compile SDK 36, NDK 28.2.13676358.

The `plain` flavor is the open-source development build and works out of the box with the placeholder `Alkitab/google-services.json` checked into the repo (Firebase features won't function at runtime, but the app builds and runs). Production flavors (`yuku_alkitab`, `yuku_quick_bible`, `sabda_alkitab`) require:
- `$ALKITAB_PROPRIETARY_DIR/overlay/<applicationId>/text_raw/` — proprietary Bible text
- `$ALKITAB_PROPRIETARY_DIR/google-services.json` — real Firebase config (one file with client entries for all production applicationIds)
- Signing-key env vars: `SIGN_KEYSTORE`, `SIGN_ALIAS`, `SIGN_PASSWORD`

With those set, build with a plain `./gradlew assembleYuku_alkitabRelease` (or any other production flavor).

## Architecture

### Module Structure

The project is a multi-module Gradle build. The main app module is **`:Alkitab`**. All other modules are libraries:

| Module | Purpose |
|--------|---------|
| `AlkitabModel` | Core data models (`Ari`, `Version`, `Book`, `SingleChapterVerses`, `MVersion`) |
| `AlkitabIo` | `BibleReader` interface, UTF-8 decoding, gzip stream handling |
| `AlkitabYes2` | YES2 binary Bible format reader/writer with Snappy compression |
| `AlkitabIntegration` | Inter-app communication API (intent-based verse lookup) |
| `AlkitabFeedback` | User feedback module |
| `BiblePlus` | PalmBible+ PDB format reader |
| `KpriModel` | Song/hymn data model (`Song`, `Verse`, `Lyric`) |
| `BintexReader` / `BintexWriter` | Binary serialization format used by YES2 and reading plans |
| `Afw` | Base Android framework (preferences wrapper, adapter base, app context) |
| `Snappy` | JNI Snappy compression (native C++ via NDK) |
| `AmbilWarna` | Color picker dialog |
| `FlowLayout` | Flow layout widget |
| `PrDownloaderFixed` | Patched PRDownloader for HTTP file downloads |
| `ImportedDesktopVerseUtil` | Desktop verse reference finder/parser |

### Key Singletons and Entry Points

- **`App.java`** — Application class, extends `yuku.afw.App`. Initializes Firebase, PRDownloader, preference defaults, extension receivers.
- **`S.kt`** — Central service locator. Holds lazy references to `InternalDb`, `SongDb`, active Bible version, calculated UI dimensions. All global state access goes through `S`.
- **`IsiActivity.kt`** (~2900 lines) — Main Bible reader activity. Manages verse display, split view, navigation history, action mode (copy/share/bookmark), gestures (pinch zoom, swipe), and volume button navigation.

### Data Flow: Bible Text Rendering

```
S.activeVersion() → MVersion → Version (abstract)
  → Version.loadChapterText() → SingleChapterVerses
  → Version.loadPericope() → section headers
  → VersesDataModel (merges verses + pericopes via itemPointer array)
  → VersesControllerImpl (RecyclerView adapter)
  → VerseRenderer / FormattedTextRenderer (applies formatting codes)
  → VerseItem (custom RelativeLayout with highlight/selection drawing)
```

### ARI (Alkitab Resource Identifier)

The fundamental addressing scheme — a 24-bit integer encoding book, chapter, and verse:
```
bits 23-16: bookId (0-65)
bits 15-8:  chapter (1-based, 0 = whole book)
bits 7-0:   verse (1-based, 0 = whole chapter)
```
Used everywhere: database storage, intent extras, sync protocol, content provider URIs. See `Ari.java` in AlkitabModel.

### Database Schema

`InternalDb` (SQLite) has these core tables:
- **Marker** — bookmarks, notes, highlights (distinguished by `kind` column). Each row has a `gid` (globally unique ID) for sync.
- **Label** — bookmark categories with custom background colors.
- **Marker_Label** — many-to-many junction between markers and labels.
- **Version** — metadata for downloaded Bible versions (filename, locale, active flag, ordering).
- **ProgressMark** — 5 reading progress pins with ARI positions.
- **ReadingPlan** / **ReadingPlanProgress** — reading plan data and daily completion tracking.
- **Devotion** — cached devotional articles.
- **SyncShadow** / **SyncLog** — sync state tracking.
- **PerVersion** — per-version settings.

### Verse Text Formatting Codes

Verse text uses inline formatting codes (processed by `VerseRenderer` and `FormattedTextRenderer`):
- `@@` — marks verse as formatted
- `@0`–`@4`, `@^` — paragraph indentation levels
- `@6`/`@5` — red letter (Jesus' words) start/end
- `@9`/`@7` — italic start/end
- `@8` — line break
- `@<tag@>...@/` — special inline elements (cross-references, footnotes)

`FormattedVerseText.removeSpecialCodes()` strips these for plain-text operations (copy, search).

### Sync Protocol

Delta-based sync over REST (`/sync/api/sync`). Each entity type has a dedicated class:
- `Sync_Mabel` — markers, labels, marker-label associations
- `Sync_Pins` — progress marks
- `Sync_Rp` — reading plan progress
- `Sync_History` — reading history

Uses operation deltas (add/mod/del) with base revisions. Firebase Cloud Messaging triggers sync on other devices. Auth is simple token-based (`simpleToken`).

### Content Provider

`yuku.alkitab.base.cp.Provider` — read-only ContentProvider for external apps to query Bible verses by ARI or LID (sequential verse ID). Supports single verse, range queries, and version listing.

### YES2 Binary Bible Format

Custom binary format for Bible text files (`.yes`):
- Header magic bytes, then section index (Bintex-encoded)
- Sections: `versionInfo`, `booksInfo`, `text`, `xrefs`, `footnotes`, `pericopies`
- Text section supports Snappy compression
- Loaded via `YesReaderFactory` → `Yes2Reader`
- PDB (PalmBible+) files are converted to YES2 on import

## Module Documentation

Detailed documentation for each major feature module:

- [Songs](docs/modules/songs.md) — Song book browsing, search, audio (MP3/MIDI) playback
- [Reading Plans](docs/modules/reading-plans.md) — RPB binary format, daily progress tracking
- [Versions](docs/modules/versions.md) — Bible version management, download, YES2 format
- [Markers](docs/modules/markers.md) — Bookmarks, notes, highlights system
- [Sync](docs/modules/sync.md) — Cloud sync protocol and FCM push
- [Devotions](docs/modules/devotions.md) — Daily devotional articles
- [Audio Playback](docs/modules/audio-playback.md) — ExoPlayer/MIDI controllers for songs
- [Search](docs/modules/search.md) — Full-text verse search engine
- [Daily Verse Widget](docs/modules/daily-verse-widget.md) — Home screen widget
- [Data Transfer](docs/modules/data-transfer.md) — JSON export/import of user data

## Technical Documentation

- [Architecture Deep Dive](docs/architecture.md) — Singleton patterns, data flow, module dependencies
- [Build System](docs/build-system.md) — Flavors, signing, CI/CD, release process
- [Text Rendering](docs/text-rendering.md) — Verse formatting pipeline and codes
- [Binary Formats](docs/binary-formats.md) — YES2, Bintex, RPB file format specs
- [Storage & Database](docs/storage.md) — SQLite schema, preferences, file storage
- [Backend Communication](docs/backend-communication.md) — API endpoints, download flows
- [Tech Debt & Improvements](docs/tech-debt.md) — Known issues with specific file/line references, potential bugs, deprecated APIs
- [Tech Debt Remediation Plan](docs/tech-debt-remediation.md) — Prioritized remediation tasks with BRICE evaluation, steps, difficulty, and suggested sprint plan

## Code Conventions

- Mixed Java/Kotlin codebase (Kotlin preferred for new code, many files still Java)
- JVM toolchain 17 across all modules
- No obfuscation in ProGuard (`-dontobfuscate`), only shrinking
- EditorConfig disables `import-ordering` and `no-wildcard-imports` for Kotlin
- Preference keys are defined as enum entries in `Prefkey.kt`
- GIDs (globally unique IDs) are used alongside database `_id` for sync-capable entities
- `Ari` encoding is used universally for verse references — never store book/chapter/verse separately
- Version IDs follow format `"internal"`, `"preset/[name]"`, or `"file/[path]"`

## Unit Testing

- **Prefer Kotlin backtick identifiers with descriptive sentences** for test function names — they render as readable prose in JUnit output. Use full sentences, not short names.
  ```kotlin
  // Good
  @Test
  fun `patchNoConflict treats add on an existing gid as an overwrite (same as mod)`() { ... }

  // Avoid
  @Test
  fun patchNoConflict_addExistingGid_overwrites() { ... }
  ```
- **Use Robolectric if needed** for tests that exercise Android framework code (Context, Intents, Parcelable, DB helpers, etc.). Pure-logic tests should stay plain JUnit. If an Android method is blocking a pure-logic test with `"Method not mocked"`, first consider whether a tiny test-scope shadow (e.g. `src/test/java/android/util/Pair.java`) is enough before reaching for Robolectric.
- When writing unit tests, assume the production code is correct. If you spot what looks like an obvious bug while writing tests, flag it rather than silently working around it.

## Important Caveats

- `IsiActivity.kt` is ~2900 lines — the monolithic main activity handles Bible reading, split view, navigation, gestures, and action mode. Changes here require careful testing.
- `KpriModel.Song` uses `Parcelable` serialization for database storage (acknowledged as a bad design decision in the code).
- The `Snappy` module has native C++ code — NDK must be installed for builds.
- A placeholder `Alkitab/google-services.json` is checked in so `plainDebug` works out of the box; Firebase features won't actually function with it. For production flavors, the real `google-services.json` is sourced from `$ALKITAB_PROPRIETARY_DIR/google-services.json` at build time and copied into the gitignored `Alkitab/src/<flavor>/google-services.json` (where the GMS plugin's source-set lookup finds it).
- The internal Bible version data is split per flavor: the placeholder `ddd_*` files live under `Alkitab/src/plain/assets/internal/` (used by the `plain` open-source build only). Production flavors get their `tb_*`/`kjv_*` files copied from the proprietary overlay into `build/generated/proprietaryAssets/<flavor>/internal/` at build time.
