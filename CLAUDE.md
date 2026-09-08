# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Bible for Android** (Alkitab / Quick Bible) — a 100% free, open-source Bible reader app for Android. Published on Google Play as "Alkitab" (Indonesian) and "Quick Bible" (non-Indonesian). The codebase supports multiple product flavors, 100+ downloadable Bible versions, song books, devotions, reading plans, cloud sync, and a daily verse widget.

- Official site: https://alkitab.app
- Developer docs: https://alkitab.app/developer

## Branches

**`develop` is the main integration branch — base all work on `develop` and target every PR at `develop`, never `master`.** The repo follows git-flow: `master` only receives merges from `release/*` branches when a version ships.

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

**Requirements**: JDK 21 (Zulu recommended), Android SDK with compile SDK 36, NDK 28.2.13676358.

The `plain` flavor is the open-source development build and works out of the box with the placeholder `Alkitab/google-services.json` checked into the repo (Firebase features won't function at runtime, but the app builds and runs). Production flavors (`yuku_alkitab`, `yuku_quick_bible`, `sabda_alkitab`) require:
- `$ALKITAB_PROPRIETARY_DIR/overlay/<applicationId>/text_raw/` — proprietary Bible text
- `$ALKITAB_PROPRIETARY_DIR/google-services.json` — real Firebase config (one file with client entries for all production applicationIds)
- Signing-key env vars: `SIGN_KEYSTORE`, `SIGN_ALIAS`, `SIGN_PASSWORD`

With those set, build with a plain `./gradlew assembleYuku_alkitabRelease` (or any other production flavor).

### Building in the Claude Code sandbox (one-time setup)

The Claude Code VM does not ship with the Android SDK — you must provision it yourself before running Gradle, and do not rely on GitHub Actions for verification. Its preinstalled JDK is version 21, matching the `jvmToolchain(21)` used across modules; run `java -version` first, and skip step 1 below if it already reports 21. Prefer `plainDebug` since it needs no proprietary overlay or signing secrets.

Install paths used below (pick any, but keep them consistent):

- JDK 21 (only if not already preinstalled): `/home/user/tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64`
- Android SDK: `/home/user/android-sdk`

**Disk footprint:** expect ~5 GB across all of these combined (less if the preinstalled JDK 21 is used directly) — NDK r28c alone is ~2 GB unpacked, the rest of the SDK is ~1 GB, and `~/.gradle` grows to ~2 GB after the first build. Check free space before starting.

**Environment.** Every step below needs the same env vars. Write them once and source them each time — point `JAVA_HOME` at the preinstalled JDK (`dirname $(dirname $(readlink -f $(which java)))`) unless you installed Zulu JDK 21 yourself in step 1, in which case use that path instead:

```bash
cat > /home/user/tools/android-env.sh <<'EOF'
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
export ANDROID_HOME=/home/user/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
unset JAVA_TOOL_OPTIONS  # strip the sandbox's -Dhttp.proxyHost that would poison Gradle downloads
EOF
```

Then `source /home/user/tools/android-env.sh` at the start of any shell that runs `sdkmanager` or Gradle.

1. **Install Zulu JDK 21** — only needed if `java -version` did not already report 21 (e.g. the sandbox image changed):

   ```bash
   mkdir -p /home/user/tools && cd /home/user/tools
   curl -fsSL -o zulu21.tar.gz \
     https://cdn.azul.com/zulu/bin/zulu21.52.203-ca-jdk21.0.12.1-linux_x64.tar.gz
   echo "068872f682e4157896649c242a13660b33ece808f395095a00fbbd3de8b4bbca  zulu21.tar.gz" | sha256sum -c -
   tar xzf zulu21.tar.gz && rm zulu21.tar.gz
   ```

   Then edit `android-env.sh` to point `JAVA_HOME` at `/home/user/tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64` instead of the `which java` lookup, and re-source it.

2. **Trust the sandbox egress CA in the JDK truststore.** Outbound HTTPS in the Claude Code sandbox goes through an Anthropic TLS-inspection proxy (`sandbox-egress-production TLS Inspection CA`). `curl` trusts it via `/etc/ssl/certs`, but the JDK keeps its own `cacerts`, so `sdkmanager` and Gradle will fail with `PKIX path building failed` until you import the system CAs. This applies to the preinstalled JDK too, not just a freshly downloaded one:

   ```bash
   source /home/user/tools/android-env.sh
   for crt in /usr/local/share/ca-certificates/*.crt; do
     "$JAVA_HOME/bin/keytool" -importcert -noprompt -trustcacerts \
       -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit \
       -alias "$(basename "$crt" .crt)" -file "$crt"
   done
   ```

3. **Install the Android SDK command-line tools**, then use `sdkmanager` to fetch the exact packages Gradle expects (`compileSdk 36`, `build-tools 36.0.0`, `ndk 28.2.13676358` — the NDK is required because the `Snappy` module has JNI C++; note that `sdkmanager` resolves that NDK coordinate to `android-ndk-r28c` on disk, which is expected):

   ```bash
   mkdir -p /home/user/android-sdk/cmdline-tools && cd /home/user/android-sdk/cmdline-tools
   curl -fsSL -o cmdline-tools.zip \
     https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
   echo "7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee  cmdline-tools.zip" | sha256sum -c -
   unzip -q cmdline-tools.zip && mv cmdline-tools latest && rm cmdline-tools.zip

   source /home/user/tools/android-env.sh
   yes | sdkmanager --licenses > /dev/null  # silent; prints thousands of "y\n" lines otherwise
   sdkmanager "platform-tools" "platforms;android-36" \
              "build-tools;36.0.0" "ndk;28.2.13676358"
   ```

4. **Write `local.properties`** so Gradle skips its own SDK discovery:

   ```bash
   echo "sdk.dir=/home/user/android-sdk" > local.properties
   ```

5. **Build.**

   ```bash
   source /home/user/tools/android-env.sh
   ./gradlew assemblePlainDebug
   ```

   Expect ~3 minutes cold (Gradle downloads its pinned wrapper distribution and dependencies into `~/.gradle`). The APK lands at `Alkitab/build/outputs/apk/plain/debug/Alkitab-<code>-<version>-<hash>-yuku.alkitab.debug-dev.apk`.

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
| `AlkitabFeedback` | User feedback module |
| `BiblePlus` | PalmBible+ PDB format reader |
| `KpriModel` | Song/hymn data model (`Song`, `Verse`, `Lyric`) |
| `BintexReader` / `BintexWriter` | Binary serialization format used by YES2 and reading plans |
| `Afw` | Base Android framework (preferences wrapper, adapter base, app context) |
| `Snappy` | JNI Snappy compression (native C++ via NDK) |
| `FlowLayout` | Flow layout widget |
| `ImportedDesktopVerseUtil` | Desktop verse reference finder/parser |

Bible-version downloads run inside `VersionDownloadWorker` (a `CoroutineWorker`) using the shared OkHttp client; the previous `PrDownloaderFixed` module was deleted in REM-19.

### Key Singletons and Entry Points

- **`App.java`** — Application class, extends `yuku.afw.App`. Initializes Firebase, FCM registration, preference defaults, extension receivers. Holds the eagerly-initialized `App.services` (`AppServices` container) introduced by REM-24.
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

With the "Verse (Compose)" experimental setting enabled, `IsiActivity` swaps the
two RecyclerViews for `VersesComposeView`s driven by `VersesComposeControllerImpl`
(a LazyColumn-based implementation of the same `VersesController` interface):
`VerseRendererCompose` renders verse text to an `AnnotatedString`, rows reuse
`VerseItemComposeContent`, and pericope headers are composed natively. Dialogs
(`VersesDialog`, `XrefDialog`) always use the RecyclerView pipeline.

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
- [Google Play Publishing](docs/play-publishing.md) — Manual store uploads via `tools/play/publish.py`, GPP-shaped metadata layout
- [Text Rendering](docs/text-rendering.md) — Verse formatting pipeline and codes
- [Binary Formats](docs/binary-formats.md) — YES2, Bintex, RPB file format specs
- [Storage & Database](docs/storage.md) — SQLite schema, preferences, file storage
- [Backend Communication](docs/backend-communication.md) — API endpoints, download flows
- [Tech Debt & Improvements](docs/tech-debt.md) — Known issues with specific file/line references, potential bugs, deprecated APIs
- [Tech Debt Remediation Plan](docs/tech-debt-remediation.md) — Prioritized remediation tasks with BRICE evaluation, steps, difficulty, and suggested sprint plan

## Code Conventions

- **IMPORTANT. Never write change-narrating comments.** Code comments must describe the code as it is *now*, not the history of how it got there. Do not reference past revisions, removed approaches, or the act of editing: no "previously", "used to", "formerly", "earlier iterations", "an earlier version", "we changed/renamed/moved this from…", "now uses…", "no longer…", and the like. Git history is the record of *change*; the code and its comments describe the *present*. When explaining why the current design was chosen over an alternative is genuinely useful, state it as present-tense rationale about the alternative ("the cluster is centered with Spacers because a weighted slot would clip the label"), not as a story about what the code used to do. This applies to all comments, KDoc/Javadoc, and commit-adjacent code. Keep the narrative of change out of the source. The same rule applies to milestone labels: do not tag comments with the sprint or task that produced the code ("(M3)", "Out of scope for M4"), because they date instantly and mean nothing to a later reader.
- **IMPORTANT. Never use em dashes to join clauses in comments or docs.** The "—" character (U+2014) is hard to read in running prose. Write two plain sentences instead, or use a comma, colon, semicolon, or parentheses, whichever reads most naturally. This applies to code comments, KDoc/Javadoc, Markdown docs, commit messages, and PR descriptions. (Existing prose in older files may still contain them; leave it alone unless you are already editing that comment or were asked to sweep the file.)
- **Do not over-comment.** Default to writing no comment. A comment earns its place only by explaining a non-obvious *why*: a hidden constraint, a subtle invariant, a threading or lifecycle rule, a workaround for a specific bug, or behavior that would surprise a reader. Never restate what a well-named symbol already says, never narrate the happy path line by line, and never write multi-paragraph rationale where one or two sentences do the job. If deleting a comment would not confuse a future reader, delete it.
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

- **Do not include SHAs, commit hashes, line numbers, line counts, or library version numbers** in long-lived docs (`docs/tech-debt.md`, `docs/tech-debt-remediation.md`, this `CLAUDE.md`, etc.). They rot on rebase/squash-merge, refactors, and dependency bumps, and are fragile to maintain. Refer to code by symbol name (class/method), and point at `gradle/libs.versions.toml` instead of naming dependency versions. When recording that a step is done, describe what shipped (file paths, test counts, scope) and use an absolute date — never a SHA. (Older entries in these docs may still reference SHAs; leave them alone unless explicitly asked to clean up.)

## Important Caveats

- `IsiActivity.kt` is still large, but no longer monolithic: gesture handling, the action-mode callback, and split-view management were extracted (REM-06/07/08) into `ReaderGestureHandler`, `VerseActionModeController`, and `SplitViewManager` under `widget/`. Bible reading, navigation history, and volume-button handling are still inline; changes here require careful testing.
- `KpriModel.Song` (`Parcelable`, acknowledged as a bad design decision in the code) is retained only as the on-device legacy decoder's target type. REM-32 migrated the storage *engine* to Room; REM-21 (app-side done 2026-07-13) swapped the payload to JSON (`yuku.alkitab.songs.newdoc.SongDocument`) on top of that, with legacy Parcelable rows converted lazily on first read — see `docs/modules/songs.md` and `docs/features/portable-songs/design.md`. Backend redirect branching and `kidung-data` authoring support are separate repos and out of scope for this app.
- The `Snappy` module has native C++ code — NDK must be installed for builds.
- A placeholder `Alkitab/google-services.json` is checked in so `plainDebug` works out of the box; Firebase features won't actually function with it. For production flavors, the real `google-services.json` is sourced from `$ALKITAB_PROPRIETARY_DIR/google-services.json` at build time and copied into the gitignored `Alkitab/src/<flavor>/google-services.json` (where the GMS plugin's source-set lookup finds it).
- The internal Bible version data is split per flavor: the placeholder `ddd_*` files live under `Alkitab/src/plain/assets/internal/` (used by the `plain` open-source build only). Production flavors get their `tb_*`/`kjv_*` files copied from the proprietary overlay into `build/generated/proprietaryAssets/<flavor>/internal/` at build time.
- **Backend host:** `BuildConfig.SERVER_HOST` resolves to `https://api.alkitab.app` (defined in `Alkitab/build.gradle.kts`), **not** the marketing site at `alkitab.app`. The two hostnames are different origins behind separate Cloudflare cache zones — when reproducing a backend issue with `curl`, hit `api.alkitab.app/...` to match what the app actually requests.
