package yuku.alkitab.base.util

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LabelColorUtilTest {
    @Test
    fun `label text uses the higher contrast of black and white across the color cube`() {
        for (red in 0..255 step 17) {
            for (green in 0..255 step 17) {
                for (blue in 0..255 step 17) {
                    val background = Color.rgb(red, green, blue)
                    val foreground = LabelColorUtil.getForegroundBasedOnBackground(background and 0xffffff) or Color.BLACK
                    assertTrue(foreground == Color.BLACK || foreground == Color.WHITE)
                    assertEquals(
                        maxOf(
                            ColorUtils.calculateContrast(Color.BLACK, background),
                            ColorUtils.calculateContrast(Color.WHITE, background),
                        ),
                        ColorUtils.calculateContrast(foreground, background),
                        0.00001,
                    )
                }
            }
        }
    }
}
