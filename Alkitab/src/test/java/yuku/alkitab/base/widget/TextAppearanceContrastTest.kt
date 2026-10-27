package yuku.alkitab.base.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import java.io.File
import kotlinx.coroutines.android.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], qualifiers = "en-rUS-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextAppearanceContrastTest {
    @Test
    fun `English appearance panel updates contrast and restores the saved percentage`() {
        verifyPanel("Text color contrast", listOf("", "1.88", "2.75", "3.00", "4.50"), "2.30", "en")
    }

    @Test
    @Config(qualifiers = "in-rID-w360dp-h800dp-mdpi")
    fun `Indonesian appearance panel uses decimal commas and hides the zero ratio`() {
        verifyPanel("Kontras warna teks", listOf("", "1,88", "2,75", "3,00", "4,50"), "2,30", "in")
    }

    private fun verifyPanel(title: String, ratios: List<String>, restoredRatio: String, language: String) {
        yuku.afw.App.initWithAppContext(RuntimeEnvironment.getApplication())
        Preferences.invalidate()
        Preferences.remove(Prefkey.textColorAdjustment)
        Preferences.setFloat("highlightColorAdjustment", 0f)
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(R.style.Theme_Alkitab)
        val frame = FrameLayout(activity)
        activity.setContentView(frame)
        val clock = BroadcastFrameClock()
        val recomposer = frame.createLifecycleAwareWindowRecomposer(Handler(Looper.getMainLooper()).asCoroutineDispatcher() + clock)
        var changes = 0
        fun openPanel(): TextAppearancePanel = TextAppearancePanel(activity, frame, object : TextAppearancePanel.Listener {
            override fun onValueChanged() { changes++ }
            override fun onCloseButtonClick() {}
        }, 1, 2).also {
            it.show()
            (frame.getChildAt(0) as ComposeView).setParentCompositionContext(recomposer)
        }
        var panel = openPanel()
        var frameNumber = 0L
        fun settle(frames: Int) {
            repeat(frames) {
                Snapshot.sendApplyNotifications()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                clock.sendFrame(++frameNumber * 16_000_000L)
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                frame.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                frame.layout(0, 0, 360, 800)
            }
        }
        fun nodes() = ((frame.getChildAt(0) as ComposeView).getChildAt(0) as RootForTest).semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
        fun textNode(text: String) = nodes().single { it.config.getOrNull(SemanticsProperties.Text)?.any { label -> label.text == text } == true }
        fun slider() = nodes().single { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 0f..100f }
        try {
            settle(40)
            assertEquals(70f, slider().config[SemanticsProperties.ProgressBarRangeInfo].current)
            textNode("$title (${if (language == "in") "3,45" else "3.45"}:1)")
            textNode("70%")
            for ((index, percent) in listOf(0, 25, 50, 57, 100).withIndex()) {
                assertTrue(requireNotNull(slider().config[SemanticsActions.SetProgress].action).invoke(percent.toFloat()))
                settle(5)
                assertEquals(percent / 100f, Preferences.getFloat(Prefkey.textColorAdjustment, -1f))
                val label = if (percent == 0) title else "$title (${ratios[index]}:1)"
                val titleNode = textNode(label)
                textNode("$percent%")
                val themeNode = textNode(activity.getString(R.string.text_appearance_color_theme))
                assertTrue(titleNode.boundsInRoot.top > themeNode.boundsInRoot.bottom)
                assertTrue(titleNode.boundsInRoot.bottom <= slider().boundsInRoot.top)
                val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
                frame.draw(Canvas(bitmap))
                val file = File("build/snapshots/text-adjustment/panel-$language-$percent.png")
                requireNotNull(file.parentFile).mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            assertEquals(5, changes)
            Preferences.setFloat(Prefkey.textColorAdjustment, 0.37f)
            panel.displayValues()
            settle(5)
            textNode("$title ($restoredRatio:1)")
            panel.hide()
            panel = openPanel()
            settle(40)
            textNode("$title ($restoredRatio:1)")
            textNode("37%")
        } finally {
            panel.hide()
            controller.pause().stop().destroy()
            Preferences.remove(Prefkey.textColorAdjustment)
            Preferences.remove("highlightColorAdjustment")
        }
    }
}
