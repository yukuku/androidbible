package yuku.alkitab.base.widget

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.afw.App as AfwApp
import yuku.afw.storage.Preferences
import yuku.alkitab.debug.R

/**
 * Regression tests for [ConfigurationWrapper.localizedContext]: strings must
 * follow the app's own language preference, not the device locale.
 *
 * Components that never go through `BaseActivity.attachBaseContext` (services,
 * receivers, workers) resolve against the device locale by default, which
 * silently ignores the in-app language setting. The device locale is pinned to
 * English here while the preference asks for Indonesian, so a regression shows
 * up as English text rather than as a passing tautology.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], qualifiers = "en-rUS")
class ConfigurationWrapperLocalizedContextTest {

    private lateinit var defaultLocaleBeforeTest: Locale

    @Before
    fun setUp() {
        AfwApp.initWithAppContext(ApplicationProvider.getApplicationContext())
        defaultLocaleBeforeTest = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocaleBeforeTest)
        val context = ApplicationProvider.getApplicationContext<Application>()
        Preferences.remove(context.getString(R.string.pref_language_key))
    }

    @Test
    fun `strings resolve in the app language preference even when the device locale differs`() {
        Preferences.setString(R.string.pref_language_key, "in")

        val context = ApplicationProvider.getApplicationContext<Application>()
        val localized = ConfigurationWrapper.localizedContext(context)

        assertEquals("Memuat Kejadian 1", localized.getString(R.string.audio_log_loading, "Kejadian 1"))
    }

    @Test
    fun `the plain application context still resolves in the device locale`() {
        Preferences.setString(R.string.pref_language_key, "in")

        val context = ApplicationProvider.getApplicationContext<Application>()

        // The bug being guarded against: this is what a Service resolving
        // against itself gets, and it is why log strings need an explicit
        // localized context rather than `this`.
        assertNotEquals(
            context.getString(R.string.audio_log_loading, "Genesis 1"),
            ConfigurationWrapper.localizedContext(context).getString(R.string.audio_log_loading, "Genesis 1"),
        )
    }

    @Test
    fun `DEFAULT preference falls back to the device locale`() {
        Preferences.setString(R.string.pref_language_key, "DEFAULT")

        val context = ApplicationProvider.getApplicationContext<Application>()
        val localized = ConfigurationWrapper.localizedContext(context)

        assertEquals("Loading Genesis 1", localized.getString(R.string.audio_log_loading, "Genesis 1"))
    }
}
