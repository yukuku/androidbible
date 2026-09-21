package yuku.alkitab.base.verses

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences
import yuku.alkitab.base.S
import yuku.alkitab.base.util.SearchEngine
import yuku.alkitab.debug.R

/**
 * Tests for the verse text the screens outside the reader show: the Compose
 * renderer they call, the decorations they layer on it, and the slot that puts
 * a [VerseTextComposeView] where their TextView used to be.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class VerseTextComposeTest {

    private val FONT_COLOR = 0xff212121.toInt()
    private val HILITE_COLOR = 0xff0000ff.toInt()
    private val ARI = 0x010203

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        yuku.afw.App.initWithAppContext(context)
        Preferences.setBoolean(context.getString(R.string.pref_useComposeVerseItem_key), false)

        S.overrideAppliedDimensions(
            S.CalculatedDimensions().apply {
                fontColor = FONT_COLOR
                fontRedColor = 0xffb71c1c.toInt()
                verseNumberColor = 0xff828282.toInt()
                backgroundColor = 0xfff0f0f0.toInt()
                fontSize2dp = 17f
                lineSpacingMult = 1.2f
            }
        )
    }

    private fun setComposeVerseItem(enabled: Boolean) {
        Preferences.setBoolean(context.getString(R.string.pref_useComposeVerseItem_key), enabled)
    }

    private fun rowWithSnippet(snippetMaxLines: Int = Int.MAX_VALUE): LinearLayout {
        val row = LinearLayout(context)
        row.addView(
            TextView(context).apply {
                id = R.id.lSnippet
                maxLines = snippetMaxLines
            }
        )
        return row
    }

    private fun readyTokens(vararg tokens: String) = SearchEngine.ReadyTokens(arrayOf(*tokens))

    @Test
    fun `a verse renders without its number so the row shows text alone`() {
        val rendered = renderVerseText(ARI, "@@@0In the beginning")

        assertEquals("In the beginning", rendered.text)
    }

    @Test
    fun `search hilite colors and bolds every matched run of the rendered verse`() {
        val rendered = renderVerseText(ARI, "the light and the dark")

        val hilited = rendered.withSearchHilite(readyTokens("the"), HILITE_COLOR)

        assertEquals("the light and the dark", hilited.text)
        val colored = hilited.spanStyles.filter { it.item.color == Color(HILITE_COLOR) }
        assertEquals(listOf(0 to 3, 14 to 17), colored.map { it.start to it.end })
        val bolded = hilited.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(listOf(0 to 3, 14 to 17), bolded.map { it.start to it.end })
    }

    @Test
    fun `search hilite with no tokens leaves the rendered verse untouched`() {
        val rendered = renderVerseText(ARI, "the light and the dark")

        assertSame(rendered, rendered.withSearchHilite(null, HILITE_COLOR))
    }

    @Test
    fun `the reference prefix is underlined and separated from the verse by a space`() {
        val prefixed = AnnotatedString("In the beginning").withReferencePrefix("Genesis 1:1")

        assertEquals("Genesis 1:1 In the beginning", prefixed.text)
        val underlined = prefixed.spanStyles.filter { it.item.textDecoration == TextDecoration.Underline }
        assertEquals(listOf(0 to 11), underlined.map { it.start to it.end })
    }

    @Test
    fun `with the setting off the slot keeps the row's TextView`() {
        setComposeVerseItem(false)
        val row = rowWithSnippet()
        val original = row.findViewById<TextView>(R.id.lSnippet)

        val slot = VerseTextSlot.of(row, R.id.lSnippet)
        slot.setText(
            textSizeMult = 1f,
            legacy = { it.text = "from the TextView" },
            compose = { throw AssertionError("the Compose branch must not run") },
        )

        assertSame(original, row.findViewById(R.id.lSnippet))
        assertEquals("from the TextView", original.text.toString())
    }

    @Test
    fun `with the setting on the slot swaps in a Compose view that keeps the id and position`() {
        setComposeVerseItem(true)
        val row = rowWithSnippet()
        row.addView(TextView(context), 0) // a sibling above, so the swap has an index to preserve

        val slot = VerseTextSlot.of(row, R.id.lSnippet)
        slot.setText(
            textSizeMult = 1f,
            legacy = { throw AssertionError("the TextView branch must not run") },
            compose = { AnnotatedString("from Compose") },
        )

        val swapped = row.findViewById<VerseTextComposeView>(R.id.lSnippet)
        assertEquals(1, row.indexOfChild(swapped))
        assertEquals("from Compose", swapped.contentDescription.toString())
    }

    @Test
    fun `asking for the slot again on a recycled row reuses the Compose view`() {
        setComposeVerseItem(true)
        val row = rowWithSnippet()

        VerseTextSlot.of(row, R.id.lSnippet)
        val firstView = row.findViewById<VerseTextComposeView>(R.id.lSnippet)

        VerseTextSlot.of(row, R.id.lSnippet).setText(
            textSizeMult = 1f,
            legacy = { throw AssertionError("the TextView branch must not run") },
            compose = { AnnotatedString("rebound") },
        )

        assertSame(firstView, row.findViewById(R.id.lSnippet))
        assertEquals("rebound", firstView.contentDescription.toString())
    }

    @Test
    fun `the swapped in view inherits the line cap the row's TextView carried`() {
        setComposeVerseItem(true)
        val row = rowWithSnippet(snippetMaxLines = 4)

        VerseTextSlot.of(row, R.id.lSnippet).setText(
            textSizeMult = 1f,
            legacy = { throw AssertionError("the TextView branch must not run") },
            compose = { AnnotatedString("a long snippet") },
        )

        assertEquals(4, checkNotNull(row.findViewById<VerseTextComposeView>(R.id.lSnippet).state).maxLines)
    }

    @Test
    fun `a color override replaces the reader's verse color for a checked row`() {
        setComposeVerseItem(true)
        val checkedColor = 0xffffffff.toInt()
        val view = VerseTextComposeView(context)

        view.bind(AnnotatedString("selected"), textSizeMult = 2f, colorOverride = checkedColor)

        val state = checkNotNull(view.state)
        assertEquals(checkedColor, state.fontColor)
        assertEquals(34f, state.fontSizeDp, 0.001f)
    }

    @Test
    fun `without a color override the slot takes the reader's configured verse color`() {
        val view = VerseTextComposeView(context)

        view.bind(AnnotatedString("plain"), textSizeMult = 1f)

        assertEquals(FONT_COLOR, checkNotNull(view.state).fontColor)
        assertTrue(checkNotNull(view.state).lineSpacingMult == 1.2f)
    }
}
