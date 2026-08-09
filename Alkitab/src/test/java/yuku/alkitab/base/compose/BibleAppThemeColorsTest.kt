package yuku.alkitab.base.compose

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.afw.App as AfwApp
import yuku.alkitab.debug.R

/**
 * Pins the Compose color scheme to the XML theme resources, so the two halves
 * of the app cannot silently drift apart: if someone rebrands the XML palette,
 * these tests point straight at the Compose roles that must follow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34])
class BibleAppThemeColorsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AfwApp.initWithAppContext(context)
    }

    private fun res(colorRes: Int): Int = ResourcesCompat.getColor(context.resources, colorRes, null)

    @Test
    fun `primary carries the accent color that colorAccent gives every View widget`() {
        assertEquals(res(R.color.accent), BibleAppDarkColorScheme.primary.toArgb())
    }

    @Test
    fun `secondary carries the escape color used for interesting texts on the View side`() {
        assertEquals(res(R.color.escape), BibleAppDarkColorScheme.secondary.toArgb())
    }

    @Test
    fun `tertiaryContainer is exactly the XML toolbar color and onTertiary the status bar color`() {
        assertEquals(res(R.color.primary), BibleAppDarkColorScheme.tertiaryContainer.toArgb())
        assertEquals(res(R.color.primary_dark), BibleAppDarkColorScheme.onTertiary.toArgb())
    }

    @Test
    fun `surface and background equal the XML window background so Compose and View screens share one ground`() {
        assertEquals(res(R.color.window), BibleAppDarkColorScheme.surface.toArgb())
        assertEquals(res(R.color.window), BibleAppDarkColorScheme.background.toArgb())
    }
}
