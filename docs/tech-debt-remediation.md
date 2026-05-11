# Tech Debt Remediation Plan

This document provides a prioritized remediation plan for each tech debt item identified in [tech-debt.md](tech-debt.md). Each task is scoped to a specific module or subsystem, evaluated using the BRICE framework.

## BRICE Evaluation Framework

| Dimension | Description | Scale |
|-----------|-------------|-------|
| **B** — Business Impact | How much does fixing this benefit users, stability, or maintainability? | 1 (low) – 5 (critical) |
| **R** — Risk of Inaction | What happens if we don't fix this? Data loss? Security? Developer churn? | 1 (low) – 5 (critical) |
| **I** — Implementation Cost | Developer time and effort (inverse: 5 = trivial, 1 = massive rewrite) | 1 (months) – 5 (hours) |
| **C** — Confidence | How confident are we that the fix works and doesn't introduce regressions? | 1 (risky) – 5 (certain) |
| **E** — Ecosystem Alignment | Does this align with modern Android best practices and library support? | 1 (niche) – 5 (standard) |

**BRICE Score** = (B + R + I + C + E) / 5. Higher = do first.

---

## Phase 1: Quick Wins & Safety Fixes (BRICE ≥ 4.0)

### REM-01: Fix SongBookUtil Resource Leak & Unsafe Deserialization ✅ COMPLETED
**Addresses:** TD-04, PB-06  
**Module:** Songs  
**BRICE:** B=4 R=5 I=4 C=5 E=5 → **4.6**

**Completed in:** `1b9b74d9` (2026-04-11)

**What was done (steps 1-3):**
1. ✅ Wrapped `Response` and streams in try-with-resources to prevent leaks
2. ✅ Added response body size validation (rejects >50MB)
3. ✅ Introduced `SafeObjectInputStream` with class whitelist (`java.util.*`, `java.lang.*`, and Song model classes only) to prevent deserialization attacks. Also added `instanceof` check before casting.
4. ⬜ Long-term: Migrate song download format from Java serialization to JSON (Kotlinx Serialization) — not yet started, requires server change

**Tests added:** 7 unit tests in `SongBookUtilTest.java` covering single/multiple songs, empty list, gzip, Unicode, multiple lyrics, and null fields.

---

### REM-02: Fix Preferences hold()/unhold() Safety ✅ COMPLETED
**Addresses:** TD-09, PB-07  
**Module:** Afw  
**BRICE:** B=3 R=4 I=5 C=5 E=4 → **4.2**

**Completed in:** `0b61084a`, `cffe49ad`, `80cd9f34` (2026-04-10/11)

**What was done:**
1. ✅ Added `Preferences.withTransaction(Runnable)` method that wraps `hold()/unhold()` in try/finally
2. ✅ Migrated all 6 existing `hold()`/`unhold()` call sites to `withTransaction` (`CurrentReading.java`, `DailyVerseData.java`, `SyncSettingsActivity.java`, `IsiActivity.kt`, `InternalDbHelper.java`, `SecretSyncDebugActivity.kt`)
3. ✅ Made `hold()` and `unhold()` private — stronger than a safety timeout since external code can no longer call them directly, eliminating the misuse risk entirely

**Note:** Two call sites (`SyncSettingsActivity`, `SecretSyncDebugActivity`) were previously missing try/finally, meaning an exception would have buffered preference writes indefinitely. This bug is now fixed.

---

### ~~REM-03: Replace LocalBroadcastManager~~ ✅ COMPLETED (2026-04-22)
**Addresses:** TD-03 (LocalBroadcastManager)  
**Module:** Cross-cutting  
**BRICE:** B=3 R=3 I=3 C=4 E=5 → **3.8**

**Outcome:** All 24 files that used `App.getLbm()` now talk through a single Kotlin event-bus object at `yuku.alkitab.base.events.AppEvents`. Each former `ACTION_*` broadcast became a named `MutableSharedFlow` (most `<Unit>`, one `<Boolean>` for the version-list refreshing spinner, and one `<DevotionDownloadedEvent>` data-class-typed bus carrying the kind name + `yyyyMMdd` date). Buses are configured with `extraBufferCapacity = 16, onBufferOverflow = DROP_OLDEST` — `tryEmit` is guaranteed to succeed regardless of buffer pressure, and when bursts exceed the buffer the oldest queued "reload" signal is dropped rather than the newest (which is what a collector catching up actually needs). Preserves LBM's fire-and-forget, in-process, no-replay semantics.

**What was done:**

1. **Senders** — every `App.getLbm().sendBroadcast(new Intent(ACTION_*))` replaced with a `@JvmStatic` emit helper on `AppEvents` (e.g. `AppEvents.emitAttributeMapChanged()`). Touched: `IsiActivity`, `SyncAdapter` (9 call sites), `MarkerListActivity` (4 call sites), `SecretSyncDebugActivity`, `VersionListFragment` (5 call sites), `VersionsActivity`, `VersionConfigUpdaterService`, `VersionDownloadCompleteReceiver`, `DownloadMapper`, `DevotionDownloader`, `ProgressMarkRenameDialog`, `DataTransferFragment`, `DisplayFragment`, `CurrentReading`.

2. **Receivers (Kotlin activities/fragments)** — replaced `BroadcastReceiver` + `registerReceiver`/`unregisterReceiver` with `lifecycleScope.launch { AppEvents.foo.collect { ... } }` in `IsiActivity`, `MarkerListActivity`, `VersionListFragment`. The associated `onDestroy` overrides (only doing `unregisterReceiver`) became no-ops and were deleted.

3. **Receivers (Java activities/fragments)** — added two Java-friendly helpers on `AppEvents`: `observe(LifecycleOwner, Flow<*>, Runnable)` collects until `DESTROYED`, and `observeWhileStarted` (+ typed `observeWhileStartedWithValue`) uses `repeatOnLifecycle(STARTED)` for onStart/onStop-scoped flows. Migrated `MarkersActivity`, `ReadingPlanActivity`, `SyncSettingsActivity.SyncSettingsFragment`, `DailyVerseAppWidgetConfigurationActivity` (lifecycle = DESTROYED) and `DevotionActivity` (lifecycle = STARTED, typed value consumer).

4. **Receivers (Java custom views)** — `LabeledSplitHandleButton.init()` and `LeftDrawer.Text.onFinishInflate` call a third helper, `AppEvents.observeOnView(view, flow, runnable)`. The helper installs an `OnAttachStateChangeListener`: collection starts on attach (launching a fresh `MainScope` job each time) and is cancelled on detach, so a view that's inflated but never attached does not leak a live collector, and a re-attached view automatically resubscribes. Contract: call once per view instance — calling from `onAttachedToWindow` would accumulate a listener per attach cycle.

5. **Stale constants removed** — `IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED` / `ACTION_ACTIVE_VERSION_CHANGED` / `ACTION_NIGHT_MODE_CHANGED` / `ACTION_NEEDS_RESTART`, `MarkersActivity.ACTION_RELOAD`, `MarkerListActivity.ACTION_RELOAD`, `ReadingPlanActivity.ACTION_READING_PLAN_PROGRESS_CHANGED`, `SyncSettingsActivity.ACTION_RELOAD`, `CurrentReading.ACTION_CURRENT_READING_CHANGED`, `DevotionDownloader.ACTION_DOWNLOADED`, `VersionListFragment.ACTION_RELOAD` / `ACTION_UPDATE_REFRESHING_STATUS` / `EXTRA_refreshing`, plus the private `DevotionDownloader.broadcastDownloaded` helper.

6. **Dependency removed** — `androidx-localbroadcastmanager` deleted from `gradle/libs.versions.toml` and `Alkitab/build.gradle.kts`. `App.getLbm()` helper and the `LocalBroadcastManager` import removed from `App.java`.

Coroutines and `lifecycleScope` were already on the classpath transitively through `androidx.fragment:fragment-ktx:1.8.9` (pulling `kotlinx-coroutines-android:1.9.0` and `lifecycle-runtime-ktx:2.7.0`); no new dependency was needed.

**Verified:** `./gradlew :Alkitab:assemblePlainDebug` plus `testPlainDebugUnitTest testPlainReleaseUnitTest` — all 313 unit tests pass.

---

### ~~REM-04: Fix FCM Token Re-registration Retry~~ ✅ COMPLETED (2026-04-22)
**Addresses:** PB-04  
**Module:** Sync  
**BRICE:** B=4 R=4 I=5 C=4 E=4 → **4.2**

**Outcome:** `Sync.sendFcmRegistrationId` now has a two-tier recovery path. In-process: a failed send schedules up to three retries on a single-thread `ScheduledExecutorService` (`fcmRetryExecutor`, daemon) at 1 min / 5 min / 30 min; any retry that succeeds clears the persistent flag and short-circuits the chain. Cross-launch: every send-failure branch (`!response.success`, `IOException | JsonIOException`, `JsonSyntaxException`) sets `Prefkey.fcm_registration_pending = true`, the success branch sets it to `false`, and `App.staticInit()` calls the new `Sync.retryPendingFcmRegistrationIfNeeded(registrationId)` with the FCM id returned by `Fcm.renewFcmRegistrationIdIfNeeded` — so if the in-process chain never completed (e.g. process died, network was off the whole 30 min), the next launch that already has a stored FCM id re-sends it. If no id is stored yet, the existing listener path (`Fcm.renewFcmRegistrationIdIfNeeded(Sync::notifyNewFcmRegistrationId)`) still fires `notifyNewFcmRegistrationId` once registration completes, which then runs its own retry chain.

**What was done:**
1. ✅ Added `Prefkey.fcm_registration_pending` to `Prefkey.kt` with a kdoc comment describing the contract.
2. ✅ Added `FCM_RETRY_DELAYS_MS = {1min, 5min, 30min}`, `fcmRetryExecutor` (single-thread, daemon), `pendingFcmRetry` (`AtomicReference<ScheduledFuture<?>>`), and `scheduleFcmRegistrationRetries(registrationId, attemptIndex)` in `Sync.java`. Each scheduled attempt re-reads `Prefkey.sync_simpleToken` so a mid-chain logout stops retrying.
3. ✅ `notifyNewFcmRegistrationId` now schedules a retry chain when the inline send fails.
4. ✅ Public `retryPendingFcmRegistrationIfNeeded(@NonNull String registrationId)` reads the pending flag and (if set) re-enters `notifyNewFcmRegistrationId`. Called once from `App.staticInit()` with the id returned by `Fcm.renewFcmRegistrationIdIfNeeded`.
5. ✅ Bumped all four send-failure log sites in `sendFcmRegistrationId` from `AppLog.d` to `AppLog.w` and added `Preferences.setBoolean(Prefkey.fcm_registration_pending, …)` on each path (`true` on failure, `false` on success).

**Not retried on:** if the response is valid but `success == false` (server explicitly rejected the registration id), the flag is still set and the retry chain still runs. This is intentional — the plan did not distinguish retriable vs. terminal failures, and rejections are rare enough that the extra requests are harmless. If this turns out to generate noise, a future change can key off `response.message`.

**Verified:** `./gradlew :Alkitab:assemblePlainDebug testPlainDebugUnitTest testPlainReleaseUnitTest` — all 313 unit tests pass.

---

### REM-05: Refactor DevotionDownloader threading ✅ COMPLETED
**Addresses:** TD-06 (DevotionDownloader)  
**Module:** Devotions  
**BRICE:** B=2 R=3 I=5 C=5 E=5 → **4.0**

**Completed in:** `758793f5` (2026-04-20)

**What was done:**
1. ✅ Replaced `extends Thread` with a single-thread `ExecutorService` (`Executors.newSingleThreadExecutor()`)
2. ✅ Replaced `queue_.wait()/notify()` with `LinkedBlockingDeque.take()` — blocking take provides natural backpressure
3. ✅ Added `shutdown()` method that sets a `volatile boolean shutdown_` flag and calls `executor_.shutdownNow()`; the download loop checks the flag and propagates `InterruptedException` by re-interrupting and breaking out
4. ✅ Hardcoded `SystemClock.sleep(50)` removed
5. ✅ HTTP response handling now goes through `Connections.downloadString(url)`, which handles stream cleanup internally

**Remaining:** The broadcast at the end of `downloadLoop` still uses `App.getLbm()` (LocalBroadcastManager). That is tracked separately under REM-03.

---

## Phase 2: Architecture Improvements (BRICE 3.0–3.9)

### REM-06: Extract IsiActivity Gesture Handling ✅ COMPLETED
**Addresses:** TD-01 (gesture cluster)  
**Module:** Main reader  
**BRICE:** B=4 R=3 I=3 C=3 E=4 → **3.4**

**What was done:**
1. ✅ Created `Alkitab/src/main/java/yuku/alkitab/base/widget/ReaderGestureHandler.kt` (~125 lines). The class implements **three** listener interfaces in one place: `TwofingerLinearLayout.Listener` (split-root pinch + swipe), `GotoButton.FloaterDragListener` (drag-from-goto-button), and `Floater.Listener` (final ari selection).
2. ✅ Moved the three inline lambda objects out of `IsiActivity.kt`: the `splitRoot_listener` (75 lines, two-finger pinch/scale/dragX/dragY/end + one-finger left/right), `bGoto_floaterDrag` (18 lines, floater drag-start/move/complete), and `floater_listener` (3 lines, ari selection).
3. ✅ Defined two interfaces following the REM-07 pattern: `ReaderGestureHost.kt` (read-only state — `chapter_1`, `activeSplit0Book`, `activeSplit0Version`, `floater`, `textAppearancePanel`, plus pre-computed `gestureDisplayDensity` and `defaultUkuranHuruf2` to avoid forcing the handler to hold a `Resources` reference) and `ReaderGestureActions.kt` (write-side triggers — `onFloaterAriSelected`, `goToPreviousChapter`, `goToNextChapter`, `applyPreferences`, `setFullScreenWithDrawerHandle`).
4. ✅ `IsiActivity` now implements both interfaces, holds a `gestureHandler` lazy field, and wires the handler into `splitRoot.setListener`, `bGoto.setFloaterDragListener`, and `floater.setListener` (was previously three different objects).
5. ✅ Behavior preserved verbatim: split-pane drag, pinch zoom (with the 2f–42f clamp), one-finger left/right chapter swipe, two-finger horizontal drag for multi-chapter skip, two-finger vertical drag toggling fullscreen (paired with `leftDrawer.handle.setFullScreen` under a single new action). Gesture state (`startFontSize`, `startDx`, `moreSwipeYAllowed`, `chapterSwipeCellWidth`, `floaterLocationOnScreen`) now lives on the handler instead of inline objects.

**Result:** `IsiActivity.kt` shrank from 2452 → 2371 lines (−81 lines). Gesture-state fields and 96 lines of listener code are gone from the activity; 13 lines of host/actions implementations remain as the seam between the activity and the new handler.

**What stayed in `IsiActivity`:** the gesture-triggered Activity methods themselves (`bLeft_click`, `bRight_click`, `jumpToAri`, `applyPreferences`, `setFullScreen`) and the existing `leftDrawer.handle.setFullScreen` follow-up call — all wrapped in thin action overrides.

**Files touched:**
- `Alkitab/src/main/java/yuku/alkitab/base/widget/ReaderGestureHandler.kt` (new, 125 lines)
- `Alkitab/src/main/java/yuku/alkitab/base/widget/ReaderGestureHost.kt` (new, 38 lines)
- `Alkitab/src/main/java/yuku/alkitab/base/widget/ReaderGestureActions.kt` (new, 27 lines)
- `Alkitab/src/main/java/yuku/alkitab/base/IsiActivity.kt` (modified: −81 lines, class signature gains two interfaces, four import lines added)

**Difficulty:** Medium (actually 2-3 hours, came in well under the 4-6 hour estimate).

---

### REM-07: Extract IsiActivity Action Mode ✅ COMPLETED
**Addresses:** TD-01 (action mode cluster)  
**Module:** Main reader  
**BRICE:** B=4 R=3 I=3 C=3 E=4 → **3.4**

**Completed in:** `89a1894e` (REM-07 refactor) + `716eb1ce` (follow-up split-1 fix, 2026-04-16)

**What was done:**
1. ✅ Created `VerseActionModeController.kt` (~593 lines) implementing `ActionMode.Callback`. Moved the entire ~500-line `actionMode_callback` object from `IsiActivity.kt` into this class.
2. ✅ Defined two interfaces: `VerseActionModeHost` (queries activity state — selected verses, versions, book data, chapter) and `VerseActionModeActions` (callbacks back into the activity — navigate, show toasts, etc.)
3. ✅ Extracted pure text-building logic into `VerseTextFormatter` (no Android dependencies, purely testable under plain JUnit)
4. ✅ Moved `RibkaEligibility` to a standalone top-level file `RibkaEligibility.kt`
5. ✅ Added `mockk` to the test classpath. Added 26 unit tests: 11 pure-JUnit tests for `VerseTextFormatterTest`, 15 Robolectric tests for `VerseActionModeControllerTest` (menu visibility rules, click routing)
6. ✅ Follow-up PR `716eb1ce` fixed the split-1 share URL metadata bug (PB-08) that had been preserved verbatim in the REM-07 refactor. Four regression tests added for copy/share split-0/1 metadata routing.

**Result:** `IsiActivity.kt` shrank from 2894 → 2320 lines (−574 lines). Action mode is now independently testable without instantiating the Activity.

**Difficulty:** Medium (6-8 hours). Risk: action mode references many Activity-level fields and methods.

---

### REM-08: Extract IsiActivity Split View Manager
**Addresses:** TD-01 (split view cluster)  
**Module:** Main reader  
**BRICE:** B=3 R=2 I=3 C=4 E=4 → **3.2**

**Steps:**
1. Create `SplitViewManager.kt` containing `openSplitDisplay()` (line 2108), `closeSplitDisplay()` (line 2168), `displaySplitFollowingMaster()` (line 2365), `loadSplitVersion()` (line 1458) — currently scattered across `IsiActivity.kt`
2. Encapsulate `activeSplit1`, split layout views, and split-related preferences
3. Define callbacks: `onSplitOpened`, `onSplitClosed`, `onSplitVersionChanged`
4. `IsiActivity` holds a `SplitViewManager` instance and delegates split operations

**Difficulty:** Medium (4-6 hours).

---

### REM-09: Introduce ViewModel for IsiActivity
**Addresses:** TD-07  
**Module:** Main reader  
**BRICE:** B=4 R=3 I=2 C=3 E=5 → **3.4**

**Steps:**
1. Create `IsiViewModel : ViewModel()` with:
   - `activeVersion: StateFlow<MVersion>`
   - `currentAri: StateFlow<Int>`
   - `versesDataModel: StateFlow<VersesDataModel>`
   - `selectedVerses: StateFlow<Set<Int>>`
   - `splitVersion: StateFlow<MVersion?>`
2. Move version loading, chapter display logic, and verse selection state from `IsiActivity` into `IsiViewModel`
3. `IsiActivity` observes StateFlows and updates UI
4. Navigation history moves to ViewModel (survives rotation)

**Prerequisite:** ~~REM-06~~✅, ~~REM-07~~✅, REM-08 should be done first to reduce IsiActivity size before extracting ViewModel.

**Difficulty:** Hard (2-3 days). Risk: extensive refactoring of the largest file in the codebase.

---

### REM-24: Refactor S.kt Service Locator
**Addresses:** TD-15  
**Module:** Cross-cutting (50 files)  
**BRICE:** B=4 R=2 I=2 C=3 E=5 → **3.2**

**Current state:** `S.kt` (313 lines) is a Kotlin `object` singleton mixing database access (`db`, `songDb`), active version state, and UI dimensions (`CalculatedDimensions`). Imported by 50 files with 161+ call sites. Untestable without a full Android environment.

**Recommended approach: Incremental interface extraction, then manual DI**

Hilt/Dagger adds significant complexity (annotation processing, code generation) for a codebase that doesn't yet use any DI. A lighter approach is to extract interfaces first, then provide them via a simple app-level container.

**Steps:**

**Step 24a: Extract interfaces (no DI yet, no callers change)**
1. Create `StorageProvider` interface:
   ```kotlin
   interface StorageProvider {
       val db: InternalDb
       val songDb: SongDb
   }
   ```
2. Create `VersionManager` interface:
   ```kotlin
   interface VersionManager {
       fun activeVersion(): Version
       fun activeMVersion(): MVersion
       fun activeVersionId(): String
       fun setActiveVersion(mv: MVersion)
       fun getVersionFromVersionId(versionId: String?): MVersion?
       fun getAvailableVersions(): List<MVersion>
       fun getMVersionInternal(): MVersionInternal
   }
   ```
3. Create `UiDimensionsProvider` interface:
   ```kotlin
   interface UiDimensionsProvider {
       fun applied(): CalculatedDimensions
       fun recalculate()
   }
   ```
4. Make `S` implement all three interfaces, delegating to its existing internal holders. This is a no-op refactor — all existing `S.db` / `S.applied()` / `S.activeVersion()` calls keep working.

**Step 24b: Move UI dialogs out of S**
1. Move `openVersionsDialog()` and `openVersionsDialogWithNone()` (lines 271-320) into a standalone `VersionDialogHelper` object or extension function. These are UI operations that don't belong in a service locator.

**Step 24c: Fix thread safety**
1. Make `activeVersion()` / `activeMVersion()` / `activeVersionId()` getters `@Synchronized` to match the setter
2. Or better: replace the three mutable fields with a single `AtomicReference<ActiveVersionState>` data class to ensure atomic reads

**Step 24d: Introduce app-level service container**
1. Create `AppServices` class initialized in `App.onCreate()`:
   ```kotlin
   class AppServices(
       val storage: StorageProvider,
       val versions: VersionManager,
       val uiDimensions: UiDimensionsProvider,
   )
   ```
2. Initialize in `App`: `val services = AppServices(S, S, S)` — initially delegates back to `S`
3. New code uses `App.services.storage.db` instead of `S.db`
4. Gradually migrate existing callers (50 files, can be done file-by-file)
5. In tests, provide fake implementations of the interfaces

**Step 24e: (Optional, later) Migrate to Hilt**
If the project adopts Hilt for other reasons (e.g., ViewModel injection in REM-09), the interfaces from Step 24a become `@Provides` targets naturally.

**Difficulty:** Medium-Hard (2-3 days for steps 24a-24c, then ongoing migration for 24d). Step 24a is the critical enabler — once interfaces exist, the rest is incremental.

---

### REM-10: Migrate InternalDb to Room (Markers Table)
**Addresses:** TD-02  
**Module:** Storage — Markers subsystem  
**BRICE:** B=4 R=3 I=2 C=3 E=5 → **3.4**

**Steps — incremental, table by table, starting with Markers:**
1. Add Room dependency (not yet present in any build.gradle). Define Room entities: `MarkerEntity`, `LabelEntity`, `MarkerLabelEntity`
2. Define `MarkerDao` with type-safe queries replacing the 7 `rawQuery()` calls scattered across `InternalDb.java` (lines 201, 204, 264, 666, 1059, 1319, 1342). Marker-related queries are at lines 201, 204, and 264.
3. Create `AppDatabase : RoomDatabase()` with migration from existing SQLite schema (table/column constants are in `Db.java` with nested static classes per table)
4. Replace `InternalDb.insertOrUpdateMarker()` (line 151), `deleteMarkerById()` (line 171), `listMarkersForAriKind()` (line 135), etc. with DAO calls
5. Keep `InternalDb` as a facade initially, delegating to Room internally
6. Test: Write instrumented tests for `MarkerDao` using Room's in-memory database

**Difficulty:** Hard (3-5 days for Markers table alone). Subsequent tables (Version, ReadingPlan, Devotion, SyncShadow) can follow the same pattern.

---

### REM-11: Migrate InternalDb to Room (Version Table)
**Addresses:** TD-02  
**Module:** Storage — Versions subsystem  
**BRICE:** B=3 R=2 I=3 C=3 E=5 → **3.2**

**Steps:**
1. Define `VersionEntity` Room entity
2. Define `VersionDao` with queries: `listAllVersions()`, `insertOrUpdateVersion()`, `deleteVersion()`, `setVersionActive()`
3. Add Room migration for the `Version` table
4. Replace `InternalDb.listAllVersions()` (line 531) with DAO call
5. Update `S.getAvailableVersions()` to use the DAO

**Difficulty:** Medium (1-2 days). Lower risk than Markers since the Version table is simpler.

---

### REM-12: Replace DragSortListView with ItemTouchHelper ✅ COMPLETED
**Addresses:** TD-12  
**Module:** Markers (label management)  
**BRICE:** B=2 R=2 I=4 C=4 E=5 → **3.4**

**Completed changes:**
- `MarkersActivity.java` rewritten to use `RecyclerView` + custom `ItemTouchHelper.Callback` (drag-handle initiated via `startDrag`, divider guarded via `getMovementFlags` and `canDropOver`). Native context menu replaced with a `PopupMenu` shown on long-press.
- `VersionListFragment.kt` converted to `RecyclerView.Adapter<VersionItemHolder>` with `ItemTouchHelper` installed only when `downloadedOnly == true`.
- DB persistence still uses the existing pair-wise `reorderLabels` / `reorderVersions`. Instead of committing on every `onMove`, the adapter snapshots its item list at drag start and issues a single reorder call in `clearView` using `snapshot[startPos]` and `snapshot[endPos]`.
- Translucent-white drag overlay (`0x22ffffff`) preserved via `onSelectedChanged` / `clearView`.
- Three layouts (`activity_markers.xml`, `fragment_versions_all.xml`, `fragment_versions_downloaded.xml`) switched to `androidx.recyclerview.widget.RecyclerView`.
- `DragSortListView` module removed from `settings.gradle`, `Alkitab/build.gradle`, and the filesystem.

**Difficulty:** Easy-Medium (4-6 hours).

---

### ~~REM-14: Replace material-dialogs with Material 3~~ ✅ COMPLETED
**Addresses:** TD-12  
**Module:** Cross-cutting UI  
**BRICE:** B=2 R=3 I=3 C=4 E=5 → **3.4**

**Completed in:** `ca9a9138` (PR #140)

**Outcome:** All `com.afollestad.materialdialogs` usages replaced with `MaterialAlertDialogBuilder` (Material 3). The `material-dialogs` dependencies are gone from the build, and no remaining imports exist in the codebase.

---

## Phase 3: Modernization (BRICE 2.5–3.0)

### REM-15: Introduce Kotlin Coroutines
**Addresses:** TD-06 (threading)  
**Module:** Cross-cutting  
**BRICE:** B=4 R=2 I=2 C=3 E=5 → **3.2**

**Steps — incremental by module:**

**Step 15a: Sync module**
1. Add `kotlinx-coroutines-android` dependency
2. Convert `SyncAdapter` (WorkManager `Worker`) to `CoroutineWorker`
3. Replace `Thread`-based sync execution with `withContext(Dispatchers.IO)`
4. Add `supervisorScope` for independent entity sync (markers, pins, history can fail independently)

**Step 15b: Devotion downloads**
1. Replace `DevotionDownloader` thread with a coroutine-based downloader using `Channel`
2. Use `flow {}` for download progress tracking
3. Integrate with `DevotionActivity` via `lifecycleScope`

**Step 15c: Search engine**
1. Wrap `SearchEngine.searchByGrep()` in `withContext(Dispatchers.Default)`
2. Add `yield()` between book iterations for cancellation support
3. Return results via `Flow<SearchResult>` for progressive display

**Step 15d: Version loading**
1. Make `Version.loadChapterText()` suspending or wrap in `Dispatchers.IO`
2. IsiActivity display pipeline becomes: `viewModelScope.launch { loadAndDisplay() }`

**Difficulty:** Hard (1-2 weeks total, but can be done module-by-module over time).

---

### REM-16: Convert Core Java Files to Kotlin
**Addresses:** TD-11  
**Module:** Various  
**BRICE:** B=3 R=1 I=3 C=3 E=5 → **3.0**

**Steps — ordered by value and risk (lowest risk first):**

| Priority | File | Lines | Risk | Notes |
|----------|------|-------|------|-------|
| 1 | `Highlights.java` | ~200 | Low | Standalone utility, good test coverage target |
| 2 | `TargetDecoder.java` | ~150 | Low | Has tests, pure logic |
| 3 | `Jumper.java` | ~200 | Low | Has tests, pure logic |
| 4 | `QueryTokenizer.java` | ~100 | Low | Has tests |
| 5 | `DevotionDownloader.java` | 111 | Low | Small, standalone thread (do with REM-05) |
| 6 | `Provider.java` | ~200 | Medium | Content provider, external API contract |
| 7 | `SongBookUtil.java` | 219 | Medium | Network + deserialization (do with REM-01) |
| 8 | ~~`VerseRenderer.java`~~ | ~~423~~ | ~~Medium~~ | ✅ ported (REM-26) |
| 9 | `SearchEngine.java` | 537 | Medium | Performance-critical, no tests |
| 10 | `Sync.java` | 508 | High | Threading + network, many call sites |
| 11 | `InternalDb.java` | 1771 | High | Core database, skip if doing Room migration (REM-10) |

Use Android Studio's "Convert Java File to Kotlin" as a starting point, then manually clean up:
- Replace `@Nullable`/`@NonNull` with Kotlin nullability
- Replace `static` methods with top-level or companion object
- Replace `for` loops with `forEach`/`map`
- Replace `switch` with `when`
- Use data classes where appropriate

**Difficulty:** Varies (1 hour to 1 day per file depending on size). Do alongside other changes to avoid merge conflicts.

---

### ~~REM-17: Migrate Build to Kotlin DSL & Version Catalogs~~ ✅ COMPLETED (2026-04-20)
**Addresses:** TD-12 (build system)  
**Module:** Build  
**BRICE:** B=2 R=1 I=3 C=4 E=5 → **3.0**

**Outcome:**
- Created `gradle/libs.versions.toml` centralising all 30+ dependency versions, 20+ library coordinates, and 8 plugin IDs. Firebase BOM dependencies declared without version (BOM-managed). SDK integer versions (`compileSdk`, `minSdk`, `targetSdk`) stored as strings and accessed via `.get().toInt()` in build files.
- `settings.gradle` → `settings.gradle.kts`: added `pluginManagement` and `dependencyResolutionManagement` (with `FAIL_ON_PROJECT_REPOS`) so all repository declarations are centralised in one place. `jitpack.io` (needed for `FancyShowCaseView`) declared there. Foojay toolchain resolver kept inline (version catalog not yet available at settings `plugins {}` eval time).
- Root `build.gradle` → `build.gradle.kts`: replaced `buildscript` + `allprojects` with a lean `plugins { ... apply false }` block. The `ext {}` block is gone — all versions live in the catalog.
- `Alkitab/build.gradle` → `Alkitab/build.gradle.kts`: `CopyProprietaryAssetsTask` converted to Kotlin abstract class with `@Inject constructor(private val fs: FileSystemOperations)`. `firebaseApiKeyProblem` converted to a top-level function with `@Suppress("UNCHECKED_CAST")`. `String.capitalize()` (deprecated in Kotlin 2.x) replaced with `replaceFirstChar { it.uppercaseChar() }`. All `$androidxActivityVersion` ext references replaced with `libs.androidx.activity.ktx` catalog accessors. Server-host constants (`SERVER_HOST`, `RIBKA_FUNCTIONS_HOST`, `RIBKA_FUNCTIONS_HOST_DEBUG`) inlined as `val` in this file and duplicated in `AlkitabFeedback` (only two consumers; no ext lookup needed).
- All 15 library module `build.gradle` files converted to Kotlin DSL. `apply plugin:` replaced with `alias(libs.plugins.*)`. `compileSdkVersion`/`minSdkVersion`/`targetSdkVersion` methods replaced with `compileSdk`/`minSdk`/`targetSdk` property assignments. `minifyEnabled` → `isMinifyEnabled`, `shrinkResources` → `isShrinkResources`. Stale `repositories {}` blocks in `AlkitabYes2` and `AlkitabFeedback` removed (centralised in settings). `kotlin-android` / `kotlin-parcelize` plugin IDs replaced with their full `org.jetbrains.kotlin.*` IDs.
- `apply plugin: 'com.google.gms.google-services'` (legacy bottom-of-file pattern) moved to the `plugins {}` block at the top of `Alkitab/build.gradle.kts`.

**Difficulty:** Medium (1-2 days). Mostly mechanical but Groovy-specific constructs need manual translation.

---

### REM-18: Add Test Coverage for Core Modules
**Addresses:** TD-13  
**Module:** Cross-cutting  
**BRICE:** B=4 R=3 I=2 C=3 E=5 → **3.4**

**Steps — prioritized by risk coverage:**

**Step 18a: Highlight encoding tests** ✅ COMPLETED
**Completed in:** `513961b9` / `1ca73821` (2026-04-16)
1. ✅ Added 31 unit tests in `HighlightsTest.kt`: encode/decode round-trip, hash verification, partial-highlight guard, alphaMix with ARGB input
2. ✅ Fixed `Highlights.alphaMix()` ARGB leak: changed `0xa0000000 | colorRgb` to `0xa0000000 | (colorRgb & 0x00ffffff)` so the output alpha is always `0xA0` regardless of what the caller passes
3. ✅ Added test-scope no-op shadows for `android.util.Log` and `com.google.firebase.crashlytics.FirebaseCrashlytics` — these unblock all future Alkitab module unit tests that touch `AppLog`

**Step 18b: Sync protocol tests** ✅ COMPLETED
**Completed in:** `b4bce934` (2026-04-16)
1. ✅ Added 101 unit tests across `SyncDeltaTest.kt`, `Sync_MabelTest.kt`, `Sync_PinsTest.kt`, `Sync_RpTest.kt` — covers `SyncAdapter.patchNoConflict` delta application (add/mod/del, missing GIDs, conflict, idempotency), `Sync.entitiesEqual`, `SyncUtils.findEntity/isSameContent`, `Sync_Mabel.updateMarker/Label/Marker_Label`, and `Content equals/hashCode/toString` for all three sync sets
2. ✅ Fixed `Sync_Pins.Content.equals()`: was sorting copies but comparing the original unsorted lists — now compares sorted copies. Fixed `hashCode()` to match order-insensitive equals (violations would cause misbehavior inside `HashMap`/`HashSet`)
3. ✅ Added test-scope stub for `android.util.Pair` so `patchNoConflict` runs without Robolectric

**Step 18c: InternalDb tests (or Room DAO tests)** ✅ COMPLETED
1. ✅ Added Robolectric (`4.14.1`) as a `testImplementation` dependency and enabled `testOptions.unitTests.includeAndroidResources` in `Alkitab/build.gradle` — Robolectric is required because `InternalDbHelper` extends Android's `SQLiteOpenHelper`
2. ✅ Added `InternalDbTest.kt` under `Alkitab/src/test/java/yuku/alkitab/base/storage/` using `RobolectricTestRunner` with `@Config(application = Application::class)` so `yuku.alkitab.base.App.onCreate` (Firebase / PRDownloader / FCM) doesn't run. Reuses the existing test-scope shadows of `android.util.Log` and `com.google.firebase.crashlytics.FirebaseCrashlytics` (added in REM-18a) so `AppLog`'s static initializer loads without bootstrapping Firebase. `yuku.afw.App.context` is set manually in `@Before`; because no `sync_simpleToken` preference is present, `Sync.notifySyncNeeded` early-returns and no background work fires
3. ✅ Test names follow the Kotlin backtick-sentence convention from CLAUDE.md. Covers marker CRUD (`insertMarker`, `insertOrUpdateMarker`, `getMarkerById/Gid`, `listMarkersForAriKind`, `listAllMarkers`, `deleteMarkerById` with cascade to `Marker_Label`, `countMarkersForBookChapter`), label ordering (`insertLabel`, `getLabelMaxOrdering`, `reorderLabels` up/down, `sortLabelsAlphabetically`, `listLabelsByMarker` ordering), highlight storage (`updateOrInsertHighlights` insert/update/delete, `updateOrInsertPartialHighlight` including dedup of sync-duplicates, `getHighlightColorRgb` single/multi-verse), and attribute loading (`putAttributes` bookmarks, notes, multi-verse highlight spread, ordering by `modifyTime`, book-chapter filtering)

**Step 18d: SearchEngine tests** ✅ COMPLETED
1. ✅ Unit tests added in `SearchEngineTest.kt` — covers `ReadyTokens` construction, `satisfiesTokens`, and end-to-end `searchByGrep` against a small in-memory fake `Version`. Exercises single token, multi-token (AND) intersection, whole-word matching, quoted phrases (multiword), book-id filtering, duplicate-token de-duplication, and cross-verse-boundary rejection.
2. ✅ Runs under `RobolectricTestRunner` so `android.util.SparseBooleanArray` is a real implementation rather than the "not mocked" stub. `AppLog` is kept quiet via the existing test-scope shadows (`android.util.Log`, `FirebaseCrashlytics`) introduced for `HighlightsTest`.
3. Difficulty: Medium (4-6 hours)

**Step 18e: YES2 reader/writer round-trip tests** ✅ COMPLETED
1. ✅ Added `Yes2RoundTripTest.kt` (12 tests) and `SnappyStreamRoundTripTest.kt` (4 tests) under `AlkitabYes2/src/test/java/`. Round-trip covers: single-book uncompressed read-back of every verse; multi-book boundaries (Genesis / Exodus / Revelation) preserving book ordering and chapter offsets; BMP UTF-8 content (Indonesian, Greek, Hebrew, extended Latin) — limited to U+FFFF because `Utf8Decoder` is documented as "intentionally incomplete" and does not support 4-byte UTF-8 sequences; `dontSeparateVerses` newline-joined form; `lowercase` flag; out-of-range chapter returns null; pericope round-trip with parallels and ARI-keyed lookup; pericope empty-chapter and no-pericope-section cases; missing xref / footnote sections return null.
2. ✅ Snappy-compressed text section round-trip: writes multi-block compressed Bible text and verifies every verse decodes identically; separately verifies that a compressed file is strictly smaller than the uncompressed equivalent for highly-repetitive text.
3. ✅ Direct `SnappyOutputStream` / `SnappyInputStream` round-trip: single-block, multi-block (4 blocks with partial trailing block), mid-block `seek` crossing block boundaries, and compression-ratio check on highly-repetitive input (each 4 KB block compresses to under 400 bytes via the pure-Java codec).
4. ✅ Added a test-scope `android.util.Log` shadow at `AlkitabYes2/src/test/java/android/util/Log.java` — mirrors the one in the Alkitab module so direct `Log.e` calls in `Yes2Reader`, `SectionIndex`, and the xref/footnote sections don't trigger "Method not mocked" during plain JUnit.
5. The tests exposed a latent bug in `SnappyInputStream`: after the last byte of the last block is read, calling `read()` again increments `current_block_index` past the end and throws `ArrayIndexOutOfBoundsException` instead of returning -1. Production callers in `Yes2Reader.TextSectionReader.loadVerseText` always read exact-length ranges derived from verse `varuint` lengths, so they never hit EOF this way. Flagged in-file rather than silently worked around; see `SnappyStreamRoundTripTest.kt`.

**Step 18f: Content provider tests** ✅ COMPLETED (2026-05-11)
1. ✅ Added `ProviderTest.kt` (14 tests) under `Alkitab/src/test/java/yuku/alkitab/base/cp/` exercising every URI path the read-only [`Provider`](../Alkitab/src/main/java/yuku/alkitab/base/cp/Provider.java) supports: single-verse by ARI (with `_id`, `ari`, book short name, and verse-text assertions), single-verse by LID (resolved via `LidToAri`), range by ARI within a single chapter, range by ARI crossing a chapter boundary, range by ARI crossing a book boundary, range by LID, whole-chapter shorthand (`bbcc00-bbcc00` → all verses in that chapter), and the `bible/versions` listing query (asserts the internal version row using `AppConfig.get()` for `shortName` / `longName` / `description`).
2. ✅ Pinned the contract edges: `formatting=0` (default) strips inline formatting codes via `FormattedVerseText.removeSpecialCodes`, `formatting=1` returns the raw verse text including codes, out-of-range `bookId` (single or range) returns an empty `MatrixCursor` rather than null, an unknown URI path returns null, and `getType` returns null for every URI (production behaviour — `Provider.getType` is hardcoded to null).
3. ✅ Same Robolectric setup as steps 18c/d: `@Config(application = Application::class)` to skip `App.onCreate`; existing test-scope shadows of `android.util.Log` and `FirebaseCrashlytics` keep `AppLog` quiet; `yuku.afw.App.context` is wired in `@Before`. The fake `MVersion` returns an anonymous `Version` subclass with two books, three chapters, and one verse carrying real `@@` / `@9` / `@7` formatting codes; `S.setActiveVersion(fakeMv)` overwrites the active version after `ActiveVersionHolder`'s init naturally falls back to the placeholder DDD `MVersionInternal` (constructor-only, no asset I/O). The provider is constructed as an anonymous subclass that no-ops `onCreate` to skip `App.staticInit` (FCM / PRDownloader / FeedbackSender) while still running `attachInfo` to set up the static `UriMatcher`.

---

### REM-19: Replace PRDownloaderFixed with WorkManager Downloads
**Addresses:** TD-12  
**Module:** Downloads  
**BRICE:** B=2 R=2 I=2 C=3 E=5 → **2.8**

**Steps:**
1. Create `VersionDownloadWorker : CoroutineWorker` using OkHttp for HTTP downloads
2. Implement progress reporting via `setProgress(Data)` and observe with `WorkManager.getWorkInfoByIdLiveData()`
3. Support resume via HTTP `Range` headers (OkHttp handles this natively)
4. Replace `PRDownloader` calls in version download flow
5. Replace `PRDownloader` calls in song book download flow
6. Remove `PrDownloaderFixed` module from `settings.gradle`
7. Remove `PRDownloader.initialize()` from `App.java`

**Difficulty:** Medium-Hard (2-3 days). Risk: download resume behavior must be tested thoroughly.

---

### REM-20: Replace AmbilWarna with Material Color Picker
**Addresses:** TD-12  
**Module:** UI — Color settings  
**BRICE:** B=1 R=1 I=4 C=4 E=5 → **3.0**

**Steps:**
1. `AmbilWarnaDialog` is used in 2 files: `MarkersActivity.java` (lines 43, 233-243 — label color picker) and `TypeHighlightDialog.java` (line 19 — highlight color selection). There is no `ColorSettingsActivity`.
2. Replace with `MaterialColorPickerDialog` from a Material-compatible library or implement custom using Material 3 color palette
3. Ensure selected colors are stored in the same format (hex int)
4. Delete `AmbilWarna` module

**Difficulty:** Easy (3-4 hours).

---

### ~~REM-26: Port VerseRenderer to Kotlin~~ ✅ COMPLETED (2026-04-20)
**Addresses:** TD-10 (leftover Unicode-constants comment), TD-11 (one of the listed Java files)  
**Module:** Main reader (verse rendering)  
**BRICE:** B=3 R=2 I=4 C=5 E=5 → **3.8**

**Outcome:** `VerseRenderer.java` is now `VerseRenderer.kt` — a Kotlin `object` with `@JvmStatic` on the public `render` and `appendSuperscriptNumber` entry points, idiomatic `when` over the marker switch, range-based char checks (`text_c[3] in '1'..'4'`), and proper nullable types on optional parameters. All four parameters that the deleted `VerseRendererJavaHelper` shimmed (`lText`, `lVerseNumber`, `isVerseNumberShown`, `verseNumberText`, `highlightInfo`, `checked`, `inlineLinkSpanFactory`, `ftr`) now have Kotlin default values directly on `render`, with `verseNumberText` defaulting to `Ari.toVerse(ari).toString()`. The four callers of `VerseRendererJavaHelper.render(...)` (`MarkerListActivity`, `VerseActionModeController`, `VersesControllerImpl`, `RibkaReportActivity`) now call `VerseRenderer.render(...)` directly.

The previously-documented private helpers (`renderVerseNumber`, `processFormattingCodes`, `applyHighlight`, `bindToTextViews`, `applyParaStyle`, `simpleRender`, `processSpecialTag`, `reportInvalidSpecialTag`, `createLeadingMarginSpan`) are now genuinely `private` — Kotlin lets us tighten visibility from Java's package-private default. No public API changes; the only caller-visible adjustment is `FormattedTextResult.result` becoming a typed nullable `CharSequence?` (matching the actual Java semantics), which surfaced one previously-implicit `!!` in `MarkerListActivity`.

The undocumented Unicode constants flagged in TD-10 (the `superscriptDigits` array and `XREF_MARK`) now have inline comments naming the code points and what they're used for.

Behavior preservation enforced by the same 39 Robolectric characterization tests (`VerseRendererTest.kt`) plus 274 other Alkitab tests — all 313 pass on both `testPlainDebugUnitTest` and `testPlainReleaseUnitTest`. `VerseRendererJavaHelper.kt` deleted.

---

### ~~REM-25: Decompose VerseRenderer.render()~~ ✅ COMPLETED (2026-04-20)
**Addresses:** TD-10  
**Module:** Main reader (verse rendering)  
**BRICE:** B=3 R=2 I=4 C=4 E=4 → **3.4**

**Outcome:** The 200-line `render()` method in `VerseRenderer.java` (lines 71–271 before the refactor) is now an orchestrator that delegates to four named helpers:
- `renderVerseNumber(sb, text_c, text_len, isVerseNumberShown, verseNumberText, checked) → int` — embeds the inline verse number (and decides when `@^`/`@1`–`@4` should suppress it), returns the start position past the prefix
- `processFormattingCodes(text, text_c, text_len, sb, startPosAfterVerseNumber, verseNumberText, checked, ari, factory)` — runs the marker loop (paragraph, italic, red, line break, special tags) and flushes the trailing paragraph's `applyParaStyle`
- `applyHighlight(sb, highlightInfo, startPosAfterVerseNumber)` — attaches the `BackgroundColorSpan`, including the partial-highlight hash check
- `bindToTextViews(lText, lVerseNumber, sb, isVerseNumberShown, startPosAfterVerseNumber, verseNumberText)` — pushes the result into the optional `TextView`s

The pre-existing `applyParaStyle` and `processSpecialTag` helpers were kept as-is. No public API change. Behavior preservation is enforced by the 39 Robolectric characterization tests added in commit `ec5e058` (`VerseRendererTest.kt`), which lock in every code path including the `@@@0Body` two-paragraph quirk and the `checked=true` red-letter suppression. Full Alkitab unit test suite (313 tests) and `assemblePlainDebug` both still pass.

**Out of scope (TD-10 leftover):** the undocumented `superscriptDigits` Unicode constant on `VerseRenderer.java:23` — trivial follow-up.

---

### ~~REM-23: Port ybuild.sh to Gradle~~ ✅ COMPLETED (2026-04-16)
**Addresses:** TD-14  
**Module:** Build  
**BRICE:** B=4 R=3 I=3 C=4 E=5 → **3.8**

**Outcome:** `ybuild.sh` deleted. Production builds are now `./gradlew assemble<Flavor>Release`, working on any platform Gradle supports. The placeholder `ddd_*` Bible files moved from `Alkitab/src/main/assets/internal/` to `Alkitab/src/plain/assets/internal/` so production flavors don't inherit them. A typed `CopyProprietaryAssetsTask` per production flavor copies `$ALKITAB_PROPRIETARY_DIR/overlay/<applicationId>/text_raw/*` into `Alkitab/build/generated/proprietaryAssets/<flavor>/internal/`, wired into AGP via `androidComponents { onVariants { ... addGeneratedSourceDirectory(...) } }` so every consumer (mergeAssets, lint vital, etc.) automatically depends on it. The git commit hash is now `BuildConfig.LAST_COMMIT_HASH` (the `R.string.last_commit_hash` resource was removed). APKs are still named `Alkitab-{versionCode}-{versionName}-{commitHash}-{applicationId}-{BUILD_DIST}.apk` (with `BUILD_DIST` defaulting to `dev`). All four flavors (plain debug, yuku_alkitab, yuku_quick_bible, sabda_alkitab) verified building end-to-end.

---

**Original analysis:** Production release builds require running `ybuild.sh`, a 168-line macOS-only bash script that creates a RAM disk, copies proprietary assets from an external directory, stamps the git commit hash, runs Gradle, and renames the output APK. This cannot be run on Linux CI and prevents building with a simple `./gradlew assembleYuku_alkitabRelease`.

Gradle already handles signing (`signingConfigs.release` at `Alkitab/build.gradle:32-38`) and product flavors (lines 72-87). What's missing are 4 operations that can all be expressed as Gradle tasks.

**Steps:**

**Step 23a: Proprietary asset injection via Gradle**
1. Add a `ALKITAB_PROPRIETARY_DIR` environment variable check in `Alkitab/build.gradle` — only required for non-`plain` flavors
2. For each production flavor (`yuku_alkitab`, `yuku_quick_bible`, `sabda_alkitab`), define a mapping from flavor to its `BUILD_PACKAGE_NAME` overlay subdirectory (e.g., `yuku_alkitab` → `yuku`)
3. Register a `preBuild`-dependent task (e.g., `copyProprietaryAssets${flavorName}`) that:
   - Deletes `Alkitab/src/main/assets/internal/`
   - Copies files from `$ALKITAB_PROPRIETARY_DIR/overlay/$BUILD_PACKAGE_NAME/text_raw/*` into `Alkitab/src/main/assets/internal/`
4. Alternatively, use `sourceSets` to point each production flavor's `assets.srcDirs` to the proprietary directory directly, avoiding the copy:
   ```groovy
   yuku_alkitab {
       applicationId 'yuku.alkitab'
       assets.srcDirs = ['src/main/assets_without_internal', "$proprietaryDir/overlay/yuku"]
   }
   ```
   This would require restructuring `src/main/assets` so that `internal/` is in its own source set.

**Step 23b: Git commit hash injection via Gradle**
1. Add a task that reads the git commit hash:
   ```groovy
   def gitHash = providers.exec {
       commandLine 'git', 'log', '-1', '--format=format:%h'
   }.standardOutput.asText.get().trim()
   ```
2. Option A: Generate `last_commit.xml` as a build output (preferred — avoids modifying source):
   ```groovy
   android.applicationVariants.all { variant ->
       def task = tasks.register("generateLastCommit${variant.name.capitalize()}") {
           def outputDir = layout.buildDirectory.dir("generated/res/lastCommit/${variant.name}")
           outputs.dir(outputDir)
           doLast {
               def dir = outputDir.get().asFile
               new File(dir, "values/last_commit.xml").with {
                   parentFile.mkdirs()
                   text = """<?xml version="1.0" encoding="utf-8"?>
   <resources><string name="last_commit_hash">${gitHash}</string></resources>"""
               }
           }
       }
       variant.registerGeneratedResFolders(project.files(task.map { it.outputs.files.singleFile }))
   }
   ```
3. Option B (simpler): Use `buildConfigField` instead of a resource:
   ```groovy
   defaultConfig {
       buildConfigField 'String', 'LAST_COMMIT_HASH', "\"${gitHash}\""
   }
   ```
   Then update code that reads `R.string.last_commit_hash` to read `BuildConfig.LAST_COMMIT_HASH`. Grep for `last_commit_hash` to find all readers.

**Step 23c: Custom APK naming via Gradle**
1. Use the existing `applicationVariants.all` block (line 99 of `Alkitab/build.gradle`) to set the output filename:
   ```groovy
   android.applicationVariants.all { variant ->
       variant.outputs.all { output ->
           def flavor = variant.flavorName
           def buildType = variant.buildType.name
           def dist = System.getenv("BUILD_DIST") ?: "dev"
           def pkgName = System.getenv("BUILD_PACKAGE_NAME") ?: "plain"
           outputFileName = "Alkitab-${variant.versionCode}-${variant.versionName}-${gitHash}-${pkgName}-${dist}.apk"
       }
   }
   ```

**Step 23d: Remove RAM disk dependency**
1. The RAM disk served two purposes: build isolation and speed. Neither is necessary:
   - **Isolation:** Gradle's `build/` directory already isolates outputs. `./gradlew clean` handles cleanup.
   - **Speed:** Modern SSDs make the speed benefit negligible. CI runners typically have fast storage.
2. No Gradle changes needed — just stop using the RAM disk.

**Step 23e: Validate environment and retire ybuild.sh**
1. Add a validation task that checks required env vars for production flavors:
   ```groovy
   tasks.register("validateProductionEnv") {
       doFirst {
           if (System.getenv("ALKITAB_PROPRIETARY_DIR") == null) {
               throw new GradleException("ALKITAB_PROPRIETARY_DIR not set")
           }
           // ... check SIGN_KEYSTORE, etc.
       }
   }
   // Wire it: only for non-plain release builds
   ```
2. After all steps are verified, delete `ybuild.sh` and update documentation
3. The new build command becomes:
   ```bash
   ALKITAB_PROPRIETARY_DIR=/path/to/proprietary \
   BUILD_PACKAGE_NAME=yuku \
   BUILD_DIST=playstore \
   SIGN_KEYSTORE=/path/to/keystore \
   SIGN_ALIAS=mykey \
   SIGN_PASSWORD=secret \
   ./gradlew assembleYuku_alkitabRelease
   ```

**Difficulty:** Medium (1-2 days). Step 23a (asset injection) is the trickiest part — need to decide between copy-on-build vs. sourceSets approach. Steps 23b-23d are straightforward Gradle configuration. Low risk since existing `ybuild.sh` can remain as fallback during transition.

---

## Phase 4: Long-term / Major Refactors (BRICE < 2.5)

### REM-21: Migrate Song Storage from Parcelable to JSON
**Addresses:** TD-04 (long-term part)  
**Module:** Songs  
**BRICE:** B=3 R=3 I=1 C=2 E=5 → **2.8**

**Steps:**
1. Define `SongJsonModel` using Kotlinx Serialization with explicit field names. Note: `KpriModel/Song.java` (line 13) implements both `Serializable` and `Parcelable`, and line 10-11 has a comment acknowledging this is a "Bad decision".
2. Add database migration that reads all songs via old `Parcelable` format (currently stored as `Parcel.marshall()` byte arrays via `SongDb.marshallSong()` at line 29-35 and `unmarshallSong()` at line 37-44), re-serializes as JSON, and writes back
3. Update `SongDb.java` to read/write JSON instead of `Parcelable` blobs — the "data" column (line 81) currently stores marshalled byte arrays
4. Update server-side song book format to JSON (coordinate with backend team)
5. Support both formats during transition (detect format by first byte)
6. Remove `KpriModel.Song.writeToParcel()` / `createFromParcel()` (lines 31-38, 75-85) after migration period

**Difficulty:** Hard (3-5 days). Risk: data migration must handle all existing song books without data loss. Requires server-side changes.

---

### REM-22: Introduce Jetpack Compose for New Screens
**Status:** Kicked off — `GotoActivity` + 3 fragments ported as the first screen.  
**Addresses:** General modernization  
**Module:** UI  
**BRICE:** B=3 R=1 I=2 C=2 E=5 → **2.6**

**Progress:**
- Compose BOM `2026.04.01` (Compose 1.11.0 / Material 3 1.4.0) and `androidx.activity:activity-compose:1.13.0` added; `buildFeatures { compose = true }` enabled.
- `BibleAppTheme` (dynamic color on Android 12+) introduced under `yuku.alkitab.base.compose`.
- `GotoActivity` (`ComponentActivity` + `setContent`) and the three goto tabs (Dialer / Direct / Grid) implemented as Composables, replacing ~1,400 lines of Java/XML (activity, 3 fragments, base fragment, 9 layouts, menu, drawable). Public Java API on `GotoActivity` (`createIntent` / `obtainResult` / `Result`) is preserved so call sites in `IsiActivity` keep working unchanged.

**Remaining steps:**
1. Continue with simpler screens: `AboutActivity.kt` (Kotlin, View-based with custom animations), `HelpActivity.java` (Java, WebView-based — convert to Kotlin first)
2. Gradually migrate: `SettingsActivity` → Compose Preference screens
3. Do NOT migrate `IsiActivity` verse rendering — too complex and performance-critical for initial Compose adoption

**Difficulty:** Hard (ongoing effort over months). Risk: Compose interop with existing View-based code requires careful fragment/activity management.

---

## Priority Summary (Sorted by BRICE Score)

| ID | Task | BRICE | Phase |
|----|------|-------|-------|
| REM-01 | ~~Fix SongBookUtil deserialization safety~~ ✅ | **4.6** | 1 |
| REM-02 | ~~Fix Preferences hold/unhold safety~~ ✅ | **4.2** | 1 |
| REM-04 | ~~Fix FCM token retry~~ ✅ | **4.2** | 1 |
| REM-05 | ~~Fix DevotionDownloader threading~~ ✅ | **4.0** | 1 |
| REM-03 | ~~Replace LocalBroadcastManager~~ ✅ | **3.8** | 1 |
| REM-23 | ~~Port ybuild.sh to Gradle~~ ✅ | **3.8** | 2 |
| REM-06 | ~~Extract IsiActivity gestures~~ ✅ | **3.4** | 2 |
| REM-07 | ~~Extract IsiActivity action mode~~ ✅ | **3.4** | 2 |
| REM-09 | Introduce ViewModel | **3.4** | 2 |
| REM-10 | Room migration (Markers) | **3.4** | 2 |
| REM-12 | ~~Replace DragSortListView~~ ✅ | **3.4** | 2 |
| REM-14 | ~~Replace material-dialogs~~ ✅ | **3.4** | 2 |
| REM-18 | Add test coverage | **3.4** | 2 |
| REM-24 | Refactor S.kt service locator | **3.2** | 2 |
| REM-08 | Extract split view manager | **3.2** | 2 |
| REM-11 | Room migration (Version) | **3.2** | 2 |
| REM-15 | Introduce coroutines | **3.2** | 3 |
| REM-16 | Java→Kotlin conversion | **3.0** | 3 |
| REM-17 | ~~Kotlin DSL build migration~~ ✅ | **3.0** | 3 |
| REM-20 | Replace AmbilWarna | **3.0** | 3 |
| REM-19 | Replace PRDownloader | **2.8** | 3 |
| REM-21 | Song storage migration | **2.8** | 4 |
| REM-22 | Jetpack Compose adoption (kicked off) | **2.6** | 4 |

## Suggested Execution Order

**Sprint 1 (1 week):** ~~REM-01~~✅, ~~REM-02~~✅, ~~REM-04~~✅, ~~REM-05~~✅ — quick safety fixes (all done)  
**Sprint 2 (1 week):** ~~REM-03~~✅ — LocalBroadcastManager removal (done)  
**Sprint 3 (2 weeks):** ~~REM-07~~✅, ~~REM-06~~✅, REM-08 — IsiActivity decomposition (REM-06, REM-07 done)  
**Sprint 4 (1 week):** ~~REM-12~~✅, ~~REM-14~~✅ — deprecated library replacements (done)  
**Sprint 5 (2 weeks):** REM-10, REM-11 — Room migration for core tables  
**Sprint 6 (2 weeks):** REM-09, ~~REM-18a-f~~✅ — ViewModel + test coverage (REM-18a/b/c/d/e/f done)  
**Ongoing:** REM-15, REM-16, ~~REM-17~~✅ — modernization work mixed into feature sprints (REM-17 done)
