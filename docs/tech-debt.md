# Tech Debt, Improvements & Critiques

Status last verified against the code on 2026-07-15. Refer to code by symbol name, not line number, and don't record library version numbers — both rot quickly (see Documentation Conventions in CLAUDE.md).

## TD-01: IsiActivity Mega Class

**File:** `Alkitab/src/main/java/yuku/alkitab/base/IsiActivity.kt`

The main Bible reader activity is still the largest class in the codebase (though much smaller since the REM-06/07/08 extractions), but it is no longer monolithic. Extracted so far:

- **~~Gesture handling~~** ✅ **Extracted (REM-06):** pinch zoom, one/two-finger swipes, and floater drag live in `ReaderGestureHandler.kt` (under `widget/`) behind the `ReaderGestureHost` / `ReaderGestureActions` interfaces. Gesture-local state lives on the handler.
- **~~Action mode~~** ✅ **Extracted (REM-07):** the large `actionMode_callback` object moved to `VerseActionModeController.kt` (under `actionmode/`) behind `VerseActionModeHost` / `VerseActionModeActions`. Pure text-building logic is in `VerseTextFormatter` (no Android deps). `RibkaEligibility` is a top-level declaration in the `actionmode` package. Covered by `VerseActionModeControllerTest` + `VerseTextFormatterTest`.
- **~~Split view management~~** ✅ **Extracted (REM-08):** `openSplitDisplay()`, `closeSplitDisplay()`, `displaySplitFollowingMaster()`, and `loadSplitVersion()` live in `SplitViewManager.kt` (under `widget/`); the activity delegates to `splitViewManager`.
- **~~Broadcast receivers~~** ✅ **Gone (REM-03):** the two inline `BroadcastReceiver`s were replaced by `AppEvents` `SharedFlow` collectors launched from `lifecycleScope` in `onCreate`.

Still inline in the activity:

- **Verse selection listeners:** two `SelectedVersesListener` implementations (`lsSplit0_selectedVerses`, `lsSplit1_selectedVerses`) with partially duplicated logic.
- **Navigation history:** `BackForwardListController` usage, `jumpToAri()`, `jumpTo(reference)`, `History` tracking.
- **Volume-button navigation**, chapter display logic, and many inline callbacks.

**Impact:** Any change to one concern risks breaking unrelated functionality. Testing individual behaviors requires instantiating the entire Activity. REM-09 (ViewModel) is the planned next step.

---

## TD-02: InternalDb — Raw SQL & Manual Statement Caching

**File:** `Alkitab/src/main/java/yuku/alkitab/base/storage/InternalDb.java`

**Status:** Significantly improved. Per-table DAOs (`MarkerDao`, `LabelDao`, `VersionDao`, `DevotionDao`, `ReadingPlanDao`, `ProgressMarkDao`, `PerVersionDao`, `Marker_LabelDao`, `SyncShadowDao`) and `SyncApplier` were extracted, cutting `InternalDb.java` to roughly half its former size. The remaining issues below are the parts not covered by that refactor.

### Raw SQL string concatenation (~10 instances remain)
Examples in `listMarkersForLabel`, `getLabelById`, and the reordering helpers:
```java
c = db.rawQuery("select " + Db.TABLE_Marker + ".* from " + Db.TABLE_Marker +
  " where " + Db.TABLE_Marker + "." + Db.Marker.kind + "=? and " +
  Db.TABLE_Marker + "." + Db.Marker.gid + " not in (select distinct " +
  Db.Marker_Label.marker_gid + " from " + Db.TABLE_Marker_Label + ")", ...);
```
These are unreadable, fragile, and impossible to verify at compile time.

### ~~Manual compiled statement caching~~ ✅ FIXED
The problematic `private SQLiteStatement stmt_countMarkersForBookChapter` field and null-check pattern have been removed. `MarkerDao.countForAriRange` now uses `compileStatement(...).use { }` so the statement is closed deterministically. `LabelDao.getMaxOrdering` uses the same pattern. No cross-request caching, no invalidation concerns.

### 2MB CursorWindow workaround (SyncShadowDao.kt)
Still present, but moved out of `InternalDb.java` into `SyncShadowDao.kt`:
```kotlin
"select substr(${Table.SyncShadow.data.name}, ${i + 1}, $chunkSize)" +
```
Hard-coded chunk size `1_000_000` to work around the undocumented 2MB CursorWindow limit. This is fragile and breaks if the system limit changes.

### Duplicated reordering SQL (`reorderLabels` / `reorderVersions` area)
Two near-identical `execSQL()` pairs to reorder labels and versions with `+1`/`-1` arithmetic — should be a single parameterized method parameterized by table name.

### ~~TODO comment~~ ✅ GONE
The `TODO this is only called together with putAttributes(), make it private` comment was removed during the DAO refactor.

### ~~Verse-255 boundary bug~~ ✅ FIXED
**Fixed in REM-18c** (2026-04-16). `countMarkersForBookChapter` and `putAttributes` used exclusive upper bound (`ari < ariMax`) when `ariMax = ari_bookchapter | 0xff`, silently dropping any marker on verse 255. Changed to inclusive (`ari <= ariMax`) to match `getHighlightColorRgb`. No real data was affected (no Bible chapter has 255 verses), but the boundary is now correct and locked down by two regression tests.

---

## TD-03: Deprecated API Usage

### ~~LocalBroadcastManager~~ ✅ removed in REM-03
All `App.getLbm().sendBroadcast` / `registerReceiver` usages migrated to Kotlin `SharedFlow`-based buses in `yuku.alkitab.base.events.AppEvents`. The `androidx.localbroadcastmanager` dependency and `App.getLbm()` helper are gone.

### Handler(Looper.getMainLooper()) for thread switching
- `VerseRenderer.kt` `reportInvalidSpecialTag` — creates Handler to show Toast
- `util/Foreground.java` — static main-thread Handler for lifecycle tracking

Should use `Dispatchers.Main` with coroutines or `lifecycleScope`. (The former `DownloadService.java` instance was deleted along with PRDownloader in REM-19.)

### Static Toast caching (VerseRenderer.kt `invalidSpecialTagToast`)
```kotlin
private var invalidSpecialTagToast: Toast? = null
```
Object-scope UI reference can leak Activity context. Should create Toast inline or use `Snackbar`.

---

## TD-04: ~~Unsafe Deserialization in Songs~~ FIXED

**File:** `Alkitab/src/main/java/yuku/alkitab/songs/SongBookUtil.kt`

**Fixed in REM-01** (2026-04-11):
- ✅ **Security risk:** switched to `SafeObjectInputStream` with a class whitelist, plus `instanceof` checks before casting.
- ✅ **Resource leak:** Response and streams wrapped in try-with-resources.
- ✅ **No size limits:** Response body size validation added (rejects >50MB).

**Fixed in REM-21** (portable songs, 2026-07-13):
- ✅ **Fragility / long-term:** `SongBookUtil.deserializeSongs` now parses a gzipped JSON song-book wrapper instead of Java-serializing a `List<Song>`; `SafeObjectInputStream`/`ObjectInputStream` were deleted outright, removing the Java-deserialization gadget surface entirely rather than just allow-listing it. On-device storage (`song_info.data`) moved the same way: it's UTF-8 JSON (`yuku.alkitab.songs.newdoc.SongDocument`) at `dataFormatVersion = 5`, with a pure-JVM `LegacyParcelDecoder` lazily converting pre-existing Parcelable rows on first read (see `docs/modules/songs.md`, `docs/features/portable-songs/design.md`). `KpriModel.Song`/`Lyric`/`Verse`/`VerseKind` remain only as that decoder's target type.

---

## TD-06: Threading & Concurrency Issues

### ~~DevotionDownloader infinite loop~~ ✅ FIXED (REM-05)
`DevotionDownloader` (now Kotlin) uses `Executors.newSingleThreadExecutor()` with a `LinkedBlockingDeque.take()` loop, a `volatile` shutdown flag, and a `shutdown()` method calling `executor_.shutdownNow()`. The hardcoded `SystemClock.sleep(50)` is gone; `InterruptedException` is handled by re-interrupting and breaking.

### Sync lock complexity (Sync.java, `syncUpdatesOngoingCounters` / `syncSetNameQueue`)
Multiple `synchronized` blocks/methods on different objects around a `ConcurrentLinkedQueue`. The mixed locking granularity is hard to reason about and risks subtle races with nested synchronization.

### Coroutine adoption is partial
Coroutines are now used in ~16 files (the Bible-audio subsystem, `VersionDownloadWorker`, `DownloadMapper`, `AppEvents` flows, Compose Goto screen, `IsiActivity` collectors, song audio), but the sync module, `InternalDb`/DAO layer, search, and devotion downloads still use raw `Thread`, `Handler`, and executors. REM-15 tracks the remaining module-by-module migration.

---

## TD-07: No ViewModel / Architecture Components

Activities directly hold all state as fields. `IsiActivity` maintains:
- Active Bible versions (`activeSplit0`, `activeSplit1`)
- Selected verses and selection mode
- Navigation history
- Current chapter data
- UI dimensions and appearance settings

On configuration change (rotation), this state is partially lost. The `onSaveInstanceState/onRestoreInstanceState` approach is incomplete — not all state is serializable.

(The Compose Goto screen introduced the first `ViewModel` in the app — `GotoViewModel` — but `IsiActivity` itself still has none; see REM-09.)

---

## TD-08: Sync Protocol Hardcoded Endpoints

**File:** `Alkitab/src/main/java/yuku/alkitab/base/sync/Sync.java`

Five API endpoints hardcoded as string concatenations across the file (`register_gcm_client` in `sendFcmRegistrationId`, plus `create_own_user`, `login_own_user`, `forgot_password`, `change_password`). Four more `/sync/api/sync` call sites are spread across `SyncAdapter.java` (one per sync set). All should be centralized in an API constants file or a Retrofit interface.

Error handling is uniform — all failures produce `NotOkException` with no distinction between network errors, validation errors, or server errors. FCM registration now has retry with backoff (REM-04), but the sync data endpoints themselves still have no retry logic or exponential backoff for transient failures.

---

## TD-09: Preferences Architecture

**File:** `Afw/src/main/java/yuku/afw/storage/Preferences.java`

### ~~Manual hold/unhold transaction~~ FIXED
**Fixed in REM-02** (2026-04-10/11):
- ✅ `hold()`/`unhold()` are now **private** — external code uses `withTransaction(Runnable)` which guarantees `unhold()` via try/finally
- ✅ All 6 call sites migrated; two previously unsafe call sites (`SyncSettingsActivity`, `SecretSyncDebugActivity`) were fixed

### Manual cache with dirty flag
- `dirty` flag requires manual `invalidate()` calls from external code

### 100+ preference keys as enum (Prefkey.kt)
Preference keys are a flat enum with no grouping or type safety. Each access requires an explicit type cast (`getString`, `getInt`, `getBoolean`). DataStore with typed proto schema would be safer.

---

## TD-10: VerseRenderer Complexity

**File:** `Alkitab/src/main/java/yuku/alkitab/base/widget/VerseRenderer.kt`

### ~~Monolithic render() method~~ ✅ RESOLVED (REM-25)
The monolithic `render()` body has been decomposed into `renderVerseNumber()`, `processFormattingCodes()`, `applyHighlight()`, and `bindToTextViews()`, alongside the existing `applyParaStyle()` and `processSpecialTag()`. Behavior is locked down by 39 characterization tests in `VerseRendererTest.kt`.

### ~~Undocumented Unicode constants~~ ✅ RESOLVED (REM-26)
The `superscriptDigits` array now has an inline comment naming the Unicode code points (U+2070, U+00B9, U+00B2, U+00B3, U+2074..U+2079) and explaining its use in `appendSuperscriptNumber`. The `XREF_MARK` constant is similarly documented as U+203B REFERENCE MARK.

---

## TD-11: Mixed Java/Kotlin

`Alkitab/src/main` is currently ~107 Java files vs ~182 Kotlin files. Of the core files originally flagged here, most have been ported (REM-16): `SearchEngine`, `DevotionDownloader`, `Provider`, `Highlights`, `Jumper`, `TargetDecoder`, `QueryTokenizer`, `SongBookUtil`, and `VerseRenderer` are all Kotlin now.

Still Java, with no clear migration plan:
- `InternalDb.java` — plus `InternalDbHelper` and several activities
- `Sync.java`
- `SyncAdapter.java`
- All devotion article parsers (`DevotionArticle.java`, `ArticleFromSabda`, `ArticleMeidA`, `ArticleMorningEveningEnglish`, `ArticleRenunganHarian`, `ArticleRoc`, `ArticleSantapanHarian`)

The mixed codebase means the remaining Java code can't use Kotlin features (extension functions, coroutines, sealed classes, null safety).

---

## TD-12: Deprecated / Unmaintained Dependencies

| Dependency | Issue |
|------------|-------|
| ~~`material-dialogs`~~ | ✅ **Fixed in REM-14.** All call sites migrated to `MaterialAlertDialogBuilder` (Material 3); the `com.afollestad.materialdialogs` artifacts have been removed. `MaterialDialogJavaHelper` / `MaterialDialogAdapterHelper` are now thin wrappers around `MaterialAlertDialogBuilder`. |
| `FancyShowCaseView` | Low maintenance activity. Evaluate alternatives. |
| ~~`PRDownloader` (patched)~~ | ✅ **Fixed in REM-19** (2026-05-13). Bible-version downloads were migrated to `VersionDownloadWorker : CoroutineWorker` (OkHttp + Range-based resume), observed by `DownloadMapper` via `WorkManager.getWorkInfoByIdFlow(uuid)`. The `:PrDownloaderFixed` module was deleted entirely. |
| ~~`AmbilWarna`~~ | ✅ **Fixed in REM-20** (2026-05-12). Color-picker call sites migrated to a Compose `ModalBottomSheet`-hosted picker (`IosColorPicker` + `ColorPickerDialog`) and a new `ColorPreference` subclass. The `AmbilWarna` Gradle module was deleted entirely. |
| ~~`LocalBroadcastManager`~~ | Removed in REM-03; replaced by `AppEvents` `SharedFlow` buses. |
| ~~`androidx.percentlayout`~~ | ✅ **Fixed in REM-22 follow-up** (2026-05-07). Orphaned by the GotoActivity Compose port; dependency, version-catalog entry, and dialer-only resources removed. |

---

## TD-13: Test Coverage

43 test files now exist under `Alkitab/src/test` (all Alkitab-module unit tests unless noted). Coverage grew from ~10 pre-existing files through REM-18 and the audio/Room/portable-songs work:

**Storage & DAO:** `InternalDbTest`, `InternalDbHelperMigrationTest`, `DevotionDaoTest`, `PerVersionDaoTest`, `ProgressMarkDaoTest`, `ReadingPlanDaoTest`, `VersionDaoTest`, `SongDbTest`, `SongRoomDaoTest`, `SongRoomDatabaseMigrationTest`, `SongDbDataMigrationTest` (Robolectric where needed)

**Sync:** `SyncDeltaTest`, `Sync_MabelTest`, `Sync_PinsTest`, `Sync_RpTest`

**Reader & rendering:** `VerseRendererTest` (39 characterization tests), `FormattedTextRendererTest`, `VerseActionModeControllerTest`, `VerseTextFormatterTest`, `VerseItemSideBySideSnapshotTest`, `GotoButtonBalanceWrapTest`, `GotoButtonSideBySideSnapshotTest`

**Audio (Bible audio feature):** `AudioBarControllerReshowTest`, `AudioCatalogRepositoryTest`, `AudioPlaybackCoordinatorTest`, `BibleAudioRepositoryTest`, `BibleNeighborResolverTest`, `HighlightTrackerTest`, `AudioHighlightColorTest`

**Search / util:** `SearchEngineTest`, `QueryTokenizerTest`, `HighlightsTest`, `JumperTest`, `TargetDecoderTest`, `RemoveSpecialCodesTest`, `DownloadMapperTest`

**Songs:** `SongBookUtilTest`, `PortableSongsBridgeTest` (drives the same songs through the legacy Parcelable path and the JSON `SongDocument` path and asserts equivalence)

**Other:** `ProviderTest` (content provider), `AppServicesTest`, `JsonFileExportTest`, `VersionTest`, `GetVersionInitialsTest`; `DesktopVerseFinderTest`/`DesktopVerseParserTest` (tools/AlkitabConverter); `LauncherTest`/`VerseProviderTest` (AlkitabIntegration, androidTest)

**Still not tested:** YES2 reader/writer round-trip, devotion downloading (the DAO is tested, the downloader is not), reading-plan progress logic, widget update flow.

---

## ~~TD-14: ybuild.sh — Custom Shell Script for Production Builds~~ ✅ FIXED

**Fixed in REM-23** (2026-04-16). `ybuild.sh` has been deleted; production builds are now pure Gradle:

- ✅ **Proprietary asset injection:** a typed `CopyProprietaryAssetsTask` per production flavor copies `$ALKITAB_PROPRIETARY_DIR/overlay/<applicationId>/text_raw/*` into `Alkitab/build/generated/proprietaryAssets/<flavor>/internal/`, wired into AGP via `androidComponents.onVariants`. The placeholder `ddd_*` files moved to `Alkitab/src/plain/assets/internal/` so production builds never inherit them.
- ✅ **Git commit hash stamping:** read at config time and exposed as `BuildConfig.LAST_COMMIT_HASH`.
- ✅ **Custom APK naming:** `Alkitab-{versionCode}-{versionName}-{commitHash}-{applicationId}-{BUILD_DIST}.apk`, mirroring the legacy scheme.
- ✅ **RAM disk dependency:** removed; not needed.
- ✅ **Linux CI compatibility:** production builds run via `./gradlew assemble<Flavor>Release` on any platform.

See `docs/build-system.md` for the current release-build command and environment variables.

---

## TD-15: S.kt — Service Locator (Being Retired via REM-24)

**File:** `Alkitab/src/main/java/yuku/alkitab/base/S.kt`

A Kotlin `object` singleton that was the central service locator for the entire app. REM-24 (steps 24a–24d, completed 2026-05-12) extracted interfaces and migrated most callers:

- The `yuku.alkitab.base.services` package holds `StorageProvider`, `VersionManager`, and `UiDimensionsProvider`, bundled in `AppServices` and reached via `App.services` (the path most call sites use). `S` implements all three interfaces.
- The version-picker dialogs (`openVersionsDialog()` / `openVersionsDialogWithNone()`) live in `util/VersionDialogHelper`, not the locator.
- Active-version state is a single `@Volatile var state: ActiveVersionState` data-class reference, so readers always see a consistent `(mVersion, version, versionId)` triple.
- The UI-dimensions recompute entry point is `recalculate()` on `UiDimensionsProvider`.

### Remaining debt
- `S` still exists as the implementation behind the interfaces; ~8–10 files still import `S` directly (~25 call sites). New code should use `App.services.*`.
- `S.db` / `S.songDb` are still lazy singletons requiring `App.context` — implicit initialization-order dependency remains.
- `CalculatedDimensions` is still a bag of 18 mutable fields, replaced atomically by `recalculate()` with no observer mechanism — consumers must re-read `applied()` manually.
- No DI framework; `AppServices` is a hand-rolled container (step 24e, Hilt, is deferred). Test doubles are possible via the interfaces (`AppServicesTest` demonstrates this), but most call sites still resolve through the global `App.services`.

---

## Known Bugs & Open Fixes

All specific bugs — both the ones fixed to date and the open ones surfaced by the 2026-07 code audit — are tracked as remediation tasks (REM-33 … REM-44) in [tech-debt-remediation.md](tech-debt-remediation.md), each with a full failure description, fix steps, and a BRICE score. Notable open items include data-transfer import safety (REM-33), the YES2 ASCII search decoder (REM-36), song-book update `dataFormatVersion` handling (REM-35), Bible-audio playback state (REM-37), last-write-wins sync conflicts (REM-43), and the version cache eviction policy (REM-42).
