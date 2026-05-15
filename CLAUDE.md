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

### Building in the Claude Code sandbox (one-time setup)

The Claude Code VM does not ship with a compatible JDK or the Android SDK. You must provision them yourself before running Gradle — do not rely on GitHub Actions for verification. Prefer `plainDebug` since it needs no proprietary overlay or signing secrets.

Install paths used below (pick any, but keep them consistent):

- JDK 17: `/home/user/tools/zulu17.64.17-ca-jdk17.0.18-linux_x64`
- Android SDK: `/home/user/android-sdk`

**Disk footprint:** expect ~6 GB across all of these combined — NDK r28c alone is ~2 GB unpacked, the rest of the SDK is ~1 GB, and `~/.gradle` grows to ~2 GB after the first build. Check free space before starting.

**Environment.** Every step after the JDK install needs the same env vars. Write them once and source them each time:

```bash
cat > /home/user/tools/android-env.sh <<'EOF'
export JAVA_HOME=/home/user/tools/zulu17.64.17-ca-jdk17.0.18-linux_x64
export ANDROID_HOME=/home/user/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
unset JAVA_TOOL_OPTIONS  # strip the sandbox's -Dhttp.proxyHost that would poison Gradle downloads
EOF
```

Then `source /home/user/tools/android-env.sh` at the start of any shell that runs `sdkmanager` or Gradle.

1. **Install Zulu JDK 17** (the preinstalled JDK is 21, which the Android Gradle Plugin rejects for the `jvmToolchain(17)` used across modules):

   ```bash
   mkdir -p /home/user/tools && cd /home/user/tools
   curl -fsSL -o zulu17.tar.gz \
     https://cdn.azul.com/zulu/bin/zulu17.64.17-ca-jdk17.0.18-linux_x64.tar.gz
   echo "819e3f09ea628901a21b2104ed8f5256e17ae91a4145b272b2eb2131f832af1d  zulu17.tar.gz" | sha256sum -c -
   tar xzf zulu17.tar.gz && rm zulu17.tar.gz
   ```

2. **Trust the sandbox egress CA in the JDK truststore.** Outbound HTTPS in the Claude Code sandbox goes through an Anthropic TLS-inspection proxy (`sandbox-egress-production TLS Inspection CA`). `curl` trusts it via `/etc/ssl/certs`, but the JDK keeps its own `cacerts`, so `sdkmanager` and Gradle will fail with `PKIX path building failed` until you import the system CAs:

   ```bash
   JAVA_HOME=/home/user/tools/zulu17.64.17-ca-jdk17.0.18-linux_x64
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

   Expect ~3 minutes cold (Gradle downloads the 9.0.0 distribution and dependencies into `~/.gradle`). The APK lands at `Alkitab/build/outputs/apk/plain/debug/Alkitab-<code>-<version>-<hash>-yuku.alkitab.debug-dev.apk`.

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
- **`S.kt`** — Legacy service locator (~317 lines). Holds lazy references to `InternalDb`, `SongDb`, active Bible version state, and calculated UI dimensions. New code should depend on the narrower `StorageProvider` / `VersionManager` / `UiDimensionsProvider` interfaces via `App.services.*`; existing `S.foo` call sites are being migrated incrementally (REM-24).
- **`IsiActivity.kt`** (~2170 lines, at `yuku/alkitab/base/IsiActivity.kt`) — Main Bible reader activity. Verse display, navigation history, and volume-button navigation still live here; gesture handling, the action-mode callback, and split-view management have been extracted into `ReaderGestureHandler` (REM-06), `VerseActionModeController` (REM-07), and `SplitViewManager` (REM-08) respectively.

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

The app uses two SQLite files:

- **`AlkitabRoomDb`** — Room database (`yuku.alkitab.base.storage.room.AppDatabase`, currently at `@Database(version = 7)`). Holds every InternalDb table after the REM-10/REM-11/REM-27/REM-28/REM-29/REM-30/REM-31 plan:
  - **version** (REM-11) — metadata for downloaded Bible versions (filename, locale, active flag, ordering).
  - **marker** (REM-10) — bookmarks, notes, highlights (distinguished by `kind` column). Each row has a `gid` (globally unique ID) for sync.
  - **label** (REM-10) — bookmark categories with custom background colors.
  - **marker_label** (REM-10) — many-to-many junction between markers and labels.
  - **devotion** (REM-27) — cached devotional articles keyed by `(name, date, dataFormatVersion)`.
  - **per_version** (REM-28) — per-version settings keyed uniquely by `versionId`.
  - **progress_mark** / **progress_mark_history** (REM-29) — 5 reading progress pins (addressed by `preset_id`) plus the append-only history of every pin update.
  - **reading_plan** / **reading_plan_progress** (REM-30) — downloaded reading plans (metadata + RPB binary blob) and per-day completion rows keyed by `(reading_plan_progress_gid, reading_code)`.
  - **sync_shadow** / **sync_log** (REM-31) — sync state tracking (last-synced entity snapshot per sync set) plus an append-only audit log of every sync event. `sync_shadow.data` BLOBs routinely exceed the 2 MB CursorWindow, so `SyncShadowDao.getBySyncSetName` reads them in 1 MB chunks via raw `substr()` against `roomDb.openHelper.readableDatabase` (Room can't express chunked cursors natively).
- **`AlkitabDb`** — legacy hand-rolled `SQLiteOpenHelper` (`InternalDbHelper`). After REM-31, every table here is a rollback safety net only — no production code writes to it.

The legacy `Marker` / `Label` / `Marker_Label` / `Version` / `Devotion` / `PerVersion` / `ProgressMark` / `ProgressMarkHistory` / `ReadingPlan` / `ReadingPlanProgress` / `SyncShadow` / `SyncLog` tables are still created in `AlkitabDb` as a rollback safety net; a one-time copy in `MarkerDataMigration` / `VersionDataMigration` / `DevotionDataMigration` / `PerVersionDataMigration` / `ProgressMarkDataMigration` / `ReadingPlanDataMigration` / `SyncShadowDataMigration` (wired from `S.db`'s lazy initializer) moves their rows into Room on first launch with the migrated code. `InternalDb` plus the per-table facades (`MarkerDao`, `LabelDao`, `Marker_LabelDao`, `VersionDao`, `DevotionDao`, `PerVersionDao`, `ProgressMarkDao`, `ReadingPlanDao`, `SyncShadowDao`) preserve the public surface so callers don't change; all of them now route through Room.

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

## Documentation Conventions

- **Do not include SHAs or commit hashes** in long-lived docs (`docs/tech-debt.md`, `docs/tech-debt-remediation.md`, this `CLAUDE.md`, etc.). They rot on rebase/squash-merge and are fragile to maintain. When recording that a step is done, describe what shipped (file paths, test counts, scope) and use an absolute date — never a SHA. (Older entries in these docs may still reference SHAs; leave them alone unless explicitly asked to clean up.)

## Important Caveats

- `IsiActivity.kt` is ~2170 lines — still large, but no longer monolithic: gesture handling, the action-mode callback, and split-view management were extracted (REM-06/07/08) into `ReaderGestureHandler`, `VerseActionModeController`, and `SplitViewManager` under `widget/`. Bible reading, navigation history, and volume-button handling are still inline; changes here require careful testing.
- `KpriModel.Song` uses `Parcelable` serialization for database storage (acknowledged as a bad design decision in the code).
- The `Snappy` module has native C++ code — NDK must be installed for builds.
- A placeholder `Alkitab/google-services.json` is checked in so `plainDebug` works out of the box; Firebase features won't actually function with it. For production flavors, the real `google-services.json` is sourced from `$ALKITAB_PROPRIETARY_DIR/google-services.json` at build time and copied into the gitignored `Alkitab/src/<flavor>/google-services.json` (where the GMS plugin's source-set lookup finds it).
- The internal Bible version data is split per flavor: the placeholder `ddd_*` files live under `Alkitab/src/plain/assets/internal/` (used by the `plain` open-source build only). Production flavors get their `tb_*`/`kjv_*` files copied from the proprietary overlay into `build/generated/proprietaryAssets/<flavor>/internal/` at build time.
- **Backend host:** `BuildConfig.SERVER_HOST` resolves to `https://api.alkitab.app` (defined in `Alkitab/build.gradle.kts`), **not** the marketing site at `alkitab.app`. The two hostnames are different origins behind separate Cloudflare cache zones — when reproducing a backend issue with `curl`, hit `api.alkitab.app/...` to match what the app actually requests.
