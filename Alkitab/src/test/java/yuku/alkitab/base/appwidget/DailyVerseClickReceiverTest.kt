package yuku.alkitab.base.appwidget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.base.br.DailyVerseAppWidgetReceiver

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DailyVerseClickReceiverTest {
    @Test
    fun `fallback action opens configuration for the tapped widget`() {
        for (appWidgetId in listOf(7, 8)) {
            val context = mockk<Context>(relaxed = true)
            val launched = slot<Intent>()
            every { context.startActivity(capture(launched)) } just Runs
            DailyVerseAppWidgetReceiver.ClickReceiver().onReceive(context, Intent()
                .putExtra("app_widget_action", "configure_widget")
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

            assertEquals(DailyVerseAppWidgetConfigurationActivity::class.java.name, launched.captured.component?.className)
            assertEquals(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE, launched.captured.action)
            assertEquals(appWidgetId, launched.captured.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
            assertTrue(launched.captured.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        }
    }

    @Test
    fun `fallback action without a widget ID does not launch configuration`() {
        val context = mockk<Context>()
        DailyVerseAppWidgetReceiver.ClickReceiver().onReceive(context, Intent().putExtra("app_widget_action", "configure_widget"))
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun `widget launches reuse only a reader at the top without clearing other screens`() {
        for (action in listOf("open_app", "open_verse")) {
            val context = mockk<Context>()
            val launched = slot<Intent>()
            every { context.startActivity(capture(launched)) } just Runs
            DailyVerseAppWidgetReceiver.ClickReceiver().onReceive(context,
                Intent().putExtra("app_widget_action", action).putExtra("ari", 0x000203))

            verify(exactly = 1) { context.startActivity(any()) }
            val intent = launched.captured
            assertEquals("yuku.alkitab.action.VIEW", intent.action)
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
            assertEquals(0, intent.flags and (Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            if (action == "open_verse") assertEquals(0x000203, intent.getIntExtra("ari", 0))
            else assertFalse(intent.hasExtra("ari"))
        }
    }
}
