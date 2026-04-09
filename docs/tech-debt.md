# Tech Debt, Improvements & Critiques

## TD-01: IsiActivity God Class (2897 lines)

**File:** `Alkitab/src/main/java/yuku/alkitab/base/IsiActivity.kt`

The main Bible reader activity is a monolithic class containing 40+ inline lambda callbacks, 2900 lines of mixed concerns. Specific clusters that violate single-responsibility:

- **Gesture handling (lines 143–370):** `onFloaterDragStart/Move/Complete`, `onOnefingerLeft/Right`, `onTwofingerStart/Scale/DragX/DragY/End` — all inline lambdas that implement `TwofingerLinearLayout.Listener`. This is ~230 lines of touch gesture processing embedded in the Activity.
- **Action mode (lines 530–1014):** `onCreateActionMode`, `onPrepareActionMode`, `onActionItemClicked`, `onDestroyActionMode` — ~480 lines of context menu handling for copy, share, bookmark, highlight, compare, dictionary, extensions.
- **Broadcast receivers (lines 451–519):** Two anonymous `BroadcastReceiver` instances registered inline, one for verse attribute changes and one for version changes.
- **Verse selection listeners (lines 463–525):** Two `SelectedVersesListener` implementations (`lsSplit0_selectedVerses`, `lsSplit1_selectedVerses`) with partially duplicated logic.
- **Split view management:** `openSplitDisplay()`, `closeSplitDisplay()`, `displaySplitFollowingMaster()`, `loadSplitVersion()` scattered across the file.
- **Navigation history:** `BackForwardListController` usage, `jumpToAri()`, `jumpTo(reference)`, `History` tracking.

**Impact:** Any change to one concern risks breaking unrelated functionality. Testing individual behaviors requires instantiating the entire Activity. New developers face a ~3000-line class with no clear entry point.

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

## TD-04: Unsafe Deserialization in Songs

**File:** `Alkitab/src/main/java/yuku/alkitab/songs/SongBookUtil.java:186-188`

```java
final ObjectInputStream ois = new ObjectInputStream(
    new OptionalGzipInputStream(response.body().byteStream()));
@SuppressWarnings("unchecked")
final List<Song> songs = (List<Song>) ois.readObject();
ois.close();
```

- **Security risk:** `ObjectInputStream.readObject()` from a network stream can deserialize arbitrary classes. A compromised or MITM'd server could exploit this.
- **Fragility:** Any change to `KpriModel.Song` fields (which uses `Parcelable` as its serialization format — acknowledged in the code as "Bad decision") breaks deserialization of all stored songs.
- **Resource leak:** `ois.close()` not in try-with-resources — if `readObject()` throws, the stream leaks.
- **No size limits:** No check on response size before loading entire stream into memory.

---

## TD-05: Search Engine Performance

**File:** `Alkitab/src/main/java/yuku/alkitab/base/util/SearchEngine.java`

### Sequential grep-based search (lines 61–110)
For each search token, loads entire chapters into memory and performs `indexOf()` calls:
```java
final String oneChapter = version.loadChapterTextLowercasedWithoutSplit(book, chapter_1);
```
Worst case: 5 tokens × 66 books × ~30 chapters × `indexOf()` per verse = millions of string comparisons. No inverted index, no caching of previous search results.

### O(n³) multiword search (lines 374–462)
`indexOfWholeMultiword()` has three nested loops: outer word finder, inner remaining-word iterator, innermost tag-consumption loop with `substring()` and `indexOf("@>", pos)` calls. Performance degrades badly with many short search tokens on heavily formatted text.

### No FTS (Full-Text Search)
SQLite FTS5 would provide orders-of-magnitude faster search with minimal implementation effort, especially since the app already uses SQLite for everything else.

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

### Manual cache with dirty flag (lines 16–24)
```java
private static SharedPreferences cache;
private static boolean dirty = true;
private static int held = 0;
```
- `dirty` flag requires manual `invalidate()` calls from external code
- `held` counter implements a manual transaction system where `hold()/unhold()` must be balanced — if `unhold()` is missed, changes are never persisted

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
| `DragSortListView` | bundled | Ancient library. `ItemTouchHelper` (built into RecyclerView) is the modern replacement. |
| `AmbilWarna` | bundled | Bundled color picker. Material color picker components exist now. |
| `LocalBroadcastManager` | latest | Officially deprecated by AndroidX team. |

---

## TD-13: Minimal Test Coverage

14 test files exist across the project:
- `FormattedTextRendererTest.java` — verse formatting codes
- `QueryTokenizerTest.kt` — search tokenization
- `TargetDecoderTest.java` — verse reference parsing
- `JumperTest.java` — verse navigation
- `RemoveSpecialCodesTest.java` — formatting code stripping
- `JsonFileExportTest.kt` — data transfer export
- `VersionTest.java`, `GetVersionInitialsTest.java` — version model
- `OptionalGzipInputStreamTest.java` — I/O utility
- `UnsignedBinarySearchKtTest.kt` — binary search utility
- `DesktopVerseFinderTest.java` — desktop verse finder (in tools/AlkitabConverter)
- `DesktopVerseParserTest.java` — desktop verse parser (in tools/AlkitabConverter)
- `LauncherTest.java` — integration launcher test (in AlkitabIntegration, androidTest)
- `VerseProviderTest.java` — verse provider test (in AlkitabIntegration, androidTest)

**Not tested:** Database operations, sync protocol, version loading, search engine, devotion downloading, song management, highlight encoding, content provider, widget logic.

---

## TD-14: ybuild.sh — Custom Shell Script for Production Builds

**File:** `ybuild.sh`

The production release build process is handled by a 168-line bash script that performs operations outside of Gradle's build lifecycle. This means production builds cannot be reproduced with a single `./gradlew` invocation — `ybuild.sh` must be run instead.

### What ybuild.sh does beyond Gradle:

1. **RAM disk creation (lines 79-91):** Creates a macOS-specific 1GB RAM disk via `hdiutil`/`diskutil`, copies the entire project there via `rsync`, and builds from the copy. This is macOS-only and prevents building on Linux CI.

2. **Proprietary asset injection (lines 120-129):** Deletes the placeholder `assets/internal/` directory (containing `ddd_*` dummy files) and copies real Bible text files from `$ALKITAB_PROPRIETARY_DIR/overlay/$BUILD_PACKAGE_NAME/text_raw`. This is the critical step — production flavors need real Bible text that isn't in the open-source repo.

3. **Git commit hash stamping (lines 93-95, 146-148):** Reads the last commit hash via `git log` and uses `sed` to replace the `0000000` placeholder in `res/values/last_commit.xml`.

4. **Custom APK renaming (lines 156-165):** Renames the output APK to include version code, version name, commit hash, and distribution identifier (e.g., `Alkitab-17000532-4.11.2-a580062-yuku-playstore.apk`).

### What Gradle already handles:
- **Signing** — `signingConfigs.release` (Alkitab/build.gradle:32-38) already reads `SIGN_KEYSTORE`, `SIGN_ALIAS`, `SIGN_PASSWORD` from env vars
- **Product flavors** — `yuku_alkitab`, `yuku_quick_bible`, `sabda_alkitab` are defined (lines 72-87) with flavor-specific source sets already containing icons and configs

### Required environment variables:
| Variable | Purpose |
|----------|---------|
| `ALKITAB_PROPRIETARY_DIR` | Root of proprietary overlay files |
| `SIGN_KEYSTORE` | Path to signing keystore |
| `SIGN_ALIAS` | Key alias |
| `SIGN_PASSWORD` | Keystore/key password |
| `FLAVOR` | Product flavor name |
| `BUILD_PACKAGE_NAME` | Overlay subdirectory name (e.g., `yuku`, `sabda`) |
| `BUILD_DIST` | Distribution channel (e.g., `playstore`, `direct`) |

**Impact:** Production builds are not reproducible via Gradle alone. The build process is macOS-only due to RAM disk. CI/CD must invoke a shell script rather than Gradle tasks. New developers must learn a custom build procedure.

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

### PB-06: SongBookUtil Resource Leak
`SongBookUtil.java:186-188` — `ObjectInputStream` is closed in a `close()` call after `readObject()`, but not in a try-with-resources or finally block. If `readObject()` throws, both the OIS and the underlying response body stream are leaked.

### PB-07: Preferences hold() Without unhold()
The `Preferences.hold()/unhold()` pattern is manually balanced. If any code path calls `hold()` but throws before `unhold()`, all subsequent preference writes are buffered indefinitely and never persisted to disk. No timeout or safety mechanism.
