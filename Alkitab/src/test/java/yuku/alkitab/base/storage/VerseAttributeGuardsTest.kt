package yuku.alkitab.base.storage

import android.app.Application
import android.content.ContentResolver
import android.database.MatrixCursor
import io.mockk.every
import io.mockk.mockk
import java.util.Date
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.base.verses.VerseAttributeLoader
import yuku.alkitab.model.ProgressMark
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.Ari

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class VerseAttributeGuardsTest {
    private lateinit var helper: InternalDbHelper

    @Before
    fun setUp() {
        yuku.afw.App.context = RuntimeEnvironment.getApplication()
        helper = InternalDbHelper(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() = helper.close()

    @Test
    fun `verse zero and oversized progress and map locations are skipped`() {
        val db = InternalDb(helper)
        for ((preset, verse) in listOf(0, 1, 3).withIndex()) {
            db.insertOrUpdateProgressMark(ProgressMark().apply {
                preset_id = preset
                ari = Ari.encode(0, 1, verse)
                caption = "test"
                modifyTime = Date()
            })
        }
        val resolver = mockk<ContentResolver>()
        val cursor = MatrixCursor(arrayOf("aris")).apply { addRow(arrayOf("[256,257,259]")) }
        every { resolver.query(any(), any(), any(), any(), any(), any()) } returns cursor
        val verses = object : SingleChapterVerses {
            override val verseCount = 2
            override fun getVerse(verse_0: Int) = "verse"
        }

        val attributes = VerseAttributeLoader.load(db, resolver, Ari.encode(0, 1, 0), verses)

        assertTrue(attributes.progressMarkBitsMap_[0] != 0)
        assertEquals(0, attributes.progressMarkBitsMap_[1])
        assertArrayEquals(booleanArrayOf(true, false), attributes.hasMapsMap_)
        assertTrue(cursor.isClosed)
    }
}
