package yuku.alkitab.base.verses

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import yuku.alkitab.base.IsiActivity
import yuku.alkitab.base.S
import yuku.alkitab.base.widget.AttributeView
import yuku.alkitab.base.widget.VerseTextView
import yuku.alkitab.debug.R
import yuku.alkitab.tracking.Tracker
import java.util.*

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

    lateinit var lText: VerseTextView
    lateinit var lVerseNumber: TextView
    lateinit var attributeView: AttributeView

    /** the ari of the verse represented by this view. If this is 0, this is a pericope or something else.  */
    private var ari: Int = 0

    /**
     * Whether we briefly "color" this verse resulting from navigation from other parts of the app (e.g. verse navigation, search results).
     * If the value is 0, it means do not color. If nonzero, it is the starting time when the animation starts.
     */
    private var attentionStart: Long = 0

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

        if (Build.VERSION.SDK_INT >= 21) {
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
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height

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

    fun callAttention(attentionStartTime: Long) {
        if (this.attentionStart != attentionStartTime) {
            this.attentionStart = attentionStartTime

            if (attentionStartTime != 0L) {
                setWillNotDraw(false)
            }
            invalidate()
        }
    }

    fun setAri(ari: Int) {
        this.ari = ari
    }

    override fun onDragEvent(event: DragEvent): Boolean {
        // Dropping only works on verse not pericope or something else
        if (ari == 0) return false

        return when (event.action) {
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
                Tracker.trackEvent("pin_drop")
                val item = event.clipData.getItemAt(0)
                val preset_id = Integer.parseInt(item.text.toString())

                val progressMark = S.getDb().getProgressMarkByPresetId(preset_id)
                progressMark!!.ari = this.ari
                progressMark.modifyTime = Date()
                S.getDb().insertOrUpdateProgressMark(progressMark)

                App.getLbm().sendBroadcast(Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED))

                true
            }

            else -> false
        }
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
                val progressMark = S.getDb().getProgressMarkByPresetId(preset_id)

                val caption: String
                if (TextUtils.isEmpty(progressMark!!.caption)) {
                    caption = context.getString(AttributeView.getDefaultProgressMarkStringResource(preset_id))
                } else {
                    caption = progressMark.caption
                }

                res.append(' ').append(context.getString(R.string.desc_verse_attribute_progress_mark, caption))
            }
        }

        return res
    }

    // TODO(VersesView revamp)
    fun invalidateSelectedVersePaints() {
//        checkedPaintSolid = null
//        attentionPaint = null
    }

    companion object {
        const val PROGRESS_MARK_DRAG_MIME_TYPE = "application/vnd.yuku.alkitab.progress_mark.drag"
        private const val ATTENTION_DURATION = 2000f
    }
}
