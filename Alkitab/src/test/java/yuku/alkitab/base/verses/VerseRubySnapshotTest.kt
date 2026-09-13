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
import yuku.alkitab.base.widget.VerseRendererCompose
import yuku.alkitab.debug.R
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.Ari

/**
 * Renders verses carrying ruby (`@<r=..@>..@/`) through the Compose verse row
 * into `Alkitab/build/snapshots/verse-ruby/` for visual review, one sheet per
 * group: basics (furigana, pinyin wider than its base, wrapped runs), poetry
 * and paragraph codes, highlights (full and partial, selected or not), inline
 * styles (red letters, italics, footnotes), and typography variants (hidden
 * verse number, bold, large, wide line spacing, night theme). Nothing is
 * pixel-compared; the test only asserts that the sheets were produced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VerseRubySnapshotTest {

    private val ROW_WIDTH_PX = 360
    private val LABEL_HEIGHT_PX = 22
    private val ROW_GAP_PX = 8

    /**
     * [partial] names a substring of the rendered verse body (base text, no
     * verse number) that the highlight covers; null with [highlight] means a
     * full-verse highlight.
     */
    private class Sample(
        val label: String,
        val text: String,
        val checked: Boolean = false,
        val highlight: Boolean = false,
        val partial: String? = null,
    )

    private class Variant(
        val name: String,
        val fontSizeDp: Float = 17f,
        val bold: Boolean = false,
        val lineSpacingMult: Float = 1.15f,
        val verseNumberShown: Boolean = true,
        val night: Boolean = false,
    )

    private val JOHN_3_16_PINYIN = "@@“@<r=Shén@>神@/@<r=ài@>爱@/@<r=shì@>世@/@<r=rén@>人@/，@<r=shèn@>甚@/@<r=zhì@>至@/@<r=jiāng@>将@/@<r=tā@>他@/@<r=de@>的@/@<r=dú@>独@/@<r=shēng@>生@/@<r=zǐ@>子@/@<r=cì@>赐@/@<r=gěi@>给@/@<r=tā@>他@/@<r=men@>们@/，@<r=jiào@>叫@/@<r=yī@>一@/@<r=qiè@>切@/@<r=xìn@>信@/@<r=tā@>他@/@<r=de@>的@/@<r=bú@>不@/@<r=zhì@>至@/@<r=miè@>灭@/@<r=wáng@>亡@/，@<r=fǎn@>反@/@<r=dé@>得@/@<r=yǒng@>永@/@<r=shēng@>生@/。"
    private val PSALM_1_1_FURIGANA = "@@@1@<r=あ@>悪@/しき@<r=もの@>者@/のはかりごとに@<r=あゆ@>歩@/まず、@1@<r=つみ@>罪@/びとの@<r=みち@>道@/に@<r=た@>立@/たず、@1あざける@<r=もの@>者@/の@<r=ざ@>座@/にすわらぬ@<r=ひと@>人@/はさいわいである。"
    private val MATTHEW_5_3_RED = "@@@6「@<r=こころ@>心@/の@<r=まず@>貧@/しい@<r=ひと@>人@/たちは、さいわいである、@<r=てんごく@>天国@/は@<r=かれ@>彼@/らのものである。@5"
    private val STRONGS = "@@In the @<r=H7225@>beginning@/ @<r=H430@>God@/ @<r=H1254@>created@/ the @<r=H8064@>heaven@/ and the @<r=H776@>earth@/."

    private val groups: Map<String, List<Sample>> = linkedMapOf(
        "basics" to listOf(
            Sample("furigana", "@@はじめに@<r=かみ@>神@/は@<r=てん@>天@/と@<r=ち@>地@/とを@<r=そうぞう@>創造@/された。"),
            Sample("pinyin, ruby wider than base", "@@@<r=Qǐ@>起@/@<r=chū@>初@/，@<r=shén@>神@/@<r=chuàng@>创@/@<r=zào@>造@/@<r=tiān@>天@/@<r=dì@>地@/。"),
            Sample("pinyin, long verse wrapping", JOHN_3_16_PINYIN),
            Sample("latin base, Strong's style ruby", STRONGS),
            Sample("multi-word gloss over one word", "@@@<r=in the beginning@>בְּרֵאשִׁית@/ @<r=created@>בָּרָא@/ @<r=God@>אֱלֹהִים@/"),
            Sample("wrapped base run", "@@aaaa bbbb cccc dddd eeee ffff gggg hhhh iiii jjjj kkkk llll mmmm nnnn @<r=one two three four five six seven@>wrapped-across-lines@/ and after."),
            Sample("no ruby, same font", "@@In the beginning God created the heaven and the earth."),
        ),
        "poetry" to listOf(
            Sample("gutter number, @1 lines", PSALM_1_1_FURIGANA),
            Sample("indent levels @1 @2 @3 @4", "@@@1@<r=しゅ@>主@/はわが@<r=ぼくしゃ@>牧者@/であって、@2わたしには@<r=とぼ@>乏@/しいことがない。@3@<r=しゅ@>主@/はわたしを@<r=みどり@>緑@/の@<r=まきば@>牧場@/に@<r=ふ@>伏@/させ、@4いこいのみぎわに@<r=ともな@>伴@/われる。"),
            Sample("continuation indent @^", "@@@^@<r=かみ@>神@/は「@<r=ひかり@>光@/あれ」と@<r=い@>言@/われた。すると@<r=ひかり@>光@/があった。@<r=かみ@>神@/はその@<r=ひかり@>光@/を@<r=み@>見@/て、@<r=よ@>良@/しとされた。"),
            Sample("prose, @8 break, then @1 lines", "@@@<r=よげんしゃ@>預言者@/イザヤによって、@8@1「@<r=あらの@>荒野@/で@<r=よ@>呼@/ばわる@<r=もの@>者@/の@<r=こえ@>声@/がする、@1『@<r=しゅ@>主@/の@<r=みち@>道@/を@<r=そな@>備@/えよ』」。"),
            Sample("same without ruby", "@@預言者イザヤによって、@8@1「荒野で呼ばわる者の声がする、@1『主の道を備えよ』」。"),
            Sample("lines then @0 prose", "@@@1「@<r=み@>見@/よ、おとめがみごもって@<r=おとこ@>男@/の@<r=こ@>子@/を@<r=う@>産@/むであろう。@1その@<r=な@>名@/はインマヌエルと@<r=よ@>呼@/ばれるであろう」。@0これは、「@<r=かみ@>神@/われらと@<r=とも@>共@/にいます」という@<r=いみ@>意味@/である。"),
            Sample("poetry, pinyin", "@@@1@<r=Yē@>耶@/@<r=hé@>和@/@<r=huá@>华@/@<r=shì@>是@/@<r=wǒ@>我@/@<r=de@>的@/@<r=mù@>牧@/@<r=zhě@>者@/，@1@<r=wǒ@>我@/@<r=bì@>必@/@<r=bù@>不@/@<r=zhì@>致@/@<r=quē@>缺@/@<r=fá@>乏@/。"),
        ),
        "highlights" to listOf(
            Sample("full highlight, not selected", STRONGS, highlight = true),
            Sample("full highlight, selected", STRONGS, checked = true, highlight = true),
            Sample("partial over whole ruby runs, not selected", STRONGS, highlight = true, partial = "beginning God created"),
            Sample("partial over whole ruby runs, selected", STRONGS, checked = true, highlight = true, partial = "beginning God created"),
            Sample("partial starting inside a ruby run", STRONGS, highlight = true, partial = "ning God"),
            Sample("partial inside a ruby run, selected", STRONGS, checked = true, highlight = true, partial = "ginni"),
            Sample("furigana, partial highlight", "@@はじめに@<r=かみ@>神@/は@<r=てん@>天@/と@<r=ち@>地@/とを@<r=そうぞう@>創造@/された。", highlight = true, partial = "天と地とを創造"),
            Sample("pinyin, partial highlight, selected", JOHN_3_16_PINYIN, checked = true, highlight = true, partial = "叫一切信他的"),
            Sample("poetry, partial highlight on second line", PSALM_1_1_FURIGANA, highlight = true, partial = "罪びとの道に立たず"),
            Sample("selected, no highlight", PSALM_1_1_FURIGANA, checked = true),
        ),
        "inline styles" to listOf(
            Sample("red letters with furigana", MATTHEW_5_3_RED),
            Sample("red letters with furigana, selected", MATTHEW_5_3_RED, checked = true),
            Sample("red letters, highlighted", MATTHEW_5_3_RED, highlight = true, partial = "心の貧しい人たちは"),
            Sample("red letters with pinyin", "@@@<r=Yē@>耶@/@<r=sū@>稣@/@<r=shuō@>说@/：@6“@<r=wǒ@>我@/@<r=jiù@>就@/@<r=shì@>是@/@<r=dào@>道@/@<r=lù@>路@/、@<r=zhēn@>真@/@<r=lǐ@>理@/、@<r=shēng@>生@/@<r=mìng@>命@/。”@5"),
            Sample("italic base with ruby", "@@And God said, Let there be light: and @9@<r=H1961@>there was@/@7 light. Let @9@<r=H1961@>there be@/ a @<r=H7549@>firmament@/@7 in the midst."),
            Sample("italic and red letters mixed", "@@@6Blessed are the @<r=G4434@>poor@/ @9@<r=G4151@>in spirit@/@7: for @<r=G846@>theirs@/ is the kingdom.@5"),
            Sample("footnote and xref next to ruby", "@@In the @<r=H7225@>beginning@/@<f1@>@/ @<r=H430@>God@/@<x1@>@/ @<r=H1254@>created@/ the heaven and the earth.@<f2@>@/"),
            Sample("ruby adjacent to ruby without gap", "@@@<r=しゅ@>主@/@<r=しゅ@>主@/@<r=しゅ@>主@/ @<r=xiōng@>兄@/@<r=dì@>弟@/@<r=jiě@>姐@/@<r=mèi@>妹@/"),
        ),
        "adversarial" to listOf(
            Sample("reading wider than the row", "@@A @<r=${"very ".repeat(40)}long reading@>word@/ in a sentence that goes on."),
            Sample("base wider than the row, short reading", "@@@<r=ok@>${"supercalifragilistic".repeat(4)}@/ end."),
            Sample("long base with long reading, wrapped", "@@@<r=${"ruby ".repeat(30)}@>${"base ".repeat(12)}@/ end."),
            Sample("emoji base and reading", "@@@<r=\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00@>\uD83E\uDD16\uD83E\uDD16@/ and @<r=family@>\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67@/ ok"),
            Sample("combining marks, RTL marks, ZWJ", "@@@<r=e\u0301e\u0301e\u0301@>e\u0301@/ @<r=\u05E9\u05C1\u05B8\u05DC\u05D5\u05B9\u05DD@>shalom@/ @<r=\u200F\u200E\u200D@>x@/"),
            Sample("whitespace reading and base", "@@@<r=   @>abc@/ @<r=abc@>   @/ end"),
            Sample("unterminated tags", "@@@<r=abc@>base without close @<r=@> @<r=x"),
            Sample("stray closers and empty tags", "@@@/@/@<@>x@/@<r@>y@/@/ done"),
            Sample("nested rubies", "@@@<r=outer@>a@<r=inner@>b@/c@/ done"),
            Sample("base across @8 and paragraph codes", "@@@<r=reading@>first@8second@1third@/ tail"),
            Sample("two hundred rubies", "@@" + buildString { repeat(200) { append("@<r=${it % 10}@>x@/") } }),
            Sample("ruby at very start and very end", "@@@<r=start@>S@/ middle @<r=end@>E@/"),
            Sample("ruby at very start in gutter mode", "@@@1@<r=start@>S@/ middle @<r=end@>E@/"),
            Sample("reading with @-like text and tabs", "@@@<r=a\tb=c@>base@/ @<r=<r=x@>@>y@/"),
        ),
    )

    private val variants = listOf(
        Variant("verse number hidden", verseNumberShown = false),
        Variant("bold", bold = true),
        Variant("large 26dp", fontSizeDp = 26f),
        Variant("small 12dp", fontSizeDp = 12f),
        Variant("line spacing 1.6", lineSpacingMult = 1.6f),
        Variant("line spacing 1.0", lineSpacingMult = 1.0f),
        Variant("night theme", night = true),
    )

    private val variantSamples = listOf(
        Sample("furigana", "@@はじめに@<r=かみ@>神@/は@<r=てん@>天@/と@<r=ち@>地@/とを@<r=そうぞう@>創造@/された。"),
        Sample("poetry, red letters", "@@@1@6「@<r=こころ@>心@/の@<r=まず@>貧@/しい@<r=ひと@>人@/たちは、さいわいである、@1@<r=てんごく@>天国@/は@<r=かれ@>彼@/らのものである。@5"),
        Sample("pinyin, partial highlight, selected", "@@@<r=Qǐ@>起@/@<r=chū@>初@/，@<r=shén@>神@/@<r=chuàng@>创@/@<r=zào@>造@/@<r=tiān@>天@/@<r=dì@>地@/。", checked = true, highlight = true, partial = "创造天地"),
        Sample("no ruby", "@@In the beginning God created the heaven and the earth."),
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
        try {
            for ((group, samples) in groups) {
                val sheet = renderSheet(pipeline, samples, Variant(group))
                write(sheet, File(outputDir, "ruby-${group.replace(' ', '-')}.png"))
            }
            for (variant in variants) {
                val sheet = renderSheet(pipeline, variantSamples, variant)
                write(sheet, File(outputDir, "ruby-variant-${variant.name.replace(' ', '-')}.png"))
            }
        } finally {
            pipeline.controller.pause().stop().destroy()
        }

        val file = File(outputDir, "ruby-basics.png")
        println("Ruby snapshots written to: ${outputDir.absolutePath}")
        assertTrue(file.exists())
    }

    private fun applyVariant(variant: Variant) {
        dims.fontSize2dp = variant.fontSizeDp
        dims.fontBold = if (variant.bold) Typeface.BOLD else Typeface.NORMAL
        dims.lineSpacingMult = variant.lineSpacingMult
        if (variant.night) {
            dims.backgroundColor = 0xff000000.toInt()
            dims.fontColor = 0xffaeaeae.toInt()
            dims.fontRedColor = 0xffa25c5c.toInt()
            dims.verseNumberColor = 0xff6c6cb3.toInt()
        } else {
            dims.backgroundColor = 0xfff0f0f0.toInt()
            dims.fontColor = 0xff212121.toInt()
            dims.fontRedColor = 0xffb71c1c.toInt()
            dims.verseNumberColor = 0xff828282.toInt()
        }
    }

    private fun renderSheet(pipeline: Pipeline, samples: List<Sample>, variant: Variant): Bitmap {
        applyVariant(variant)
        pipeline.frame.setBackgroundColor(dims.backgroundColor)
        val data = buildData(samples, variant)
        val ui = VersesUiModel.EMPTY.copy(isVerseNumberShown = variant.verseNumberShown)
        val rendered = samples.mapIndexed { index, sample -> sample to renderCompose(pipeline, data, ui, index, sample.checked) }

        val height = LABEL_HEIGHT_PX + rendered.sumOf { (_, bitmap) -> LABEL_HEIGHT_PX + bitmap.height + ROW_GAP_PX }
        val sheet = Bitmap.createBitmap(ROW_WIDTH_PX, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(dims.backgroundColor)
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (variant.night) 0xffbbbbbb.toInt() else 0xff555555.toInt()
            textSize = 11f
            typeface = Typeface.SANS_SERIF
        }
        val title = Paint(caption).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        canvas.drawText(variant.name, 4f, 14f, title)
        var y = LABEL_HEIGHT_PX
        for ((sample, bitmap) in rendered) {
            canvas.drawText(sample.label, 4f, (y + 14).toFloat(), caption)
            y += LABEL_HEIGHT_PX
            canvas.drawBitmap(bitmap, 0f, y.toFloat(), null)
            y += bitmap.height + ROW_GAP_PX
            bitmap.recycle()
        }
        return sheet
    }

    private fun write(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    /** Partial highlight offsets are relative to the rendered verse body, which is the base text without the verse number. */
    private fun buildData(samples: List<Sample>, variant: Variant): VersesDataModel {
        val attributes = VersesAttributes.createEmpty(samples.size)
        for ((index, sample) in samples.withIndex()) {
            if (!sample.highlight) continue
            attributes.highlightInfoMap_[index] = Highlights.Info().apply {
                colorRgb = 0xffff00
                val substring = sample.partial ?: return@apply
                val body = VerseRendererCompose.render(isVerseNumberShown = false, ari = Ari.encode(0, 1, index + 1), text = sample.text).text.text
                val start = body.indexOf(substring)
                assertTrue("'$substring' must occur in '$body'", start >= 0)
                partial = Highlights.Info.Partial().apply {
                    hashCode = Highlights.hashCode(body)
                    startOffset = start
                    endOffset = start + substring.length
                }
            }
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
