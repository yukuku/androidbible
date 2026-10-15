# REM-15: Introduce Kotlin Coroutines

**Addresses:** TD-06 (threading)
**Module:** Cross-cutting
**BRICE:** B=4 R=2 I=2 C=3 E=5 → **3.2**
**Phase:** 3 — Modernization

**Status:** Partial, reviewed on 2026-10-15. Version downloads, app-event flows, audio, Compose navigation, reader collectors, and devotion downloads use coroutines. Step 15b is complete; sync, search, and synchronous chapter-loading work remain. Dependencies are declared in the Gradle build and version catalog.

**Steps — incremental by module:**

**Step 15a: Sync module**
1. Add `kotlinx-coroutines-android` dependency (already present transitively; no change needed)
2. Convert `SyncAdapter` (WorkManager `Worker`) to `CoroutineWorker`
3. Replace `Thread`-based sync execution with `withContext(Dispatchers.IO)`
4. Add `supervisorScope` for independent entity sync (markers, pins, history can fail independently)

**Step 15b: Devotion downloads** ✅ Complete (verified 2026-10-15)
1. `DevotionDownloader` owns a `CoroutineScope` with a `SupervisorJob`, foreground/prefetch dispatchers, and cancellation through `shutdown()`.
2. Source/date keys deduplicate queued and active requests; selection promotes foreground work and cancels obsolete requests. The queue is an `ArrayDeque` guarded by synchronization, rather than a `Channel`.
3. `changes` exposes a `StateFlow` observed through `AppEvents.observeWhileStarted` in `DevotionActivity`.
4. `DevotionDownloaderTest` and `DevotionDownloadBackendTest` cover scheduling, cancellation, retry, cache handling, and failures; `DevotionActivityTest` covers the UI integration.

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
