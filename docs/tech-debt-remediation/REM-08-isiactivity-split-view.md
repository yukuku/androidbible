# REM-08: Extract IsiActivity Split View Manager ✅ COMPLETED

**Addresses:** TD-01 (split view cluster)
**Module:** Main reader
**BRICE:** B=3 R=2 I=3 C=4 E=4 → **3.2**
**Phase:** 2 — Architecture Improvements

**What was done:**
1. ✅ Created `Alkitab/src/main/java/yuku/alkitab/base/widget/SplitViewManager.kt` that owns `activeSplit1` and the split-pane UI: `openSplitDisplay()`, `closeSplitDisplay()`, `configureSplitSizes()`, `displaySplitFollowingMaster()`, `loadSplitVersion()`, `disableSplitVersion()`, `openSplitVersionsDialog()`, `configureTextAppearancePanelForSplitVersion()`, plus `restoreFromPreferences(verse_1)` / `saveToPreferences()` for the `lastSplitVersionId` / `lastSplitOrientation` / `lastSplitProp` round-trip.
2. ✅ Moved three listener objects out of `IsiActivity.kt`: the `splitRoot_globalLayout` (`OnGlobalLayoutListener` that re-invokes `configureSplitSizes` on bounds change), `splitHandleButton_listener` (drag-prop math + `lastSplitProp` persistence), and `splitHandleButton_labelPressed` (rotate / start / end label-button dispatch).
3. ✅ Defined two interfaces following the REM-06 / REM-07 pattern: `SplitViewHost.kt` (read-only state — `splitRoot`, `splitHandleButton`, `lsSplit0`, `lsSplit1`, `bVersion`, `leftDrawer`, `textAppearancePanel`, `actionMode`, `chapter_1`, `activeSplit0Book`, `activeSplit0Version`, plus `getVerse_1BasedOnScrolls()`) and `SplitViewActions.kt` (write-side triggers — `applyPreferences`, `openPrimaryVersionsDialog`, `loadChapterIntoSplit1`, `setSplit1DataModel`).
4. ✅ `IsiActivity` now implements both interfaces, holds a `splitViewManager by lazy { SplitViewManager(this, this) }`, and `onCreate` calls `splitViewManager.installListeners()` (replaces the inline `addOnGlobalLayoutListener` + two handle-button listener setups) and `splitViewManager.restoreFromPreferences(...)` (replaces the 17-line `Preferences.getString(Prefkey.lastSplitVersionId)` restore block). `display(...)` delegates to `splitViewManager.displaySplitFollowingMaster(...)`; `onStop` to `splitViewManager.saveToPreferences()`; `cSplitVersion_checkedChange` to the manager's open/disable methods.
5. ✅ Moved `ActiveSplit1` from a nested data class inside `IsiActivity` to a top-level data class in `yuku.alkitab.base.widget`. `IsiActivity.activeSplit1` is now a read-only `val` getter that delegates to `splitViewManager.activeSplit1` — every existing call site (audio bar host, action mode host overrides, `applyPreferences` padding logic, `consumeKey` split scrolling, `VerseInlineLinkSpan` xref/footnote routing, Ribka eligibility, etc.) keeps reading `activeSplit1?.version` unchanged.
6. ✅ Behavior preserved verbatim: split-pane open/close transitions (including `bVersion` show/hide, `leftDrawer.handle.setSplitVersion(...)` toggling, and `actionMode.invalidate()` re-prepare), the split handle drag with proportion clamp and `lastSplitProp` persistence, the rotate-orientation label button, master → split chapter following including the "split version can't display this book" empty-message path, the uncheck-suppression guard around split chapter loads (via the new `loadChapterIntoSplit1` action), text-appearance-panel split version label, and the `lastSplitVersionId` / `lastSplitOrientation` save/restore round-trip across app restarts.

**Result:** `IsiActivity.kt` shrank substantially. Split view is now a self-contained component testable in isolation; `activeSplit1` is read-only from `IsiActivity`'s perspective (all writes happen inside the manager).

**Files touched:**
- `Alkitab/src/main/java/yuku/alkitab/base/widget/SplitViewManager.kt` (new)
- `Alkitab/src/main/java/yuku/alkitab/base/widget/SplitViewHost.kt` (new)
- `Alkitab/src/main/java/yuku/alkitab/base/widget/SplitViewActions.kt` (new)
- `Alkitab/src/main/java/yuku/alkitab/base/IsiActivity.kt` (modified: class signature gains the two interfaces; six lateinit vars + `actionMode` + `getVerse_1BasedOnScrolls()` widened to `override`)

**Verification:**
- `./gradlew assemblePlainDebug` → BUILD SUCCESSFUL in 14s (cold) / 5s (warm)
- `./gradlew testPlainDebugUnitTest testPlainReleaseUnitTest` → BUILD SUCCESSFUL, 420 tests pass per variant

**Difficulty:** Medium (came in around the lower end of the 4-6 hour estimate). The fan-out of `activeSplit1` references was the main risk; addressed by keeping a read-through getter on the activity so no call site outside the moved methods had to change.

---

[← Back to remediation index](../tech-debt-remediation.md)
