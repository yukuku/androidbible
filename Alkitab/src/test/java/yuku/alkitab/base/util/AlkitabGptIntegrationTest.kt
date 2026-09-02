package yuku.alkitab.base.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPackageManager

/**
 * Robolectric is required here: the whole point of [AlkitabGptIntegration] is what PackageManager
 * answers, so the tests install and omit the companion app through `ShadowPackageManager`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class AlkitabGptIntegrationTest {

    private lateinit var context: Context
    private lateinit var shadowPm: ShadowPackageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowPm = shadowOf(context.packageManager)
    }

    @Test
    fun `resolveLaunchIntent returns null when Alkitab GPT is not installed`() = runBlocking {
        assertNull(AlkitabGptIntegration.resolveLaunchIntent(context))
    }

    @Test
    fun `resolveLaunchIntent prefers the verse-lookup action when Alkitab GPT declares it`() = runBlocking {
        installViewActivity()
        installLauncherActivity()

        val intent = AlkitabGptIntegration.resolveLaunchIntent(context)

        assertEquals("org.sabda.gpt.action.VIEW", intent?.action)
        assertEquals(AlkitabGptIntegration.PACKAGE_NAME, intent?.getPackage())
    }

    @Test
    fun `resolveLaunchIntent falls back to the launcher entry point when the verse-lookup action is not declared`() = runBlocking {
        installLauncherActivity()

        val intent = AlkitabGptIntegration.resolveLaunchIntent(context)

        assertEquals(Intent.ACTION_MAIN, intent?.action)
        assertEquals(AlkitabGptIntegration.PACKAGE_NAME, intent?.component?.packageName)
    }

    @Test
    fun `withVerse attaches the verse extras to a copy, leaving the shared template untouched`() {
        val template = Intent("org.sabda.gpt.action.VIEW").setPackage(AlkitabGptIntegration.PACKAGE_NAME)

        val intent = AlkitabGptIntegration.withVerse(template, ari = 257, reference = "Gen 1:1", verseText = "In the beginning...")

        assertEquals("org.sabda.gpt.action.VIEW", intent.action)
        assertEquals(AlkitabGptIntegration.PACKAGE_NAME, intent.getPackage())
        assertEquals(257, intent.getIntExtra(AlkitabGptIntegration.EXTRA_ARI, 0))
        assertEquals("Gen 1:1", intent.getStringExtra(AlkitabGptIntegration.EXTRA_REFERENCE))
        assertEquals("In the beginning...", intent.getStringExtra(AlkitabGptIntegration.EXTRA_VERSE_TEXT))
        assertEquals(
            Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK,
            intent.flags,
        )

        assertFalse(template.hasExtra(AlkitabGptIntegration.EXTRA_ARI))
    }

    private fun installViewActivity() {
        val component = ComponentName(AlkitabGptIntegration.PACKAGE_NAME, "${AlkitabGptIntegration.PACKAGE_NAME}.ViewActivity")
        shadowPm.addActivityIfNotPresent(component)
        shadowPm.addIntentFilterForActivity(
            component,
            IntentFilter("org.sabda.gpt.action.VIEW").apply { addCategory(Intent.CATEGORY_DEFAULT) },
        )
    }

    private fun installLauncherActivity() {
        val component = ComponentName(AlkitabGptIntegration.PACKAGE_NAME, "${AlkitabGptIntegration.PACKAGE_NAME}.MainActivity")
        shadowPm.addActivityIfNotPresent(component)
        shadowPm.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                addCategory(Intent.CATEGORY_DEFAULT)
            },
        )
    }
}
