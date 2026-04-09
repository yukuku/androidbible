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

### REM-01: Fix SongBookUtil Resource Leak & Unsafe Deserialization
**Addresses:** TD-04, PB-06  
**Module:** Songs  
**BRICE:** B=4 R=5 I=4 C=5 E=5 → **4.6**

**Current state:** `SongBookUtil.java:186-188` uses `ObjectInputStream.readObject()` from a network stream without try-with-resources, size validation, or type checking. Potential security vulnerability and resource leak.

**Steps:**
1. Wrap `ObjectInputStream` in try-with-resources in `SongBookUtil.java`
2. Add response body size check before reading (reject >50MB)
3. Add `ObjectInputFilter` (Java 9+/Android API 28+) to restrict deserialization to `java.util.ArrayList`, `yuku.kpri.model.Song`, and related classes only
4. Long-term: Migrate song download format from Java serialization to JSON (Kotlinx Serialization), updating both server and client

**Difficulty:** Step 1-3: Easy (1-2 hours). Step 4: Medium (1-2 days, requires server change).

---

### REM-02: Fix Preferences hold()/unhold() Safety
**Addresses:** TD-09, PB-07  
**Module:** Afw  
**BRICE:** B=3 R=4 I=5 C=5 E=4 → **4.2**

**Current state:** `Preferences.java` uses a manual `hold()/unhold()` counter. If any code path throws between `hold()` and `unhold()`, preference writes buffer indefinitely.

**Steps:**
1. Add a `Preferences.withTransaction(block: () -> Unit)` Kotlin extension that wraps `hold()/unhold()` in try/finally:
   ```kotlin
   fun withTransaction(block: () -> Unit) {
       Preferences.hold()
       try { block() } finally { Preferences.unhold() }
   }
   ```
2. Grep for all `hold()`/`unhold()` pairs and migrate to `withTransaction`
3. Add a safety timeout in `hold()` — if held for >10 seconds, auto-commit and log a warning

**Difficulty:** Easy (2-3 hours).

---

### REM-03: Replace LocalBroadcastManager
**Addresses:** TD-03 (LocalBroadcastManager)  
**Module:** Cross-cutting (15+ files)  
**BRICE:** B=3 R=3 I=4 C=4 E=5 → **3.8** (rounded up due to deprecation urgency)

**Current state:** `LocalBroadcastManager` is deprecated since AndroidX 1.1.0. Used in DevotionDownloader, DevotionActivity, MarkersActivity, ReadingPlanActivity, and ~12 other files via `App.getLbm()`.

**Steps — by usage pattern:**

**Step 3a: Devotion events (DevotionDownloader → DevotionActivity)**
1. Create `DevotionEventBus` object with `MutableSharedFlow<DevotionEvent>`
2. Replace `App.getLbm().sendBroadcast()` in `DevotionDownloader.java:109` with `DevotionEventBus.emit()`
3. Replace `BroadcastReceiver` in `DevotionActivity` with `lifecycleScope.launch { DevotionEventBus.collect {} }`
4. Delete devotion-related `IntentFilter` registrations

**Step 3b: Marker/attribute change events**
1. Create `MarkerEventBus` with `MutableSharedFlow<MarkerEvent>`
2. Replace attribute change broadcasts in `InternalDb` and marker-editing activities
3. Update `IsiActivity` listeners (lines 451-519) to collect from `MarkerEventBus`

**Step 3c: Version change events**
1. Same pattern with `VersionEventBus`
2. Update `IsiActivity` version-change broadcast receiver

**Step 3d: Remove `App.getLbm()` and `LocalBroadcastManager` dependency**

**Difficulty:** Medium (1-2 days total, can be done incrementally by event type).

---

### REM-04: Fix FCM Token Re-registration Retry
**Addresses:** PB-04  
**Module:** Sync  
**BRICE:** B=4 R=4 I=5 C=4 E=4 → **4.2**

**Current state:** `Sync.sendFcmRegistrationId()` (lines 306-337) silently logs failures. Failed registration means the device never receives sync push notifications again.

**Steps:**
1. In `Sync.sendFcmRegistrationId()`, on failure, store a `Prefkey.fcm_registration_pending` flag
2. On app launch (`App.staticInit()`), check flag and retry registration
3. Add exponential backoff: retry after 1min, 5min, 30min, then once per app launch
4. Log registration failures at WARN level instead of DEBUG

**Difficulty:** Easy (2-3 hours).

---

### REM-05: Add try-with-resources to DevotionDownloader
**Addresses:** TD-06 (DevotionDownloader)  
**Module:** Devotions  
**BRICE:** B=2 R=3 I=5 C=5 E=5 → **4.0**

**Current state:** `DevotionDownloader.java` has an infinite loop with `@SuppressWarnings("InfiniteLoopStatement")`, hardcoded 50ms sleep, and no graceful shutdown.

**Steps:**
1. Replace `extends Thread` with `ExecutorService` (single-thread executor)
2. Replace `queue_.wait()/notify()` with `LinkedBlockingQueue.take()`
3. Add `shutdown()` method that sets a volatile flag and interrupts the thread
4. Remove hardcoded `SystemClock.sleep(50)` — the blocking queue provides natural backpressure
5. Wrap HTTP response handling in try-with-resources

**Difficulty:** Easy (3-4 hours).

---

## Phase 2: Architecture Improvements (BRICE 3.0–3.9)

### REM-06: Extract IsiActivity Gesture Handling
**Addresses:** TD-01 (gesture cluster)  
**Module:** Main reader  
**BRICE:** B=4 R=3 I=3 C=3 E=4 → **3.4**

**Steps:**
1. Create `ReaderGestureHandler.kt` implementing `TwofingerLinearLayout.Listener`
2. Move lines 143-370 from `IsiActivity.kt` into `ReaderGestureHandler`
3. Pass required callbacks as constructor parameters: `onChapterChange`, `onFontSizeChange`, `onFullscreenToggle`
4. In `IsiActivity`, instantiate `ReaderGestureHandler` and delegate the listener interface

**Difficulty:** Medium (4-6 hours). Risk: gesture state depends on Activity fields (`chapter_1`, `unchunkedFontSizeDp`).

---

### REM-07: Extract IsiActivity Action Mode
**Addresses:** TD-01 (action mode cluster)  
**Module:** Main reader  
**BRICE:** B=4 R=3 I=3 C=3 E=4 → **3.4**

**Steps:**
1. Create `VerseActionModeController.kt` implementing `ActionMode.Callback`
2. Move lines 530-1014 from `IsiActivity.kt` into this new class
3. Define a `VerseActionModeCallback` interface for actions that need Activity context (navigate, share, etc.)
4. Inject dependencies: `selectedVerses`, `activeVersion`, `ClipboardManager`
5. Handle extension menu items via delegate pattern

**Difficulty:** Medium (6-8 hours). Risk: action mode references many Activity-level fields and methods.

---

### REM-08: Extract IsiActivity Split View Manager
**Addresses:** TD-01 (split view cluster)  
**Module:** Main reader  
**BRICE:** B=3 R=2 I=3 C=4 E=4 → **3.2**

**Steps:**
1. Create `SplitViewManager.kt` containing `openSplitDisplay()`, `closeSplitDisplay()`, `displaySplitFollowingMaster()`, `loadSplitVersion()`
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

**Prerequisite:** REM-06, REM-07, REM-08 should be done first to reduce IsiActivity size before extracting ViewModel.

**Difficulty:** Hard (2-3 days). Risk: extensive refactoring of the largest file in the codebase.

---

### REM-10: Migrate InternalDb to Room (Markers Table)
**Addresses:** TD-02  
**Module:** Storage — Markers subsystem  
**BRICE:** B=4 R=3 I=2 C=3 E=5 → **3.4**

**Steps — incremental, table by table, starting with Markers:**
1. Define Room entities: `MarkerEntity`, `LabelEntity`, `MarkerLabelEntity`
2. Define `MarkerDao` with type-safe queries replacing the 15+ `rawQuery()` calls in `InternalDb.java:130-280`
3. Create `AppDatabase : RoomDatabase()` with migration from existing SQLite schema
4. Replace `InternalDb.insertOrUpdateMarker()`, `deleteMarkerById()`, `listMarkersForAriKind()`, etc. with DAO calls
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
4. Replace `InternalDb.listAllVersions()` (lines 785-820) with DAO call
5. Update `S.getAvailableVersions()` to use the DAO

**Difficulty:** Medium (1-2 days). Lower risk than Markers since the Version table is simpler.

---

### REM-12: Replace DragSortListView with ItemTouchHelper
**Addresses:** TD-12  
**Module:** Markers (label management)  
**BRICE:** B=2 R=2 I=4 C=4 E=5 → **3.4**

**Steps:**
1. In `MarkersActivity.java`, replace `DragSortListView` with standard `RecyclerView`
2. Attach `ItemTouchHelper` with `ItemTouchHelper.SimpleCallback` for drag-to-reorder
3. Migrate adapter from `DragSortListView.DragSortController` callbacks to `ItemTouchHelper.Callback.onMove()`
4. Delete the `DragSortListView` module from `settings.gradle`
5. Remove module directory

**Difficulty:** Easy-Medium (4-6 hours).

---

### REM-13: Add FTS5 Search Index
**Addresses:** TD-05  
**Module:** Search  
**BRICE:** B=5 R=2 I=2 C=3 E=5 → **3.4**

**Steps:**
1. Create FTS5 virtual table in Room (or raw SQLite): `CREATE VIRTUAL TABLE verse_fts USING fts5(text, content=...)`
2. Populate FTS table when a Bible version is loaded/downloaded — index all verse text with formatting codes stripped
3. Replace `SearchEngine.searchByGrep()` with `SELECT * FROM verse_fts WHERE verse_fts MATCH ?`
4. Handle multi-version search: either index per-version or re-index on version switch
5. Handle token operators: quoted phrases map to FTS5 phrase queries, plus-prefix maps to NEAR queries
6. Rebuild index when a version is deleted or updated

**Difficulty:** Hard (3-5 days). Requires careful design of index lifecycle (when to build, invalidate, rebuild). The indexing step could be slow for large Bibles (background with progress).

---

### REM-14: Replace material-dialogs with Material 3
**Addresses:** TD-12  
**Module:** Cross-cutting UI  
**BRICE:** B=2 R=3 I=3 C=4 E=5 → **3.4**

**Steps:**
1. Grep for `MaterialDialog` usage across the codebase
2. Replace each dialog instance with `MaterialAlertDialogBuilder` (Material 3):
   - Simple alerts → `MaterialAlertDialogBuilder`
   - Input dialogs → custom layout with `TextInputEditText`
   - List/choice dialogs → `setSingleChoiceItems()` / `setMultiChoiceItems()`
   - Color picker dialogs → evaluate Material color picker or keep `AmbilWarna`
3. Remove `material-dialogs` dependency from `build.gradle`
4. Test each dialog replacement (manual — no UI tests exist)

**Difficulty:** Medium (1-2 days). Tedious but low risk per dialog.

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
| 8 | `VerseRenderer.java` | 423 | Medium | Complex rendering logic, no tests |
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

### REM-17: Migrate Build to Kotlin DSL & Version Catalogs
**Addresses:** TD-12 (build system)  
**Module:** Build  
**BRICE:** B=2 R=1 I=3 C=4 E=5 → **3.0**

**Steps:**
1. Create `gradle/libs.versions.toml` with all dependency versions and coordinates
2. Convert `settings.gradle` → `settings.gradle.kts`
3. Convert root `build.gradle` → `build.gradle.kts`
4. Convert `Alkitab/build.gradle` → `Alkitab/build.gradle.kts`
5. Convert each library module's `build.gradle` → `.kts` (16 modules)
6. Replace `$variable` references with version catalog accessors (`libs.okhttp`, etc.)

**Difficulty:** Medium (1-2 days). Mostly mechanical but Groovy-specific constructs need manual translation.

---

### REM-18: Add Test Coverage for Core Modules
**Addresses:** TD-13  
**Module:** Cross-cutting  
**BRICE:** B=4 R=3 I=2 C=3 E=5 → **3.4**

**Steps — prioritized by risk coverage:**

**Step 18a: Highlight encoding tests**
1. Write unit tests for `Highlights.java`: encode/decode, hash verification, partial highlights
2. Test edge cases: empty highlights, Unicode text, version text changes
3. Difficulty: Easy (2-3 hours)

**Step 18b: Sync protocol tests**
1. Create mock server responses for `Sync_Mabel`, `Sync_Pins`, `Sync_Rp`
2. Test delta application: add, mod, del operations
3. Test conflict scenarios: concurrent edits, missing GIDs
4. Difficulty: Medium (1 day)

**Step 18c: InternalDb tests (or Room DAO tests)**
1. If Room migration done (REM-10): write DAO tests using in-memory database
2. If not: use Robolectric with `InternalDbHelper` for instrumented tests
3. Cover: marker CRUD, label ordering, highlight storage, attribute loading
4. Difficulty: Medium (1-2 days)

**Step 18d: SearchEngine tests**
1. Create test Bible version with known text
2. Test: single token, multi-token intersection, quoted phrases, whole-word matching
3. Test performance: measure time for full-Bible search
4. Difficulty: Medium (4-6 hours)

**Step 18e: YES2 reader/writer round-trip tests**
1. Create a small test Bible in YES2 format
2. Write with `Yes2Writer`, read with `Yes2Reader`, verify content matches
3. Test Snappy compression/decompression
4. Difficulty: Medium (4-6 hours)

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
1. Identify all usages of `AmbilWarnaDialog` (likely in `ColorSettingsActivity` and highlight color picker)
2. Replace with `MaterialColorPickerDialog` from a Material-compatible library or implement custom using Material 3 color palette
3. Ensure selected colors are stored in the same format (hex int)
4. Delete `AmbilWarna` module

**Difficulty:** Easy (3-4 hours).

---

## Phase 4: Long-term / Major Refactors (BRICE < 2.5)

### REM-21: Migrate Song Storage from Parcelable to JSON
**Addresses:** TD-04 (long-term part)  
**Module:** Songs  
**BRICE:** B=3 R=3 I=1 C=2 E=5 → **2.8**

**Steps:**
1. Define `SongJsonModel` using Kotlinx Serialization with explicit field names
2. Add database migration that reads all songs via old `Parcelable` format, re-serializes as JSON, and writes back
3. Update `SongDb` to read/write JSON instead of `Parcelable` blobs
4. Update server-side song book format to JSON (coordinate with backend team)
5. Support both formats during transition (detect format by first byte)
6. Remove `KpriModel.Song.writeToParcel()` / `createFromParcel()` after migration period

**Difficulty:** Hard (3-5 days). Risk: data migration must handle all existing song books without data loss. Requires server-side changes.

---

### REM-22: Introduce Jetpack Compose for New Screens
**Addresses:** General modernization  
**Module:** UI  
**BRICE:** B=3 R=1 I=1 C=2 E=5 → **2.4**

**Steps:**
1. Add Compose dependencies and configure `buildFeatures { compose = true }`
2. Start with simpler screens: `AboutActivity`, `HelpActivity`
3. Create Compose equivalents and swap in the Activity
4. Gradually migrate: `SettingsActivity` → Compose Preference screens
5. Do NOT migrate `IsiActivity` verse rendering — too complex and performance-critical for initial Compose adoption

**Difficulty:** Hard (ongoing effort over months). Risk: Compose interop with existing View-based code requires careful fragment/activity management.

---

## Priority Summary (Sorted by BRICE Score)

| ID | Task | BRICE | Phase |
|----|------|-------|-------|
| REM-01 | Fix SongBookUtil deserialization safety | **4.6** | 1 |
| REM-02 | Fix Preferences hold/unhold safety | **4.2** | 1 |
| REM-04 | Fix FCM token retry | **4.2** | 1 |
| REM-05 | Fix DevotionDownloader threading | **4.0** | 1 |
| REM-03 | Replace LocalBroadcastManager | **3.8** | 1 |
| REM-06 | Extract IsiActivity gestures | **3.4** | 2 |
| REM-07 | Extract IsiActivity action mode | **3.4** | 2 |
| REM-09 | Introduce ViewModel | **3.4** | 2 |
| REM-10 | Room migration (Markers) | **3.4** | 2 |
| REM-12 | Replace DragSortListView | **3.4** | 2 |
| REM-13 | Add FTS5 search | **3.4** | 2 |
| REM-14 | Replace material-dialogs | **3.4** | 2 |
| REM-18 | Add test coverage | **3.4** | 2 |
| REM-08 | Extract split view manager | **3.2** | 2 |
| REM-11 | Room migration (Version) | **3.2** | 2 |
| REM-15 | Introduce coroutines | **3.2** | 3 |
| REM-16 | Java→Kotlin conversion | **3.0** | 3 |
| REM-17 | Kotlin DSL build migration | **3.0** | 3 |
| REM-20 | Replace AmbilWarna | **3.0** | 3 |
| REM-19 | Replace PRDownloader | **2.8** | 3 |
| REM-21 | Song storage migration | **2.8** | 4 |
| REM-22 | Jetpack Compose adoption | **2.4** | 4 |

## Suggested Execution Order

**Sprint 1 (1 week):** REM-01, REM-02, REM-04, REM-05 — all quick safety fixes  
**Sprint 2 (1 week):** REM-03 — LocalBroadcastManager removal (touches many files, best done in isolation)  
**Sprint 3 (2 weeks):** REM-06, REM-07, REM-08 — IsiActivity decomposition  
**Sprint 4 (1 week):** REM-12, REM-14 — deprecated library replacements  
**Sprint 5 (2 weeks):** REM-10, REM-11 — Room migration for core tables  
**Sprint 6 (2 weeks):** REM-09, REM-18a-b — ViewModel + test coverage  
**Ongoing:** REM-13, REM-15, REM-16, REM-17 — modernization work mixed into feature sprints
