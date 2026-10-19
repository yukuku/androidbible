package yuku.alkitab.base.ac

import android.app.Application
import android.os.Looper
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.base.services.VersionManager
import yuku.alkitab.debug.R
import yuku.alkitab.model.Book
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], qualifiers = "en-rUS")
class SearchSuggestionsTest {
    private lateinit var originalServices: AppServices
    private lateinit var activity: SearchActivity
    private lateinit var adapter: SearchActivity.SearchHistoryAdapter

    @Before
    fun setUp() {
        yuku.afw.App.context = RuntimeEnvironment.getApplication()
        Preferences.invalidate()
        Preferences.setString(yuku.afw.App.context.getString(R.string.pref_language_key), "en")
        originalServices = App.services
        val books = arrayOf(Book().apply {
            bookId = 0
            shortName = "Kejadian"
            chapter_count = 50
            verse_counts = IntArray(50) { 32 }
        })
        val version = mockk<Version> {
            every { consecutiveBooks } returns books
            every { reference(Ari.encode(0, 5, 0)) } returns "Kejadian 5"
        }
        val versions = mockk<VersionManager> {
            every { activeVersion() } returns version
            every { activeVersionId() } returns "internal"
        }
        App.services = AppServices(mockk(relaxed = true), versions, mockk(relaxed = true))
        activity = Robolectric.buildActivity(SearchActivity::class.java).get()
        activity.setTheme(R.style.Theme_Alkitab)
        adapter = activity.SearchHistoryAdapter()
    }

    @After
    fun tearDown() {
        adapter.changeCursor(null)
        App.services = originalServices
    }

    @Test
    fun `reference precedes matching history and colors only the reference`() {
        adapter.setData(SearchActivity.SearchHistory(mutableListOf(SearchActivity.SearchHistory.Entry("kej5 love"))))
        adapter.setQuery("kej5")
        shadowOf(Looper.getMainLooper()).idle()
        val cursor = adapter.cursor
        assertEquals(3, cursor.count)
        cursor.moveToFirst()
        assertEquals(Ari.encode(0, 5, 0), cursor.getInt(2))
        val view = TextView(activity).apply { id = android.R.id.text1 }
        adapter.bindView(view, activity, cursor)
        assertEquals("Go to: Kejadian 5", view.text.toString())
        val text = view.text as Spanned
        val span = text.getSpans(0, text.length, ForegroundColorSpan::class.java).single()
        assertEquals(7, text.getSpanStart(span))
        assertEquals(text.length, text.getSpanEnd(span))
        assertEquals(activity.getColor(R.color.accent), span.foregroundColor)
        cursor.moveToNext()
        assertEquals("kej5 love", cursor.getString(1))
    }

    @Test
    fun `reference without matching history has no clear history row`() {
        adapter.setQuery("kej5")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, adapter.count)
    }

    @Test
    fun `book only retains matching history without a reference row`() {
        adapter.setData(SearchActivity.SearchHistory(mutableListOf(SearchActivity.SearchHistory.Entry("kej love"))))
        adapter.setQuery("kej")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, adapter.count)
        adapter.cursor.moveToFirst()
        assertEquals("kej love", adapter.cursor.getString(1))
        adapter.setData(SearchActivity.SearchHistory(mutableListOf()))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, adapter.count)
    }
}
