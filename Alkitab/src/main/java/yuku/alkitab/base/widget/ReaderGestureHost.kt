package yuku.alkitab.base.widget

import yuku.alkitab.model.Book
import yuku.alkitab.model.Version

/**
 * Read-only state surface that [ReaderGestureHandler] reads from to drive
 * reader gestures (split-pane swipe, pinch zoom, single-finger chapter swipe,
 * two-finger fullscreen drag, and the goto-button floater drag).
 * Implemented by the hosting Activity.
 */
interface ReaderGestureHost {
    val chapter_1: Int
    val activeSplit0Book: Book
    val activeSplit0Version: Version

    /** The goto-button floater overlay; the gesture handler drives its drag lifecycle. */
    val floater: Floater

    /**
     * The "Aa" text-appearance panel, when open. The gesture handler refreshes
     * its displayed font-size value after a pinch-zoom updates the preference.
     */
    val textAppearancePanel: TextAppearancePanel?

    /**
     * Display density in `dp -> px` units. Used to size the cell width for
     * two-finger horizontal chapter-swipe gestures.
     */
    val gestureDisplayDensity: Float

    /**
     * Default value of the `ukuranHuruf2` (font size) preference, resolved
     * from `R.integer.pref_ukuranHuruf2_default`. Used as the fallback when
     * a pinch-zoom starts before the user has ever changed the size.
     */
    val defaultUkuranHuruf2: Float
}
