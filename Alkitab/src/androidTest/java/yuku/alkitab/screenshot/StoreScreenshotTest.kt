package yuku.alkitab.screenshot

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.IsiActivity
import yuku.alkitab.base.ac.GotoActivity
import yuku.alkitab.base.ac.MarkerListActivity
import yuku.alkitab.base.ac.MarkersActivity
import yuku.alkitab.base.ac.SearchActivity
import yuku.alkitab.base.settings.SettingsActivity
import yuku.alkitab.base.widget.ConfigurationWrapper
import yuku.alkitab.debug.R
import yuku.alkitab.model.Marker
import yuku.alkitab.util.Ari
import yuku.alkitab.versionmanager.VersionsActivity

/**
 * Captures the Play Store screenshot set.
 *
 * Screens are reached by launching their activity directly rather than by
 * tapping through the UI: taps would have to match on-screen text, which
 * changes with every language this runs in.
 *
 * Driven by tools/screenshots/capture.py. See docs/screenshots.md.
 */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotTest {
    private class Shot(val id: String, val intent: () -> Intent)

    @Test
    fun captureStoreScreenshots() {
        val languages = argument("languages", "in").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val outputRoot = outputRoot()

        ScreenshotSeed.apply()

        for (language in languages) {
            setAppLanguage(language)
            val languageDir = File(outputRoot, language).apply { mkdirs() }
            for (shot in SHOTS) {
                capture(shot, File(languageDir, "${shot.id}.png"))
            }
        }
    }

    private fun capture(shot: Shot, target: File) {
        ActivityScenario.launch<android.app.Activity>(shot.intent()).use {
            device.waitForIdle(IDLE_TIMEOUT_MILLIS)
            // waitForIdle returns as soon as the event queue drains, which for
            // the reader is before the verse list has drawn its first frame.
            Thread.sleep(SETTLE_MILLIS)
            assertTrue("could not write $target", device.takeScreenshot(target))
        }
    }

    /**
     * The app's language is its own preference, independent of the device
     * locale, so one device produces every language. [ConfigurationWrapper]
     * is consulted by `BaseActivity.attachBaseContext`, so the change takes
     * effect for the next activity launched, which is the next screenshot.
     */
    private fun setAppLanguage(language: String) {
        Preferences.setString(R.string.pref_language_key, language)
        ConfigurationWrapper.notifyConfigurationNeedsUpdate()
    }

    /**
     * `additionalTestOutputDir` is set by AGP, which copies whatever is written
     * there back to the host once the run finishes. That is the only way to get
     * files off a Gradle managed device, which is torn down before anything
     * could pull from it. The fallback keeps `adb` runs against a connected
     * device working when AGP does not set it.
     */
    private fun outputRoot(): File {
        val fromAgp = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val root = if (fromAgp != null) {
            File(fromAgp, "store-screenshots")
        } else {
            File(App.context.getExternalFilesDir(null), "store-screenshots")
        }
        root.deleteRecursively()
        root.mkdirs()
        return root
    }

    private fun argument(name: String, fallback: String): String =
        InstrumentationRegistry.getArguments().getString(name) ?: fallback

    companion object {
        private const val IDLE_TIMEOUT_MILLIS = 10_000L
        private const val SETTLE_MILLIS = 1_500L

        private const val PSALMS = 18

        private lateinit var device: UiDevice

        @BeforeClass
        @JvmStatic
        fun setUpDevice() {
            device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        }

        private val SHOTS = listOf(
            Shot("reader") { IsiActivity.createIntent(Ari.encode(PSALMS, 23, 1)) },
            Shot("goto") { GotoActivity.createIntent(PSALMS, 23, 1) },
            Shot("search") { SearchActivity.createIntent(PSALMS) },
            Shot("markers") { MarkersActivity.createIntent() },
            Shot("bookmarks") { MarkerListActivity.createIntent(App.context, Marker.Kind.bookmark, 0) },
            Shot("highlights") { MarkerListActivity.createIntent(App.context, Marker.Kind.highlight, 0) },
            Shot("notes") { MarkerListActivity.createIntent(App.context, Marker.Kind.note, 0) },
            Shot("versions") { VersionsActivity.createIntent() },
            Shot("settings") { Intent(App.context, SettingsActivity::class.java) },
        )
    }
}
