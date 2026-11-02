package yuku.alkitab.base.dialog

import android.os.Looper
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version
import yuku.alkitab.model.XrefEntry
import yuku.alkitab.util.IntArrayList

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
class XrefDialogSizingTest {
    @Test
    fun `reader cross references use the shared width and grow from a short target to a bounded long target`() {
        val isLegacy = Preferences.getBoolean(R.string.pref_useLegacyVerseItem_key, R.bool.pref_useLegacyVerseItem_default)
        Preferences.setBoolean(App.context.getString(R.string.pref_useLegacyVerseItem_key), true)
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(R.style.Theme_Alkitab)
        try {
            val source = mockk<Version>(relaxed = true)
            every { source.getXrefEntry(any()) } returns XrefEntry().apply {
                content = "@<ta:257@>Short@/; @<ta:258-287@>Long@/"
            }
            every { source.loadVersesByAriRanges(any(), any(), any()) } answers {
                val ranges = firstArg<IntArrayList>()
                val aris = secondArg<IntArrayList>()
                val texts = thirdArg<MutableList<String>>()
                aris.clear()
                texts.clear()
                for (ari in ranges[0]..ranges[1]) {
                    aris.add(ari)
                    texts.add("A verse with enough text to wrap across several lines.")
                }
                texts.size
            }
            val dialog = XrefDialog.newInstance(0x10101)
            dialog.init(source, "test", { _, _ -> })
            dialog.showNow(activity.supportFragmentManager, "xref")
            assertEquals(288, dialog.requireDialog().window?.attributes?.width)
            val view = dialog.requireView()
            fun measure() {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(288, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(560, View.MeasureSpec.AT_MOST),
                )
                view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            }
            measure()
            val shortHeight = view.height
            assertTrue("a short cross reference should hug its content", shortHeight in 1 until 400)
            val header = view.findViewById<TextView>(R.id.tXrefText)
            val links = (header.text as Spanned).getSpans(0, header.length(), ClickableSpan::class.java)
            assertEquals(1, links.size)
            links.single().onClick(header)
            measure()
            assertEquals(560, view.height)
            assertTrue(view.height > shortHeight)
            dialog.dismissNow()
        } finally {
            Preferences.setBoolean(App.context.getString(R.string.pref_useLegacyVerseItem_key), isLegacy)
            activity.finish()
        }
    }
}
