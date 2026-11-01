package yuku.alkitab.base.dialog

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import io.mockk.every
import io.mockk.mockk
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.model.PerVersionSettings
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.base.services.StorageProvider
import yuku.alkitab.base.services.VersionManager
import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.debug.R
import yuku.alkitab.model.ProgressMark
import yuku.alkitab.model.Version

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
class ProgressMarkListDialogSizingTest {
    @Test
    fun `pins share dialog widths and wrap short lists while long lists scroll with either renderer`() {
        val originalServices = App.services
        val isOriginalLegacy = Preferences.getBoolean(R.string.pref_useLegacyVerseItem_key, R.bool.pref_useLegacyVerseItem_default)
        val db = mockk<InternalDb>()
        val storage = mockk<StorageProvider>()
        every { storage.db } returns db
        val version = mockk<Version>(relaxed = true)
        val versions = mockk<VersionManager>()
        every { versions.activeVersion() } returns version
        every { versions.activeVersionId() } returns "test"
        every { db.getPerVersionSettings("test") } returns PerVersionSettings.createDefault()
        var marks = emptyList<ProgressMark>()
        var verseText = "A short verse."
        every { db.listAllProgressMarks() } answers { marks }
        every { version.reference(any()) } returns "Genesis 1:1"
        every { version.loadVerseText(any()) } answers { verseText }
        App.services = AppServices(storage, versions, originalServices.uiDimensions)

        try {
            for (isLegacy in listOf(true, false)) {
                Preferences.setBoolean(App.context.getString(R.string.pref_useLegacyVerseItem_key), isLegacy)
                for ((screenWidth, screenHeight) in listOf(360 to 640, 640 to 360)) {
                    RuntimeEnvironment.setQualifiers("w${screenWidth}dp-h${screenHeight}dp-mdpi")
                    val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
                    activity.setTheme(R.style.Theme_Alkitab)
                    try {
                        val expectedWidth = if (screenWidth == 360) 288 else 448
                        val maxHeight = screenHeight - 80
                        var shortHeight = 0
                        for (isLong in listOf(false, true)) {
                            verseText = if (isLong) "A verse with enough text to wrap across several lines. ".repeat(40) else "A short verse."
                            marks = (0 until if (isLong) 5 else 1).map { index ->
                                ProgressMark().apply {
                                    preset_id = index
                                    caption = "Pin ${index + 1}"
                                    ari = 0x10101 + index
                                    modifyTime = Date(1_700_000_000_000L)
                                }
                            }
                            val dialog = ProgressMarkListDialog()
                            dialog.showNow(activity.supportFragmentManager, "pins")
                            assertEquals(expectedWidth, dialog.requireDialog().window?.attributes?.width)
                            assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, dialog.requireDialog().window?.attributes?.height)
                            val view = dialog.requireView()
                            Shadows.shadowOf(Looper.getMainLooper()).idle()
                            view.measure(
                                View.MeasureSpec.makeMeasureSpec(expectedWidth, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST),
                            )
                            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
                            if (isLong) {
                                assertEquals(maxHeight, view.height)
                                assertTrue(view.height > shortHeight)
                                assertTrue(view.findViewById<RecyclerView>(R.id.lsProgressMark).canScrollVertically(1))
                            } else {
                                shortHeight = view.height
                                assertTrue("a short Pins list should hug its content", shortHeight in 1 until maxHeight)
                            }
                            dialog.dismissNow()
                        }
                    } finally {
                        activity.finish()
                    }
                }
            }
        } finally {
            App.services = originalServices
            Preferences.setBoolean(App.context.getString(R.string.pref_useLegacyVerseItem_key), isOriginalLegacy)
        }
    }
}
