# REM-15: Introduce Kotlin Coroutines

**Addresses:** TD-06 (threading)
**Module:** Cross-cutting
**BRICE:** B=4 R=2 I=2 C=3 E=5 → **3.2**
**Phase:** 3 — Modernization

**Status:** Partial. Coroutines are already on the classpath transitively via `androidx.fragment:fragment-ktx` (pulled in by [REM-03](REM-03-localbroadcastmanager.md)), and `VersionDownloadWorker` (a `CoroutineWorker`, from [REM-19](REM-19-prdownloader-replacement.md)) plus the `AppEvents` `SharedFlow` buses already use them. The remaining work is module-by-module.

**Steps — incremental by module:**

**Step 15a: Sync module**
1. Add `kotlinx-coroutines-android` dependency (already present transitively; no change needed)
2. Convert `SyncAdapter` (WorkManager `Worker`) to `CoroutineWorker`
3. Replace `Thread`-based sync execution with `withContext(Dispatchers.IO)`
4. Add `supervisorScope` for independent entity sync (markers, pins, history can fail independently)

**Step 15b: Devotion downloads**
1. Replace `DevotionDownloader` thread with a coroutine-based downloader using `Channel`
2. Use `flow {}` for download progress tracking
3. Integrate with `DevotionActivity` via `lifecycleScope`

(`DevotionDownloader` is now an `ExecutorService`-based class after [REM-05](REM-05-devotion-downloader-threading.md), and the broadcast at the end of the loop went through `AppEvents`/`SharedFlow` in [REM-03](REM-03-localbroadcastmanager.md). The full migration to coroutines here is still pending.)

**Step 15c: Search engine**
1. Wrap `SearchEngine.searchByGrep()` in `withContext(Dispatchers.Default)`
2. Add `yield()` between book iterations for cancellation support
3. Return results via `Flow<SearchResult>` for progressive display

**Step 15d: Version loading**
1. Make `Version.loadChapterText()` suspending or wrap in `Dispatchers.IO`
2. IsiActivity display pipeline becomes: `viewModelScope.launch { loadAndDisplay() }`

**Difficulty:** Hard (1-2 weeks total, but can be done module-by-module over time).

---

[← Back to remediation index](../tech-debt-remediation.md)
