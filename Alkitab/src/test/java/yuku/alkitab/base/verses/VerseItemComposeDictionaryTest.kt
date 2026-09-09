package yuku.alkitab.base.verses

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.afw.App as AfwApp
import yuku.alkitab.base.S
import yuku.alkitab.base.widget.DictionaryLinkInfo
import yuku.alkitab.debug.R
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.Ari

/**
 * Fake analyzer for the dictionary app's content provider: recognizes every
 * occurrence of the word "created" in the analyzed text.
 */
class FakeDictionaryProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor {
        val text = uri.getQueryParameter("text").orEmpty()
        lastAnalyzeText = text

        val cursor = MatrixCursor(arrayOf("offset", "len", "key"))
        var index = text.indexOf(RECOGNIZED_WORD)
        while (index >= 0) {
            cursor.addRow(arrayOf<Any>(index, RECOGNIZED_WORD.length, "key_$RECOGNIZED_WORD"))
            index = text.indexOf(RECOGNIZED_WORD, index + 1)
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    companion object {
        const val RECOGNIZED_WORD = "created"
        var lastAnalyzeText: String? = null
    }
}

/**
 * Tests for the dictionary-mode word links on the Compose verse row state:
 * words reported by the analyzer content provider become underlined,
 * tappable link ranges that call the dictionary listener, mirroring the
 * legacy DictionaryLinkSpan behavior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34])
class VerseItemComposeDictionaryTest {

    private class FakeVerses(private val texts: List<String>) : SingleChapterVerses {
        override val verseCount: Int get() = texts.size
        override fun getVerse(verse_0: Int): String = texts[verse_0]
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AfwApp.initWithAppContext(context)

        FakeDictionaryProvider.lastAnalyzeText = null

        val dims = S.CalculatedDimensions().apply {
            fontSize2dp = 17f
            fontFace = Typeface.DEFAULT
            fontBold = Typeface.NORMAL
            fontColor = 0xff202020.toInt()
            fontRedColor = 0xffcc0000.toInt()
            verseNumberColor = 0xff445566.toInt()
            lineSpacingMult = 1.15f
        }
        S.overrideAppliedDimensions(dims)
    }

    private fun registerDictionaryProvider() {
        Robolectric.buildContentProvider(FakeDictionaryProvider::class.java).create(
            ProviderInfo().apply { authority = "org.sabda.kamus.provider" }
        )
    }

    private val verseText = "In the beginning God created the heavens; created them all."

    private fun buildState(
        dictionaryMode: Boolean,
        checked: Boolean = false,
        clicked: MutableList<DictionaryLinkInfo> = mutableListOf(),
    ): VerseItemComposeState {
        val data = VersesDataModel(
            ari_bc_ = Ari.encode(1, 2, 0),
            verses_ = FakeVerses(listOf(verseText)),
        )
        val ari = Ari.encodeWithBc(data.ari_bc_, 1)
        val ui = VersesUiModel.EMPTY.copy(
            isVerseNumberShown = true,
            dictionaryModeAris = if (dictionaryMode) setOf(ari) else emptySet(),
        )
        val listeners = VersesListeners.EMPTY.copy(
            dictionaryListener_ = { info -> clicked.add(info) },
        )
        return buildVerseItemComposeState(
            context = context,
            data = data,
            ui = ui,
            listeners = listeners,
            index = 0,
            checked = checked,
            currentPosition = { 0 },
            toggleChecked = {},
            inlineLinkViewProvider = { View(context) },
        )
    }

    @Test
    fun `dictionary mode analyzes the text after the verse number and links every recognized word`() {
        registerDictionaryProvider()
        val clicked = mutableListOf<DictionaryLinkInfo>()
        val state = buildState(dictionaryMode = true, clicked = clicked)

        val startPos = state.render.startPosAfterVerseNumber
        val fullText = state.render.text.text

        // The analyzer must have received the verse text without the
        // verse-number prefix.
        assertTrue(startPos > 0)
        assertEquals(fullText.substring(startPos), FakeDictionaryProvider.lastAnalyzeText)

        val links = state.render.text.getLinkAnnotations(0, fullText.length)
        assertEquals(2, links.size)
        for (link in links) {
            assertEquals(FakeDictionaryProvider.RECOGNIZED_WORD, fullText.substring(link.start, link.end))
        }

        // Each linked word range is underlined, like the legacy ClickableSpan, and carries no
        // color of its own so it keeps the color of the run it sits in.
        val underlined = state.render.text.spanStyles.filter { it.item.textDecoration == TextDecoration.Underline }
        assertEquals(2, underlined.size)
        assertEquals(links.map { it.start to it.end }.toSet(), underlined.map { it.start to it.end }.toSet())
        for (style in underlined) {
            assertEquals(Color.Unspecified, style.item.color)
        }

        // Tapping a link reports the word and the analyzer's key.
        val first = links.minByOrNull { it.start }!!.item as LinkAnnotation.Clickable
        first.linkInteractionListener!!.onClick(first)
        assertEquals(1, clicked.size)
        assertEquals(FakeDictionaryProvider.RECOGNIZED_WORD, clicked[0].orig_text)
        assertEquals("key_${FakeDictionaryProvider.RECOGNIZED_WORD}", clicked[0].key)
    }

    @Test
    fun `without dictionary mode the verse text carries no dictionary links`() {
        registerDictionaryProvider()
        val state = buildState(dictionaryMode = false)

        assertTrue(state.render.text.getLinkAnnotations(0, state.render.text.length).isEmpty())
    }

    @Test
    fun `automatic lookup analyzes a checked verse even without dictionary mode`() {
        registerDictionaryProvider()
        yuku.afw.storage.Preferences.setBoolean(context.getString(R.string.pref_autoDictionaryAnalyze_key), true)
        try {
            val state = buildState(dictionaryMode = false, checked = true)
            assertEquals(2, state.render.text.getLinkAnnotations(0, state.render.text.length).size)
        } finally {
            yuku.afw.storage.Preferences.remove(context.getString(R.string.pref_autoDictionaryAnalyze_key))
        }
    }

    @Test
    fun `a missing dictionary provider leaves the rendered text untouched`() {
        // No provider registered for the authority: safeQuery returns null
        // and the render result must pass through unchanged.
        val state = buildState(dictionaryMode = true)
        assertTrue(state.render.text.getLinkAnnotations(0, state.render.text.length).isEmpty())
    }
}
