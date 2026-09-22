package yuku.alkitab.base.audio.ui

import android.graphics.Color
import androidx.core.graphics.ColorUtils

/**
 * Picks the verse-highlight overlay drawn behind verse text while audio is
 * playing, adapting to whatever reading background the user has configured.
 *
 * Yellow is used when it is both visible against the background and legible
 * under the verse text. Otherwise the overlay is black or white, whichever is
 * more visible. Visibility is measured as LAB ΔE rather than luminance contrast,
 * because a 20%-alpha overlay barely shifts luminance, while ΔE catches the
 * chroma shift on light backgrounds and its absence on yellow paper. Yellow
 * paper is the case that would otherwise leave the highlight invisible.
 */
object AudioHighlightColor {
    private const val WCAG_AA_CONTRAST = 4.5

    /**
     * Chosen to sit between the ΔE values the supported reading themes produce
     * against yellow. White 18.4, sepia 15.9 and light paper 12.6 keep it.
     * Saturated 9.3, bright 6.0 and warm 5.2 paper reject it, because those are
     * already close enough to yellow that the overlay composites back to nearly
     * the same hue. `AudioHighlightColorTest._debug_print delta-E values`
     * prints the figures.
     */
    private const val MIN_VISIBILITY_DELTA_E = 10.0

    private const val OVERLAY_ALPHA = 0x33

    private val YELLOW_OVERLAY = Color.argb(OVERLAY_ALPHA, 0xFF, 0xEB, 0x3B)
    private val BLACK_OVERLAY = Color.argb(OVERLAY_ALPHA, 0x00, 0x00, 0x00)
    private val WHITE_OVERLAY = Color.argb(OVERLAY_ALPHA, 0xFF, 0xFF, 0xFF)

    fun pickHighlightColor(readingBackground: Int, verseTextColor: Int): Int {
        val yellowComposite = ColorUtils.compositeColors(YELLOW_OVERLAY, readingBackground)
        val yellowVisibility = labDistance(yellowComposite, readingBackground)
        val yellowTextContrast = ColorUtils.calculateContrast(verseTextColor, yellowComposite)
        if (yellowVisibility >= MIN_VISIBILITY_DELTA_E && yellowTextContrast >= WCAG_AA_CONTRAST) {
            return YELLOW_OVERLAY
        }

        // Both 20%-alpha neutrals preserve text readability (the verse text
        // color still dominates the composite), so WCAG isn't re-checked here.
        val blackComposite = ColorUtils.compositeColors(BLACK_OVERLAY, readingBackground)
        val whiteComposite = ColorUtils.compositeColors(WHITE_OVERLAY, readingBackground)
        val blackVisibility = labDistance(blackComposite, readingBackground)
        val whiteVisibility = labDistance(whiteComposite, readingBackground)
        return if (blackVisibility >= whiteVisibility) BLACK_OVERLAY else WHITE_OVERLAY
    }

    /** ΔE*ab: 1-2 is barely perceptible, 5 clearly different, 10 obviously distinct. */
    private fun labDistance(colorA: Int, colorB: Int): Double {
        val labA = DoubleArray(3)
        val labB = DoubleArray(3)
        ColorUtils.colorToLAB(colorA, labA)
        ColorUtils.colorToLAB(colorB, labB)
        return ColorUtils.distanceEuclidean(labA, labB)
    }
}
