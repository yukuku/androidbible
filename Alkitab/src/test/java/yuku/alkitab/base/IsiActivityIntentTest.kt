package yuku.alkitab.base

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences
import yuku.alkitab.base.audio.AudioBarController
import yuku.alkitab.base.audio.ui.AudioBarUiState
import yuku.alkitab.base.audio.ui.AudioHighlightColor
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.History

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class IsiActivityIntentTest {
    @Test
    fun `lid parsing returns the destination without touching uninitialized reader views`() {
        val activity = Robolectric.buildActivity(IsiActivity::class.java).get()
        val intent = Intent("yuku.alkitab.action.VIEW")
            .putExtra("lid", 32).putExtra("selectVerse", true).putExtra("selectVerseCount", 3)
        val result = extract(activity, intent)
        assertEquals(IsiActivity.IntentResult(0x000201, true, 3), result)
    }

    @Test
    fun `invalid lid does not produce a destination`() {
        val activity = Robolectric.buildActivity(IsiActivity::class.java).get()
        assertNull(extract(activity, Intent("yuku.alkitab.action.VIEW").putExtra("lid", 0)))
    }

    @Test
    @Config(application = App::class)
    fun `restoring the reader preserves history entries and their identity`() {
        val original = History.listAllEntries()
        val entry = History.Entry("existing", 0x000101, 123L, "device")
        History.replaceAllEntries(listOf(entry))
        try {
            val saved = Bundle().apply { putInt("ari", 0x000101) }
            val controller = Robolectric.buildActivity(IsiActivity::class.java)
            controller.create(saved)
            assertEquals(listOf(entry), History.listAllEntries())
            controller.destroy()
        } finally {
            History.replaceAllEntries(original)
        }
    }

    @Test
    @Config(application = App::class)
    fun `appearance changes repaint the current audio verse without a playback event`() {
        val controller = Robolectric.buildActivity(IsiActivity::class.java).create()
        val activity = controller.get()
        val isNightMode = Preferences.getBoolean(Prefkey.is_night_mode, false)
        try {
            activity.lsSplit0 = mockk(relaxed = true)
            activity.lsSplit1 = mockk(relaxed = true)
            val binder = IsiActivity::class.java.getDeclaredMethod("getAudioBinder").apply { isAccessible = true }
                .invoke(activity) as AudioBarController
            @Suppress("UNCHECKED_CAST")
            val state = AudioBarController::class.java.getDeclaredField("_uiState").apply { isAccessible = true }
                .get(binder) as MutableStateFlow<AudioBarUiState>
            state.value = AudioBarUiState.HIDDEN.copy(playingVersionId = activity.activeSplit0.versionId, verse_1 = 1)
            activity.applyPreferences()
            clearMocks(activity.lsSplit0, activity.lsSplit1, answers = false)
            IsiActivity::class.java.getDeclaredField("audioHighlightColorCached").apply { isAccessible = true }
                .setInt(activity, 1)
            Preferences.setBoolean(Prefkey.is_night_mode, !isNightMode)
            activity.applyPreferences()
            val appearance = App.services.uiDimensions.applied()
            val color = AudioHighlightColor.pickHighlightColor(appearance.backgroundColor, appearance.fontColor)
            verify { activity.lsSplit0.setAudioHighlight(1, color) }
            verify { activity.lsSplit1.setAudioHighlight(0, 0) }
        } finally {
            Preferences.setBoolean(Prefkey.is_night_mode, isNightMode)
            App.services.uiDimensions.recalculate()
            controller.destroy()
        }
    }

    @Test
    @Config(application = App::class)
    fun `a detached history row ignores clicks`() {
        val original = History.listAllEntries()
        History.replaceAllEntries(listOf(History.Entry("existing", 0x000101, 123L, "device")))
        val controller = Robolectric.buildActivity(IsiActivity::class.java).create()
        try {
            val activity = controller.get()
            val adapter = activity.HistoryAdapter()
            val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
            adapter.onBindViewHolder(holder, 0)
            assertEquals(-1, holder.bindingAdapterPosition)
            holder.itemView.performClick()
        } finally {
            controller.destroy()
            History.replaceAllEntries(original)
        }
    }

    @Test
    @Config(application = App::class)
    fun `a new verse intent navigates the existing reader and records the destination`() {
        val original = History.listAllEntries()
        val controller = Robolectric.buildActivity(IsiActivity::class.java,
            Intent("yuku.alkitab.action.VIEW").putExtra("ari", 0x000101)).create()
        try {
            val activity = controller.get()
            activity.lsSplit0 = mockk(relaxed = true)
            activity.lsSplit1 = mockk(relaxed = true)
            val intent = Intent("yuku.alkitab.action.VIEW").putExtra("ari", 0x000203)
            controller.newIntent(intent)

            assertSame(intent, activity.intent)
            assertEquals(0, activity.activeSplit0.book.bookId)
            assertEquals(2, activity.chapter_1)
            verify { activity.lsSplit0.scrollToVerse(3) }
            assertTrue(History.listAllEntries().any { it.ari == 0x000203 })
            assertNull(Shadows.shadowOf(activity).nextStartedActivity)

            val history = History.listAllEntries()
            controller.newIntent(Intent("yuku.alkitab.action.VIEW"))
            assertEquals(2, activity.chapter_1)
            assertEquals(history, History.listAllEntries())
        } finally {
            controller.destroy()
            History.replaceAllEntries(original)
        }
    }

    @Test
    @Config(application = App::class)
    fun `reused reader respects verse selection for an incoming lid intent`() {
        val original = History.listAllEntries()
        val controller = Robolectric.buildActivity(IsiActivity::class.java).create()
        try {
            val activity = controller.get()
            activity.lsSplit0 = mockk(relaxed = true)
            activity.lsSplit1 = mockk(relaxed = true)
            controller.newIntent(Intent("yuku.alkitab.action.VIEW").putExtra("lid", 32)
                .putExtra("selectVerse", true).putExtra("selectVerseCount", 2))

            assertEquals(2, activity.chapter_1)
            verify { activity.lsSplit0.scrollToVerse(1) }
            verify { activity.lsSplit0.callAttentionForVerse(1) }
            verify { activity.lsSplit0.callAttentionForVerse(2) }
            verify { activity.lsSplit1.callAttentionForVerse(1) }
            verify { activity.lsSplit1.callAttentionForVerse(2) }
        } finally {
            controller.destroy()
            History.replaceAllEntries(original)
        }
    }

    private fun extract(activity: IsiActivity, intent: Intent): Any? =
        IsiActivity::class.java.getDeclaredMethod("extractIntent", Intent::class.java)
            .apply { isAccessible = true }.invoke(activity, intent)
}
