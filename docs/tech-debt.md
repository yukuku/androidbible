# Tech Debt, Improvements & Critiques

## TD-01: IsiActivity God Class (~2170 lines)

**File:** `Alkitab/src/main/java/yuku/alkitab/base/IsiActivity.kt`

The main Bible reader activity, down from 2897 lines after a series of extractions: REM-07 (action mode → `VerseActionModeController`), REM-06 (gestures → `ReaderGestureHandler`), and REM-08 (split view → `SplitViewManager`). The remaining ~2170 lines still contain mixed concerns. Specific clusters that violate single-responsibility:

- **~~Gesture handling~~** ✅ **Extracted (REM-06):** The three gesture lambdas — `splitRoot_listener` (`TwofingerLinearLayout.Listener` for pinch zoom + one/two-finger swipes), `bGoto_floaterDrag` (`GotoButton.FloaterDragListener`), and `floater_listener` (`Floater.Listener`) — have been moved to `ReaderGestureHandler.kt` behind two interfaces (`ReaderGestureHost`, `ReaderGestureActions`). The activity now wires a single `gestureHandler` lazy field into all three setListener call sites. Gesture-local state (`startFontSize`, `startDx`, `moreSwipeYAllowed`, `chapterSwipeCellWidth`, `floaterLocationOnScreen`) lives on the handler instead of in inline objects.
- **~~Action mode~~** ✅ **Extracted (REM-07):** The ~500-line `actionMode_callback` object has been moved to `VerseActionModeController.kt` behind two interfaces (`VerseActionModeHost`, `VerseActionModeActions`). Pure text-building logic is in `VerseTextFormatter` (no Android deps). `RibkaEligibility` is a top-level file. 26 unit tests added.
- **~~Broadcast receivers~~** ✅ **Replaced (REM-03):** The two inline `BroadcastReceiver` registrations for verse-attribute and version changes are gone — `IsiActivity` now collects from the `AppEvents` `SharedFlow` buses via `lifecycleScope.launch { ... }`. `LocalBroadcastManager` is removed from the dependencies entirely.
- **Verse selection listeners:** Two `SelectedVersesListener` implementations (`lsSplit0_selectedVerses`, `lsSplit1_selectedVerses`) with partially duplicated logic — still inline.
- **~~Split view management~~** ✅ **Extracted (REM-08):** `openSplitDisplay()`, `closeSplitDisplay()`, `displaySplitFollowingMaster()`, `loadSplitVersion()`, and the three split-handle / global-layout listeners have moved to `SplitViewManager.kt` behind `SplitViewHost` / `SplitViewActions`. `IsiActivity.activeSplit1` is now a read-only getter that delegates to the manager.
- **Navigation history:** `BackForwardListController` usage, `jumpToAri()`, `jumpTo(reference)`, `History` tracking.

**Impact:** Any change to one concern risks breaking unrelated functionality. Testing individual behaviors requires instantiating the entire Activity. New developers face a ~2170-line class with no clear entry point.

---

## TD-02: InternalDb — Raw SQL & Manual Statement Caching (~757 lines, was 1771)

**File:** `Alkitab/src/main/java/yuku/alkitab/base/storage/InternalDb.java`

**Status:** Mostly resolved — every InternalDb table has been migrated to Room.

1. Per-table DAOs (`MarkerDao`, `LabelDao`, `VersionDao`, `DevotionDao`, `ReadingPlanDao`, `ProgressMarkDao`, `PerVersionDao`, `Marker_LabelDao`, `SyncShadowDao`) and `SyncApplier` were extracted in `d805265e`, shrinking `InternalDb.java` from 1771 → 830 lines. Further null-safety cleanup landed in `70c97818`.
2. ✅ **REM-11 / REM-10 / REM-27 / REM-28 / REM-29 / REM-30 / REM-31** — every legacy hand-rolled SQLite table (`Version`, `Marker` / `Label` / `Marker_Label`, `Devotion`, `PerVersion`, `ProgressMark` / `ProgressMarkHistory`, `ReadingPlan` / `ReadingPlanProgress`, `SyncShadow` / `SyncLog`) now lives in Room's `AlkitabRoomDb` (currently `@Database(version = 7)`). The per-table facades (`MarkerDao`, `LabelDao`, …, `SyncShadowDao`) all delegate to generated Room DAOs. Legacy tables remain in `AlkitabDb` purely as a rollback safety net.

The remaining issues below are now scoped to the SyncShadow chunked-blob workaround and a couple of `execSQL` patches in `InternalDb.java` that span entities that don't fit cleanly into a single Room DAO.

### Raw SQL string concatenation (~10 instances remain)
Examples at `InternalDb.java:134, 137, 180, 593–597, 632–646`:
```java
c = db.rawQuery("select " + Db.TABLE_Marker + ".* from " + Db.TABLE_Marker +
  " where " + Db.TABLE_Marker + "." + Db.Marker.kind + "=? and " +
  Db.TABLE_Marker + "." + Db.Marker.gid + " not in (select distinct " +
  Db.Marker_Label.marker_gid + " from " + Db.TABLE_Marker_Label + ")", ...);
```
These are unreadable, fragile, and impossible to verify at compile time.

### ~~Manual compiled statement caching~~ ✅ FIXED
The problematic `private SQLiteStatement stmt_countMarkersForBookChapter` field and null-check pattern have been removed. `MarkerDao.countForAriRange` now uses `compileStatement(...).use { }` so the statement is closed deterministically. `LabelDao.getMaxOrdering` uses the same pattern. No cross-request caching, no invalidation concerns.

### 2MB CursorWindow workaround (SyncShadowDao.kt, post-REM-31)
Still present, now inside the Room-backed facade `SyncShadowDao.getBySyncSetName` rather than `InternalDb.java`. The facade runs raw `substr()` queries against `roomDb.openHelper.readableDatabase` because Room can't express chunked cursors natively:
```kotlin
"SELECT substr(data, ${i + 1}, $chunkSize) FROM sync_shadow WHERE _id = ?"
```
Hard-coded chunk size `1_000_000` to work around the undocumented 2MB CursorWindow limit. This is fragile and breaks if the system limit changes.

### Duplicated reordering SQL (InternalDb.java:593–646)
Two near-identical `execSQL()` pairs to reorder labels and versions with `+1`/`-1` arithmetic — should be a single parameterized method parameterized by table name.

### ~~TODO comment (line 234)~~ ✅ GONE
The `TODO this is only called together with putAttributes(), make it private` comment was removed during the DAO refactor.

### ~~Verse-255 boundary bug (lines 238, 262)~~ ✅ FIXED
**Fixed in REM-18c** (`ccf0a320`, 2026-04-16). `countMarkersForBookChapter` and `putAttributes` used exclusive upper bound (`ari < ariMax`) when `ariMax = ari_bookchapter | 0xff`, silently dropping any marker on verse 255. Changed to inclusive (`ari <= ariMax`) to match `getHighlightColorRgb`. No real data was affected (no Bible chapter has 255 verses), but the boundary is now correct and locked down by two regression tests.

---

## TD-03: Deprecated API Usage

### ~~LocalBroadcastManager~~ ✅ removed in REM-03
All `App.getLbm().sendBroadcast` / `registerReceiver` usages migrated to Kotlin `SharedFlow`-based buses in `yuku.alkitab.base.events.AppEvents`. The `androidx.localbroadcastmanager` dependency and `App.getLbm()` helper are gone.

### Handler(Looper.getMainLooper()) for thread switching
- `VerseRenderer.kt` `reportInvalidSpecialTag` — creates Handler in object scope to show Toast
- `Foreground.java:8` — lifecycle tracking
- `DownloadService.java:42-70` — progress callbacks

Should use `Dispatchers.Main` with coroutines or `lifecycleScope`.

### Static Toast caching (VerseRenderer.kt `invalidSpecialTagToast`)
```kotlin
private var invalidSpecialTagToast: Toast? = null
```
Object-scope UI reference can leak Activity context. Should create Toast inline or use `Snackbar`.

---

## TD-04: ~~Unsafe Deserialization in Songs~~ PARTIALLY FIXED

**File:** `Alkitab/src/main/java/yuku/alkitab/songs/SongBookUtil.java`

**Fixed in REM-01** (`1b9b74d9`, 2026-04-11):
- ✅ **Security risk:** Now uses `SafeObjectInputStream` with a class whitelist — only `java.util.*`, `java.lang.*`, and Song model classes are allowed. Also adds `instanceof` check before casting.
- ✅ **Resource leak:** Response and streams now wrapped in try-with-resources.
- ✅ **No size limits:** Response body size validation added (rejects >50MB).

**Remaining:**
- **Fragility:** `KpriModel.Song` still uses `Parcelable` as its serialization/storage format (acknowledged as "Bad decision"). Migrating to JSON is tracked as REM-21.
- **Long-term:** Migrate song download format from Java serialization to JSON (REM-01 step 4, not yet started).

---

## TD-06: Threading & Concurrency Issues

### ~~DevotionDownloader infinite loop~~ ✅ FIXED (REM-05, `758793f5`)
`DevotionDownloader` now uses `Executors.newSingleThreadExecutor()` with a `LinkedBlockingDeque.take()` loop, a `volatile boolean shutdown_` flag, and a `shutdown()` method calling `executor_.shutdownNow()`. The hardcoded `SystemClock.sleep(50)` is gone; `InterruptedException` is handled by re-interrupting and breaking. Consider migrating to `WorkManager` as a future enhancement, but the original issues (no shutdown, infinite loop, hardcoded sleep) are resolved.

### Sync lock complexity (Sync.java lines 195–267)
Multiple `synchronized` blocks on different objects (`syncSetNameQueue`, `syncUpdatesOngoingCounters`) with manual `queue_.notify()`. Risk of deadlock or race conditions with nested synchronization.

### No coroutines anywhere
The entire codebase uses raw `Thread`, `Handler`, and `SystemClock.sleep()` for async operations. Kotlin coroutines would provide structured concurrency, cancellation support, and testability.

---

## TD-07: No ViewModel / Architecture Components

Activities directly hold all state as fields. `IsiActivity` maintains:
- Active Bible versions (`activeSplit0`, `activeSplit1`)
- Selected verses and selection mode
- Navigation history
- Current chapter data
- UI dimensions and appearance settings

On configuration change (rotation), this state is partially lost. The `onSaveInstanceState/onRestoreInstanceState` approach is incomplete — not all state is serializable.

---

## TD-08: Sync Protocol Hardcoded Endpoints

**File:** `Alkitab/src/main/java/yuku/alkitab/base/sync/Sync.java`

Five API endpoints hardcoded as string concatenations across the file:
- Line 309: `/sync/api/register_gcm_client`
- Line 391: `/sync/api/create_own_user`
- Line 424: `/sync/api/login_own_user`
- Line 454: `/sync/api/forgot_password`
- Line 484: `/sync/api/change_password`

Additional endpoints in `SyncAdapter.java:280, 381, 474, 569`. All should be centralized in an API constants file or a Retrofit interface.

Error handling is uniform — all failures produce `NotOkException` with no distinction between network errors, validation errors, or server errors. No retry logic or exponential backoff for transient failures.

---

## TD-09: Preferences Architecture

**File:** `Afw/src/main/java/yuku/afw/storage/Preferences.java`

### ~~Manual hold/unhold transaction~~ FIXED
**Fixed in REM-02** (`0b61084a`–`80cd9f34`, 2026-04-10/11):
- ✅ `hold()`/`unhold()` are now **private** — external code uses `withTransaction(Runnable)` which guarantees `unhold()` via try/finally
- ✅ All 6 call sites migrated; two previously unsafe call sites (`SyncSettingsActivity`, `SecretSyncDebugActivity`) were fixed

### Manual cache with dirty flag (lines 16–24)
- `dirty` flag requires manual `invalidate()` calls from external code

### 100+ preference keys as enum (Prefkey.kt)
Preference keys are a flat enum with no grouping or type safety. Each access requires an explicit type cast (`getString`, `getInt`, `getBoolean`). DataStore with typed proto schema would be safer.

---

## TD-10: VerseRenderer Complexity

**File:** `Alkitab/src/main/java/yuku/alkitab/base/widget/VerseRenderer.kt`

### ~~200-line render method~~ ✅ RESOLVED (REM-25)
The monolithic `render()` body has been decomposed into `renderVerseNumber()`, `processFormattingCodes()`, `applyHighlight()`, and `bindToTextViews()`, alongside the existing `applyParaStyle()` and `processSpecialTag()`. Behavior is locked down by 39 characterization tests in `VerseRendererTest.kt`.

### ~~Undocumented Unicode constants~~ ✅ RESOLVED (REM-26)
The `superscriptDigits` array now has an inline comment naming the Unicode code points (U+2070, U+00B9, U+00B2, U+00B3, U+2074..U+2079) and explaining its use in `appendSuperscriptNumber`. The `XREF_MARK` constant is similarly documented as U+203B REFERENCE MARK.

---

## TD-11: Mixed Java/Kotlin

Core files still in Java:
- `InternalDb.java` (~757 lines; partially superseded for the marker / version tables by Room facade DAOs — see TD-02)
- ~~`SearchEngine.java`~~ ✅ ported to `SearchEngine.kt` (REM-16, 2026-05-13)
- ~~`VerseRenderer.java`~~ ✅ ported to Kotlin (REM-26)
- `Sync.java` (~571 lines)
- `SyncAdapter.java` (~630 lines)
- ~~`DevotionDownloader.java`~~ ✅ ported to `DevotionDownloader.kt` (REM-16, 2026-05-13)
- ~~`Provider.java`~~ ✅ ported to `Provider.kt` (REM-16, 2026-05-13)
- ~~`Highlights.java`~~ ✅ ported to `Highlights.kt` (REM-16, 2026-05-12)
- ~~`Jumper.java`~~ ✅ ported to `Jumper.kt` (REM-16, 2026-05-12)
- ~~`TargetDecoder.java`~~ ✅ ported to `TargetDecoder.kt` (REM-16, 2026-05-13)
- ~~`QueryTokenizer.java`~~ ✅ ported to `QueryTokenizer.kt` (REM-16, 2026-05-13)
- ~~`SongBookUtil.java`~~ ✅ ported to `SongBookUtil.kt` (REM-16, 2026-05-13)
- All devotion article parsers

Newer files (activities, data classes) are Kotlin, creating a mixed codebase where Java code can't use Kotlin features (extension functions, coroutines, sealed classes, null safety).

---

## TD-12: Deprecated / Unmaintained Dependencies

| Dependency | Version | Issue |
|------------|---------|-------|
| ~~`material-dialogs`~~ | ~~3.3.0~~ | ✅ **Fixed in REM-14** (`ca9a913`). All call sites migrated to `MaterialAlertDialogBuilder` (Material 3); the `com.afollestad.materialdialogs` artifacts have been removed from `Alkitab/build.gradle`. `MaterialDialogJavaHelper` / `MaterialDialogAdapterHelper` are now thin wrappers around `MaterialAlertDialogBuilder`. |
| `FancyShowCaseView` | 1.4.0 | Low maintenance activity. Evaluate alternatives. |
| ~~`PRDownloader` (patched)~~ | ~~custom~~ | ✅ **Fixed in REM-19** (2026-05-13). Bible-version downloads were migrated to `VersionDownloadWorker : CoroutineWorker` (OkHttp + Range-based resume), observed by `DownloadMapper` via `WorkManager.getWorkInfoByIdFlow(uuid)`. `PRDownloader.initialize(...)` removed from `App.java`, the `PRDownloaderOkHttpClient` adapter deleted, `:PrDownloaderFixed` dropped from `settings.gradle.kts` / `Alkitab/build.gradle.kts`, and the entire `PrDownloaderFixed/` directory deleted. |
| ~~`AmbilWarna`~~ | ~~bundled~~ | ✅ **Fixed in REM-20** (2026-05-12). The two `AmbilWarnaDialog` call sites (`MarkersActivity`, `TypeHighlightDialog`) and the 9 `AmbilWarnaPreference` widget entries (`color_settings.xml`, `color_settings_night.xml`, `settings_display.xml`) were migrated to a Compose `ModalBottomSheet`-hosted picker (`IosColorPicker` + `ColorPickerDialog`) and a new `ColorPreference` subclass. The `AmbilWarna` Gradle module has been deleted entirely (removed from `settings.gradle.kts` and `Alkitab/build.gradle.kts`; `AmbilWarna/` directory removed). |
| ~~`LocalBroadcastManager`~~ | — | Removed in REM-03; replaced by `AppEvents` `SharedFlow` buses. |
| ~~`androidx.percentlayout`~~ | ~~1.0.0~~ | ✅ **Fixed in REM-22 follow-up** (2026-05-07). The dep was orphaned by the GotoActivity Compose port (PR #160) once the last consumer (`fragment_goto_dialer.xml`) was deleted. Removed the `implementation(libs.androidx.percentlayout)` line from `Alkitab/build.gradle.kts` along with the version key and library entry in `gradle/libs.versions.toml`. Same cleanup also dropped the now-unused `KeypadButton` style and `keypad_text_color` color resource (former dialer-only assets). |

---

## TD-13: Minimal Test Coverage

23 test files now exist across the project (Alkitab module unit tests unless noted):

**Added in recent sprints:**
- `HighlightsTest.kt` — highlight encode/decode, alphaMix, partial highlights (REM-18a)
- `SyncDeltaTest.kt`, `Sync_MabelTest.kt`, `Sync_PinsTest.kt`, `Sync_RpTest.kt` — sync delta application and entity equality (REM-18b)
- `InternalDbTest.kt` — marker CRUD, label ordering, highlight storage, attribute loading via Robolectric (REM-18c)
- `SearchEngineTest.kt` — `ReadyTokens`, `satisfiesTokens`, and `searchByGrep` end-to-end via Robolectric (REM-18d)
- `ProviderTest.kt` — content provider single/range ARI & LID queries, version listing, MIME type contract, URI mismatch handling via Robolectric (REM-18f)
- `VerseTextFormatterTest.kt` — pure text-formatting logic extracted from action mode (REM-07)
- `VerseActionModeControllerTest.kt` — menu visibility rules, click routing, split-1 share URL metadata (REM-07)
- `SongBookUtilTest.java` — song deserialization safety (REM-01)

**Pre-existing:**
- `FormattedTextRendererTest.java` — verse formatting codes
- `QueryTokenizerTest.kt` — search tokenization
- `TargetDecoderTest.java` — verse reference parsing
- `JumperTest.java` — verse navigation
- `RemoveSpecialCodesTest.java` — formatting code stripping
- `JsonFileExportTest.kt` — data transfer export
- `VersionTest.java`, `GetVersionInitialsTest.java` — version model
- `DesktopVerseFinderTest.java`, `DesktopVerseParserTest.java` — desktop verse finder/parser (in tools/AlkitabConverter)
- `LauncherTest.java`, `VerseProviderTest.java` — integration tests (in AlkitabIntegration, androidTest)

**Still not tested:** Version loading (YES2 reader/writer round-trip), devotion downloading, song management, widget logic.

---

## ~~TD-14: ybuild.sh — Custom Shell Script for Production Builds~~ ✅ FIXED

**Fixed in REM-23** (2026-04-16). `ybuild.sh` has been deleted; production builds are now pure Gradle.

- ✅ **Proprietary asset injection:** A typed `CopyProprietaryAssetsTask` per production flavor copies `$ALKITAB_PROPRIETARY_DIR/overlay/<applicationId>/text_raw/*` into `Alkitab/build/generated/proprietaryAssets/<flavor>/internal/`, wired into AGP via `androidComponents.onVariants { ... addGeneratedSourceDirectory(...) }` so all consumers (mergeAssets, lint vital, etc.) automatically depend on it. The placeholder `ddd_*` files have moved to `Alkitab/src/plain/assets/internal/` so production builds never inherit them.
- ✅ **Git commit hash stamping:** Read at config time and exposed as `BuildConfig.LAST_COMMIT_HASH`. The `R.string.last_commit_hash` resource has been removed; `AboutActivity` and `InstallationUtil` now read the BuildConfig field directly.
- ✅ **Custom APK naming:** Implemented in the existing `applicationVariants.all` block — output is `Alkitab-{versionCode}-{versionName}-{commitHash}-{applicationId}-{BUILD_DIST}.apk`, mirroring the legacy scheme. `BUILD_DIST` defaults to `dev`.
- ✅ **RAM disk dependency:** Removed; not needed.
- ✅ **Linux CI compatibility:** Production builds now run via `./gradlew assemble<Flavor>Release` with no shell-script wrapper, so they work on any platform Gradle supports.

The new release build command:

```bash
ALKITAB_PROPRIETARY_DIR=/path/to/proprietary \
SIGN_KEYSTORE=/path/to/keystore \
SIGN_ALIAS=mykey \
SIGN_PASSWORD=secret \
BUILD_DIST=market \
./gradlew assembleYuku_alkitabRelease
```

---

## TD-15: S.kt — God Object Service Locator

**File:** `Alkitab/src/main/java/yuku/alkitab/base/S.kt` (~317 lines)

**Status:** Substantially improved by REM-24 (steps 24a–24d complete as of 2026-05-12). Three interfaces — `StorageProvider`, `VersionManager`, `UiDimensionsProvider` — have been extracted under `yuku.alkitab.base.services`, bundled into an `AppServices` container, and exposed via `App.services`. ~216 direct `S.xxx` references across 44 files have been migrated to `App.services.*`. The legacy `S.foo` accessors and the `S.storage` / `S.versions` / `S.uiDimensions` adapter properties are retained so existing callers compile; new code should depend on `App.services`. Active-version state was collapsed into a single `@Volatile var state: ActiveVersionState` data-class reference so all readers see a consistent `(mVersion, version, versionId)` triple (24c). `openVersionsDialog` / `openVersionsDialogWithNone` moved off `S` into `VersionDialogHelper` (24b).

A Kotlin `object` singleton that historically served as the central service locator for the entire app, mixing three unrelated concerns:

### 1. Database access
- `S.db` — lazy `InternalDb` instance (markers, labels, bookmarks, reading plans, sync, devotions)
- `S.songDb` — lazy `SongDb` instance (song books)

### 2. Active Bible version state
- `S.activeVersion()` / `S.activeMVersion()` / `S.activeVersionId()` — mutable state for the currently selected Bible version
- `S.setActiveVersion(mv)` — `@Synchronized` setter, but getters are NOT synchronized (potential torn reads)
- `S.getVersionFromVersionId()` — queries `InternalDb` to look up versions
- `S.getAvailableVersions()` — combines internal + database versions
- `S.getMVersionInternal()` — creates internal version from `AppConfig` + `Preferences`
- `S.openVersionsDialog()` / `S.openVersionsDialogWithNone()` — UI dialogs (shouldn't be in a service locator)

### 3. UI dimensions and styling
- `S.applied()` — returns `CalculatedDimensions` (font size, colors, spacing, brightness)
- `S.recalculateAppliedValuesBasedOnPreferences()` — recomputes from `Preferences` + `Resources`
- `CalculatedDimensions` class (lines 31-95) — 18 mutable fields covering fonts, colors, indentation, and spacing

### Coupling issues
- **50 files** import `S`, with **161+ call sites**
- Requires `App.context` to be set before any access (implicit initialization order dependency)
- `ActiveVersionHolder.init` reads `Preferences` at object creation time — happens before any setup code
- `CalculatedDimensions` is replaced atomically via `recalculateAppliedValuesBasedOnPreferences()`, but no observer mechanism notifies consumers of changes — callers must re-read `S.applied()` manually
- `setActiveVersion()` is synchronized but `activeVersion()` / `activeMVersion()` are not — race condition risk

### Impact on testing
- `S` is an `object` singleton — cannot be mocked without bytecode manipulation
- No interfaces to substitute test doubles
- Any code using `S.db` requires a real SQLite database (`InternalDbHelper`)
- Any code using `S.applied()` requires `Preferences`, `Resources`, and `FontManager`
- All sync modules, activities, dialogs, and content providers are tightly coupled to `S`

---

## Potential Bugs

### PB-01: Soft Reference Cache Thrashing
`MVersionDb` caches `VersionImpl` with `SoftReference` in a `ConcurrentHashMap`. Under memory pressure, all cached versions are GC'd simultaneously, causing a burst of file I/O as they're reloaded. No monitoring, no LRU eviction strategy.

### ~~PB-02: Highlight Hash Invalidation~~ ❎ DISMISSED
`Highlights.java` stores a hash of the verse text alongside partial-highlight offsets. On hash mismatch (verse text changed), `VerseRenderer` falls back to a full-verse highlight rather than dropping or mis-applying the span (VerseRenderer.java:236-248, 394-409) — so the degradation is graceful: the user still sees the verse highlighted, just at verse granularity instead of character range. Since published translation revisions are rare, this is acceptable behavior and not worth the complexity of fuzzy re-anchoring.

### PB-03: Concurrent Sync Data Loss for Highlights and Progress Pins
The client-side patch logic (`SyncAdapter.patchNoConflict`, SyncAdapter.java:70-109) is last-write-wins for every Mabel entity and for progress pins — so concurrent edits to a highlight color or a progress-pin position on two devices silently discard one side. Note and bookmark caption text may be merged server-side before deltas are emitted, but the client unconditionally overwrites whatever arrives. The `SyncShadow` table is used to compute the local delta, not to detect or surface cross-device conflicts to the user.

### PB-04: FCM Token Refresh Failure
`FcmMessagingService.onNewToken()` re-registers with the backend, but if the HTTP call fails (network down, server error), it's silently logged at debug level (Sync.java:306-337). The device stops receiving sync push notifications with no retry mechanism.

### PB-05: Widget Verse Fallback
Daily verse widget selects from a predefined list, but if the user's selected Bible version doesn't contain a particular book (e.g., some Protestant versions vs. Catholic with deuterocanonical books), it silently falls back to the internal version, potentially showing a verse in a different language.

### ~~PB-06: SongBookUtil Resource Leak~~ ✅ FIXED
**Fixed in REM-01** (`1b9b74d9`, 2026-04-11). Response and all streams now wrapped in try-with-resources.

### ~~PB-07: Preferences hold() Without unhold()~~ ✅ FIXED
**Fixed in REM-02** (`0b61084a`–`80cd9f34`, 2026-04-10/11). `hold()`/`unhold()` are now private; all external code uses `withTransaction(Runnable)` with try/finally guarantee.

### ~~PB-08: Copy/Share Split1 Uses Split0 Metadata in Share URL~~ ✅ FIXED
**Fixed in** `716eb1ce` (2026-04-16). When split view was active and the user picked "Copy Split1" or "Share Split1", the clipboard/share text was correctly built from split1, but the share URL metadata (`version`, `preset_name`, `ari_bc`) came from split0 — so the generated URL pointed at the wrong version. The bug existed in `IsiActivity.actionMode_callback` and was preserved verbatim by the REM-07 refactor (intentionally, to keep it a pure refactor). Now `menuCopySplit1`/`menuShareSplit1` route metadata through split1; split0 and BothSplits remain correct. Four regression tests added.

### ~~PB-09: Highlights.alphaMix() ARGB Leak~~ ✅ FIXED
**Fixed in REM-18a** (`1ca73821`, 2026-04-16). `Highlights.alphaMix()` OR-ed `0xa0000000` without masking the high byte: any caller passing an ARGB value instead of an RGB one would bleed the original alpha into the result. Fixed by masking the input: `0xa0000000 | (colorRgb & 0x00ffffff)`.

### ~~PB-10: Sync_Pins.Content.equals() Compared Unsorted Lists~~ ✅ FIXED
**Fixed in REM-18b** (`b4bce934`, 2026-04-16). `Sync_Pins.Content.equals()` sorted copies of the pin lists but then compared the original unsorted lists, defeating the intended order-insensitive equality. In practice masked because `getEntitiesFromCurrent()` always builds pins in `preset_id` order, but a deserialized shadow with pins in a different order would incorrectly trigger a spurious "mod" sync op. Fixed to compare the sorted copies; `hashCode()` also fixed to be order-insensitive to satisfy the `equals`/`hashCode` contract.
