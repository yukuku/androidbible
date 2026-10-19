package yuku.alkitab.base.verses

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.android.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.afw.App as AfwApp
import yuku.alkitab.base.S
import yuku.alkitab.base.compose.composeFontFamilyFor
import yuku.alkitab.base.util.FontManager
import yuku.alkitab.debug.R
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.Ari

@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VerseFontFamilyTest {
    private lateinit var context: Context
    private val frameClock = BroadcastFrameClock()
    private var frameTimeNanos = 0L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AfwApp.initWithAppContext(context)
    }

    @Test
    fun `Compose resolves the same typeface as TextView for each family and style`() {
        val resolver = createFontFamilyResolver(context)
        val faces = listOf(null, Typeface.DEFAULT) + fontOptions().map { it.second }
        for (face in faces) {
            val family = composeFontFamilyFor(face)
            for (style in listOf(Typeface.NORMAL, Typeface.BOLD, Typeface.ITALIC, Typeface.BOLD_ITALIC)) {
                val legacy = TextView(context).apply { setTypeface(face, style) }
                val resolved = resolver.resolve(
                    fontFamily = family,
                    fontWeight = if (style and Typeface.BOLD != 0) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (style and Typeface.ITALIC != 0) FontStyle.Italic else FontStyle.Normal,
                ).value as Typeface
                assertEquals("typeface $face, style $style", legacy.typeface ?: Typeface.DEFAULT, resolved)
            }
        }
    }

    @Test
    fun `native screenshots distinguish selected families and preserve bold and italic verse text`() {
        val output = File(System.getenv("VERSE_FONT_SNAPSHOT_DIR") ?: "build/snapshots/verse-fonts").apply { mkdirs() }
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get().apply { setTheme(androidx.appcompat.R.style.Theme_AppCompat) }
        val frame = FrameLayout(activity)
        activity.setContentView(frame)
        // Compose caches its main dispatcher across Robolectric tests, so each fixture
        // needs its own dispatcher and frame clock to render deterministically.
        val dispatcher = Handler(Looper.getMainLooper()).asCoroutineDispatcher()
        val recomposer = frame.createLifecycleAwareWindowRecomposer(dispatcher + frameClock)
        val previousDimensions = S.applied()
        val dims = S.CalculatedDimensions().apply {
            fontSize2dp = 20f
            lineSpacingMult = 1.2f
            fontColor = Color.BLACK
            fontRedColor = 0xffb71c1c.toInt()
            verseNumberColor = Color.GRAY
            backgroundColor = Color.WHITE
        }
        S.overrideAppliedDimensions(dims)
        val sample = "@@In the beginning was the Word, @9and the Word was with God,@7 and the Word was God."
        val data = VersesDataModel(
            ari_bc_ = Ari.encode(42, 1, 0),
            verses_ = object : SingleChapterVerses {
                override val verseCount = 1
                override fun getVerse(verse_0: Int) = sample
            },
            versesAttributes = VersesAttributes.createEmpty(1),
        )
        val ui = VersesUiModel.EMPTY.copy(isVerseNumberShown = true)
        val sheets = mutableListOf<Bitmap>()
        val regularPixels = mutableListOf<IntArray>()
        try {
            for ((label, face) in fontOptions()) {
                dims.fontFace = face
                val rows = mutableListOf<Pair<Bitmap, Bitmap>>()
                for (isBold in listOf(false, true)) {
                    dims.fontBold = if (isBold) Typeface.BOLD else Typeface.NORMAL
                    val legacy = activity.layoutInflater.inflate(R.layout.item_verse, frame, false) as VerseItem
                    frame.addView(legacy)
                    idle()
                    VerseTextHolder(legacy).bind(data, ui, VersesListeners.EMPTY, Attention(), AudioHighlight(), false, {}, 0)
                    val left = draw(legacy)
                    frame.removeView(legacy)
                    val compose = VerseItemComposeView(activity)
                    compose.setParentCompositionContext(recomposer)
                    frame.addView(compose)
                    idle()
                    compose.bind(buildVerseItemComposeState(
                        context = activity,
                        data = data,
                        ui = ui,
                        listeners = VersesListeners.EMPTY,
                        index = 0,
                        checked = false,
                        currentPosition = { 0 },
                        toggleChecked = {},
                        inlineLinkViewProvider = { compose },
                    ))
                    idle()
                    val right = draw(compose)
                    frame.removeView(compose)
                    idle()
                    assertTrue("$label (bold=$isBold) must draw visible text", pixels(right).any { it != Color.WHITE })
                    rows += left to right
                }
                regularPixels += pixels(rows[0].second)
                assertFalse("Bold must change the rendered verse", pixels(rows[0].second).contentEquals(pixels(rows[1].second)))
                val sheet = Bitmap.createBitmap(820, 80 + rows.sumOf { maxOf(it.first.height, it.second.height) + 32 }, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(sheet).apply { drawColor(Color.WHITE) }
                val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 16f }
                canvas.drawText(label, 10f, 24f, caption)
                canvas.drawText("Legacy XML", 10f, 52f, caption)
                canvas.drawText("Compose", 420f, 52f, caption)
                var y = 80
                for ((index, row) in rows.withIndex()) {
                    canvas.drawText(if (index == 0) "Normal + italic" else "Bold + bold italic", 10f, y.toFloat(), caption)
                    y += 10
                    canvas.drawBitmap(row.first, 0f, y.toFloat(), null)
                    canvas.drawBitmap(row.second, 420f, y.toFloat(), null)
                    y += maxOf(row.first.height, row.second.height) + 22
                    row.first.recycle()
                    row.second.recycle()
                }
                File(output, "${label.substringBefore(' ')}.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
                sheets += sheet
            }
            for (index in 1..2) {
                assertFalse("Selected families must render differently", regularPixels[0].contentEquals(regularPixels[index]))
            }
            val combined = Bitmap.createBitmap(820, sheets.sumOf { it.height }, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(combined)
            var y = 0
            for (sheet in sheets) {
                canvas.drawBitmap(sheet, 0f, y.toFloat(), null)
                y += sheet.height
                sheet.recycle()
            }
            File(output, "comparison.png").outputStream().use { combined.compress(Bitmap.CompressFormat.PNG, 100, it) }
            combined.recycle()
            println("Verse font screenshots: ${output.absolutePath}")
        } finally {
            controller.pause().stop().destroy()
            S.overrideAppliedDimensions(previousDimensions)
        }
    }

    private fun fontOptions() = listOf(
        "Roboto (system sans)" to FontManager.typeface("DEFAULT"),
        "Droid-Serif (system serif)" to FontManager.typeface("SERIF"),
        "Droid-Mono (system monospace)" to FontManager.typeface("MONOSPACE"),
        "Cursive (non-singleton typeface)" to Typeface.create("cursive", Typeface.NORMAL),
    )

    private fun draw(view: View): Bitmap {
        repeat(2) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight.coerceAtLeast(1))
            idle()
        }
        return Bitmap.createBitmap(400, view.measuredHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it).apply { drawColor(Color.WHITE) }
            view.draw(canvas)
        }
    }

    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun idle() {
        Snapshot.sendApplyNotifications()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        frameTimeNanos += 16_000_000L
        frameClock.sendFrame(frameTimeNanos)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}
