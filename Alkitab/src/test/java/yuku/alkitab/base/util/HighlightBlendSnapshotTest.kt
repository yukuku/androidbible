package yuku.alkitab.base.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.afw.App as AfwApp

/**
 * Visual matrix of every highlight color drawn over a range of reading backgrounds.
 *
 * Each background gets two renderings of the same rows. The left one fills the band with a
 * constant 0xA0 alpha; the right one uses [Highlights.blendOver], which solves the alpha per
 * (color, background) pair. Putting them next to each other is the point of the test: it shows
 * where a constant alpha turns unreadable (a saturated blue on a light page, anything bright on
 * the black night page) and where the two agree because the color already sits near the
 * background.
 *
 * Per-background PNGs, a combined `contact-sheet.png`, and an `index.html` land in
 * `Alkitab/build/snapshots/highlight-blend/`.
 * Nothing is pixel-compared: the test asserts only that the report was produced, and exists so
 * the blend can be eyeballed after any change to [Highlights.blendOver]'s constants.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "w720dp-h1280dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HighlightBlendSnapshotTest {

    private val FLAT_ALPHA = 0xa0
    private val WIDTH_PX = 640
    private val TEXT_SIZE_SP = 13f
    private val SAMPLE = "In the beginning God created the heavens and the earth."

    private data class Background(val name: String, val rgb: Int, val textColor: Int)

    private data class Swatch(val name: String, val rgb: Int)

    private val backgrounds = listOf(
        Background("light-default", 0xf0f0f0, 0x212121),
        Background("white", 0xffffff, 0x212121),
        Background("sepia", 0xfff8e7, 0x3b3021),
        Background("mid-grey", 0x808080, 0x101010),
        Background("dark-grey", 0x303030, 0xaeaeae),
        Background("night-default", 0x000000, 0xaeaeae),
    )

    private val swatches = listOf(
        Swatch("red", 0xff0000),
        Swatch("orange", 0xff8000),
        Swatch("yellow", 0xffff00),
        Swatch("chartreuse", 0x80ff00),
        Swatch("green", 0x00ff00),
        Swatch("spring", 0x00ff80),
        Swatch("cyan", 0x00ffff),
        Swatch("azure", 0x0080ff),
        Swatch("blue", 0x0000ff),
        Swatch("violet", 0x8000ff),
        Swatch("magenta", 0xff00ff),
        Swatch("rose", 0xff0080),
        Swatch("black", 0x000000),
        Swatch("dark-grey", 0x303030),
        Swatch("mid-grey", 0x808080),
        Swatch("near-white", 0xf0f0f0),
        Swatch("white", 0xffffff),
    )

    @Before
    fun setUp() {
        AfwApp.initWithAppContext(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `render every highlight color over every reading background for visual review`() {
        val outputDir = resolveSnapshotDir()
        outputDir.mkdirs()

        val rows = StringBuilder()

        val pairs = mutableListOf<Triple<Background, Bitmap, Bitmap>>()

        for (background in backgrounds) {
            val flat = render(background, blended = false)
            val blend = render(background, blended = true)
            pairs += Triple(background, flat, blend)

            val flatFile = File(outputDir, "${background.name}-flat.png")
            val blendFile = File(outputDir, "${background.name}-blend.png")
            write(flat, flatFile)
            write(blend, blendFile)

            rows.append(
                "<tr>" +
                    "<td class=\"label\"><div class=\"name\">${background.name}</div>" +
                    "<pre>background #%06x\ntext #%06x</pre></td>".format(background.rgb, background.textColor) +
                    "<td><img src=\"${flatFile.name}\"/></td>" +
                    "<td><img src=\"${blendFile.name}\"/></td>" +
                    "</tr>\n"
            )
        }

        val html = """
            <!doctype html>
            <html><head><meta charset="utf-8"/><title>Highlight blend across backgrounds</title>
            <style>
            body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; margin: 24px; background: #fafafa; }
            table { border-collapse: collapse; }
            td, th { vertical-align: top; padding: 10px 12px; border-bottom: 1px solid #ddd; }
            th { text-align: left; background: #f0f0f0; position: sticky; top: 0; }
            td.label { width: 150px; }
            td.label .name { font-weight: 600; }
            td.label pre { margin: 4px 0 0; color: #555; font-size: 12px; }
            img { display: block; border: 1px solid #ccc; }
            </style></head>
            <body>
            <h1>Highlight color × reading background</h1>
            <p>Left: a constant 0xA0 alpha for every pair. Right: <code>Highlights.blendOver</code>, which
            solves the alpha so the band sits a fixed perceptual (Oklab) distance from the background,
            floored by a minimum raw sRGB separation so it stays visible on dark pages. The
            <code>a=</code> figure on each row is the alpha that was used.</p>
            <table>
              <thead><tr><th>Background</th><th>Constant 0xA0 alpha</th><th>blendOver</th></tr></thead>
              <tbody>
            $rows
              </tbody>
            </table>
            </body></html>
        """.trimIndent()

        val indexFile = File(outputDir, "index.html")
        indexFile.writeText(html)

        val contactSheet = File(outputDir, "contact-sheet.png")
        write(renderContactSheet(pairs), contactSheet)

        println("Highlight blend report written to: ${indexFile.absolutePath}")
        assertTrue("index.html should exist", indexFile.exists())
        assertTrue("contact-sheet.png should exist", contactSheet.exists())
    }

    private fun renderContactSheet(pairs: List<Triple<Background, Bitmap, Bitmap>>): Bitmap {
        val gutter = 16
        val headerHeight = 44
        val blockGap = 24

        val sheetWidth = gutter + WIDTH_PX + gutter + WIDTH_PX + gutter
        val sheetHeight = headerHeight + blockGap +
            pairs.sumOf { (_, flat, blend) -> headerHeight + maxOf(flat.height, blend.height) + blockGap }

        val bitmap = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xfffafafa.toInt())

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xff111111.toInt()
            textSize = 15f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xff555555.toInt()
            textSize = 13f
            typeface = Typeface.SANS_SERIF
        }

        canvas.drawText("constant 0xA0 alpha", gutter.toFloat(), 28f, title)
        canvas.drawText("blendOver", (gutter + WIDTH_PX + gutter).toFloat(), 28f, title)

        var y = headerHeight + blockGap
        for ((background, flat, blend) in pairs) {
            canvas.drawText(background.name, gutter.toFloat(), (y + 16).toFloat(), title)
            canvas.drawText(
                "background #%06x   text #%06x".format(background.rgb, background.textColor),
                gutter.toFloat(),
                (y + 34).toFloat(),
                caption,
            )
            y += headerHeight

            canvas.drawBitmap(flat, gutter.toFloat(), y.toFloat(), null)
            canvas.drawBitmap(blend, (gutter + WIDTH_PX + gutter).toFloat(), y.toFloat(), null)
            y += maxOf(flat.height, blend.height) + blockGap
        }

        return bitmap
    }

    private fun resolveSnapshotDir(): File {
        val override = System.getenv("HIGHLIGHT_BLEND_SNAPSHOT_DIR")
        if (!override.isNullOrBlank()) return File(override)
        val moduleDir = File(System.getProperty("user.dir") ?: ".")
        return File(moduleDir, "build/snapshots/highlight-blend")
    }

    private fun render(background: Background, blended: Boolean): Bitmap {
        val sb = SpannableStringBuilder()

        for ((index, swatch) in swatches.withIndex()) {
            if (index > 0) sb.append("\n")

            val argb = if (blended) {
                Highlights.blendOver(swatch.rgb, background.rgb)
            } else {
                (FLAT_ALPHA shl 24) or swatch.rgb
            }

            val labelStart = sb.length
            sb.append("#%06x a=%02x ".format(swatch.rgb, (argb ushr 24) and 0xff))
            sb.setSpan(TypefaceSpan("monospace"), labelStart, sb.length, 0)

            val bandStart = sb.length
            sb.append(SAMPLE)
            sb.setSpan(BackgroundColorSpan(argb), bandStart, sb.length, 0)
        }

        val view = TextView(ApplicationProvider.getApplicationContext()).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP)
            setTextColor(0xff000000.toInt() or background.textColor)
            setBackgroundColor(0xff000000.toInt() or background.rgb)
            setLineSpacing(6f, 1f)
            setPadding(10, 10, 10, 10)
            includeFontPadding = false
            typeface = Typeface.SANS_SERIF
            text = sb
        }

        val widthSpec = View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        val height = view.measuredHeight
        view.layout(0, 0, WIDTH_PX, height)

        val bitmap = Bitmap.createBitmap(WIDTH_PX, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun write(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
