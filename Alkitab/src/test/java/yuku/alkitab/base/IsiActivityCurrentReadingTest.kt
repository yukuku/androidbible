package yuku.alkitab.base

import androidx.drawerlayout.widget.DrawerLayout
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.base.util.CurrentReading
import yuku.alkitab.base.util.ReadingRange
import yuku.alkitab.base.verses.ReadingGuideMode
import yuku.alkitab.base.verses.VersesUiModel
import yuku.alkitab.base.widget.ActiveSplit1
import yuku.alkitab.base.widget.SplitViewManager
import yuku.alkitab.debug.R
import yuku.alkitab.model.Book
import yuku.alkitab.model.Version

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class IsiActivityCurrentReadingTest {
    @Test
    fun `each pane retains the parts of the daily reading available in its version`() {
        yuku.afw.App.initWithAppContext(ApplicationProvider.getApplicationContext())
        val previousServices = App.services
        val previousMode = Preferences.getString(R.string.pref_currentReadingDisplay_key)
        try {
            App.services = AppServices(mockk(), mockk(), mockk())
            Preferences.setString(R.string.pref_currentReadingDisplay_key, "line")
            CurrentReading.setRanges(intArrayOf(0x000100, 0x000100, 0x270103, 0x27010a))
            fun book(id: Int, verseCount: Int) = Book().apply {
                bookId = id
                chapter_count = 1
                verse_counts = intArrayOf(verseCount)
            }
            val shortMatthew = book(39, 8)
            val primary = mockk<Version>()
            every { primary.getBook(0) } returns null
            every { primary.getBook(39) } returns shortMatthew
            val secondary = mockk<Version>()
            every { secondary.getBook(0) } returns book(0, 10)
            every { secondary.getBook(39) } returns book(39, 10)

            val activity = Robolectric.buildActivity(IsiActivity::class.java).get()
            activity.setTheme(R.style.Theme_Alkitab)
            activity.activeSplit0 = IsiActivity.ActiveSplit0(mockk(), primary, "primary", shortMatthew)
            activity.leftDrawer = mockk(relaxed = true)
            activity.lsSplit0 = mockk(relaxed = true)
            activity.lsSplit1 = mockk(relaxed = true)
            IsiActivity::class.java.getDeclaredField("drawerLayout").apply { isAccessible = true }
                .set(activity, DrawerLayout(activity))
            val manager = IsiActivity::class.java.getDeclaredMethod("getSplitViewManager").apply { isAccessible = true }
                .invoke(activity) as SplitViewManager
            SplitViewManager::class.java.getDeclaredField("activeSplit1").apply { isAccessible = true }
                .set(manager, ActiveSplit1(mockk(), secondary, "secondary"))

            IsiActivity::class.java.getDeclaredMethod("updateCurrentReading").apply { isAccessible = true }.invoke(activity)
            val mainUi = slot<VersesUiModel>()
            val splitUi = slot<VersesUiModel>()
            verify { activity.lsSplit0.versesUiModel = capture(mainUi) }
            verify { activity.lsSplit1.versesUiModel = capture(splitUi) }
            assertEquals(ReadingGuideMode.LINE, mainUi.captured.readingGuide.mode)
            assertEquals(listOf(ReadingRange(0x270103, 0x270108)), mainUi.captured.readingGuide.ranges)
            assertEquals(ReadingGuideMode.LINE, splitUi.captured.readingGuide.mode)
            assertEquals(listOf(ReadingRange(0x000101, 0x00010a), ReadingRange(0x270103, 0x27010a)), splitUi.captured.readingGuide.ranges)
        } finally {
            CurrentReading.clear()
            if (previousMode == null) Preferences.remove(yuku.afw.App.context.getString(R.string.pref_currentReadingDisplay_key))
            else Preferences.setString(R.string.pref_currentReadingDisplay_key, previousMode)
            App.services = previousServices
        }
    }
}
