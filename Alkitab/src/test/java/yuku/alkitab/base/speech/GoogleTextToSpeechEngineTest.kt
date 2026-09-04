package yuku.alkitab.base.speech

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleTextToSpeechEngineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `engine package is fixed to Google Speech Services`() {
        assertEquals("com.google.android.tts", GoogleTextToSpeechEngine.ENGINE_PACKAGE)
    }

    @Test
    fun `missing Google package is reported before initialization`() {
        assertFalse(GoogleTextToSpeechEngine.isGoogleEngineInstalled(context))
    }

    @Test
    fun `installed Google package is detected`() {
        val packageInfo = PackageInfo().apply {
            packageName = GoogleTextToSpeechEngine.ENGINE_PACKAGE
            applicationInfo = ApplicationInfo().apply {
                packageName = GoogleTextToSpeechEngine.ENGINE_PACKAGE
            }
        }
        shadowOf(context.packageManager).installPackage(packageInfo)

        assertTrue(GoogleTextToSpeechEngine.isGoogleEngineInstalled(context))
    }
}
