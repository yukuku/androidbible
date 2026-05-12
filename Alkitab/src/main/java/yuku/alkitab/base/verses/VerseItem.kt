package yuku.alkitab.base.verses

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.view.DragEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.widget.AttributeView
import yuku.alkitab.base.widget.LeftDrawer.PROGRESS_MARK_DRAG_MIME_TYPE
import yuku.alkitab.base.widget.VerseTextView
import yuku.alkitab.debug.R

class VerseItem(context: Context, attrs: AttributeSet) : RelativeLayout(context, attrs) {
    init {
        setWillNotDraw(false)
    }

    var checked = false
        set(value) {
            field = value
            invalidate()
        }

    /** Whether to set the measured height of this view to zero (i.e. verses without text)  */
    var collapsed = false
        set(value) {
            field = value
            requestLayout()
        }

    var onPinDropped: (presetId: Int) -> Unit = {}

    private var dragHover = false
        set(value) {
            field = value
            invalidate()
        }

    private val dragHoverBg by lazy(LazyThreadSafetyMode.NONE) {
        // assert not null
        ResourcesCompat.getDrawable(resources, R.drawable.item_verse_bg_draghovered, context.theme) as Drawable
    }

    private val checkedPaintSolid by lazy(LazyThreadSafetyMode.NONE) {
        Paint().apply {
            style = Paint.Style.FILL
        }
    }
    private val attentionPaint by lazy(LazyThreadSafetyMode.NONE) {
        Paint().apply {
            style = Paint.Style.FILL
        }
    }

    private val audioHighlightPaint by lazy(LazyThreadSafetyMode.NONE) {
        Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }

    /**
     * Target argb color for the audio-highlight overlay. `0` means "no highlight".
     * Setting a non-zero color fades the alpha in over [AUDIO_HIGHLIGHT_FADE_IN_MS];
     * setting `0` fades it out over [AUDIO_HIGHLIGHT_FADE_OUT_MS]. The chosen color
     * is computed by `AudioHighlightColor.pickHighlightColor` once per theme and
     * cached on the VersesController.
     */
    var audioHighlightColor: Int = 0
        set(value) {
            if (field == value) return
            field = value
            startAudioHighlightAnim(value)
        }

    /** Current animated alpha (0.0..1.0) of the audio overlay. */
    private var audioHighlightAlpha: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private var audioHighlightAnimator: ValueAnimator? = null

    private fun startAudioHighlightAnim(targetColor: Int) {
        audioHighlightAnimator?.cancel()
        val from = audioHighlightAlpha
        val to = if (targetColor == 0) 0f else 1f
        if (from == to) {
            audioHighlightAlpha = to
            return
        }
        val duration = if (to > from) AUDIO_HIGHLIGHT_FADE_IN_MS else AUDIO_HIGHLIGHT_FADE_OUT_MS
        audioHighlightAnimator = ValueAnimator.ofFloat(from, to).apply {
            this.duration = duration.toLong()
            addUpdateListener { audioHighlightAlpha = it.animatedValue as Float }
            start()
        }
    }

    lateinit var lText: VerseTextView
    lateinit var lVerseNumber: TextView
    lateinit var attributeView: AttributeView

    /**
     * Whether we briefly "color" this verse resulting from navigation from other parts of the app (e.g. verse navigation, search results).
     * If the value is 0, it means do not color. If nonzero, it is the starting time when the animation starts.
     */
    private var attentionStart = 0L
        set(value) {
            field = value
            invalidate()
        }

    override fun onFinishInflate() {
        super.onFinishInflate()

        lText = findViewById(R.id.lText)
        lVerseNumber = findViewById(R.id.lVerseNumber)
        attributeView = findViewById(R.id.attributeView)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (collapsed) {
            setMeasuredDimension(measuredWidth, 0)
            return
        }

        // Bug in Android 8.0 and under (8.1 are ok!), the text can be cut off when changing text size.
        if (Build.VERSION.SDK_INT <= 26) {
            val lText = this.lText
            val oldTag = lText.getTag(R.id.TAG_textSize)
            if (oldTag == null) {
                lText.setTag(R.id.TAG_textSize, lText.textSize)
            } else {
                val oldSize = oldTag as Float
                val newSize = lText.textSize

                if (oldSize != newSize) {
                    lText.setTag(R.id.TAG_textSize, newSize)
                    lText.post { lText.requestLayout() }
                }
            }
        }

        // Fix bug on Lollipop where the last line of the text does not calculate line spacing mult/add.
        // https://code.google.com/p/android/issues/detail?id=77941
        val lText = this.lText
        val layout = lText.layout

        if (layout != null) {
            val lastLine = layout.lineCount - 1
            val spacing = if (lText.includeFontPadding) layout.getLineBottom(lastLine) - layout.getLineTop(lastLine) else layout.getLineDescent(lastLine) - layout.getLineAscent(lastLine)
            val extra = (spacing * (layout.spacingMultiplier - 1) + layout.spacingAdd + 0.5f).toInt()

            setMeasuredDimension(measuredWidth, measuredHeight + extra)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height

        // Audio highlight overlay drawn first, BELOW the selection overlay,
        // so a verse the user is selecting while audio plays still reads as
        // "selected" rather than blending with the audio tint.
        if (audioHighlightColor != 0 && audioHighlightAlpha > 0f) {
            val baseAlpha = ((audioHighlightColor ushr 24) and 0xff) / 255f
            val alpha = (baseAlpha * audioHighlightAlpha * 255f).toInt().coerceIn(0, 255)
            audioHighlightPaint.color = ColorUtils.setAlphaComponent(audioHighlightColor, alpha)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), audioHighlightPaint)
        }

        if (checked) {
            val solid = checkedPaintSolid
            val colorRgb = Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default)
            val color = ColorUtils.setAlphaComponent(colorRgb, 0xa0)
            solid.color = color

            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), solid)
        }

        if (dragHover) {
            dragHoverBg.setBounds(0, 0, w, h)
            dragHoverBg.draw(canvas)
        }

        if (attentionStart != 0L) {
            val now = System.currentTimeMillis()
            val elapsed = now - attentionStart
            if (elapsed >= ATTENTION_DURATION) {
                attentionStart = 0
            } else {
                val colorRgb = Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default)
                // Clamp to prevent crash
                val alpha = (0.4f * 255f * (1f - elapsed.toFloat() / ATTENTION_DURATION)).toInt()
                    .coerceIn(0, 255)

                val p = attentionPaint
                p.color = ColorUtils.setAlphaComponent(colorRgb, alpha)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

                invalidate() // animate
            }
        }

        super.onDraw(canvas)
    }

    fun callAttention(startTime: Long) {
        this.attentionStart = startTime
    }

    override fun onDragEvent(event: DragEvent) = when (event.action) {
        DragEvent.ACTION_DRAG_STARTED -> {
            // Determines if this View can accept the dragged data
            val desc = event.clipDescription
            desc != null && desc.hasMimeType(PROGRESS_MARK_DRAG_MIME_TYPE)
        }

        DragEvent.ACTION_DRAG_ENTERED -> {
            // Indicate this will receive the drag data.
            dragHover = true
            true
        }

        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
            // Indicate this will no more receive the drag data.
            dragHover = false
            true
        }

        DragEvent.ACTION_DROP -> {
            val item = event.clipData.getItemAt(0)
            val presetId = Integer.parseInt(item.text.toString())
            onPinDropped(presetId)
            true
        }

        else -> false
    }

    /**
     * This is called AND used when explore-by-touch is off.
     * Force TalkBack to use the content description and not explore the children of this view.
     */
    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
        event.text.add(contentDescription)

        return true
    }

    /**
     * Make sure TalkBack reads the verse correctly by setting content description.
     */
    @SuppressLint("StringFormatMatches", "GetContentDescriptionOverride")
    override fun getContentDescription(): CharSequence {
        val res = StringBuilder()

        if (lVerseNumber.length() > 0) {
            res.append(lVerseNumber.text).append(' ')
        }

        res.append(lText.text)

        val bookmark_count = attributeView.bookmarkCount
        if (bookmark_count == 1) {
            res.append(' ').append(context.getString(R.string.desc_verse_attribute_one_bookmark))
        } else if (bookmark_count > 1) {
            res.append(' ').append(context.getString(R.string.desc_verse_attribute_multiple_bookmarks, bookmark_count))
        }

        val note_count = attributeView.noteCount
        if (note_count == 1) {
            res.append(' ').append(context.getString(R.string.desc_verse_attribute_one_note))
        } else if (note_count > 1) {
            res.append(' ').append(context.getString(R.string.desc_verse_attribute_multiple_notes, note_count))
        }

        val progress_mark_bits = attributeView.progressMarkBits
        for (preset_id in 0 until AttributeView.PROGRESS_MARK_TOTAL_COUNT) {
            if (progress_mark_bits and (1 shl AttributeView.PROGRESS_MARK_BITS_START + preset_id) != 0) {
                App.services.storage.db.getProgressMarkByPresetId(preset_id)?.let { progressMark ->
                    val caption = if (TextUtils.isEmpty(progressMark.caption)) {
                        context.getString(AttributeView.getDefaultProgressMarkStringResource(preset_id))
                    } else {
                        progressMark.caption
                    }

                    res.append(' ').append(context.getString(R.string.desc_verse_attribute_progress_mark, caption))
                }
            }
        }

        return res
    }

    companion object {
        private const val ATTENTION_DURATION = 2000f

        /** Audio overlay fade-in/out (PRD §4.3) — slow enough to avoid strobe at speed 1.0×. */
        private const val AUDIO_HIGHLIGHT_FADE_IN_MS = 200
        private const val AUDIO_HIGHLIGHT_FADE_OUT_MS = 150
    }
}
