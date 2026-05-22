package yuku.alkitab.base.widget

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
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
import yuku.alkitab.debug.R

/**
 * Visual side-by-side snapshot test for the reader toolbar title button.
 *
 * The left column is the OLD behaviour: the title was set as
 * `reference.replace(' ', ' ')` on a plain 2-line button, so the whole
 * reference was one unbreakable token — when it didn't fit, the default
 * TextView line-breaking could only ellipsize, clipping the chapter number.
 *
 * The right column is the NEW [GotoButton], which wraps an overflowing title
 * into two character-balanced lines (mid-word allowed) so both lines fit and
 * the chapter number stays visible.
 *
 * Each (reference, width) case is rendered through both paths, the bitmaps are
 * saved as PNGs under `Alkitab/build/snapshots/goto-button/`, and an
 * `index.html` is generated with a two-column comparison table for eyeballing.
 *
 * Not a strict pixel-diff: PNGs are written, not compared. Robolectric's text
 * metrics are uniform and narrower than a real device, so on-device overflow
 * widths don't match here and the dramatic chapter-number clip is hard to
 * reproduce headless; the report is for eyeballing relative behaviour, not
 * pixel-accurate device fidelity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GotoButtonSideBySideSnapshotTest {

    private val TEXT_SIZE_SP = 16f
    private val BUTTON_HEIGHT_PX = 56
    private val TEXT_COLOR = AndroidColor.WHITE
    private val BUTTON_BG = 0xff3367d6.toInt()

    @Before
    fun setUp() {
        AfwApp.initWithAppContext(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `produce side-by-side snapshots of the toolbar title for many references and widths`() {
        val cases = buildCases()
        val outputDir = resolveSnapshotDir()
        outputDir.mkdirs()

        val rows = StringBuilder()

        for (case in cases) {
            val oldBitmap = renderOld(case)
            val newBitmap = renderNew(case)

            val oldFile = File(outputDir, "${case.name}-old.png")
            val newFile = File(outputDir, "${case.name}-new.png")
            FileOutputStream(oldFile).use { oldBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileOutputStream(newFile).use { newBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

            rows.append(
                "<tr>" +
                    "<td class=\"label\"><div class=\"name\">${escapeHtml(case.name)}</div><pre>${escapeHtml(case.describe())}</pre></td>" +
                    "<td><img src=\"${oldFile.name}\"/></td>" +
                    "<td><img src=\"${newFile.name}\"/></td>" +
                    "</tr>\n"
            )
        }

        val html = """
            <!doctype html>
            <html><head><meta charset="utf-8"/><title>GotoButton title wrap: old vs new</title>
            <style>
            body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; margin: 24px; }
            table { border-collapse: collapse; }
            td, th { vertical-align: top; padding: 8px 12px; border-bottom: 1px solid #ddd; }
            th { text-align: left; background: #f4f4f4; position: sticky; top: 0; }
            td.label { width: 320px; }
            td.label .name { font-weight: 600; }
            td.label pre { margin: 4px 0 0; white-space: pre-wrap; word-break: break-word; color: #444; font-size: 12px; }
            img { display: block; image-rendering: pixelated; border: 1px solid #eee; }
            </style></head>
            <body>
            <h1>Reader toolbar title: old vs new wrapping</h1>
            <p>${cases.size} cases. Left = old (non-breaking-space token, ellipsized when overflowing — chapter number clipped). Right = new GotoButton balanced 2-line wrap (chapter number preserved). Blue box edges show the available button width.</p>
            <table>
              <thead><tr><th>Case</th><th>Old (nbsp, default wrap)</th><th>New (balanced wrap)</th></tr></thead>
              <tbody>
            ${rows}
              </tbody>
            </table>
            </body></html>
        """.trimIndent()

        val indexFile = File(outputDir, "index.html")
        indexFile.writeText(html)

        println("Snapshot report written to: ${indexFile.absolutePath}")
        assertTrue("index.html should exist", indexFile.exists())
    }

    private fun resolveSnapshotDir(): File {
        val override = System.getenv("GOTO_BUTTON_SNAPSHOT_DIR")
        if (!override.isNullOrBlank()) return File(override)
        val moduleDir = File(System.getProperty("user.dir") ?: ".")
        return File(moduleDir, "build/snapshots/goto-button")
    }

    // ---- Test data ------------------------------------------------------------------------------

    private data class Case(
        val name: String,
        val text: String,
        val widthPx: Int,
    ) {
        fun describe(): String = "text=\"$text\"\nwidth=${widthPx}px"
    }

    private fun buildCases(): List<Case> {
        val references = listOf(
            "Kej 1",
            "Wahyu 22",
            "1 Korintus 16",
            "1 Tesalonika 2",
            "Kidung Agung 8",
            "Pengkhotbah 12",
        )
        val widths = listOf(70, 90, 120, 160, 200, 280)
        val cases = mutableListOf<Case>()
        var i = 1
        for (ref in references) {
            for (w in widths) {
                val slug = ref.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                cases += Case("%02d-%s-w%d".format(i, slug, w), ref, w)
                i++
            }
        }
        return cases
    }

    // ---- Rendering helpers ----------------------------------------------------------------------

    private fun renderOld(case: Case): Bitmap {
        val activity = buildActivity()
        val button = AppCompatButton(activity).apply {
            applyCommonStyle()
            text = case.text.replace(' ', ' ')
        }
        attachAndDoFirstLayout(activity, button)
        return measureAndDraw(button, case.widthPx)
    }

    private fun renderNew(case: Case): Bitmap {
        val activity = buildActivity()
        val button = GotoButton(activity).apply {
            applyCommonStyle()
            text = case.text
        }
        attachAndDoFirstLayout(activity, button)
        return measureAndDraw(button, case.widthPx)
    }

    private fun AppCompatButton.applyCommonStyle() {
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        setTextColor(TEXT_COLOR)
        setBackgroundColor(BUTTON_BG)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP)
    }

    private fun buildActivity(): AppCompatActivity {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        return activity
    }

    private fun attachAndDoFirstLayout(activity: Activity, view: View) {
        val frame = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            setBackgroundColor(AndroidColor.WHITE)
        }
        activity.setContentView(frame)
        idleLoopers()
    }

    private fun measureAndDraw(view: View, widthPx: Int): Bitmap {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(BUTTON_HEIGHT_PX, View.MeasureSpec.EXACTLY)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        idleLoopers()

        val w = view.measuredWidth.coerceAtLeast(1)
        val h = view.measuredHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)
        view.draw(canvas)
        return bitmap
    }

    private fun idleLoopers() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
