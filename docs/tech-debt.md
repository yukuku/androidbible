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

- New `yuku.alkitab.base.services` package: `StorageProvider`, `VersionManager`, `UiDimensionsProvider`, bundled in `AppServices` and reached via `App.services` (~257 call sites now use this path). `S` implements all three interfaces.
- ~~UI dialogs in the locator~~ — `openVersionsDialog()` / `openVersionsDialogWithNone()` moved to `util/VersionDialogHelper`.
- ~~Torn reads on active-version state~~ — the three mutable fields were collapsed into a single `@Volatile var state: ActiveVersionState` data-class reference, so readers always see a consistent `(mVersion, version, versionId)` triple.
- `recalculateAppliedValuesBasedOnPreferences()` was renamed `recalculate()` (on `UiDimensionsProvider`).

### Remaining debt
- `S` still exists as the implementation behind the interfaces; ~8–10 files still import `S` directly (~25 call sites). New code should use `App.services.*`.
- `S.db` / `S.songDb` are still lazy singletons requiring `App.context` — implicit initialization-order dependency remains.
- `CalculatedDimensions` is still a bag of 18 mutable fields, replaced atomically by `recalculate()` with no observer mechanism — consumers must re-read `applied()` manually.
- No DI framework; `AppServices` is a hand-rolled container (step 24e, Hilt, is deferred). Test doubles are possible via the interfaces (`AppServicesTest` demonstrates this), but most call sites still resolve through the global `App.services`.

---

## Potential Bugs

### PB-01: Soft Reference Cache Thrashing
`MVersionDb` caches `VersionImpl` with `SoftReference` in a `ConcurrentHashMap`. Under memory pressure, all cached versions are GC'd simultaneously, causing a burst of file I/O as they're reloaded. No monitoring, no LRU eviction strategy.

### ~~PB-02: Highlight Hash Invalidation~~ ❎ DISMISSED
`Highlights` stores a hash of the verse text alongside partial-highlight offsets. On hash mismatch (verse text changed), `VerseRenderer` falls back to a full-verse highlight rather than dropping or mis-applying the span — so the degradation is graceful: the user still sees the verse highlighted, just at verse granularity instead of character range. Since published translation revisions are rare, this is acceptable behavior and not worth the complexity of fuzzy re-anchoring.

### PB-03: Concurrent Sync Data Loss for Highlights and Progress Pins
The client-side patch logic (`SyncAdapter.patchNoConflict`) is last-write-wins for every Mabel entity and for progress pins — so concurrent edits to a highlight color or a progress-pin position on two devices silently discard one side. Note and bookmark caption text may be merged server-side before deltas are emitted, but the client unconditionally overwrites whatever arrives. The `SyncShadow` table is used to compute the local delta, not to detect or surface cross-device conflicts to the user.

### ~~PB-04: FCM Token Refresh Failure~~ ✅ FIXED
**Fixed in REM-04** (2026-04-22). `Sync.sendFcmRegistrationId` failures now schedule an in-process retry chain (1 min / 5 min / 30 min via a daemon `ScheduledExecutorService`) and set a persistent `Prefkey.fcm_registration_pending` flag; `App.staticInit()` calls `retryPendingFcmRegistrationIfNeeded` so the next launch re-sends if the in-process chain never completed.

### PB-05: Widget Verse Fallback
`DailyVerseData.getVersion()` falls back to the internal version whenever the user's selected version can't be loaded (no data file, version deleted, or unset). A warning is logged, but the user sees the widget quietly switch language/translation instead of an error state.

### ~~PB-06: SongBookUtil Resource Leak~~ ✅ FIXED
**Fixed in REM-01** (2026-04-11). Response and all streams now wrapped in try-with-resources.

### ~~PB-07: Preferences hold() Without unhold()~~ ✅ FIXED
**Fixed in REM-02** (2026-04-10/11). `hold()`/`unhold()` are now private; all external code uses `withTransaction(Runnable)` with try/finally guarantee.

### ~~PB-08: Copy/Share Split1 Uses Split0 Metadata in Share URL~~ ✅ FIXED
**Fixed 2026-04-16.** When split view was active and the user picked "Copy Split1" or "Share Split1", the clipboard/share text was correctly built from split1, but the share URL metadata (`version`, `preset_name`, `ari_bc`) came from split0 — so the generated URL pointed at the wrong version. Now `menuCopySplit1`/`menuShareSplit1` route metadata through split1; split0 and BothSplits remain correct. Four regression tests added.

### ~~PB-09: Highlights.alphaMix() ARGB Leak~~ ✅ FIXED
**Fixed in REM-18a** (2026-04-16). `Highlights.alphaMix()` OR-ed `0xa0000000` without masking the high byte: any caller passing an ARGB value instead of an RGB one would bleed the original alpha into the result. Fixed by masking the input: `0xa0000000 | (colorRgb & 0x00ffffff)`.

### ~~PB-10: Sync_Pins.Content.equals() Compared Unsorted Lists~~ ✅ FIXED
**Fixed in REM-18b** (2026-04-16). `Sync_Pins.Content.equals()` sorted copies of the pin lists but then compared the original unsorted lists, defeating the intended order-insensitive equality. In practice masked because `getEntitiesFromCurrent()` always builds pins in `preset_id` order, but a deserialized shadow with pins in a different order would incorrectly trigger a spurious "mod" sync op. Fixed to compare the sorted copies; `hashCode()` also fixed to be order-insensitive to satisfy the `equals`/`hashCode` contract.

---

## Potential Bugs — 2026-07 audit

Found in a code audit on 2026-07-15 (each verified by reading all involved code paths; "plausible" marks findings whose trigger conditions were not reproduced). Remediation is tracked as REM-33 – REM-41 in [tech-debt-remediation.md](tech-debt-remediation.md).

### Data transfer & storage

### PB-11: Import "Simulation" Permanently Replaces the User's Reading History — HIGH
`ImportProcess.history()` (`datatransfer/process/ImportProcess.kt`) calls `History.replaceAllEntries()` unconditionally — but History lives in memory/Preferences, not SQLite, so the dry-run rollback (transaction never committed) does not undo it. Import always runs a simulation first: a user who opens Import, watches the simulation, and presses Close has already had their in-memory history replaced by the file's contents; the next `IsiActivity.onStop()` persists it **and** triggers `Sync.notifySyncNeeded(SYNC_SET_HISTORY)`, pushing the bogus history to all devices. Conversely, on an *actual* run `History.save()` is never called by the import, so a process kill before the next `onStop` silently drops the imported history. (REM-33)

### PB-12: InternalDbTxWrapper.transact Has No try/finally — HIGH
`InternalDbTxWrapper.transact` calls `beginTransactionNonExclusive()` then runs the action with no `try/finally`; if the action throws (e.g. an imported `MarkerEntity` with an invalid `kind` enum → NPE in `MarkerDao.markerToContentValues`, or any SQLite error mid-import), `endTransaction()` is never called. The exception propagates with the write transaction still open; `DataTransferActivity` catches it and tells the user "all changes have been rolled back" — false — and the held writer connection blocks every subsequent DB write (ANR) until process kill. The sync-side `SyncApplier` does this correctly with `finally { endTransaction() }`. (REM-33)

### PB-13: Import/Export Round-Trip Gaps — LOW
(a) `ImportProcess` has a "check validity of marker gid and label gid" comment but performs no check — `Marker_Label` rows referencing nonexistent marker/label gids are imported as dangling junction rows and then pushed to sync. (b) `ReadonlyStorageImpl.rpps()` skips progress whose reading plan was deleted, whereas the sync path deliberately preserves such progress (`InternalDb.deleteReadingPlanById`: "the progress will be kept") — an export/import migration loses data that sync would have kept. (REM-33)

### PB-14: No onDowngrade for the versionCode-Keyed Database — LOW (catastrophic UX when hit)
`InternalDbHelper` sets `user_version` to `App.getVersionCode()` and does not override `onDowngrade`, so `SQLiteOpenHelper` throws on open after any downgrade (beta opt-out, staged-rollout rollback) — a crash loop on every launch until the user clears app data, destroying all markers/notes. Every release bumps `user_version` even without schema changes, so any downgrade at all triggers it. (REM-41)

### PB-15: Verse-0 ARIs Index Attribute Maps at −1 — LOW-MEDIUM
`InternalDb.putAttributes` and both loops in `VerseAttributeLoader.load` compute `mapOffset = Ari.toVerse(ari) - 1` and guard only the upper bound, never `< 0`. A marker/progress-mark with a whole-chapter ari (verse byte 0) — possible via sync (`content.ari` is stored unvalidated) or data-transfer import — falls inside the chapter's query range and throws `ArrayIndexOutOfBoundsException: -1`, crashing the reader **every time that chapter is opened** until the row is deleted. The existing "mapOffset too many" log shows the over-bound case was hit in the field; the under-bound case was left open. (REM-41)

### Sync

### PB-16: notifySyncNeeded Drops Remaining Sync Sets — MEDIUM
In `Sync.notifySyncNeeded`, the ongoing-updates check inside the `for (syncSetName : syncSetNames)` loop does `return` where `continue` was intended (the log message even says "ignored"). An FCM push carrying `["mabel", "pins", "rp"]` while a mabel delta is being applied aborts on "mabel" — "pins" and "rp" are never queued and stay stale until an unrelated local change triggers sync. (REM-34)

### PB-17: syncNow Silently Drops Requests While a Sync Worker Is Running — MEDIUM
`SyncKotlin.syncNow` sees the unique work in state RUNNING, logs `worker_is_currently_running`, and does not enqueue — but by then `notifySyncNeeded` has already drained the names out of `syncSetNameQueue`, so the request is lost, not deferred. A bookmark added while a sync worker is mid-HTTP-request stays unsynced indefinitely. Secondary: the RUNNING check + `ExistingWorkPolicy.REPLACE` is a TOCTOU pair — a request enqueued just as a worker transitions to RUNNING cancels it mid-apply. (REM-34)

### PB-18: Preferences Editor Race Can Silently Lose Writes — MEDIUM
`Afw/Preferences.getEditor()` (check-then-create on the shared static `currentEditor`) and the `put*` methods are unsynchronized; only `commitIfNotHeld()` is `synchronized`. A background writer (sync state, `fcm_registration_pending`, the history JSON) racing the constantly-writing UI thread can stage its write into an editor that was just applied-and-nulled by the other thread, then apply nothing — silent write loss. Losing the history JSON write is user-visible data loss. Related to TD-09. (REM-34)

### PB-19: History.listAllEntries() Reads Shared State Without the Lock — MEDIUM
Every mutator in `History.kt` is `@Synchronized`, but `listAllEntries()` copies the list without the lock. The sync worker (`Sync_History.getEntitiesFromCurrent`) iterating concurrently with a UI-thread `History.add()` gets a `ConcurrentModificationException` (sync fails) or a torn snapshot feeding a wrong delta. (REM-34)

### PB-20: applyMabelAppendDelta Lacks the Null-Content Guard Its Siblings Have — LOW
`SyncApplier.applyPinsAppendDelta` / `applyRpAppendDelta` both do `o.content ?: return unknown_kind`; the mabel path passes `o.content` straight into `updateMarkerWithEntityContent` (Integer unboxing NPE / `Marker.Kind.fromCode` null → NPE). A malformed server operation crashes the worker with an unhandled exception instead of recording a parse failure, and that sync set wedges permanently (same delta re-fetched every run). Rollback itself is correct. (REM-34)

### Songs

### PB-21: "Update Song Book" Re-downloads at a Stale dataFormatVersion — HIGH
`SongViewActivity.updateSongBook()` passes `SongDb.getDataFormatVersionForSongs(bookName)` into the download — but that query is a `LIMIT 1` with no `ORDER BY`, and after REM-21's lazy per-row write-back a pre-REM-21 book is *mixed-version* (viewed rows at 5, unviewed rows still at 2/3/4). Consequences: (a) `storeSongs` deletes only rows matching the passed `dataFormatVersion`, so the other rows survive → **duplicate songs** in listings; (b) the new rows are always JSON payloads (`writeDocument`) but get stamped with the stale version → next read dispatches JSON bytes to `LegacyParcelDecoder` / `Parcel.unmarshall` → crash. `updateSongBook` also lacks the `isSupportedDataFormatVersion` guard that the `alkitab://` download path has. Fix point: always pass `SongDocumentJson.DATA_FORMAT_VERSION` and delete by bookName alone. (REM-35)

### Versions, YES2 & search

### PB-22: YES2 ASCII Decoder Lowercases the Wrong Byte — HIGH (for affected versions)
`Yes2VerseTextDecoder.Ascii.separateIntoVerses` indexes the lowercase loop with the verse counter instead of the byte counter: `verseBuf[i] |= 0x20` should be `verseBuf[j]`. For any downloaded `.yes` version with `textEncoding == 1` (also the fallback for unknown encodings), search misses every verse whose match spans an uppercase letter, and byte `i` of verse `i` is actively corrupted in the lowercased buffer (false matches/misses). Display is unaffected (the lowercased path is search-only). The YES2 round-trip test only covers the UTF-8 decoder. (REM-36)

### PB-23: Utf8Decoder Uses a Shared Mutable Static Buffer — MEDIUM
`Utf8Decoder.buf` is a `public static char[]` used by `toString`/`toStringLowerCase` with no synchronization (the sibling `byte_buf_`/`char_buf_` are correctly `ThreadLocal`, showing the hazard was known). `VersionImpl`'s `synchronized` is per-instance, so two different versions decoding concurrently (widget thread + UI thread, search + split view, content-provider binder thread) interleave writes → garbled verse text, or the swallowed `ArrayIndexOutOfBoundsException` (`// biarin`) silently truncates verses. (REM-39)

### PB-24: Turkish/Azeri "i" — Query and Verse Text Lowercased With Different Rules — MEDIUM
`QueryTokenizer` lowercases the query with `Locale.getDefault()`, but verse text is lowercased locale-invariantly (`Character.toLowerCase` in the decoders / `lowercaseChar()` in `SearchEngine.hilite`). On a Turkish-locale device, "KIRK" tokenizes to `kırk` but the haystack has `kirk` → zero results and broken highlighting for any query containing I/i typed in uppercase. (REM-39)

### PB-25: Version Download Finalization Leaks Streams and Skips AtomicFile.failWrite — MEDIUM
In `VersionDownloadCompleteReceiver`, `fis`, `ogis`, and the `AtomicFile` output stream are not in try-with-resources; on any `IOException` mid-copy all three leak and `af.failWrite(fos)` is never called, leaving the temp file and open fds behind. An exception in `af.finishWrite` on the success path also leaks. (REM-40)

### PB-26: WorkManager Downloads Are Orphaned Across Process Death — MEDIUM-LOW
WorkManager persists the download request across process death, but `DownloadMapper`'s rows, observer coroutines, and the attrs needed by `VersionDownloadCompleteReceiver` are in-memory only, and nothing on startup reconciles or cancels stale `WORK_TAG` work. After a mid-download process kill, the worker re-runs on next launch, downloads the full file, reports SUCCEEDED — and nothing consumes it: bandwidth silently spent, nothing installed, temp file lingers. FAILED work also leaves its partial temp file (only `consumeAndRemove` cleans up). (REM-40)

### Reading plans, devotions & widget

### PB-27: Reading-Plan Progress Can Index Past readMarks → Crash — MEDIUM (plausible)
`ReadingPlanManager.filterProgressForReadingPlan` writes `readMarks[sequence] = true` unguarded; `sequence` comes from persisted/synced `ReadingPlanProgress` rows (kept across plan deletion by design, and synced from other devices via `Sync_Rp`), while `readMarks` is sized from the currently loaded plan blob. A plan revised server-side to have fewer readings on a day (deleted + re-downloaded, or progress synced against a different revision) throws `ArrayIndexOutOfBoundsException` every time that day renders. (REM-41)

### PB-28: ReadingPlanManager Reads the Footer After close() — LOW (latent)
The footer validation `reader.readUint8() != 0` executes *after* the `finally { reader.close() }` block. It works today only because the sole caller passes a `ByteArrayInputStream` (no-op `close()`); any future caller with a file/network stream gets an exception for every valid plan. (REM-41)

### PB-29: Devotion Download De-duplication Never Works — LOW
`DevotionDownloader`'s `queue.contains(article)` relies on `DevotionArticle.equals`, which no article class overrides, and callers create a fresh instance per call — so `contains` is always false. Rapid date/kind navigation enqueues the same devotion repeatedly, each duplicate doing a full HTTP GET and redundant DB store. (REM-41)

### PB-30: Daily-Verse Widget Passes appWidgetId as the "direction" Extra — MEDIUM
`DailyVerseAppWidgetReceiver` does `putExtra(EXTRA_direction, appWidgetId)` (copy-paste of the line above it). `DailyVerseData.getAris` then does `savedState.click += direction` per retry **and persists it** — so a widget (id e.g. 47) configured with a version missing some daily verses jumps its click state by 47 per retry, and the header (built with `direction=47`) and the verse body (reloaded with `direction=1`) can resolve *different verses*, making the reference line disagree with the displayed text. (REM-41)

### Reader UI & audio

### PB-31: Bible Audio Service Never Observes MediaSession-Driven Play/Pause — HIGH
`BibleAudioPlayer.Listener` forwards only `onPlaybackStateChanged` and `onPlayerError` — no `onIsPlayingChanged`. The notification, lock screen, and Bluetooth controls drive the wrapped ExoPlayer directly (no `playbackState` transition), so `BibleAudioService`'s `_playbackState.isPlaying` and the 100 ms position-polling job stay stale: resume-from-notification leaves the in-app bar showing Play with a frozen slider and dead verse highlight/auto-scroll; pause-from-notification leaves the poller running indefinitely (battery drain while the notification sits overnight). The sibling `SongAudioService` *does* override `onIsPlayingChanged` — the omission is specific to Bible audio. (REM-37)

### PB-32: Every Rotation Is Treated as an Explicit "Open Verse" — MEDIUM
When `savedInstanceState` is present, `IsiActivity.onCreate` wraps the saved ari in an `IntentResult`, and the following `history.add(openingAri)` can't distinguish "restored after rotation" from "launched via VIEW intent". Every configuration change inserts a fresh history entry (new gid, new timestamp), reorders "Recent verses", creates a duplicate back/forward entry, and triggers a history sync delta on the next `onStop`. (REM-38)

### PB-33: Split-View Changes Never Notify AudioBarController or the Options Menu — MEDIUM
`AudioBarController.onActiveVersionChanged()` is documented to handle "split toggled, version swapped" but is only wired to `AppEvents.activeVersionChanged`, which fires solely from `IsiActivity.loadVersion`. `SplitViewManager` open/close/version-swap emits nothing and doesn't invalidate the options menu: closing a split whose version was the audio source keeps audio playing for an off-screen version with the chapter-nav buttons blanked out, and opening a split with an audio-capable version doesn't show the toolbar audio icon until something else rebuilds the menu. (REM-37)

### PB-34: Audio Verse-Highlight Color Goes Stale on Theme Change — MEDIUM-LOW
`IsiActivity.audioHighlightColorCached` is recomputed only when `0` and reset only in `onStart`; night-mode toggles and TextAppearancePanel changes recolor the reader without invalidating it. Toggling night mode during playback leaves the overlay color chosen for the old background — e.g. a black overlay at 20% alpha on a black background, an effectively invisible highlight — until the activity restarts. (REM-37)

### PB-35: lid VIEW-Intent Path Navigates Twice — LOW
In `IsiActivity.tryGetIntentResultFromView`, the `lid` branch calls `jumpToAri(ari)` as a side effect *and* returns an `IntentResult`, so `onCreate` runs the display/history/back-forward flow a second time. The back/forward list gets two identical entries, so the first "back" press is a no-op. The `ari` branch directly above does it correctly. (REM-38)

### PB-36: ShareUrl Progress Dialog Leaks Across Rotation — LOW-MEDIUM
`ShareUrl` shows a plain `AlertDialog` across an async OkHttp call; nothing dismisses it on activity destruction, and the completion guard checks only `activity.isFinishing` — `false` during a configuration-change destroy (`isDestroyed` is the right check). Rotating during the request leaks the dialog window and runs `onSuccess`/`onFinally` against the destroyed activity (snackbar on a detached hierarchy, `mode.finish()` on a stale action mode). (REM-38)

### PB-37: HistoryAdapter Click Handler Missing the NO_POSITION Guard — LOW
`IsiActivity`'s history-dialog adapter calls `history.getEntry(holder.bindingAdapterPosition)` without the `!= -1` guard that every other adapter in the file uses; a click delivered during dialog dismissal/layout throws `IndexOutOfBoundsException`. (REM-38)
