package yuku.alkitab.base.verses

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
import yuku.alkitab.debug.R

/**
 * The reader panes and the verse dialogs all build their list through
 * [createVersesController], so these cover the pick between the two
 * [VersesController] implementations and the in-place pane swap the Compose
 * one needs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class VersesControllerFactoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        yuku.afw.App.initWithAppContext(context)
    }

    private fun setComposeVerseItem(enabled: Boolean) {
        Preferences.setBoolean(context.getString(R.string.pref_useComposeVerseItem_key), enabled)
    }

    private fun parentWithPane(): LinearLayout {
        val parent = LinearLayout(context)
        parent.addView(View(context)) // a sibling above, so the swap has an index to preserve
        parent.addView(
            EmptyableRecyclerView(context).apply { id = R.id.lsView },
            ViewGroup.LayoutParams(300, 400),
        )
        return parent
    }

    @Test
    fun `with the setting off the pane keeps its RecyclerView`() {
        setComposeVerseItem(false)
        val parent = parentWithPane()
        val rv = parent.findViewById<EmptyableRecyclerView>(R.id.lsView)

        val controller = createVersesController(rv, "pane")

        assertTrue(controller is VersesControllerImpl)
        assertSame(rv, parent.findViewById(R.id.lsView))
    }

    @Test
    fun `with the setting on the RecyclerView is swapped for a Compose pane in the same slot`() {
        setComposeVerseItem(true)
        val parent = parentWithPane()

        val controller = createVersesController(parent.findViewById(R.id.lsView), "pane")

        assertTrue(controller is VersesComposeControllerImpl)
        val pane = parent.findViewById<VersesComposeView>(R.id.lsView)
        assertEquals(1, parent.indexOfChild(pane))
        assertEquals(2, parent.childCount)
        assertEquals(300, pane.layoutParams.width)
        assertEquals(400, pane.layoutParams.height)
    }

    @Test
    fun `the swapped in pane is driven by the controller the factory returned`() {
        setComposeVerseItem(true)
        val parent = parentWithPane()

        val controller = createVersesController(parent.findViewById(R.id.lsView), "pane")

        val pane = parent.findViewById<VersesComposeView>(R.id.lsView)
        assertSame(controller, pane.controller)
    }

    @Test
    fun `the verses dialog layout keeps its weighted slot after the swap`() {
        setComposeVerseItem(true)
        val root = LayoutInflater.from(context).inflate(R.layout.dialog_verses, null) as ViewGroup
        val original = root.findViewById<EmptyableRecyclerView>(R.id.lsView)
        val originalIndex = root.indexOfChild(original)
        val originalParams = original.layoutParams as LinearLayout.LayoutParams

        createVersesController(original, "verses")

        val pane = root.findViewById<VersesComposeView>(R.id.lsView)
        assertEquals(originalIndex, root.indexOfChild(pane))
        assertEquals(originalParams.weight, (pane.layoutParams as LinearLayout.LayoutParams).weight, 0.001f)
    }

    @Test
    fun `the name and models given to the factory reach the controller`() {
        setComposeVerseItem(true)
        val parent = parentWithPane()
        val ui = VersesUiModel.EMPTY.copy(isVerseNumberShown = true)

        val controller = createVersesController(parent.findViewById(R.id.lsView), "xref", versesUiModel = ui)

        assertEquals("xref", controller.name)
        assertEquals(ui, controller.versesUiModel)
    }
}
