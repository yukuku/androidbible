# AGENTS.md

This file provides guidance to coding agents when working with code in this repository.

## Project Overview

**Bible for Android** (Alkitab / Quick Bible) — a 100% free, open-source Bible reader app for Android. Published on Google Play as "Alkitab" (Indonesian) and "Quick Bible" (non-Indonesian). The codebase supports multiple product flavors, 100+ downloadable Bible versions, song books, devotions, reading plans, cloud sync, and a daily verse widget.

- Official site: https://alkitab.app
- Developer docs: https://alkitab.app/developer

## Branches

**`develop` is the main integration branch — base all work on `develop` and target every PR at `develop`, never `master`.** The repo follows git-flow: `master` only receives merges from `release/*` branches when a version ships.

## Complete implementation tasks with a pull request

Unless the user explicitly requests otherwise, finish every implementation task by committing the changes, pushing a task branch to `yukuku/androidbible`, and opening a pull request targeting `develop`. This is part of completing the task, including requests phrased as building a feature or fixing a bug; do not ask for separate permission to create the PR.

Use a branch in this repository so the PR triggers GitHub Actions to build signed release APKs and publish APK preview links. See [the CI and preview documentation](docs/build-system.md#cicd).

- Run the checks appropriate to the changes before pushing; CI supplements local verification.
- When continuing a task that already has an open PR, push updates to its branch and update its description as needed.
- Return the PR link and the observed CI status to the user.
- Leave merging the PR and publishing a production release to an explicit user request.
- Write PR descriptions for a reviewer who has not followed the conversation. Describe the problem, resulting behavior, relevant implementation details, and validation. Omit the agent's work diary, temporary computer problems, process restarts, and environment troubleshooting unless they are necessary to reproduce the issue or explain a remaining verification limitation. Validation should state what was tested, the result, and any material coverage limits.

Read-only analysis and advice do not require a PR.

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

**Requirements**: JDK 21 and the Android SDK/NDK versions in `gradle/libs.versions.toml`. The NDK is required for Snappy.

**Versioning**: `versionName` comes from `version.properties` (`versionBase` +
`betaNumber`) combined with a stage: `dev` by default (`5.0.0-dev.<commits
since versionBase last changed>`), `beta` via `-PversionStage=beta`
(`5.0.0-beta.<n>`), `release` via `-PversionStage=release` (`5.0.0`). `versionCode` is derived from wall-clock
time and is unrelated to the stage. Never hardcode a version in
`Alkitab/build.gradle.kts`; see the "Versioning" section of
`docs/build-system.md`.

The `plain` flavor is the open-source development build and works out of the box with the placeholder `Alkitab/google-services.json` checked into the repo (Firebase features won't function at runtime, but the app builds and runs). Production flavors (`yuku_alkitab`, `yuku_quick_bible`, `sabda_alkitab`) require:
- `$ALKITAB_PROPRIETARY_DIR/overlay/<applicationId>/text_raw/` — proprietary Bible text
- `$ALKITAB_PROPRIETARY_DIR/google-services.json` — real Firebase config (one file with client entries for all production applicationIds)
- Signing-key env vars: `SIGN_KEYSTORE`, `SIGN_ALIAS`, `SIGN_PASSWORD`. The `sabda_alkitab` flavor has its own upload key and needs `SIGN_SABDA_KEYSTORE`, `SIGN_SABDA_ALIAS`, `SIGN_SABDA_PASSWORD` instead (there is no fallback to `SIGN_*`)

With those set, build with a plain `./gradlew assembleYuku_alkitabRelease` (or any other production flavor).

### Environment setup

Use the installed JDK and Android SDK when available. Check `java -version`, and read SDK/NDK requirements from `gradle/libs.versions.toml`. Set `ANDROID_HOME` or `sdk.dir` in the untracked `local.properties` file. For a VM without these tools, see [Build Environment Setup](docs/build-environment.md).

Code changes should be verified with a local `./gradlew assemblePlainDebug` (and relevant `testPlainDebugUnitTest` invocations) before committing — do not treat the GitHub Actions run as the first build.

## Architecture

### Module Structure

The project is a multi-module Gradle build. The main app module is **`:Alkitab`**. All other modules are libraries:

| Module | Purpose |
|--------|---------|
| `AlkitabModel` | Core data models (`Ari`, `Version`, `Book`, `SingleChapterVerses`, `MVersion`) |
| `AlkitabIo` | `BibleReader` interface, UTF-8 decoding, gzip stream handling |
| `AlkitabYes2` | YES2 binary Bible format reader/writer with Snappy compression |
| `AlkitabIntegration` | Inter-app communication API (intent-based verse lookup) |
| `Bintex` | Binary serialization format used by YES2 and reading plans |
| `Snappy` | JNI Snappy compression (native C++ via NDK) |

App implementation code lives under `Alkitab/src/main/java/`:

| Package | Purpose |
|---------|---------|
| `com.example.android.wizardpager`, `yuku.alkitabfeedback` | Feedback wizard and submission |
| `com.compactbyte.android.bible`, `com.compactbyte.bibleplus.reader` | PalmBible+ PDB import |
| `yuku.kpri.model` | Legacy song decoding models; preserve class names for stored data compatibility |
| `yuku.afw` | Application context, preferences, and adapter helpers |
| `yuku.devoxx.flowlayout` | Flow layout widget |
| `yuku.alkitabconverter.util` | Verse reference finder and parser |

Desktop converters share the `AlkitabModel`, `AlkitabIo`, `AlkitabYes2`, `Bintex`, and `Snappy` source directories through `tools/common-jvm/common-jvm.iml`. Keep those source roots synchronized when moving shared code; see [Tools](docs/tools.md).

Parser tests and their golden fixture belong to the app test suite. See [parser compatibility tests](Alkitab/src/test/desktop-verse-parser.md) for the baseline contract and targeted test commands. App shrinker rules live in `Alkitab/proguard-rules.pro`.

Bible-version downloads run inside `VersionDownloadWorker` (a `CoroutineWorker`) using the shared OkHttp client.

### Key Singletons and Entry Points

- **`App.java`**: Application class, extends `yuku.afw.App`. Initializes Firebase, FCM registration, and preference defaults. Holds the eagerly-initialized `App.services` (`AppServices` container) introduced by REM-24.
- **`S.kt`** — Legacy service locator. Holds lazy references to `InternalDb`, `SongDb`, active Bible version state, and calculated UI dimensions. New code should depend on the narrower `StorageProvider` / `VersionManager` / `UiDimensionsProvider` interfaces via `App.services.*`; existing `S.foo` call sites are being migrated incrementally (REM-24).
- **`IsiActivity.kt`** (at `yuku/alkitab/base/IsiActivity.kt`) — Main Bible reader activity. Verse display, navigation history, and volume-button navigation still live here; gesture handling, the action-mode callback, and split-view management have been extracted into `ReaderGestureHandler` (REM-06), `VerseActionModeController` (REM-07), and `SplitViewManager` (REM-08) respectively.

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

Every verse list is built through `createVersesController`, which swaps the
list's RecyclerView for a `VersesComposeView` driven by
`VersesComposeControllerImpl` (a LazyColumn-based implementation of the same
`VersesController` interface): `VerseRendererCompose` renders verse text to an
`AnnotatedString`, rows reuse `VerseItemComposeContent`, and pericope headers
are composed natively. `IsiActivity`'s two panes, `VersesDialog` and
`XrefDialog` all go through it. The RecyclerView pipeline above is kept as an
opt-out, reachable through the "Verse (legacy views)" experimental setting.

Screens that show verse text outside a verse list (search results, the marker
list, the progress-mark list, the report screen) hold a `VerseTextSlot`
instead, which swaps the row's snippet TextView for a `VerseTextComposeView`
rendering the same `AnnotatedString` in the reader's verse appearance.

### ARI (Alkitab Resource Identifier)

The fundamental addressing scheme — a 24-bit integer encoding book, chapter, and verse:
```
bits 23-16: bookId (0-65)
bits 15-8:  chapter (1-based, 0 = whole book)
bits 7-0:   verse (1-based, 0 = whole chapter)
```
Used everywhere: database storage, intent extras, sync protocol, content provider URIs. See `Ari.java` in AlkitabModel.

### Database Schema

The app uses three SQLite files — `AlkitabDb` and `SongDb` (hand-rolled `SQLiteOpenHelper`s, still the active sources of truth for everything Bible-related), plus `AlkitabSongRoomDb` (Room-backed, the active source of truth for the Songs subsystem after REM-32).

- **`AlkitabDb`** — hand-rolled `SQLiteOpenHelper` (`InternalDbHelper`). Sets `user_version` to the build-time `App.getVersionCode()` and migrates through `onUpgrade` based on that. Tables:
  - **Marker** — bookmarks, notes, highlights (distinguished by `kind` column). Each row has a `gid` (globally unique ID) for sync.
  - **Label** — bookmark categories with custom background colors. Columns are Indonesian (`judul`, `urutan`, `warnaLatar`).
  - **Marker_Label** — many-to-many junction between markers and labels.
  - **Version** — metadata for downloaded Bible versions (filename, locale, active flag, ordering).
  - **Devotion** — cached devotional articles keyed by `(name, date, dataFormatVersion)`.
  - **PerVersion** — per-version settings keyed by `versionId`.
  - **ProgressMark** / **ProgressMarkHistory** — 5 reading progress pins (addressed by `preset_id`) plus the append-only history of every pin update.
  - **ReadingPlan** / **ReadingPlanProgress** — downloaded reading plans (metadata + RPB binary blob) and per-day completion rows.
  - **SyncShadow** / **SyncLog** — sync state tracking (last-synced entity snapshot per sync set) plus an append-only audit log of every sync event.
- **`SongDb`** — hand-rolled `SQLiteOpenHelper` (`SongDbHelper`). After REM-32, every table here is a rollback safety net only, plus the source of legacy rows for `SongDbDataMigration` on first launch with the migrated code.
- **`AlkitabSongRoomDb`** — Room database (`yuku.alkitab.base.storage.room.SongRoomDatabase`, at `@Database(version = 1)`). Holds the Songs subsystem tables migrated as part of REM-32. Lives in its own SQLite file because the Songs module is genuinely self-contained — it shares no rows, FKs, or transactions with the Bible-reading tables.
  - **song_info** (REM-32) — one row per song, with `(bookName, code)` and `(bookName, ordering)` indexes. The `data` BLOB holds a UTF-8 JSON-encoded `yuku.alkitab.songs.newdoc.SongDocument` at `dataFormatVersion = 5` (REM-21); rows still at an older `dataFormatVersion` are a legacy Parcelable-marshalled `yuku.kpri.model.Song`, decoded and converted to JSON lazily on first read (`SongDb.readDocument`), at most once per row.
  - **song_book_info** (REM-32) — one row per installed song book, indexed by `name`.

The legacy `SongInfo` / `SongBookInfo` tables are still created in `SongDb` as a rollback safety net. `SongDbDataMigration` (wired from `S.songDb`'s lazy initializer) copies them into Room on first launch with the migrated code; the `SongDb` facade preserves the public surface so callers don't change. `InternalDb` plus its per-table facades (`MarkerDao`, `LabelDao`, `Marker_LabelDao`, `VersionDao`, `DevotionDao`, `PerVersionDao`, `ProgressMarkDao`, `ReadingPlanDao`, `SyncShadowDao`) talk directly to `AlkitabDb` via raw SQL.

### Verse Text Formatting Codes

Verse text uses inline formatting codes (processed by `VerseRenderer` and `FormattedTextRenderer`):
- `@@` — marks verse as formatted
- `@0`–`@4`, `@^` — paragraph indentation levels
- `@6`/`@5`: red text start/end
- `@9`/`@7` — italic start/end
- `@8` — line break
- `@<tag@>...@/` — special inline elements (cross-references, footnotes, and `@<r=reading@>base@/` ruby, rendered by the Compose pipeline only; see `docs/features/ruby/design.md`)

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

Detailed documentation for each major app feature:

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
- [Google Play Publishing](docs/play-publishing.md) — Manual store uploads via `tools/play/publish.py`, GPP-shaped metadata layout
- [Tools](docs/tools.md) — The desktop converters in `tools/` for Bible versions and reading plans, and the prebuilt jars
- [Text Rendering](docs/text-rendering.md) — Verse formatting pipeline and codes
- [Binary Formats](docs/binary-formats.md) — YES2, Bintex, RPB file format specs
- [Storage & Database](docs/storage.md) — SQLite schema, preferences, file storage
- [Backend Communication](docs/backend-communication.md) — API endpoints, download flows
- [Tech Debt Backlog](docs/tech-debt-remediation.md): the single index for open, partial, and completed REM records, with problem descriptions, priorities, and next steps

## Code Conventions

- Use neutral, everyday wording for synthetic code examples, test fixtures, and test names. Avoid religious names in these examples. Describe formatting in code comments as red text or red-letter formatting.

- **IMPORTANT. Never write change-narrating comments.** Code comments must describe the code as it is *now*, not the history of how it got there. Do not reference past revisions, removed approaches, or the act of editing: no "previously", "used to", "formerly", "earlier iterations", "an earlier version", "we changed/renamed/moved this from…", "now uses…", "no longer…", and the like. Git history is the record of *change*; the code and its comments describe the *present*. When explaining why the current design was chosen over an alternative is genuinely useful, state it as present-tense rationale about the alternative ("the cluster is centered with Spacers because a weighted slot would clip the label"), not as a story about what the code used to do. This applies to all comments, KDoc/Javadoc, and commit-adjacent code. Keep the narrative of change out of the source. The same rule applies to milestone labels: do not tag comments with the sprint or task that produced the code ("(M3)", "Out of scope for M4"), because they date instantly and mean nothing to a later reader.
- **IMPORTANT. Never use em dashes to join clauses in comments or docs.** The "—" character (U+2014) is hard to read in running prose. Write two plain sentences instead, or use a comma, colon, semicolon, or parentheses, whichever reads most naturally. This applies to code comments, KDoc/Javadoc, Markdown docs, commit messages, and PR descriptions. (Existing prose in older files may still contain them; leave it alone unless you are already editing that comment or were asked to sweep the file.)
- **IMPORTANT. Do not add explaining comments except for non-obvious code.** Default to writing no comment. A comment earns its place only by explaining a non-obvious *why*: a hidden constraint, a subtle invariant, a threading or lifecycle rule, a workaround for a specific bug, or behavior that would surprise a reader. Never restate what a well-named symbol already says, never narrate the happy path line by line, and never write multi-paragraph rationale where one or two sentences do the job. Before adding a new comment, check whether its explanation is needed to understand the code.
- **Preserve useful existing comments.** Keep comment changes within the task's scope. "Default to no comments" governs adding new comments; it is not an instruction to remove existing comments throughout a touched file. Preserve comments that explain non-obvious constraints, invariants, lifecycle requirements, or design decisions. When changing code makes a comment inaccurate, update its useful explanation or remove it if it no longer applies. Do not remove useful context merely to shorten a diff.
- **Use `is` prefixes for Boolean names.** Name new Boolean variables, properties, and parameters with an `is` prefix, such as `isBottom`, `isToolbarAtBottom`, `isSelectionActive`, and `isFinished`. Apply this consistently to production code and tests. Preserve names required by overridden APIs or external contracts. Avoid unrelated renaming across the repository unless requested.
- **A Compose `modifier: Modifier = Modifier` parameter is an exception to any "drop the unused default" cleanup.** Keep it on every composable that emits UI, even when no call site passes one and the default is the only value it ever takes. It is the Compose convention, callers expect to be able to size, pad or position a composable without editing it, and a composable that has to be reopened to accept a modifier is a worse API than one carrying an unused parameter. This applies to the parameter and its default together: leave both in place.
- Mixed Java/Kotlin codebase (Kotlin preferred for new code, many files still Java)
- JVM toolchain 21 across all modules
- No obfuscation in ProGuard (`-dontobfuscate`), only shrinking
- EditorConfig enforces a single alphabetical import layout (`ij_kotlin_imports_layout=*`, no `java.*`/`kotlin.*`/static exceptions) and disables `no-wildcard-imports` for Kotlin
- Preference keys are defined as enum entries in `Prefkey.kt`
- GIDs (globally unique IDs) are used alongside database `_id` for sync-capable entities
- `Ari` encoding is used universally for verse references — never store book/chapter/verse separately
- Version IDs follow format `"internal"`, `"preset/[name]"`, or `"file/[path]"`
- **IMPORTANT. The app has its own in-app language setting (`pref_language`), independent of the device locale.** Only activities pick it up automatically: `BaseActivity.attachBaseContext` runs the context through `ConfigurationWrapper.wrap`, so anything resolving strings against an activity (including Compose `stringResource`, whose `LocalContext` is the activity) is already correct. Everything else is not. A `Service`, a `BroadcastReceiver`, a `Worker`, `App.context`, or any `applicationContext` resolves against the *device* locale, so `getString` there prints the wrong language for every user whose in-app choice differs from their device. When such a component produces user-visible text, resolve it against `ConfigurationWrapper.localizedContext(...)` instead. Cache that context rather than rebuilding it per string, and rebuild when `ConfigurationWrapper.getSerialCounter()` changes, which is how a language change is signalled. Prefer keeping the string resource unresolved (a res id plus args) until it reaches whatever component knows the right context — see `AudioLogMessage`.

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

## Documentation Conventions

- **Do not include SHAs, commit hashes, line numbers, line counts, or library version numbers** in long-lived docs (`docs/tech-debt-remediation.md`, its per-item REM records, this `AGENTS.md`, etc.). They rot on rebase/squash-merge, refactors, and dependency bumps, and are fragile to maintain. Refer to code by symbol name (class/method), and point at `gradle/libs.versions.toml` instead of naming dependency versions. When recording that a step is done, describe what shipped (file paths, test counts, scope) and use an absolute date, never a SHA. (Older entries in these docs may still reference SHAs; leave them alone unless explicitly asked to clean up.)

## Important Caveats

- `IsiActivity.kt` is still large, but no longer monolithic: gesture handling, the action-mode callback, and split-view management were extracted (REM-06/07/08) into `ReaderGestureHandler`, `VerseActionModeController`, and `SplitViewManager` under `widget/`. Bible reading, navigation history, and volume-button handling are still inline; changes here require careful testing.
- `yuku.kpri.model.Song` is the legacy song decoder’s target type. Song storage uses Room with JSON `SongDocument` payloads; legacy Parcelable rows are converted lazily on first read. Preserve stored class-name compatibility. See `docs/modules/songs.md` and `docs/features/portable-songs/design.md`. Backend redirects and `kidung-data` authoring belong to separate repositories.
- The `Snappy` module has native C++ code — NDK must be installed for builds.
- A placeholder `Alkitab/google-services.json` is checked in so `plainDebug` works out of the box; Firebase features won't actually function with it. For production flavors, the real `google-services.json` is sourced from `$ALKITAB_PROPRIETARY_DIR/google-services.json` at build time and copied into the gitignored `Alkitab/src/<flavor>/google-services.json` (where the GMS plugin's source-set lookup finds it).
- The internal Bible version data is split per flavor: the placeholder `ddd_*` files live under `Alkitab/src/plain/assets/internal/` (used by the `plain` open-source build only). Production flavors get their `tb_*`/`kjv_*` files copied from the proprietary overlay into `build/generated/proprietaryAssets/<flavor>/internal/` at build time.
- **Backend host:** `BuildConfig.SERVER_HOST` resolves to `https://api.alkitab.app` (defined in `Alkitab/build.gradle.kts`), **not** the marketing site at `alkitab.app`. The two hostnames are different origins behind separate Cloudflare cache zones — when reproducing a backend issue with `curl`, hit `api.alkitab.app/...` to match what the app actually requests.
