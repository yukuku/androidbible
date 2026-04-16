# Tech Debt, Improvements & Critiques

## TD-01: IsiActivity God Class (~2320 lines)

**File:** `Alkitab/src/main/java/yuku/alkitab/base/IsiActivity.kt`

The main Bible reader activity is a monolithic class containing many inline lambda callbacks, ~2320 lines of mixed concerns (down from 2897 after REM-07 extracted action mode). Specific clusters that violate single-responsibility:

- **Gesture handling (lines 143–370):** `onFloaterDragStart/Move/Complete`, `onOnefingerLeft/Right`, `onTwofingerStart/Scale/DragX/DragY/End` — all inline lambdas that implement `TwofingerLinearLayout.Listener`. This is ~230 lines of touch gesture processing embedded in the Activity.
- **~~Action mode~~** ✅ **Extracted (REM-07):** The ~500-line `actionMode_callback` object has been moved to `VerseActionModeController.kt` behind two interfaces (`VerseActionModeHost`, `VerseActionModeActions`). Pure text-building logic is in `VerseTextFormatter` (no Android deps). `RibkaEligibility` is a top-level file. 26 unit tests added.
- **Broadcast receivers (lines 451–519):** Two anonymous `BroadcastReceiver` instances registered inline, one for verse attribute changes and one for version changes.
- **Verse selection listeners (lines 463–525):** Two `SelectedVersesListener` implementations (`lsSplit0_selectedVerses`, `lsSplit1_selectedVerses`) with partially duplicated logic.
- **Split view management:** `openSplitDisplay()`, `closeSplitDisplay()`, `displaySplitFollowingMaster()`, `loadSplitVersion()` scattered across the file.
- **Navigation history:** `BackForwardListController` usage, `jumpToAri()`, `jumpTo(reference)`, `History` tracking.

**Impact:** Any change to one concern risks breaking unrelated functionality. Testing individual behaviors requires instantiating the entire Activity. New developers face a ~2320-line class with no clear entry point.

---

## TD-02: InternalDb — Raw SQL & Manual Statement Caching (1771 lines)

**File:** `Alkitab/src/main/java/yuku/alkitab/base/storage/InternalDb.java`

### Raw SQL string concatenation (15+ instances)
Lines 201–205, 264, 666, 1319+:
```java
c = db.rawQuery("select " + Db.TABLE_Marker + ".* from " + Db.TABLE_Marker +
  " where " + Db.TABLE_Marker + "." + Db.Marker.kind + "=? and " +
  Db.TABLE_Marker + "." + Db.Marker.gid + " not in (select distinct " +
  Db.Marker_Label.marker_gid + " from " + Db.TABLE_Marker_Label + ")", ...);
```
These are unreadable, fragile, and impossible to verify at compile time.

### Manual compiled statement caching (line 229)
```java
private SQLiteStatement stmt_countMarkersForBookChapter = null;
if (stmt_countMarkersForBookChapter == null) {
    stmt_countMarkersForBookChapter = helper.getReadableDatabase().compileStatement(...);
}
```
Fragile lifecycle management — if the database is closed and reopened, cached statements become invalid.

### 2MB CursorWindow workaround (lines 1319–1370)
```java
db.rawQuery("select substr(" + Table.SyncShadow.data.name() + ", " + (i + 1) +
  ", " + chunkSize + ") from ...", ...);
```
Hard-coded chunk size `1_000_000` to work around the undocumented 2MB CursorWindow limit. This is fragile and breaks if the system limit changes.

### Duplicated reordering SQL (lines 924–968)
Three near-identical `execSQL()` calls to reorder labels with `+1`/`-1` arithmetic — should be a single parameterized method.

### TODO comment (line 234)
`TODO this is only called together with putAttributes(), make it private` — indicates known coupling that hasn't been fixed.

### ~~Verse-255 boundary bug (lines 238, 262)~~ ✅ FIXED
**Fixed in REM-18c** (`ccf0a320`, 2026-04-16). `countMarkersForBookChapter` and `putAttributes` used exclusive upper bound (`ari < ariMax`) when `ariMax = ari_bookchapter | 0xff`, silently dropping any marker on verse 255. Changed to inclusive (`ari <= ariMax`) to match `getHighlightColorRgb`. No real data was affected (no Bible chapter has 255 verses), but the boundary is now correct and locked down by two regression tests.

---

## TD-03: Deprecated API Usage

### LocalBroadcastManager (deprecated in AndroidX 1.1.0)
Used in 15+ files for event communication:
- `DevotionDownloader.java:109` — `App.getLbm().sendBroadcast(intent)`
- `DevotionActivity.java`, `MarkersActivity.java`, `ReadingPlanActivity.java` — receivers
- Still listed as a dependency in `build.gradle`

Should migrate to `LiveData`, `SharedFlow`, or `EventBus`.

### Handler(Looper.getMainLooper()) for thread switching
- `VerseRenderer.java:310` — creates Handler in static context to show Toast
- `Foreground.java:8` — lifecycle tracking
- `DownloadService.java:42-70` — progress callbacks

Should use `Dispatchers.Main` with coroutines or `lifecycleScope`.

### Static Toast caching (VerseRenderer.java:307-318)
```java
static Toast invalidSpecialTagToast;
```
Static UI object reference can leak Activity context. Should create Toast inline or use `Snackbar`.

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

### DevotionDownloader infinite loop (lines 64–101)
```java
@SuppressWarnings("InfiniteLoopStatement")
while (true) {
    final DevotionArticle article = dequeue();
    if (article == null) {
        synchronized (queue_) { queue_.wait(); }
    } else {
        // download
        SystemClock.sleep(50); // hardcoded 50ms
    }
}
```
- No shutdown mechanism — thread runs until process death
- Hardcoded 50ms sleep between downloads
- `queue_.wait()` blocks forever if `resumeDownloading()` is never called
- Should use `ExecutorService` or `WorkManager`

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

**File:** `Alkitab/src/main/java/yuku/alkitab/base/widget/VerseRenderer.java`

### 200-line render method (lines 71–271)
The `render()` method handles verse number embedding, paragraph styles, red letter start/end, italic start/end, line breaks, cross-references, footnotes, and highlights in a single method with 7 levels of nesting. Should be decomposed into:
- `renderVerseNumber()`
- `applyParagraphStyle()`
- `processFormattingCodes()`
- `processSpecialTags()`

### Undocumented Unicode constants (line 23)
```java
static final char[] superscriptDigits = {'\u2070', '\u00b9', '\u00b2', ...};
```
No comments explaining the Unicode superscript digit range.

---

## TD-11: Mixed Java/Kotlin

Core files still in Java with no clear migration plan:
- `InternalDb.java` (1771 lines)
- `SearchEngine.java` (537 lines)
- `VerseRenderer.java` (423 lines)
- `Sync.java` (508 lines)
- `SyncAdapter.java` (600+ lines)
- `DevotionDownloader.java` (111 lines)
- `Provider.java` (content provider)
- `Highlights.java`, `Jumper.java`, `TargetDecoder.java`
- All devotion article parsers

Newer files (activities, data classes) are Kotlin, creating a mixed codebase where Java code can't use Kotlin features (extension functions, coroutines, sealed classes, null safety).

---

## TD-12: Deprecated / Unmaintained Dependencies

| Dependency | Version | Issue |
|------------|---------|-------|
| `material-dialogs` | 3.3.0 | Library archived/deprecated. No Material 3 support. |
| `FancyShowCaseView` | 1.4.0 | Low maintenance activity. Evaluate alternatives. |
| `PRDownloader` (patched) | custom | Forked as `PrDownloaderFixed`. Maintenance burden of carrying a patched fork. |
| `AmbilWarna` | bundled | Bundled color picker. Material color picker components exist now. |
| `LocalBroadcastManager` | latest | Officially deprecated by AndroidX team. |

---

## TD-13: Minimal Test Coverage

22 test files now exist across the project (Alkitab module unit tests unless noted):

**Added in recent sprints:**
- `HighlightsTest.kt` — highlight encode/decode, alphaMix, partial highlights (REM-18a)
- `SyncDeltaTest.kt`, `Sync_MabelTest.kt`, `Sync_PinsTest.kt`, `Sync_RpTest.kt` — sync delta application and entity equality (REM-18b)
- `InternalDbTest.kt` — marker CRUD, label ordering, highlight storage, attribute loading via Robolectric (REM-18c)
- `SearchEngineTest.kt` — `ReadyTokens`, `satisfiesTokens`, and `searchByGrep` end-to-end via Robolectric (REM-18d)
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

**Still not tested:** Version loading (YES2 reader/writer round-trip), devotion downloading, song management, content provider, widget logic.

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

**File:** `Alkitab/src/main/java/yuku/alkitab/base/S.kt` (322 lines)

A Kotlin `object` singleton that serves as the central service locator for the entire app, mixing three unrelated concerns:

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

### PB-02: Highlight Hash Invalidation
`Highlights.java` stores a hash of the verse text alongside highlight data. If a Bible version is updated (new translation revision), the hash check fails silently and the highlight may be dropped or applied to wrong text. No user notification.

### PB-03: Concurrent Sync Data Loss
The sync protocol uses last-write-wins without conflict notification. If two devices edit the same bookmark simultaneously and sync, one edit is silently discarded. The `SyncShadow` table detects the conflict but the resolution strategy doesn't inform the user.

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
