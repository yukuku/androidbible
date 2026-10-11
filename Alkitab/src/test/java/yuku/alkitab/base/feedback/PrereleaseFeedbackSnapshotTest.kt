package yuku.alkitab.base.feedback

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.afw.App
import yuku.afw.storage.Preferences
import yuku.alkitab.base.ac.AboutActivity
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.debug.R

class FeedbackSnapshotHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Alkitab)
        super.onCreate(savedInstanceState)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [34], qualifiers = "in-rID-w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrereleaseFeedbackSnapshotTest {
    @Before
    fun setUp() {
        assumeNotNull(PrereleaseFeedback.version)
        App.initWithAppContext(RuntimeEnvironment.getApplication())
        Preferences.setBoolean(Prefkey.prerelease_feedback_hidden, false)
    }

    @Test
    fun `reader feedback matches navigation height and opens the Compose sheet`() {
        val activity = Robolectric.buildActivity(FeedbackSnapshotHostActivity::class.java).setup().get()
        activity.setContentView(R.layout.activity_isi_content)
        val version = requireNotNull(PrereleaseFeedback.version)
        activity.findViewById<Toolbar>(R.id.toolbar).let { toolbar ->
            activity.setSupportActionBar(toolbar)
            activity.supportActionBar?.setDisplayShowTitleEnabled(false)
        }
        activity.findViewById<TextView>(R.id.bGoto).text = "Yohanes 3"
        activity.findViewById<TextView>(R.id.bVersion).text = "TB"
        activity.findViewById<View>(R.id.panelBackForwardList).visibility = View.VISIBLE
        val button = activity.findViewById<TextView>(R.id.bPrereleaseFeedback)
        button.text = activity.getString(R.string.prerelease_feedback_button, version.label)
        button.visibility = View.VISIBLE
        button.setOnClickListener { PrereleaseFeedback.show(activity) { button.visibility = View.GONE } }
        settleWindows()
        val navigation = activity.findViewById<View>(R.id.panelBackForwardList)
        assertEquals(navigation.height, button.height)
        val buttonLocation = IntArray(2).also { button.getLocationOnScreen(it) }
        val navigationLocation = IntArray(2).also { navigation.getLocationOnScreen(it) }
        assertEquals(navigationLocation[1] + navigation.height, buttonLocation[1] + button.height)
        assertTrue(buttonLocation[0] + button.width < navigationLocation[0])
        writeWindows("reader.png")

        button.performClick()
        settleWindows()
        assertTrue("Compose modal sheet must have its own window", WindowInspector.getGlobalWindowViews().size > 1)
        writeWindows("feedback-sheet.png")
    }

    @Test
    fun `About feedback stays available when the reader button is hidden`() {
        Preferences.setBoolean(Prefkey.prerelease_feedback_hidden, true)
        assertFalse(PrereleaseFeedback.isReaderButtonVisible())
        val activity = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.bAboutPrereleaseFeedback).visibility)
        settleWindows()
        writeWindows("about.png")
    }

    @Test
    fun `translated reader labels stay clear of navigation on a narrow screen`() {
        val activity = Robolectric.buildActivity(FeedbackSnapshotHostActivity::class.java).setup().get()
        activity.setContentView(R.layout.activity_isi_content)
        val button = activity.findViewById<TextView>(R.id.bPrereleaseFeedback)
        val navigation = activity.findViewById<View>(R.id.panelBackForwardList)
        button.visibility = View.VISIBLE
        navigation.visibility = View.VISIBLE
        val languages = activity.resources.getStringArray(R.array.pref_language_values).filter { it != "DEFAULT" } + "ms"
        val overlappingLanguages = mutableListOf<String>()
        languages.forEach { language ->
            val configuration = Configuration(activity.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val context = activity.createConfigurationContext(configuration)
            button.text = context.getString(R.string.prerelease_feedback_button, "Dev 123456")
            val root = activity.window.decorView
            root.measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, 320, 640)
            val buttonLocation = IntArray(2).also { button.getLocationOnScreen(it) }
            val navigationLocation = IntArray(2).also { navigation.getLocationOnScreen(it) }
            if (buttonLocation[0] + button.width >= navigationLocation[0]) overlappingLanguages += language
        }
        assertTrue("Reader feedback overlaps navigation for $overlappingLanguages", overlappingLanguages.isEmpty())
    }

    private fun settleWindows() {
        repeat(60) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            WindowInspector.getGlobalWindowViews().forEach { view ->
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, 360, 640)
            }
        }
    }

    private fun writeWindows(name: String) {
        val bitmap = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        WindowInspector.getGlobalWindowViews().forEach { it.draw(canvas) }
        val directory = File("build/snapshots/prerelease-feedback").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
