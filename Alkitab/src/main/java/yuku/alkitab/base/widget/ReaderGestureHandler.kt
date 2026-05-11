package yuku.alkitab.base.widget

import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Prefkey

/**
 * Reader gesture handling extracted from `IsiActivity` — see REM-06 in
 * docs/tech-debt-remediation.md.
 *
 * Bundles the three listener interfaces that the reader screen wires up to
 * its layout: [TwofingerLinearLayout.Listener] (split-root pinch + swipe),
 * [GotoButton.FloaterDragListener] (drag from the goto button to drop a
 * floating book/chapter picker), and [Floater.Listener] (final drop ari).
 *
 * The handler keeps no Activity references: it reads state through [host]
 * and triggers Activity operations through [actions].
 */
class ReaderGestureHandler(
    private val host: ReaderGestureHost,
    private val actions: ReaderGestureActions,
) : TwofingerLinearLayout.Listener, GotoButton.FloaterDragListener, Floater.Listener {

    // --- GotoButton.FloaterDragListener (drag-from-goto to drop a picker) ---

    private val floaterLocationOnScreen = intArrayOf(0, 0)

    override fun onFloaterDragStart(screenX: Float, screenY: Float) {
        val floater = host.floater
        floater.show(host.activeSplit0Book.bookId, host.chapter_1)
        floater.onDragStart(host.activeSplit0Version.consecutiveBooks)
    }

    override fun onFloaterDragMove(screenX: Float, screenY: Float) {
        val floater = host.floater
        floater.getLocationOnScreen(floaterLocationOnScreen)
        floater.onDragMove(screenX - floaterLocationOnScreen[0], screenY - floaterLocationOnScreen[1])
    }

    override fun onFloaterDragComplete(screenX: Float, screenY: Float) {
        val floater = host.floater
        floater.hide()
        floater.onDragComplete(screenX - floaterLocationOnScreen[0], screenY - floaterLocationOnScreen[1])
    }

    // --- Floater.Listener (selected ari from the floater) ---

    override fun onSelectComplete(ari: Int) {
        actions.onFloaterAriSelected(ari)
    }

    // --- TwofingerLinearLayout.Listener (split-root pinch/swipe gestures) ---

    private var startFontSize = 0f
    private var startDx = Float.MIN_VALUE
    private var chapterSwipeCellWidth = 0f // initted in onTwofingerStart
    private var moreSwipeYAllowed = true // to prevent setting and unsetting fullscreen many times within one gesture

    override fun onOnefingerLeft() {
        actions.goToNextChapter()
    }

    override fun onOnefingerRight() {
        actions.goToPreviousChapter()
    }

    override fun onTwofingerStart() {
        chapterSwipeCellWidth = 24f * host.gestureDisplayDensity
        startFontSize = Preferences.getFloat(Prefkey.ukuranHuruf2, host.defaultUkuranHuruf2)
    }

    override fun onTwofingerScale(scale: Float) {
        var nowFontSize = startFontSize * scale

        if (nowFontSize < 2f) nowFontSize = 2f
        if (nowFontSize > 42f) nowFontSize = 42f

        Preferences.setFloat(Prefkey.ukuranHuruf2, nowFontSize)

        actions.applyPreferences()

        host.textAppearancePanel?.displayValues()
    }

    override fun onTwofingerDragX(dx: Float) {
        if (startDx == Float.MIN_VALUE) { // just started
            startDx = dx

            if (dx < 0) {
                actions.goToNextChapter()
            } else {
                actions.goToPreviousChapter()
            }
        } else { // more
            // more to the left
            while (dx < startDx - chapterSwipeCellWidth) {
                startDx -= chapterSwipeCellWidth
                actions.goToNextChapter()
            }

            while (dx > startDx + chapterSwipeCellWidth) {
                startDx += chapterSwipeCellWidth
                actions.goToPreviousChapter()
            }
        }
    }

    override fun onTwofingerDragY(dy: Float) {
        if (!moreSwipeYAllowed) return

        if (dy < 0) {
            actions.setFullScreenWithDrawerHandle(true)
        } else {
            actions.setFullScreenWithDrawerHandle(false)
        }

        moreSwipeYAllowed = false
    }

    override fun onTwofingerEnd(mode: TwofingerLinearLayout.Mode?) {
        startFontSize = 0f
        startDx = Float.MIN_VALUE
        moreSwipeYAllowed = true
    }
}
