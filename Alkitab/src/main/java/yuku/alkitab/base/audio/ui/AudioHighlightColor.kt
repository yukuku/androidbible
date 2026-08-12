package yuku.alkitab.base.audio.ui

import android.graphics.Color
import androidx.core.graphics.ColorUtils

/**
 * Picks the verse-highlight overlay color used while audio is playing,
 * adapting to whatever reading background the user has configured.
 *
 * Strategy:
 *  1. Try Material Yellow 500 at 20% alpha. To use yellow it must satisfy
 *     **both**:
 *     - **Visibility**: when composited on top of the reading background, the
 *       result is perceptibly different from the bare background (LAB ΔE ≥
 *       [MIN_VISIBILITY_DELTA_E]). This is the check that prevents a
 *       yellow-on-yellow paper theme from making the highlight invisible.
 *       Luminance contrast is a poor proxy here, because a 20%-alpha overlay
 *       barely shifts luminance, whereas ΔE in LAB picks up the chroma shift on
 *       light backgrounds and (correctly) the lack of one on yellow paper.
 *     - **Legibility**: WCAG AA contrast (≥4.5) between the yellow-tinted
 *       composite and the verse text color, so the text is still readable.
 *  2. Otherwise fall back to whichever neutral 20%-alpha overlay (black or
 *     white) produces the larger LAB ΔE against the reading background. Both
 *     neutrals at 20% alpha keep text readable because the underlying text
 *     color still dominates.
 *
 * The returned int is an `argb` color suitable for [android.graphics.Paint.setColor]
 * and is meant to be drawn as a rectangle behind the verse text. 0 is reserved
 * for "no highlight" and is never returned.
 *
 * Pure function: no Android framework dependency at runtime beyond
 * [android.graphics.Color] / [androidx.core.graphics.ColorUtils], both of which
 * Robolectric / unit tests can satisfy without a full Android container.
 */
object AudioHighlightColor {
    private const val WCAG_AA_CONTRAST = 4.5

    /**
     * Minimum perceptual color distance (CIE LAB ΔE) the highlight must keep
     * from the bare reading background to count as "visible".
     *
     * Tuned empirically against the bibles' supported reading themes (see
     * `AudioHighlightColorTest._debug_print delta-E values`):
     *
     *   white        ΔE 18.4
     *   sepia        ΔE 15.9   ← keep yellow
     *   paper light  ΔE 12.6   ← keep yellow
     *   paper sat.   ΔE  9.3   ← reject (already yellow)
     *   paper warm   ΔE  5.2   ← reject
     *   paper bright ΔE  6.0   ← reject
     *
     * 10.0 is the sweet spot: keeps yellow on white / sepia / mildly-tinted
     * paper, rejects it on saturated yellow paper where the overlay would
     * composite back to nearly the same hue and the user can't see which
     * verse is highlighted.
     */
    private const val MIN_VISIBILITY_DELTA_E = 10.0

    /**
     * 20% alpha = 0x33; rounded to nearest convenient byte. The same alpha is
     * used for the fallback neutrals so the perceptual weight is identical.
     */
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

    /**
     * CIE LAB Euclidean distance (a perceptually-uniform color difference,
     * commonly known as ΔE*ab). Closer to 0 = visually identical;
     * 1-2 = barely perceptible; 5+ = clearly different; 10+ = obviously
     * distinct.
     */
    private fun labDistance(colorA: Int, colorB: Int): Double {
        val labA = DoubleArray(3)
        val labB = DoubleArray(3)
        ColorUtils.colorToLAB(colorA, labA)
        ColorUtils.colorToLAB(colorB, labB)
        return ColorUtils.distanceEuclidean(labA, labB)
    }
}
