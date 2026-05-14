# REM-05: Refactor DevotionDownloader threading ✅ COMPLETED

**Addresses:** TD-06 (DevotionDownloader)
**Module:** Devotions
**BRICE:** B=2 R=3 I=5 C=5 E=5 → **4.0**
**Phase:** 1 — Quick Wins & Safety Fixes

**Completed in:** `758793f5` (2026-04-20)

**What was done:**
1. ✅ Replaced `extends Thread` with a single-thread `ExecutorService` (`Executors.newSingleThreadExecutor()`)
2. ✅ Replaced `queue_.wait()/notify()` with `LinkedBlockingDeque.take()` — blocking take provides natural backpressure
3. ✅ Added `shutdown()` method that sets a `volatile boolean shutdown_` flag and calls `executor_.shutdownNow()`; the download loop checks the flag and propagates `InterruptedException` by re-interrupting and breaking out
4. ✅ Hardcoded `SystemClock.sleep(50)` removed
5. ✅ HTTP response handling now goes through `Connections.downloadString(url)`, which handles stream cleanup internally

**Remaining:** The broadcast at the end of `downloadLoop` previously used `App.getLbm()` (LocalBroadcastManager); that was migrated to the `AppEvents` `SharedFlow` bus in [REM-03](REM-03-localbroadcastmanager.md).

---

[← Back to remediation index](../tech-debt-remediation.md)
