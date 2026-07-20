# REM-07: Extract IsiActivity Action Mode ✅ COMPLETED

**Addresses:** TD-01 (action mode cluster)
**Module:** Main reader
**BRICE:** B=4 R=3 I=3 C=3 E=4 → **3.4**
**Phase:** 2 — Architecture Improvements

**Completed in:** `89a1894e` (REM-07 refactor) + `716eb1ce` (follow-up split-1 fix, 2026-04-16)

**What was done:**
1. ✅ Created `VerseActionModeController.kt` implementing `ActionMode.Callback`. Moved the entire `actionMode_callback` object from `IsiActivity.kt` into this class.
2. ✅ Defined two interfaces: `VerseActionModeHost` (queries activity state — selected verses, versions, book data, chapter) and `VerseActionModeActions` (callbacks back into the activity — navigate, show toasts, etc.)
3. ✅ Extracted pure text-building logic into `VerseTextFormatter` (no Android dependencies, purely testable under plain JUnit)
4. ✅ Moved `RibkaEligibility` to a standalone top-level file `RibkaEligibility.kt`
5. ✅ Added `mockk` to the test classpath. Added 26 unit tests: 11 pure-JUnit tests for `VerseTextFormatterTest`, 15 Robolectric tests for `VerseActionModeControllerTest` (menu visibility rules, click routing)
6. ✅ A follow-up fixed a split-1 share URL metadata bug (split-1 copy/share used split-0 version metadata in the generated URL) that the extraction had preserved verbatim. Four regression tests added for copy/share split-0/1 metadata routing.

**Result:** `IsiActivity.kt` shrank substantially. Action mode is now independently testable without instantiating the Activity.

**Difficulty:** Medium (6-8 hours). Risk: action mode references many Activity-level fields and methods.

---

[← Back to remediation index](../tech-debt-remediation.md)
