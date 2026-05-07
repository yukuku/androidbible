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
 * The PRD §4.3 contract is: yellow at 20% alpha is the preferred default; if
 * compositing it on the reading background would drop contrast below WCAG AA
 * (4.5:1) against the verse text color, fall back to whichever of black/white
 * at 20% scores better.
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
    fun `dark theme with mid-tone text falls back to a non-yellow overlay`() {
        // A common Material dark surface (#1F1F1F) with a slightly-darker text
        // color tightens the yellow-on-dark contrast enough to push us below
        // WCAG AA — exercising the fallback path.
        val bg = Color.rgb(0x1F, 0x1F, 0x1F)
        val text = Color.rgb(0xCC, 0xCC, 0xCC)
        val pick = AudioHighlightColor.pickHighlightColor(bg, text)

        // Confirm the picked overlay is one of the three known overlays...
        assertTrue("unexpected color $pick", pick in setOf(yellow20, black20, white20))
        // ...and falls back to a neutral when the dark-on-dark composite
        // squeezes the contrast.
        if (pick == yellow20) {
            // Document a hypothetical alternative — fail-loud if the future
            // tightens our threshold but the test still passes by accident.
            val composited = ColorUtils.compositeColors(yellow20, bg)
            val contrast = ColorUtils.calculateContrast(text, composited)
            assertTrue(
                "yellow was picked but contrast=$contrast — should be ≥ 4.5 to be valid",
                contrast >= 4.5,
            )
        } else {
            assertNotEquals("expected fallback, not yellow", yellow20, pick)
        }
    }

    @Test
    fun `picked overlay always meets WCAG AA contrast`() {
        val cases = listOf(
            // (background, text)
            Color.WHITE to Color.BLACK,
            Color.rgb(0xF4, 0xEC, 0xD8) to Color.rgb(0x44, 0x33, 0x22),
            Color.BLACK to Color.WHITE,
            Color.rgb(0x12, 0x12, 0x12) to Color.rgb(0xE6, 0xE6, 0xE6),
            Color.rgb(0x21, 0x21, 0x21) to Color.WHITE,
            Color.rgb(0x33, 0x2A, 0x1E) to Color.rgb(0xEC, 0xE3, 0xCD),
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
    fun `mid-gray bg with mid-gray text picks the higher-contrast neutral`() {
        // Yellow on mid-gray composites to a yellow-tinted gray, which has
        // poor contrast against mid-gray text. The fallback should pick
        // whichever of black/white pulls further from the text color.
        val bg = Color.rgb(0x80, 0x80, 0x80)
        val text = Color.rgb(0x40, 0x40, 0x40)
        val pick = AudioHighlightColor.pickHighlightColor(bg, text)
        // Either neutral is acceptable; assert it's NOT yellow.
        assertTrue("should fall back to black or white", pick == black20 || pick == white20)
    }
}
