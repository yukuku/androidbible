# REM-02: Fix Preferences hold()/unhold() Safety ✅ COMPLETED

**Addresses:** TD-09
**Module:** Afw
**BRICE:** B=3 R=4 I=5 C=5 E=4 → **4.2**
**Phase:** 1 — Quick Wins & Safety Fixes

**Completed in:** `0b61084a`, `cffe49ad`, `80cd9f34` (2026-04-10/11)

**What was done:**
1. ✅ Added `Preferences.withTransaction(Runnable)` method that wraps `hold()/unhold()` in try/finally
2. ✅ Migrated all 6 existing `hold()`/`unhold()` call sites to `withTransaction` (`CurrentReading.java`, `DailyVerseData.java`, `SyncSettingsActivity.java`, `IsiActivity.kt`, `InternalDbHelper.java`, `SecretSyncDebugActivity.kt`)
3. ✅ Made `hold()` and `unhold()` private — stronger than a safety timeout since external code can no longer call them directly, eliminating the misuse risk entirely

**Note:** Two call sites (`SyncSettingsActivity`, `SecretSyncDebugActivity`) were previously missing try/finally, meaning an exception would have buffered preference writes indefinitely. This bug is now fixed.

---

[← Back to remediation index](../tech-debt-remediation.md)
