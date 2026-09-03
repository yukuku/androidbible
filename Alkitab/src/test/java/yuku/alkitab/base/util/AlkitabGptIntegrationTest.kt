package yuku.alkitab.base.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPackageManager

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
    fun `isChatPopupAvailable is false when Alkitab GPT is not installed`() = runBlocking {
        assertFalse(AlkitabGptIntegration.isChatPopupAvailable(context))
    }

    @Test
    fun `isChatPopupAvailable is false when only the launcher activity is present, so a build too old for the popup never shows the menu item`() = runBlocking {
        installActivity("MainActivity", IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            addCategory(Intent.CATEGORY_DEFAULT)
        })

        assertFalse(AlkitabGptIntegration.isChatPopupAvailable(context))
    }

    @Test
    fun `isChatPopupAvailable is true once Alkitab GPT declares the chat-popup action`() = runBlocking {
        installChatPopupActivity()

        assertTrue(AlkitabGptIntegration.isChatPopupAvailable(context))
    }

    @Test
    fun `chatPopupIntent targets Alkitab GPT's chat popup and carries the passage in the keys its ChatContextFactory reads`() {
        val intent = AlkitabGptIntegration.chatPopupIntent(bookName = "Kejadian", chapter_1 = 1, verseStart_1 = 1, verseEnd_1 = 3)

        assertEquals("org.sabda.gpt.action.SHOW_CHAT_POPUP", intent.action)
        assertEquals("org.sabda.gpt", intent.getPackage())
        assertEquals("Kejadian", intent.getStringExtra("bookName"))
        assertEquals(1, intent.getIntExtra("chapter", -1))
        assertEquals(1, intent.getIntExtra("verseStart", -1))
        assertEquals(3, intent.getIntExtra("verseEnd", -1))
        assertEquals("Apps Alkitab", intent.getStringExtra("source"))
    }

    @Test
    fun `chatPopupIntent carries no flags, because the chat popup is a translucent activity meant to sit on top of the calling task`() {
        val intent = AlkitabGptIntegration.chatPopupIntent(bookName = "Kejadian", chapter_1 = 1, verseStart_1 = 1, verseEnd_1 = 1)

        assertEquals(0, intent.flags)
    }

    private fun installChatPopupActivity() {
        installActivity(
            "ChatPopUpActivity",
            IntentFilter(AlkitabGptIntegration.ACTION_SHOW_CHAT_POPUP).apply { addCategory(Intent.CATEGORY_DEFAULT) },
        )
    }

    private fun installActivity(simpleName: String, filter: IntentFilter) {
        val component = ComponentName(AlkitabGptIntegration.PACKAGE_NAME, "${AlkitabGptIntegration.PACKAGE_NAME}.$simpleName")
        shadowPm.addActivityIfNotPresent(component)
        shadowPm.addIntentFilterForActivity(component, filter)
    }
}
