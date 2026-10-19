package yuku.alkitab.songs

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
import androidx.compose.ui.platform.ComposeView
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
import yuku.alkitab.songs.newdoc.Line
import yuku.alkitab.songs.newdoc.PBlock
import yuku.alkitab.songs.newdoc.SongDocument
import yuku.alkitab.songs.newdoc.Span

@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SongComposeFontTest {
    private lateinit var context: Context
    private val frameClock = BroadcastFrameClock()
    private var frameTimeNanos = 0L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AfwApp.initWithAppContext(context)
    }

    @Test
    fun `song body and sans serif roles resolve their platform typefaces in every style`() {
        val resolver = createFontFamilyResolver(context)
        val faces = listOf(null, Typeface.SANS_SERIF, Typeface.SERIF, Typeface.MONOSPACE, Typeface.create("cursive", Typeface.NORMAL))
        for (face in faces) {
            val song = songStyle(face)
            for ((family, expected) in listOf(song.bodyFontFamily to face, song.sansSerifFontFamily to Typeface.SANS_SERIF)) {
                for (style in listOf(Typeface.NORMAL, Typeface.BOLD, Typeface.ITALIC, Typeface.BOLD_ITALIC)) {
                    val legacy = TextView(context).apply { setTypeface(expected, style) }
                    val resolved = resolver.resolve(
                        fontFamily = family,
                        fontWeight = if (style and Typeface.BOLD != 0) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (style and Typeface.ITALIC != 0) FontStyle.Italic else FontStyle.Normal,
                    ).value as Typeface
                    assertEquals("typeface $expected, style $style", legacy.typeface ?: Typeface.DEFAULT, resolved)
                }
            }
        }
    }

    @Test
    fun `song screenshots preserve the selected family and global bold with inline styles`() {
        val output = File("build/snapshots/song-fonts").apply { mkdirs() }
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get().apply { setTheme(androidx.appcompat.R.style.Theme_AppCompat) }
        val frame = FrameLayout(activity)
        activity.setContentView(frame)
        // A fixture-owned dispatcher and clock avoid Compose's cached main dispatcher
        // referring to a Choreographer that Robolectric has reset between tests.
        val recomposer = frame.createLifecycleAwareWindowRecomposer(Handler(Looper.getMainLooper()).asCoroutineDispatcher() + frameClock)
        val doc = SongDocument("1", blocks = listOf(
            PBlock(role = "title", content = Line.Plain("Font sample")),
            PBlock(content = Line.Styled(listOf(
                Span("Plain words, "),
                Span("bold words, ", listOf("b")),
                Span("italic words, ", listOf("i")),
                Span("bold italic words.", listOf("b", "i")),
            ))),
        ))
        val options = listOf("Roboto" to Typeface.SANS_SERIF, "Droid Serif" to Typeface.SERIF, "Droid Mono" to Typeface.MONOSPACE)
        val comparison = Bitmap.createBitmap(820, 3 * 280, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(comparison).apply { drawColor(Color.WHITE) }
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 16f }
        val regularPixels = mutableListOf<IntArray>()
        try {
            for ((index, option) in options.withIndex()) {
                val (label, face) = option
                val rows = mutableListOf<IntArray>()
                canvas.drawText("$label: normal (left), global bold (right)", 10f, (index * 280 + 24).toFloat(), caption)
                for (isBold in listOf(false, true)) {
                    val view = ComposeView(activity)
                    view.setParentCompositionContext(recomposer)
                    frame.addView(view)
                    view.setContent {
                        SongDocumentComposable(doc, songStyle(face, isBold), null, null, {}, {}, {})
                    }
                    repeat(2) {
                        idle()
                        view.measure(
                            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                        )
                        view.layout(0, 0, 400, 240)
                        idle()
                    }
                    val bitmap = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888)
                    val rowCanvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
                    view.draw(rowCanvas)
                    val pixels = IntArray(400 * 240).also { bitmap.getPixels(it, 0, 400, 0, 0, 400, 240) }
                    assertTrue("$label must render song text", pixels.any { it != Color.WHITE })
                    rows += pixels
                    canvas.drawBitmap(bitmap, if (isBold) 420f else 0f, (index * 280 + 40).toFloat(), null)
                    bitmap.recycle()
                    frame.removeView(view)
                    idle()
                }
                assertFalse("Global bold must change song text", rows[0].contentEquals(rows[1]))
                regularPixels += rows[0]
            }
            for (index in 1..2) assertFalse("Font choices must render differently", regularPixels[0].contentEquals(regularPixels[index]))
            File(output, "comparison.png").outputStream().use { comparison.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            comparison.recycle()
            controller.pause().stop().destroy()
            idle()
        }
    }

    private fun songStyle(face: Typeface?, isBold: Boolean = false) = SongComposeStyle(
        fontColor = Color.BLACK,
        verseNumberColor = Color.GRAY,
        backgroundColor = Color.WHITE,
        baseFontSizeDp = 20f,
        lineSpacingMult = 1.2f,
        typeface = face,
        zoomPercent = 100,
        isBold = isBold,
    )

    private fun idle() {
        Snapshot.sendApplyNotifications()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        frameTimeNanos += 16_000_000L
        frameClock.sendFrame(frameTimeNanos)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}
