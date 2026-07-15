# REM-06: Extract IsiActivity Gesture Handling ✅ COMPLETED

**Addresses:** TD-01 (gesture cluster)
**Module:** Main reader
**BRICE:** B=4 R=3 I=3 C=3 E=4 → **3.4**
**Phase:** 2 — Architecture Improvements

**What was done:**
1. ✅ Created `Alkitab/src/main/java/yuku/alkitab/base/widget/ReaderGestureHandler.kt`. The class implements **three** listener interfaces in one place: `TwofingerLinearLayout.Listener` (split-root pinch + swipe), `GotoButton.FloaterDragListener` (drag-from-goto-button), and `Floater.Listener` (final ari selection).
2. ✅ Moved the three inline lambda objects out of `IsiActivity.kt`: the `splitRoot_listener` (two-finger pinch/scale/dragX/dragY/end + one-finger left/right), `bGoto_floaterDrag` (floater drag-start/move/complete), and `floater_listener` (ari selection).
3. ✅ Defined two interfaces following the REM-07 pattern: `ReaderGestureHost.kt` (read-only state — `chapter_1`, `activeSplit0Book`, `activeSplit0Version`, `floater`, `textAppearancePanel`, plus pre-computed `gestureDisplayDensity` and `defaultUkuranHuruf2` to avoid forcing the handler to hold a `Resources` reference) and `ReaderGestureActions.kt` (write-side triggers — `onFloaterAriSelected`, `goToPreviousChapter`, `goToNextChapter`, `applyPreferences`, `setFullScreenWithDrawerHandle`).
4. ✅ `IsiActivity` now implements both interfaces, holds a `gestureHandler` lazy field, and wires the handler into `splitRoot.setListener`, `bGoto.setFloaterDragListener`, and `floater.setListener` (was previously three different objects).
5. ✅ Behavior preserved verbatim: split-pane drag, pinch zoom (with the 2f–42f clamp), one-finger left/right chapter swipe, two-finger horizontal drag for multi-chapter skip, two-finger vertical drag toggling fullscreen (paired with `leftDrawer.handle.setFullScreen` under a single new action). Gesture state (`startFontSize`, `startDx`, `moreSwipeYAllowed`, `chapterSwipeCellWidth`, `floaterLocationOnScreen`) now lives on the handler instead of inline objects.

**Result:** Gesture-state fields and all inline listener code are gone from the activity; a thin set of host/actions implementations remains as the seam between the activity and the new handler.

**What stayed in `IsiActivity`:** the gesture-triggered Activity methods themselves (`bLeft_click`, `bRight_click`, `jumpToAri`, `applyPreferences`, `setFullScreen`) and the existing `leftDrawer.handle.setFullScreen` follow-up call — all wrapped in thin action overrides.

**Files touched:**
- `Alkitab/src/main/java/yuku/alkitab/base/widget/ReaderGestureHandler.kt` (new)
- `Alkitab/src/main/java/yuku/alkitab/base/widget/ReaderGestureHost.kt` (new)
- `Alkitab/src/main/java/yuku/alkitab/base/widget/ReaderGestureActions.kt` (new)
- `Alkitab/src/main/java/yuku/alkitab/base/IsiActivity.kt` (modified: class signature gains the two interfaces)

**Difficulty:** Medium (actually 2-3 hours, came in well under the 4-6 hour estimate).

---

[← Back to remediation index](../tech-debt-remediation.md)
