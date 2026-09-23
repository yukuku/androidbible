# REM-46: Stop Debouncer From Leaking a Thread per Instance

**Source:** Found while converting `Debouncer` to Kotlin ([REM-16](REM-16-java-to-kotlin.md)), 2026-09-23
**Module:** Alkitab (`yuku.alkitab.base.util.Debouncer`, used by `SearchActivity` and `MarkerListActivity`)
**BRICE:** B=3 R=3 I=4 C=4 E=4 → **3.6**
**Phase:** 2 (Architecture Improvements)

**Status:** Not started.

**Problem:** Every `Debouncer` instance creates its own `Executors.newSingleThreadScheduledExecutor()`, and nothing ever shuts it down. The executor's single core thread never times out, and the running worker keeps its pool reachable, so once a payload has been submitted the thread stays alive, idle, until the process dies. `SearchActivity` holds two debouncers (`suggester` and `searcher`) and `MarkerListActivity` holds one (`filter`), all created per activity instance. Every visit to those screens that submits something therefore leaves one or two idle threads behind. The threads do not retain the activity, because the task queue is empty once the tasks have run, so the cost is threads and their stacks rather than activity memory.

**Steps:**
1. Pick one approach:
   - a. Share one scheduled executor across all `Debouncer` instances, held in the companion object. This is the smallest change and keeps the API.
   - b. Add a `close()` to `Debouncer` that shuts its executor down, and call it from the owning activity's `onDestroy`.
   - c. Replace `Debouncer` with a coroutine-based debounce, for example a `MutableStateFlow` collected with `debounce`/`mapLatest` in `lifecycleScope`. This cancels with the screen and needs no executor, but it changes the API both callers use. It fits [REM-15](REM-15-coroutines.md).
2. Extend `DebouncerTest`: for option a, the number of live threads stays flat after creating and using several debouncers; for option b, the executor is shut down after `close()`; for option c, the existing supersession tests carry over to the flow.

**Difficulty:** Easy for a or b (hours); moderate for c.

---

[← Back to remediation index](../tech-debt-remediation.md)
