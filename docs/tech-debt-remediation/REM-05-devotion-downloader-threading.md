# REM-05: Refactor DevotionDownloader threading ✅ COMPLETED

**Addresses:** TD-06 (DevotionDownloader)
**Module:** Devotions
**BRICE:** B=2 R=3 I=5 C=5 E=5 → **4.0**
**Phase:** 1 — Quick Wins & Safety Fixes

**Completed:** 2026-04-20. Current implementation reviewed on 2026-10-15.

**Current behavior:**
1. `DevotionDownloader` runs a coroutine scope with foreground and prefetch slots; it does not poll or sleep while idle.
2. Requests deduplicate by `Key(name, date)`. Selection promotes queued work and cancels obsolete foreground transfers.
3. `shutdown()` cancels requests, clears queued work, and cancels the scope.
4. `DevotionDownloadBackend` handles cancellable OkHttp calls, cached articles, and persistence, with response cleanup through `use`.
5. `DevotionActivity` observes the downloader's `changes` StateFlow while started. `DevotionDownloaderTest`, `DevotionDownloadBackendTest`, and `DevotionActivityTest` cover the associated behavior.

**Remaining:** None for this task or the devotion-download portion of [REM-15](REM-15-coroutines.md).

---

[← Back to remediation index](../tech-debt-remediation.md)
