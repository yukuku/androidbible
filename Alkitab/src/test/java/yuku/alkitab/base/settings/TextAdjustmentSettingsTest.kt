package yuku.alkitab.base.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.SeekBarPreference
import io.mockk.mockk
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.S
import yuku.alkitab.base.services.AppServices
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], qualifiers = "en-rUS-w360dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextAdjustmentSettingsTest {
    private lateinit var originalServices: AppServices

    @Before
    fun setUp() {
        yuku.afw.App.initWithAppContext(RuntimeEnvironment.getApplication())
        Preferences.invalidate()
        Preferences.remove(Prefkey.textColorAdjustment)
        originalServices = App.services
        App.services = AppServices(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        Preferences.remove(Prefkey.textColorAdjustment)
        Preferences.remove("highlightColorAdjustment")
        App.services = originalServices
    }

    @Test
    fun `one inline slider defaults to full text adjustment regardless of the obsolete highlight preference`() {
        for (oldAmount in listOf(0f, 0.37f, 1f)) {
            Preferences.setFloat("highlightColorAdjustment", oldAmount)
            assertEquals(1f, S.calculateDimensionsFromPreferences().textColorAdjustment)
            withSlider { slider ->
                assertEquals(100, slider.value)
                assertEquals(0, slider.min)
                assertEquals(100, slider.max)
                assertFalse(slider.showSeekBarValue)
                assertTrue(slider.updatesContinuously)
                assertFalse(slider.isPersistent)
            }
        }
    }

    @Test
    fun `slider persists exact text adjustment percentages and restores them when reopened`() {
        for (percent in listOf(0, 37, 50, 100)) {
            withSlider { slider ->
                assertTrue(slider.callChangeListener(percent))
                slider.value = percent
                assertEquals(slider.context.getString(R.string.text_color_adjustment_explanation), slider.summary)
                assertEquals(percent / 100f, Preferences.getFloat(Prefkey.textColorAdjustment, -1f))
            }
            withSlider { assertEquals(percent, it.value) }
        }
    }

    @Test
    fun `English slider value shows percentages and two decimal contrast ratios while dragging`() {
        verifyValueLabels(listOf("0%", "25% (contrast ratio of 1.88:1)", "37% (contrast ratio of 2.30:1)", "50% (contrast ratio of 2.75:1)", "57% (contrast ratio of 3.00:1)", "100% (contrast ratio of 4.50:1)"))
    }

    @Test
    @Config(qualifiers = "in-rID-w360dp-h1000dp-mdpi")
    fun `Indonesian slider value uses localized text and decimal commas while dragging`() {
        verifyValueLabels(listOf("0%", "25% (rasio kontras 1,88:1)", "37% (rasio kontras 2,30:1)", "50% (rasio kontras 2,75:1)", "57% (rasio kontras 3,00:1)", "100% (rasio kontras 4,50:1)"))
    }

    private fun verifyValueLabels(expected: List<String>) {
        withSlider { slider ->
            val view = android.view.LayoutInflater.from(slider.context).inflate(R.layout.preference_text_adjustment, null)
            slider.onBindViewHolder(androidx.preference.PreferenceViewHolder.createInstanceForTests(view))
            val seekBar = view.findViewById<android.widget.SeekBar>(androidx.preference.R.id.seekbar)
            val label = view.findViewById<android.widget.TextView>(R.id.text_adjustment_value)
            for ((index, percent) in listOf(0, 25, 37, 50, 57, 100).withIndex()) {
                val key = if (percent < slider.value) android.view.KeyEvent.KEYCODE_DPAD_LEFT else android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                repeat(kotlin.math.abs(percent - slider.value)) {
                    seekBar.onKeyDown(key, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key))
                }
                assertEquals(expected[index], label.text.toString())
                assertEquals(percent / 100f, Preferences.getFloat(Prefkey.textColorAdjustment, -1f))
            }
        }
    }

    private fun withSlider(check: (SeekBarPreference) -> Unit) {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(R.style.Theme_Alkitab)
        val frame = FrameLayout(activity).apply { id = View.generateViewId() }
        activity.setContentView(frame)
        try {
            val fragment = DisplayFragment()
            activity.supportFragmentManager.beginTransaction().replace(frame.id, fragment).commitNow()
            val screen = fragment.preferenceScreen
            val options = (0 until screen.preferenceCount).map(screen::getPreference)
            val slider = options.filterIsInstance<SeekBarPreference>().single()
            assertEquals(Prefkey.textColorAdjustment.name, slider.key)
            assertEquals(activity.getString(R.string.text_color_adjustment), slider.title)
            assertTrue(options.none { it.title?.contains("highlight", ignoreCase = true) == true })
            check(slider)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            frame.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
            frame.layout(0, 0, 360, 1000)
            assertTrue(frame.findViewById<android.widget.SeekBar>(androidx.preference.R.id.seekbar) != null)
            val valueLabel = frame.findViewById<android.widget.TextView>(R.id.text_adjustment_value)
            assertEquals((slider as TextAdjustmentPreference).valueLabel(slider.value), valueLabel.text.toString())
            assertTrue(valueLabel.layout.height <= valueLabel.height)
            assertTrue(valueLabel.layout.lineCount == 0 || valueLabel.layout.getEllipsisCount(valueLabel.layout.lineCount - 1) == 0)
            val bitmap = Bitmap.createBitmap(360, 1000, Bitmap.Config.ARGB_8888)
            frame.draw(Canvas(bitmap))
            val file = File("build/snapshots/text-adjustment/settings-${slider.value}.png")
            requireNotNull(file.parentFile).mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
