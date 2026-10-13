package yuku.alkitab.base.widget

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.afw.App
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
class ReaderToolbarLayoutTest {
    @Test
    fun `selection reserves space at either edge and restores both reader toolbars and fullscreen`() {
        App.initWithAppContext(ApplicationProvider.getApplicationContext())
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(R.style.Theme_Alkitab)
        val layout = activity.layoutInflater.inflate(R.layout.activity_isi_content, null)
        activity.setContentView(layout)
        val root = layout.findViewById<ViewGroup>(R.id.root)
        val host = root.findViewById<View>(R.id.toolbarHost)
        val content = root.findViewById<View>(R.id.nontoolbar)
        val toolbar = root.findViewById<View>(R.id.toolbar)
        val compose = root.findViewById<View>(R.id.composeToolbar)
        val selection = root.findViewById<View>(R.id.selectionToolbar)
        val originalAudio = root.findViewById<View>(R.id.audio_bar)
        root.removeView(originalAudio)
        val audio = View(activity).apply { id = R.id.audio_bar }
        root.addView(audio, originalAudio.layoutParams)
        try {
            for ((width, height) in listOf(360 to 640, 640 to 360)) {
                for (isBottom in listOf(false, true)) {
                    for (isFullScreen in listOf(false, true)) {
                        for (isComposeEnabled in listOf(false, true)) {
                            for (audioHeight in listOf(0, 64)) {
                                audio.updateLayoutParams { this.height = audioHeight }
                                for (isSelected in listOf(false, true, false)) {
                                    updateReaderToolbarLayout(root, isFullScreen, isBottom, isSelected, isComposeEnabled)
                                    root.measure(
                                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                                    )
                                    root.layout(0, 0, width, height)
                                    assertEquals(!isFullScreen || isSelected, host.isVisible)
                                    assertEquals(isSelected, selection.isVisible)
                                    assertEquals(!isFullScreen && !isSelected, toolbar.isVisible)
                                    assertEquals(!isFullScreen && !isSelected && isComposeEnabled, compose.isVisible)
                                    assertSame(content, root.findViewById(R.id.nontoolbar))
                                    assertTrue(content.height > 0)
                                    if (host.isVisible) {
                                        if (isSelected) assertEquals(host.height, selection.height)
                                        if (isBottom) {
                                            assertEquals(height, host.bottom)
                                            assertTrue(content.bottom <= host.top)
                                            assertTrue(audio.bottom <= host.top)
                                        } else {
                                            assertEquals(0, host.top)
                                            assertTrue(content.top >= host.bottom)
                                        }
                                    } else {
                                        assertEquals(height - audioHeight, content.height)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
