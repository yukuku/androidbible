package yuku.alkitab.base.audio.ui

import android.graphics.Color
import androidx.core.graphics.ColorUtils

/**
 * Picks the verse-highlight overlay color used while audio is playing,
 * adapting to whatever reading background the user has configured.
 *
 * Strategy (from PRD §4.3):
 *  1. Try Material Yellow 500 at 20% alpha. If, when composited on top of
 *     [readingBackground], the result keeps WCAG AA contrast (≥4.5) against
 *     [verseTextColor], use the yellow.
 *  2. Otherwise (typically dark themes), fall back to a neutral 20%-alpha
 *     overlay — black or white, whichever scores better on the reading
 *     background.
 *
 * The returned int is an `argb` color suitable for [android.graphics.Paint.setColor]
 * and is meant to be drawn as a rectangle behind the verse text. Returning 0
 * is reserved for "no highlight" — the function never returns 0.
 *
 * Pure function: no Android framework dependency at runtime beyond
 * [android.graphics.Color] / [androidx.core.graphics.ColorUtils], both of which
 * Robolectric / unit tests can satisfy without a full Android container.
 */
object AudioHighlightColor {
    private const val WCAG_AA_CONTRAST = 4.5

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
        val yellowContrast = ColorUtils.calculateContrast(verseTextColor, yellowComposite)
        if (yellowContrast >= WCAG_AA_CONTRAST) return YELLOW_OVERLAY

        val blackComposite = ColorUtils.compositeColors(BLACK_OVERLAY, readingBackground)
        val whiteComposite = ColorUtils.compositeColors(WHITE_OVERLAY, readingBackground)
        val blackContrast = ColorUtils.calculateContrast(verseTextColor, blackComposite)
        val whiteContrast = ColorUtils.calculateContrast(verseTextColor, whiteComposite)
        return if (blackContrast >= whiteContrast) BLACK_OVERLAY else WHITE_OVERLAY
    }
}
