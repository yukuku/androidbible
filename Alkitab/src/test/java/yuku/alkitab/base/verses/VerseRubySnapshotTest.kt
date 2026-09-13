package yuku.alkitab.base.verses

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.afw.App as AfwApp
import yuku.alkitab.base.S
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.debug.R
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.Ari

/**
 * Renders verses carrying ruby (`@<r=..@>..@/`) through the Compose verse row
 * into `Alkitab/build/snapshots/verse-ruby/` for visual review: furigana over
 * kanji, pinyin wider than its base, a gutter-numbered poetry verse, a wrapped
 * base run, and a highlighted, selected verse. Nothing is pixel-compared; the
 * test only asserts that the report was produced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VerseRubySnapshotTest {

    private val ROW_WIDTH_PX = 360
    private val LABEL_HEIGHT_PX = 22
    private val ROW_GAP_PX = 8

    private class Sample(val label: String, val text: String, val checked: Boolean = false, val highlight: Boolean = false)

    private val samples = listOf(
        Sample("furigana", "@@はじめに@<r=かみ@>神@/は@<r=てん@>天@/と@<r=ち@>地@/とを@<r=そうぞう@>創造@/された。"),
        Sample("pinyin, ruby wider than base", "@@@<r=qǐ@>起@/@<r=chū@>初@/，@<r=shén@>神@/@<r=chuàng@>创@/@<r=zào@>造@/@<r=tiān@>天@/@<r=dì@>地@/。"),
        Sample("latin base, Strong's style ruby", "@@In the @<r=H7225@>beginning@/ @<r=H430@>God@/ @<r=H1254@>created@/ the @<r=H8064@>heaven@/ and the @<r=H776@>earth@/."),
        Sample("gutter number, poetry lines", "@@@1@<r=あ@>悪@/しき@<r=もの@>者@/のはかりごとに@<r=あゆ@>歩@/まず、@1@<r=つみ@>罪@/びとの@<r=みち@>道@/に@<r=た@>立@/たず、"),
        Sample("wrapped base run", "@@aaaa bbbb cccc dddd eeee ffff gggg hhhh iiii jjjj kkkk llll mmmm nnnn @<r=one two three four five six seven@>wrapped-across-lines@/ and after."),
        Sample("no ruby, same font", "@@In the beginning God created the heaven and the earth."),
        Sample("highlighted and selected", "@@In the @<r=H7225@>beginning@/ @<r=H430@>God@/ @<r=H1254@>created@/ the heaven and the earth.", checked = true, highlight = true),
    )

    private class FakeVerses(private val texts: List<String>) : SingleChapterVerses {
        override val verseCount: Int get() = texts.size
        override fun getVerse(verse_0: Int): String = texts[verse_0]
    }

    private class Pipeline(val controller: ActivityController<AppCompatActivity>, val frame: FrameLayout) {
        val activity: Activity get() = controller.get()
    }

    private lateinit var context: Context
    private lateinit var dims: S.CalculatedDimensions

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AfwApp.initWithAppContext(context)
        dims = S.CalculatedDimensions().apply {
            fontSize2dp = 17f
            fontFace = Typeface.DEFAULT
            fontBold = Typeface.NORMAL
            lineSpacingMult = 1.15f
            indentParagraphFirst = 38
            indentParagraphRest = 5
            indentSpacing1 = 22
            indentSpacing2 = 38
            indentSpacing3 = 54
            indentSpacing4 = 70
            indentSpacingExtra = 6
            backgroundColor = 0xfff0f0f0.toInt()
            fontColor = 0xff212121.toInt()
            fontRedColor = 0xffb71c1c.toInt()
            verseNumberColor = 0xff828282.toInt()
        }
        S.overrideAppliedDimensions(dims)
        yuku.afw.storage.Preferences.setInt(context.getString(R.string.pref_selectedVerseBgColor_key), 0xff0277bd.toInt())
    }

    @Test
    fun `render ruby verses through the Compose verse row for visual review`() {
        val outputDir = resolveSnapshotDir()
        outputDir.mkdirs()

        val pipeline = buildPipeline()
        val rendered = try {
            val data = buildData()
            val ui = VersesUiModel.EMPTY.copy(isVerseNumberShown = true)
            samples.mapIndexed { index, sample -> sample to renderCompose(pipeline, data, ui, index, sample.checked) }
        } finally {
            pipeline.controller.pause().stop().destroy()
        }

        val height = rendered.sumOf { (_, bitmap) -> LABEL_HEIGHT_PX + bitmap.height + ROW_GAP_PX }
        val sheet = Bitmap.createBitmap(ROW_WIDTH_PX, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(dims.backgroundColor)
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xff555555.toInt()
            textSize = 11f
            typeface = Typeface.SANS_SERIF
        }
        var y = 0
        for ((sample, bitmap) in rendered) {
            canvas.drawText(sample.label, 4f, (y + 14).toFloat(), caption)
            y += LABEL_HEIGHT_PX
            canvas.drawBitmap(bitmap, 0f, y.toFloat(), null)
            y += bitmap.height + ROW_GAP_PX
            bitmap.recycle()
        }

        val file = File(outputDir, "ruby.png")
        file.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sheet.recycle()
        println("Ruby snapshot written to: ${file.absolutePath}")
        assertTrue(file.exists())
    }

    private fun buildData(): VersesDataModel {
        val attributes = VersesAttributes.createEmpty(samples.size)
        for ((index, sample) in samples.withIndex()) {
            if (!sample.highlight) continue
            attributes.highlightInfoMap_[index] = Highlights.Info().apply { colorRgb = 0xffff00 }
        }
        return VersesDataModel(
            ari_bc_ = Ari.encode(0, 1, 0),
            verses_ = FakeVerses(samples.map { it.text }),
            versesAttributes = attributes,
        )
    }

    private fun renderCompose(pipeline: Pipeline, data: VersesDataModel, ui: VersesUiModel, index: Int, checked: Boolean): Bitmap {
        val view = VerseItemComposeView(pipeline.activity)
        pipeline.frame.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        idleLoopers()
        val state = buildVerseItemComposeState(
            context = pipeline.activity,
            data = data,
            ui = ui,
            listeners = VersesListeners.EMPTY,
            index = index,
            checked = checked,
            currentPosition = { index },
            toggleChecked = {},
            inlineLinkViewProvider = { view },
        )
        view.bind(state)
        view.checked = checked
        idleLoopers()
        val bitmap = measureAndDraw(view, dims.backgroundColor)
        pipeline.frame.removeView(view)
        idleLoopers()
        return bitmap
    }

    private fun buildPipeline(): Pipeline {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        val frame = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        activity.setContentView(frame)
        idleLoopers()
        return Pipeline(controller, frame)
    }

    private fun measureAndDraw(view: View, background: Int): Bitmap {
        repeat(2) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(ROW_WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight.coerceAtLeast(1))
            idleLoopers()
        }
        val bitmap = Bitmap.createBitmap(view.measuredWidth.coerceAtLeast(1), view.measuredHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        view.draw(canvas)
        return bitmap
    }

    private fun idleLoopers() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun resolveSnapshotDir(): File {
        val moduleDir = File(System.getProperty("user.dir") ?: ".")
        return File(moduleDir, "build/snapshots/verse-ruby")
    }
}
