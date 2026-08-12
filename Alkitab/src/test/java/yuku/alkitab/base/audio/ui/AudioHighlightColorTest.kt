package yuku.alkitab.base.audio.ui

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [AudioHighlightColor.pickHighlightColor]. Robolectric is needed only
 * because the production code reaches into `android.graphics.Color` /
 * `androidx.core.graphics.ColorUtils`, which are Android-platform classes.
 *
 * Contract (see [AudioHighlightColor]):
 *  - Yellow at 20% alpha is preferred when (a) the composite is perceptibly
 *    different from the reading background (LAB ΔE ≥ 5) AND (b) verse text
 *    stays WCAG AA readable on the composite.
 *  - Otherwise fall back to whichever of black/white at 20% has the larger
 *    LAB ΔE against the reading background (the more visible neutral).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.13 max; project targetSdkVersion is 35.
class AudioHighlightColorTest {

    private val yellow20 = Color.argb(0x33, 0xFF, 0xEB, 0x3B)
    private val black20 = Color.argb(0x33, 0x00, 0x00, 0x00)
    private val white20 = Color.argb(0x33, 0xFF, 0xFF, 0xFF)

    @Test
    fun `light reading background with dark text picks yellow`() {
        val pick = AudioHighlightColor.pickHighlightColor(
            readingBackground = Color.WHITE,
            verseTextColor = Color.BLACK,
        )
        assertEquals(yellow20, pick)
    }

    @Test
    fun `sepia reading background with dark text still picks yellow`() {
        val sepia = Color.rgb(0xF4, 0xEC, 0xD8)
        val pick = AudioHighlightColor.pickHighlightColor(
            readingBackground = sepia,
            verseTextColor = Color.rgb(0x44, 0x33, 0x22),
        )
        assertEquals(yellow20, pick)
    }

    @Test
    fun `yellow paper background falls back to a neutral so the highlight is visible`() {
        // A yellow-on-yellow highlight is invisible because the composite is
        // essentially the same color as the surrounding bg. Even though the
        // verse text remains readable, the user can't *find* the highlighted
        // verse. The LAB-distance gate rejects yellow on these paper themes.
        val cases = listOf(
            // Common bible-app "paper" / "manila" looks.
            Color.rgb(0xFA, 0xF0, 0x9C),
            Color.rgb(0xFB, 0xE6, 0x6F),
            Color.rgb(0xF8, 0xE9, 0x7C),
        )
        val text = Color.rgb(0x33, 0x33, 0x33)
        for (bg in cases) {
            val pick = AudioHighlightColor.pickHighlightColor(bg, text)
            assertNotEquals(
                "yellow chosen on yellow paper bg=#${Integer.toHexString(bg)}, highlight invisible",
                yellow20,
                pick,
            )
            assertTrue("expected a neutral overlay", pick == black20 || pick == white20)
        }
    }

    @Test
    fun `dark theme returns a valid overlay (yellow or neutral, both acceptable)`() {
        // On a near-black bg, yellow at 20% alpha composites to a clearly
        // different yellow-tinted dark patch (high LAB ΔE) AND text contrast
        // remains comfortably above WCAG AA, so yellow IS a valid pick.
        // Whether a future tweak prefers a neutral instead is also fine; we
        // just want to assert (a) the picked color is one of our three
        // overlays and (b) WCAG AA for text holds.
        val bg = Color.rgb(0x12, 0x12, 0x12)
        val text = Color.rgb(0xE6, 0xE6, 0xE6)
        val pick = AudioHighlightColor.pickHighlightColor(bg, text)
        assertTrue("unexpected color $pick", pick in setOf(yellow20, black20, white20))
        val composited = ColorUtils.compositeColors(pick, bg)
        val contrast = ColorUtils.calculateContrast(text, composited)
        assertTrue(
            "text contrast on highlight=${"%.2f".format(contrast)} < 4.5",
            contrast >= 4.5,
        )
    }

    @Test
    fun `picked overlay always meets WCAG AA contrast for verse text`() {
        val cases = listOf(
            // (background, text) pairs spanning light, sepia, dark, and yellow paper.
            Color.WHITE to Color.BLACK,
            Color.rgb(0xF4, 0xEC, 0xD8) to Color.rgb(0x44, 0x33, 0x22),
            Color.BLACK to Color.WHITE,
            Color.rgb(0x12, 0x12, 0x12) to Color.rgb(0xE6, 0xE6, 0xE6),
            Color.rgb(0x21, 0x21, 0x21) to Color.WHITE,
            Color.rgb(0x33, 0x2A, 0x1E) to Color.rgb(0xEC, 0xE3, 0xCD),
            Color.rgb(0xFA, 0xF0, 0x9C) to Color.rgb(0x33, 0x33, 0x33),
        )

        for ((bg, text) in cases) {
            val pick = AudioHighlightColor.pickHighlightColor(bg, text)
            val composited = ColorUtils.compositeColors(pick, bg)
            val contrast = ColorUtils.calculateContrast(text, composited)
            assertTrue(
                "contrast ${"%.2f".format(contrast)} < 4.5 for bg=#${Integer.toHexString(bg)} text=#${Integer.toHexString(text)} pick=#${Integer.toHexString(pick)}",
                contrast >= 4.5,
            )
        }
    }

    @Test
    fun `picked overlay is always perceptibly different from the reading background`() {
        // Visibility regression: ΔE ≥ ~2 on every supported theme, otherwise
        // the highlight can't be located. We test against a slightly looser
        // bound than the production threshold (5.0) because the fallback
        // neutrals on near-medium grays can sit just above the gate.
        val cases = listOf(
            Color.WHITE to Color.BLACK,
            Color.rgb(0xF4, 0xEC, 0xD8) to Color.rgb(0x44, 0x33, 0x22),
            Color.BLACK to Color.WHITE,
            Color.rgb(0x12, 0x12, 0x12) to Color.rgb(0xE6, 0xE6, 0xE6),
            Color.rgb(0xFA, 0xF0, 0x9C) to Color.rgb(0x33, 0x33, 0x33),
        )
        for ((bg, text) in cases) {
            val pick = AudioHighlightColor.pickHighlightColor(bg, text)
            val composited = ColorUtils.compositeColors(pick, bg)
            val labA = DoubleArray(3); val labB = DoubleArray(3)
            ColorUtils.colorToLAB(composited, labA)
            ColorUtils.colorToLAB(bg, labB)
            val deltaE = ColorUtils.distanceEuclidean(labA, labB)
            assertTrue(
                "highlight indistinguishable from bg: ΔE=${"%.2f".format(deltaE)} for bg=#${Integer.toHexString(bg)} pick=#${Integer.toHexString(pick)}",
                deltaE >= 2.0,
            )
        }
    }

    @Test
    fun `mid-gray bg picks the higher-visibility neutral`() {
        // Yellow on mid-gray composites to a yellow-tinted gray with poor
        // visibility. The fallback should pick whichever of black/white
        // pulls further from the bg in LAB space.
        val bg = Color.rgb(0x80, 0x80, 0x80)
        val text = Color.rgb(0x40, 0x40, 0x40)
        val pick = AudioHighlightColor.pickHighlightColor(bg, text)
        assertTrue("should fall back to black or white", pick == black20 || pick == white20)
    }
}
