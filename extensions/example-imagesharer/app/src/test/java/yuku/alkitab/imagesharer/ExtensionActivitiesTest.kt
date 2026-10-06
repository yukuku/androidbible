package yuku.alkitab.imagesharer

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExtensionActivitiesTest {
    @Test
    fun `sharing a long verse sends a readable PNG content URI with temporary permission`() {
        val input = Intent().putExtra("aris", intArrayOf(515))
            .putExtra("verseTexts", arrayOf("A long verse with wrapping text. ".repeat(80)))
        Robolectric.buildActivity(ShareVerseActivity::class.java, input).use { controller ->
            val activity = controller.create().get()
            val chooser = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val share = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            assertNotNull(share)
            share!!
            assertEquals(Intent.ACTION_SEND, share.action)
            assertEquals("image/png", share.type)
            assertTrue(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val uri = share.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)!!
            assertEquals("content", uri.scheme)
            assertEquals("yuku.alkitab.imagesharer.fileprovider", uri.authority)
            assertEquals(uri, share.clipData!!.getItemAt(0).uri)
            activity.contentResolver.openInputStream(uri).use { stream ->
                val image = BitmapFactory.decodeStream(stream)
                assertNotNull(image)
                assertEquals(800, image.width)
                assertTrue(image.height > 100)
                image.recycle()
            }
            assertTrue(activity.isFinishing)
        }
    }

    @Test
    fun `single verse sharing rejects missing mismatched and empty payloads without launching a chooser`() {
        val inputs = listOf(
            Intent(),
            Intent().putExtra("aris", intArrayOf(515)),
            Intent().putExtra("aris", intArrayOf(515, 516)).putExtra("verseTexts", arrayOf("Text")),
            Intent().putExtra("aris", intArrayOf(515)).putExtra("verseTexts", arrayOf("Text", "Other")),
            Intent().putExtra("aris", intArrayOf(515)).putExtra("verseTexts", arrayOf<String?>(null)),
            Intent().putExtra("aris", intArrayOf(515)).putExtra("verseTexts", arrayOf("")),
            Intent().putExtra("aris", intArrayOf(-1)).putExtra("verseTexts", arrayOf("Text")),
        )
        for (input in inputs) {
            Robolectric.buildActivity(ShareVerseActivity::class.java, input).use { controller ->
                val activity = controller.create().get()
                assertTrue(activity.isFinishing)
                assertNull(shadowOf(activity).nextStartedActivity)
            }
        }
    }

    @Test
    fun `multiple verse activities preserve formatting and tolerate unavailable verse text`() {
        val input = Intent().putExtra("aris", intArrayOf(515, 516))
            .putExtra("verseTexts", arrayOf("@@@6Formatted verse", null))
        for (type in listOf(MultipleVersesPlainActivity::class.java, MultipleVersesFormattedActivity::class.java)) {
            Robolectric.buildActivity(type, input).use { controller ->
                val activity = controller.create().get()
                assertFalse(activity.isFinishing)
                val text = activity.findViewById<TextView>(R.id.tData).text.toString()
                assertTrue(text.contains("[515, 516]"))
                assertTrue(text.contains("@@@6Formatted verse"))
                assertTrue(text.contains("(Verse text unavailable)"))
            }
        }
    }

    @Test
    fun `multiple verse activities close safely when exported activity extras are invalid`() {
        val inputs = listOf(
            Intent(),
            Intent().putExtra("aris", intArrayOf()),
            Intent().putExtra("aris", intArrayOf(515, 516)).putExtra("verseTexts", arrayOf("Text")),
        )
        for (type in listOf(MultipleVersesPlainActivity::class.java, MultipleVersesFormattedActivity::class.java)) {
            for (input in inputs) {
                Robolectric.buildActivity(type, input).use { controller ->
                    assertTrue(controller.create().get().isFinishing)
                }
            }
        }
    }
}
