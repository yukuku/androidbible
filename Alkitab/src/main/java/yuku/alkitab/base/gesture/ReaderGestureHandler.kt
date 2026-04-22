package yuku.alkitab.base.gesture

import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.widget.Floater
import yuku.alkitab.base.widget.GotoButton
import yuku.alkitab.base.widget.TwofingerLinearLayout
import yuku.alkitab.debug.R

/**
 * Bundles together the three gesture-driven listeners that used to live directly
 * on `IsiActivity`:
 *
 * - [twofingerListener]: one-finger horizontal swipes for chapter navigation,
 *   two-finger scale for font size, two-finger vertical drag for fullscreen toggle.
 * - [floaterDragListener]: drag the "goto" floater to pick a book/chapter.
 * - [floaterListener]: jump to the ARI the floater reports on drop.
 *
 * Mutable gesture-local state (start positions, swipe cell width, etc.) is kept
 * here rather than on the Activity so gesture scratch doesn't leak into the
 * Activity's field set.
 */
class ReaderGestureHandler(
    private val host: ReaderGestureHost,
    private val actions: ReaderGestureActions,
) {
    val floaterDragListener: GotoButton.FloaterDragListener = object : GotoButton.FloaterDragListener {
        private val floaterLocationOnScreen = intArrayOf(0, 0)

        override fun onFloaterDragStart(screenX: Float, screenY: Float) {
            host.floater.show(host.activeSplit0Book.bookId, host.chapter_1)
            host.floater.onDragStart(host.activeSplit0Version.consecutiveBooks)
        }

        override fun onFloaterDragMove(screenX: Float, screenY: Float) {
            host.floater.getLocationOnScreen(floaterLocationOnScreen)
            host.floater.onDragMove(screenX - floaterLocationOnScreen[0], screenY - floaterLocationOnScreen[1])
        }

        override fun onFloaterDragComplete(screenX: Float, screenY: Float) {
            host.floater.hide()
            host.floater.onDragComplete(screenX - floaterLocationOnScreen[0], screenY - floaterLocationOnScreen[1])
        }
    }

    val floaterListener: Floater.Listener = Floater.Listener { ari ->
        actions.jumpToAri(ari)
    }

    val twofingerListener: TwofingerLinearLayout.Listener = object : TwofingerLinearLayout.Listener {
        var startFontSize = 0f
        var startDx = Float.MIN_VALUE
        var chapterSwipeCellWidth = 0f // initted later
        var moreSwipeYAllowed = true // to prevent setting and unsetting fullscreen many times within one gesture

        override fun onOnefingerLeft() {
            actions.bRight_click()
        }

        override fun onOnefingerRight() {
            actions.bLeft_click()
        }

        override fun onTwofingerStart() {
            val resources = host.activity.resources
            chapterSwipeCellWidth = 24f * resources.displayMetrics.density
            startFontSize = Preferences.getFloat(Prefkey.ukuranHuruf2, resources.getInteger(R.integer.pref_ukuranHuruf2_default).toFloat())
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
                    actions.bRight_click()
                } else {
                    actions.bLeft_click()
                }
            } else { // more
                // more to the left
                while (dx < startDx - chapterSwipeCellWidth) {
                    startDx -= chapterSwipeCellWidth
                    actions.bRight_click()
                }

                while (dx > startDx + chapterSwipeCellWidth) {
                    startDx += chapterSwipeCellWidth
                    actions.bLeft_click()
                }
            }
        }

        override fun onTwofingerDragY(dy: Float) {
            if (!moreSwipeYAllowed) return

            actions.onGestureFullScreenToggle(dy < 0)

            moreSwipeYAllowed = false
        }

        override fun onTwofingerEnd(mode: TwofingerLinearLayout.Mode?) {
            startFontSize = 0f
            startDx = Float.MIN_VALUE
            moreSwipeYAllowed = true
        }
    }
}
