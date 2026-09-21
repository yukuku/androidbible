package yuku.alkitab.base.widget

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuView
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.afw.App as AfwApp
import yuku.alkitab.debug.R

/**
 * Host for the real reader chrome: the production layout, the production menu,
 * and the same action-bar configuration [yuku.alkitab.base.IsiActivity] applies,
 * without any of the reader's data loading.
 */
class ToolbarAuditHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Alkitab)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_isi_content)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
            setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_isi, menu)
        return true
    }
}

/**
 * Measures the reader toolbar as it is actually laid out, across the screen
 * widths the app ships on, and draws the result as a dimensioned blueprint.
 *
 * This is a report generator for human inspection rather than a pass/fail
 * guard: it writes `blueprint.png`, `toolbar-<width>dp.png`, `measurements.md`
 * and `measurements.json` under `Alkitab/build/reports/toolbar-audit/`. The few
 * assertions it does make only protect the audit itself, so a silently empty
 * or mis-themed render cannot be mistaken for a result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "sw360dp-w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderToolbarSpaceAuditTest {

    /** Every measurement is taken at xxhdpi so 1 dp is exactly 3 px. */
    private val density = 3f

    /** Android's minimum recommended touch target. */
    private val MIN_TOUCH_DP = 48f

    private fun px(dp: Number) = (dp.toFloat() * density).roundToInt()

    private fun dp(px: Number) = px.toFloat() / density

    private fun dpStr(pxValue: Number): String {
        val v = dp(pxValue)
        return if (v == v.roundToInt().toFloat()) "${v.roundToInt()}" else String.format("%.1f", v)
    }

    // --- Measured model ---------------------------------------------------

    private class Slot(
        val key: String,
        val short: String,
        val label: String,
        val drawn: Rect,
        val touch: Rect,
        val color: Int,
        val note: String = "",
    ) {
        val drawnWidth get() = drawn.width()
        val touchWidth get() = touch.width()
        val touchHeight get() = touch.height()
    }

    private class RefFit(
        val reference: String,
        val naturalPx: Float,
        val availPx: Int,
        val lines: Int,
        val truncated: Boolean,
    )

    private class Panel(
        val widthDp: Int,
        val swBucket: String,
        val prevNextDp: Float,
        val gotoMarginDp: Float,
        val toolbar: Bitmap,
        val toolbarWidthPx: Int,
        val toolbarHeightPx: Int,
        val slots: List<Slot>,
        val navClusterDrawn: Rect,
        val gotoTextAvailPx: Int,
        val refFits: List<RefFit>,
    )

    // --- Palette (blueprint) ----------------------------------------------

    private val PAPER = Color.parseColor("#0B2A46")
    private val GRID_MINOR = Color.parseColor("#12385C")
    private val GRID_MAJOR = Color.parseColor("#17496F")
    private val INK = Color.parseColor("#E8F3FF")
    private val INK_DIM = Color.parseColor("#8FB6D6")
    private val ALERT = Color.parseColor("#FF6B6B")
    private val OK = Color.parseColor("#7BE3A8")

    private val SLOT_COLORS = mapOf(
        "hamburger" to Color.parseColor("#9BD1FF"),
        "prev" to Color.parseColor("#FFD479"),
        "reference" to Color.parseColor("#FF8FB1"),
        "next" to Color.parseColor("#FFD479"),
        "version" to Color.parseColor("#B79BFF"),
        "audio" to Color.parseColor("#7BE3A8"),
        "search" to Color.parseColor("#7BE3A8"),
        "overflow" to Color.parseColor("#7BE3A8"),
        "menu" to Color.parseColor("#7BE3A8"),
    )

    private val mono: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    private val monoBold: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    @Before
    fun setUp() {
        AfwApp.initWithAppContext(ApplicationProvider.getApplicationContext())
    }

    // --- Measuring --------------------------------------------------------

    private fun buildHost(): ToolbarAuditHostActivity =
        Robolectric.buildActivity(ToolbarAuditHostActivity::class.java).setup().get()

    private fun boundsIn(root: ViewGroup, v: View): Rect {
        val r = Rect(0, 0, v.width, v.height)
        root.offsetDescendantRectToMyCoords(v, r)
        return r
    }

    private fun labelOfMenuChild(child: View): Pair<String, String> {
        val itemView = child as? MenuView.ItemView
        val title = itemView?.itemData?.title?.toString()
        return when {
            title != null -> {
                val key = when {
                    itemView.itemData?.itemId == R.id.menuAudio -> "audio"
                    itemView.itemData?.itemId == R.id.menuSearch -> "search"
                    else -> "menu"
                }
                key to title
            }

            else -> "overflow" to (child.contentDescription?.toString() ?: "Overflow")
        }
    }

    /**
     * The toolbar's elements as laid out, with the area each one actually
     * receives touches in.
     *
     * The reference button is the one place where the two differ. It is laid
     * out underneath the chapter arrows, which the frame dispatches to first,
     * and it additionally refuses the outermost
     * `nav_prevnext_width - nav_goto_side_margin` on each side itself, so its
     * touch area is whatever survives both.
     */
    private fun collectSlots(activity: Activity, untouchable: Int): List<Slot> {
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        val bGoto = activity.findViewById<GotoButton>(R.id.bGoto)
        val bLeft = activity.findViewById<ImageButton>(R.id.bLeft)
        val bRight = activity.findViewById<ImageButton>(R.id.bRight)
        val bVersion = activity.findViewById<TextView>(R.id.bVersion)

        val slots = mutableListOf<Slot>()

        val navButton = toolbar.children.filterIsInstance<ImageButton>().firstOrNull()
        assertNotNull("the up/drawer button must be present in the toolbar", navButton)
        navButton!!
        val navIconDp = dpStr(toolbar.navigationIcon?.intrinsicWidth ?: 0)
        slots += Slot(
            "hamburger", "HAM", "Drawer (hamburger)",
            boundsIn(toolbar, navButton), boundsIn(toolbar, navButton),
            SLOT_COLORS.getValue("hamburger"),
            "${navIconDp}dp icon in AppCompat's 56dp minWidth",
        )

        val leftRect = boundsIn(toolbar, bLeft).takeIf { bLeft.visibility != View.GONE }
        if (leftRect != null) {
            slots += Slot(
                "prev", "PREV", "Previous chapter",
                leftRect, leftRect, SLOT_COLORS.getValue("prev"),
                "@dimen/nav_prevnext_width",
            )
        }

        val rightRect = boundsIn(toolbar, bRight).takeIf { bRight.visibility != View.GONE }

        val gotoRect = boundsIn(toolbar, bGoto)
        val gotoTouch = Rect(
            max(gotoRect.left + untouchable, leftRect?.right ?: gotoRect.left),
            gotoRect.top,
            min(gotoRect.right - untouchable, rightRect?.left ?: gotoRect.right),
            gotoRect.bottom,
        )
        slots += Slot(
            "reference", "REF", "Verse reference (GotoButton)",
            gotoRect, gotoTouch, SLOT_COLORS.getValue("reference"),
            "drawn over the arrows, which take the touches",
        )

        if (rightRect != null) {
            slots += Slot(
                "next", "NEXT", "Next chapter",
                rightRect, rightRect, SLOT_COLORS.getValue("next"),
                "@dimen/nav_prevnext_width",
            )
        }

        if (bVersion.visibility != View.GONE) {
            val versionRect = boundsIn(toolbar, bVersion)
            slots += Slot(
                "version", "VER", "Version changer",
                versionRect, versionRect, SLOT_COLORS.getValue("version"),
                "fixed 72dp in the layout",
            )
        }

        val menuView = toolbar.children.filterIsInstance<ActionMenuView>().firstOrNull()
        assertNotNull("the options menu must be present in the toolbar", menuView)
        for (child in menuView!!.children) {
            if (child.visibility == View.GONE) continue
            val (key, title) = labelOfMenuChild(child)
            val rect = boundsIn(toolbar, child)
            slots += Slot(
                key,
                when (key) {
                    "audio" -> "AUD"
                    "search" -> "SRCH"
                    "overflow" -> "OVF"
                    else -> key.uppercase().take(4)
                },
                title,
                rect, rect,
                SLOT_COLORS[key] ?: SLOT_COLORS.getValue("menu"),
                "${dpStr(iconWidthOf(child))}dp icon + 12dp padding per side",
            )
        }

        slots.sortBy { it.drawn.left }
        return slots
    }

    /** Intrinsic width of the icon an action menu item draws. */
    private fun iconWidthOf(child: View): Int =
        (child as? MenuView.ItemView)?.itemData?.icon?.intrinsicWidth ?: 0

    private fun measurePanel(widthDp: Int, reference: String, versionInitials: String): Panel {
        RuntimeEnvironment.setQualifiers("sw${widthDp}dp-w${widthDp}dp-h640dp-port-xxhdpi")

        val activity: Activity = buildHost()
        val res = activity.resources
        assertEquals(
            "the audit assumes xxhdpi so that 1 dp is exactly 3 px",
            density,
            res.displayMetrics.density,
            0.001f,
        )

        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        val root = activity.findViewById<ViewGroup>(R.id.root)
        val bGoto = activity.findViewById<GotoButton>(R.id.bGoto)
        val bLeft = activity.findViewById<ImageButton>(R.id.bLeft)
        val bRight = activity.findViewById<ImageButton>(R.id.bRight)
        val bVersion = activity.findViewById<TextView>(R.id.bVersion)
        val navCluster = bGoto.parent as ViewGroup

        bGoto.text = reference
        bVersion.text = versionInitials

        val widthPx = px(widthDp)
        val heightPx = px(640)

        fun layOut() {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, widthPx, heightPx)
        }
        layOut()
        layOut()

        assertTrue("the toolbar must have been laid out", toolbar.width > 0 && toolbar.height > 0)

        val prevNextPx = res.getDimensionPixelSize(R.dimen.nav_prevnext_width)
        val gotoMarginPx = res.getDimensionPixelSize(R.dimen.nav_goto_side_margin)

        // GotoButton hands the outer strip on each side back to the prev/next
        // buttons underneath it, so its touch area is narrower than its box.
        val untouchable = prevNextPx - gotoMarginPx

        val slots = collectSlots(activity, untouchable)

        val gotoTextAvail = bGoto.width - bGoto.paddingLeft - bGoto.paddingRight

        val refFits = REFERENCES.map { ref ->
            bGoto.text = ref
            layOut()
            val layout = bGoto.layout
            val truncated = layout != null && (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }
            RefFit(
                reference = ref,
                naturalPx = bGoto.paint.measureText(ref),
                availPx = bGoto.width - bGoto.paddingLeft - bGoto.paddingRight,
                lines = layout?.lineCount ?: 0,
                truncated = truncated,
            )
        }

        bGoto.text = reference
        layOut()

        val bitmap = Bitmap.createBitmap(toolbar.width, toolbar.height, Bitmap.Config.ARGB_8888)
        toolbar.draw(Canvas(bitmap))

        return Panel(
            widthDp = widthDp,
            swBucket = if (widthDp >= 360) "sw360dp" else "default (sw < 360dp)",
            prevNextDp = dp(prevNextPx),
            gotoMarginDp = dp(gotoMarginPx),
            toolbar = bitmap,
            toolbarWidthPx = toolbar.width,
            toolbarHeightPx = toolbar.height,
            slots = slots,
            navClusterDrawn = boundsIn(toolbar, navCluster),
            gotoTextAvailPx = gotoTextAvail,
            refFits = refFits,
        )
    }

    // --- Blueprint drawing -------------------------------------------------

    private fun paint(
        color: Int,
        stroke: Float? = null,
        textSize: Float? = null,
        face: Typeface = mono,
        align: Paint.Align = Paint.Align.LEFT,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        if (stroke != null) {
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        if (textSize != null) {
            this.textSize = textSize
            typeface = face
            this.textAlign = align
        }
    }

    private fun drawGrid(c: Canvas, w: Int, h: Int) {
        c.drawColor(PAPER)
        val minor = paint(GRID_MINOR, stroke = 1f)
        val major = paint(GRID_MAJOR, stroke = 1f)
        var x = 0
        while (x <= w) {
            c.drawLine(x.toFloat(), 0f, x.toFloat(), h.toFloat(), if (x % 300 == 0) major else minor)
            x += 60
        }
        var y = 0
        while (y <= h) {
            c.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), if (y % 300 == 0) major else minor)
            y += 60
        }
    }

    private fun dashed(color: Int, stroke: Float) = paint(color, stroke = stroke).apply {
        pathEffect = DashPathEffect(floatArrayOf(6f, 7f), 0f)
    }

    /** Architect-style dimension line: witness ticks at both ends, label centred above. */
    private fun drawDimension(
        c: Canvas,
        x1: Float,
        x2: Float,
        y: Float,
        label: String,
        color: Int,
        textSize: Float = 20f,
    ) {
        val line = paint(color, stroke = 2f)
        c.drawLine(x1, y, x2, y, line)
        val tick = 7f
        c.drawLine(x1, y - tick, x1, y + tick, line)
        c.drawLine(x2, y - tick, x2, y + tick, line)

        // Arrowheads pointing outward when the span is wide enough to hold them.
        val span = x2 - x1
        if (span > 26f) {
            val a = 8f
            c.drawPath(Path().apply {
                moveTo(x1, y); lineTo(x1 + a, y - a / 2); lineTo(x1 + a, y + a / 2); close()
            }, paint(color).apply { style = Paint.Style.FILL })
            c.drawPath(Path().apply {
                moveTo(x2, y); lineTo(x2 - a, y - a / 2); lineTo(x2 - a, y + a / 2); close()
            }, paint(color).apply { style = Paint.Style.FILL })
        }

        val tp = paint(color, textSize = textSize, face = monoBold, align = Paint.Align.CENTER)
        val labelWidth = tp.measureText(label)
        if (labelWidth + 12f <= span) {
            // Break the dimension line so the label sits inside it, as on a drawing.
            val cx = (x1 + x2) / 2
            c.drawRect(cx - labelWidth / 2 - 6f, y - 12f, cx + labelWidth / 2 + 6f, y + 12f, paint(PAPER).apply { style = Paint.Style.FILL })
            c.drawText(label, cx, y + 7f, tp)
        } else {
            c.drawText(label, (x1 + x2) / 2, y - 12f, tp)
        }
    }

    private fun drawPanel(c: Canvas, panel: Panel, originX: Float, originY: Float, canvasWidth: Int): Float {
        var y = originY

        val title = paint(INK, textSize = 30f, face = monoBold)
        c.drawText("${panel.widthDp} dp WIDE", originX, y, title)

        val sub = paint(INK_DIM, textSize = 20f)
        c.drawText(
            "resource bucket ${panel.swBucket}   prev/next ${fmt(panel.prevNextDp)}dp   goto side margin ${fmt(panel.gotoMarginDp)}dp   bar height ${dpStr(panel.toolbarHeightPx)}dp",
            originX,
            y + 26f,
            sub,
        )
        y += 58f

        // The rendered toolbar, pixel for pixel.
        val barTop = y
        c.drawBitmap(panel.toolbar, originX, barTop, null)
        c.drawRect(
            originX - 1f, barTop - 1f,
            originX + panel.toolbarWidthPx + 1f, barTop + panel.toolbarHeightPx + 1f,
            paint(INK, stroke = 2f),
        )
        val barBottom = barTop + panel.toolbarHeightPx

        // Element bands overlaid on the render.
        for (slot in panel.slots) {
            val l = originX + slot.drawn.left
            val r = originX + slot.drawn.right
            c.drawRect(l, barTop, r, barBottom, paint(slot.color, stroke = 2f))
            c.drawRect(
                l, barTop, r, barBottom,
                paint(slot.color).apply { style = Paint.Style.FILL; alpha = 26 },
            )
        }

        y = barBottom

        // Witness lines dropping from every boundary.
        val boundaries = sortedSetOf<Int>()
        panel.slots.forEach { boundaries += it.drawn.left; boundaries += it.drawn.right }
        boundaries += 0
        boundaries += panel.toolbarWidthPx
        val witnessBottom = y + 290f
        for (b in boundaries) {
            c.drawLine(originX + b, y, originX + b, witnessBottom, dashed(INK_DIM, 1.5f))
        }

        // Row 1: drawn width of every element.
        val rowDrawn = y + 40f
        c.drawText("BOX", originX - 14f, rowDrawn + 7f, paint(INK_DIM, textSize = 17f, align = Paint.Align.RIGHT))
        for (slot in panel.slots) {
            drawDimension(
                c,
                originX + slot.drawn.left,
                originX + slot.drawn.right,
                rowDrawn,
                dpStr(slot.drawnWidth),
                slot.color,
            )
        }

        // Row 2: touch width, flagged when under 48dp.
        val rowTouch = y + 110f
        c.drawText("TAP", originX - 14f, rowTouch + 7f, paint(INK_DIM, textSize = 17f, align = Paint.Align.RIGHT))
        for (slot in panel.slots) {
            val short = dp(slot.touchWidth) < MIN_TOUCH_DP
            drawDimension(
                c,
                originX + slot.touch.left,
                originX + slot.touch.right,
                rowTouch,
                dpStr(slot.touchWidth) + if (short) "!" else "",
                if (short) ALERT else OK,
            )
        }

        // Row 3: width that no element claims at all.
        val rowGap = y + 180f
        var cursor = 0
        var drewGap = false
        for (slot in panel.slots) {
            if (slot.drawn.left - cursor > 2) {
                drawDimension(
                    c, originX + cursor, originX + slot.drawn.left, rowGap,
                    "${dpStr(slot.drawn.left - cursor)} unused", ALERT, textSize = 18f,
                )
                drewGap = true
            }
            cursor = max(cursor, slot.drawn.right)
        }
        if (panel.toolbarWidthPx - cursor > 2) {
            drawDimension(
                c, originX + cursor, originX + panel.toolbarWidthPx, rowGap,
                "${dpStr(panel.toolbarWidthPx - cursor)} unused", ALERT, textSize = 18f,
            )
            drewGap = true
        }
        if (drewGap) {
            c.drawText("GAP", originX - 14f, rowGap + 7f, paint(ALERT, textSize = 17f, align = Paint.Align.RIGHT))
        }

        // Row 4: the whole bar, plus the nav cluster it contains.
        val rowTotal = y + 225f
        drawDimension(
            c, originX + panel.navClusterDrawn.left, originX + panel.navClusterDrawn.right, rowTotal,
            "nav cluster ${dpStr(panel.navClusterDrawn.width())}dp", INK_DIM, textSize = 18f,
        )
        val rowBar = y + 270f
        drawDimension(c, originX, originX + panel.toolbarWidthPx, rowBar, "${panel.widthDp} dp", INK, textSize = 22f)

        y = witnessBottom + 30f

        // Element key with the name of each band.
        val keyText = paint(INK, textSize = 19f)
        val keyDim = paint(INK_DIM, textSize = 17f)
        for (slot in panel.slots) {
            c.drawRect(originX, y - 13f, originX + 26f, y + 5f, paint(slot.color).apply { style = Paint.Style.FILL })
            c.drawText(slot.label, originX + 38f, y, keyText)
            val short = dp(slot.touchWidth) < MIN_TOUCH_DP
            val share = 100f * slot.touchWidth / panel.toolbarWidthPx
            c.drawText(
                "box ${dpStr(slot.drawnWidth)}dp x ${dpStr(slot.drawn.height())}dp" +
                    "   tap ${dpStr(slot.touchWidth)}dp x ${dpStr(slot.touchHeight)}dp" +
                    "   ${String.format("%4.1f", share)}% of the bar" +
                    (if (short) "  << under ${MIN_TOUCH_DP.toInt()}dp" else "") +
                    (if (slot.note.isNotEmpty()) "   (${slot.note})" else ""),
                originX + 420f,
                y,
                if (short) paint(ALERT, textSize = 17f) else keyDim,
            )
            y += 28f
        }

        y += 14f
        c.drawText(
            "text box inside the reference button: ${dpStr(panel.gotoTextAvailPx)}dp",
            originX,
            y,
            paint(INK, textSize = 19f, face = monoBold),
        )
        y += 26f
        for (fit in panel.refFits) {
            val verdict = when {
                fit.truncated -> "TRUNCATED"
                fit.lines > 1 -> "wraps to ${fit.lines} lines"
                else -> "fits on one line"
            }
            val col = when {
                fit.truncated -> ALERT
                fit.lines > 1 -> Color.parseColor("#FFD479")
                else -> OK
            }
            c.drawText(
                "  \"${fit.reference}\"".padEnd(28) +
                    "needs ${dpStr(fit.naturalPx)}dp of ${dpStr(fit.availPx)}dp  ->  $verdict",
                originX,
                y,
                paint(col, textSize = 18f),
            )
            y += 24f
        }

        y += 18f
        c.drawLine(originX, y, canvasWidth - originX, y, dashed(GRID_MAJOR, 2f))
        return y + 46f
    }

    private fun fmt(v: Float) = if (v == v.roundToInt().toFloat()) "${v.roundToInt()}" else String.format("%.1f", v)

    private fun drawTitleBlock(c: Canvas, x: Float, y: Float, w: Float, panels: List<Panel>): Float {
        c.drawText("READER TOOLBAR - SPACE AND TOUCH-TARGET AUDIT", x, y, paint(INK, textSize = 44f, face = monoBold))
        c.drawText(
            "IsiActivity / activity_isi_content.xml - AppCompat Toolbar - measured and rendered with Robolectric at xxhdpi (1dp = 3px)",
            x, y + 34f, paint(INK_DIM, textSize = 20f),
        )
        c.drawText(
            "BOX = laid-out bounds.  TAP = area that actually receives touches.  ! marks a touch target below ${MIN_TOUCH_DP.toInt()}dp.",
            x, y + 62f, paint(INK_DIM, textSize = 20f),
        )
        val widths = panels.joinToString(", ") { "${it.widthDp}dp" }
        c.drawText("sheets: $widths", x, y + 90f, paint(INK_DIM, textSize = 20f))
        c.drawLine(x, y + 116f, x + w, y + 116f, paint(GRID_MAJOR, stroke = 3f))
        return y + 164f
    }

    /**
     * Where the bar goes, as one proportional strip. The touch rectangles tile
     * the bar exactly (the reference button's box overlaps the chapter arrows,
     * its touch area does not), so they can be laid end to end without
     * double-counting.
     */
    private fun drawBudget(c: Canvas, panel: Panel, x: Float, y0: Float, w: Float): Float {
        var y = y0
        c.drawText(
            "WHERE THE ${panel.widthDp} dp GOES",
            x, y, paint(INK, textSize = 30f, face = monoBold),
        )
        y += 22f

        val barTop = y
        val barHeight = 62f
        val scale = w / panel.toolbarWidthPx
        for (slot in panel.slots) {
            val l = x + slot.touch.left * scale
            val r = x + slot.touch.right * scale
            c.drawRect(l, barTop, r, barTop + barHeight, paint(slot.color).apply { style = Paint.Style.FILL; alpha = 190 })
            c.drawRect(l, barTop, r, barTop + barHeight, paint(PAPER, stroke = 2f))
            val label = "${dpStr(slot.touchWidth)}dp"
            val tp = paint(PAPER, textSize = 19f, face = monoBold, align = Paint.Align.CENTER)
            if (tp.measureText(label) + 10f < r - l) {
                c.drawText(label, (l + r) / 2, barTop + 26f, tp)
                c.drawText(slot.short, (l + r) / 2, barTop + 50f, paint(PAPER, textSize = 17f, align = Paint.Align.CENTER))
            }
        }
        c.drawRect(x, barTop, x + w, barTop + barHeight, paint(INK, stroke = 2f))
        y = barTop + barHeight + 34f

        val ref = panel.slots.first { it.key == "reference" }
        val chrome = panel.toolbarWidthPx - ref.touchWidth
        c.drawText(
            "fixed chrome ${dpStr(chrome)}dp (${String.format("%.0f", 100f * chrome / panel.toolbarWidthPx)}%)" +
                "   vs   verse reference ${dpStr(ref.touchWidth)}dp tappable (${String.format("%.0f", 100f * ref.touchWidth / panel.toolbarWidthPx)}%)," +
                " ${dpStr(panel.gotoTextAvailPx)}dp of text box",
            x, y, paint(ALERT, textSize = 22f, face = monoBold),
        )
        y += 32f
        c.drawLine(x, y, x + w, y, paint(GRID_MAJOR, stroke = 3f))
        return y + 46f
    }

    // --- Reporting ---------------------------------------------------------

    private fun resolveOutputDir(): File {
        val override = System.getenv("TOOLBAR_AUDIT_DIR")
        return if (override != null) File(override) else File("build/reports/toolbar-audit")
    }

    private fun jsonEscape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    /** Width of the bar that no element's box covers. */
    private fun unusedPx(panel: Panel): Int {
        var cursor = 0
        var unused = 0
        for (slot in panel.slots) {
            if (slot.drawn.left > cursor) unused += slot.drawn.left - cursor
            cursor = max(cursor, slot.drawn.right)
        }
        return unused + max(0, panel.toolbarWidthPx - cursor)
    }

    private fun measurementsJson(panels: List<Panel>): String = buildString {
        append("{\n  \"density\": 3.0,\n  \"unit\": \"dp\",\n  \"panels\": [\n")
        panels.forEachIndexed { pi, p ->
            append("    {\n")
            append("      \"widthDp\": ${p.widthDp},\n")
            append("      \"heightDp\": ${dpStr(p.toolbarHeightPx)},\n")
            append("      \"bucket\": \"${jsonEscape(p.swBucket)}\",\n")
            append("      \"prevNextDp\": ${fmt(p.prevNextDp)},\n")
            append("      \"gotoSideMarginDp\": ${fmt(p.gotoMarginDp)},\n")
            append("      \"navClusterDp\": ${dpStr(p.navClusterDrawn.width())},\n")
            append("      \"unclaimedDp\": ${dpStr(unusedPx(p))},\n")
            append("      \"referenceTextBoxDp\": ${dpStr(p.gotoTextAvailPx)},\n")
            append("      \"elements\": [\n")
            p.slots.forEachIndexed { si, s ->
                append("        {\"key\": \"${s.key}\", \"label\": \"${jsonEscape(s.label)}\", ")
                append("\"leftDp\": ${dpStr(s.drawn.left)}, \"boxWidthDp\": ${dpStr(s.drawnWidth)}, ")
                append("\"tapWidthDp\": ${dpStr(s.touchWidth)}, \"tapHeightDp\": ${dpStr(s.touchHeight)}}")
                append(if (si == p.slots.lastIndex) "\n" else ",\n")
            }
            append("      ],\n")
            append("      \"referenceFits\": [\n")
            p.refFits.forEachIndexed { fi, f ->
                append("        {\"reference\": \"${jsonEscape(f.reference)}\", ")
                append("\"naturalWidthDp\": ${dpStr(f.naturalPx)}, \"availableDp\": ${dpStr(f.availPx)}, ")
                append("\"lines\": ${f.lines}, \"truncated\": ${f.truncated}}")
                append(if (fi == p.refFits.lastIndex) "\n" else ",\n")
            }
            append("      ]\n")
            append("    }")
            append(if (pi == panels.lastIndex) "\n" else ",\n")
        }
        append("  ]\n}\n")
    }

    private fun measurementsMarkdown(panels: List<Panel>): String = buildString {
        appendLine("# Reader toolbar space audit")
        appendLine()
        appendLine("Measured with Robolectric at xxhdpi (1 dp = 3 px) from the production layout")
        appendLine("`activity_isi_content.xml` and menu `activity_isi.xml`, with the same action-bar")
        appendLine("configuration `IsiActivity` applies. All figures are dp.")
        appendLine()
        for (p in panels) {
            appendLine("## ${p.widthDp} dp wide (${p.swBucket})")
            appendLine()
            appendLine("Bar ${p.widthDp} x ${dpStr(p.toolbarHeightPx)}. prev/next ${fmt(p.prevNextDp)}, goto side margin ${fmt(p.gotoMarginDp)}, nav cluster ${dpStr(p.navClusterDrawn.width())}, unclaimed ${dpStr(unusedPx(p))}.")
            appendLine()
            appendLine("| element | left | box w | tap w | tap h | tap >= 48dp |")
            appendLine("| --- | ---: | ---: | ---: | ---: | :---: |")
            for (s in p.slots) {
                val ok = if (dp(s.touchWidth) >= MIN_TOUCH_DP && dp(s.touchHeight) >= MIN_TOUCH_DP) "yes" else "NO"
                appendLine("| ${s.label} | ${dpStr(s.drawn.left)} | ${dpStr(s.drawnWidth)} | ${dpStr(s.touchWidth)} | ${dpStr(s.touchHeight)} | $ok |")
            }
            appendLine()
            appendLine("Reference text box: ${dpStr(p.gotoTextAvailPx)} dp.")
            appendLine()
            appendLine("| reference | needs | available | lines | truncated |")
            appendLine("| --- | ---: | ---: | ---: | :---: |")
            for (f in p.refFits) {
                appendLine("| ${f.reference} | ${dpStr(f.naturalPx)} | ${dpStr(f.availPx)} | ${f.lines} | ${if (f.truncated) "YES" else "no"} |")
            }
            appendLine()
        }
    }

    // --- The audit ---------------------------------------------------------

    companion object {
        /** What `Book.reference` produces today, from `Book.shortName`. */
        private val REFERENCES = listOf(
            "John 3",
            "Genesis 1",
            "Kejadian 1",
            "Revelation 22",
            "Kidung Agung 8",
            "2 Chronicles 21",
            "1 Thessalonians 5",
        )

        /** The same references built from `Book.abbreviation`, which every version carries. */
        private val ABBREVIATED = listOf(
            "Joh 3",
            "Gen 1",
            "Kej 1",
            "Rev 22",
            "Kid 8",
            "2Ch 21",
            "1Th 5",
        )

        /** The same references with the version folded in as an inline chip. */
        private val MERGED = REFERENCES.map { "$it \u00b7 TB" }

        private val WIDTHS = listOf(320, 360, 384, 411, 480, 600)

        /** `Version.getInitials` yields at most six characters, which is what the layout is sized for. */
        private val VERSION_INITIALS = listOf("TB", "KJV", "AYT", "NKJV", "VERSNM")
    }

    @Test
    fun `draw the reader toolbar at every shipping width and dimension every element`() {
        val outputDir = resolveOutputDir()
        outputDir.mkdirs()

        val panels = WIDTHS.map { measurePanel(it, reference = "Kejadian 1", versionInitials = "TB") }

        for (p in panels) {
            File(outputDir, "toolbar-${p.widthDp}dp.png").outputStream().use {
                p.toolbar.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }

        // Every element must have been found, on every sheet.
        for (p in panels) {
            val keys = p.slots.map { it.key }.toSet()
            assertTrue(
                "sheet ${p.widthDp}dp is missing toolbar elements, found $keys",
                keys.containsAll(setOf("hamburger", "prev", "reference", "next", "version", "audio", "search")),
            )
            assertTrue(
                "sheet ${p.widthDp}dp measured an empty reference button",
                p.slots.first { it.key == "reference" }.drawnWidth > 0,
            )
        }

        val margin = 90f
        val contentWidth = max(panels.maxOf { it.toolbarWidthPx }, 1580)
        val canvasWidth = (contentWidth + margin * 2).toInt()

        fun render(canvas: Canvas, width: Int): Float {
            var y = 110f
            y = drawTitleBlock(canvas, margin, y, width - margin * 2, panels)
            y = drawBudget(canvas, panels.first { it.widthDp == 360 }, margin, y, width - margin * 2)
            for (p in panels) {
                y = drawPanel(canvas, p, margin, y, width)
            }
            return y
        }

        // Lay the sheet out against a throwaway canvas to find how tall it is,
        // so the measuring pass cannot drift from the drawing pass.
        val probe = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        val height = max(render(probe, canvasWidth) + 40f, 600f).toInt()

        val sheet = Bitmap.createBitmap(canvasWidth, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        drawGrid(c, canvasWidth, height)
        render(c, canvasWidth)

        val blueprint = File(outputDir, "blueprint.png")
        blueprint.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }

        File(outputDir, "measurements.json").writeText(measurementsJson(panels))
        File(outputDir, "measurements.md").writeText(measurementsMarkdown(panels))

        println("toolbar audit written to ${outputDir.absolutePath}")
        println(measurementsMarkdown(panels))

        assertTrue("the blueprint must have been written", blueprint.length() > 0)
    }

    // --- What each candidate change buys ----------------------------------

    private class Variant(
        val name: String,
        val toolbar: Bitmap,
        val referenceBoxPx: Int,
        val refs: List<String>,
        val oneLiners: List<String>,
        val truncated: List<String>,
        val sample: String,
        val slots: List<Slot>,
    )

    private class Mutation(
        val name: String,
        val refs: List<String> = REFERENCES,
        val sample: String = "Kejadian 1",
        val apply: (ToolbarAuditHostActivity) -> Unit,
    )

    private fun mutationsAt360() = listOf(
        Mutation("as shipped") {},
        Mutation("book abbreviations in the bar", refs = ABBREVIATED, sample = "Kej 1") {},
        Mutation("version changer sized to 48dp") { a -> shrinkVersion(a, 48) },
        Mutation("chapter arrows 40dp (margins 32dp)") { a -> resizeArrows(a, 40, 32) },
        Mutation("search moved out of the bar") { a -> hideMenuItem(a, R.id.menuSearch) },
        Mutation("audio unavailable (already happens)") { a -> hideMenuItem(a, R.id.menuAudio) },
        Mutation("chapter arrows dropped entirely") { a -> dropArrows(a) },
        Mutation("48dp version + 40dp arrows + search out") { a ->
            shrinkVersion(a, 48); resizeArrows(a, 40, 32); hideMenuItem(a, R.id.menuSearch)
        },
    )

    /**
     * The narrow bucket is its own question: it shrinks the chapter arrows to
     * 32dp, which is the only place in the bar where a control drops below the
     * minimum target on purpose.
     */
    private fun mutationsAt320() = listOf(
        Mutation("as shipped (32dp arrows)") {},
        Mutation("book abbreviations in the bar", refs = ABBREVIATED, sample = "Kej 1") {},
        Mutation("arrows raised to 48dp (margins 40dp)") { a -> resizeArrows(a, 48, 40) },
        Mutation("48dp arrows + 48dp version + search out", refs = ABBREVIATED, sample = "Kej 1") { a ->
            resizeArrows(a, 48, 40); shrinkVersion(a, 48); hideMenuItem(a, R.id.menuSearch)
        },
    )

    private fun measureVariants(widthDp: Int, mutations: List<Mutation>): List<Variant> {
        return mutations.map { mutation ->
            val name = mutation.name
            val mutate = mutation.apply
            RuntimeEnvironment.setQualifiers("sw${widthDp}dp-w${widthDp}dp-h640dp-port-xxhdpi")
            val activity = buildHost()
            val root = activity.findViewById<ViewGroup>(R.id.root)
            val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
            val bGoto = activity.findViewById<GotoButton>(R.id.bGoto)
            activity.findViewById<TextView>(R.id.bVersion).text = "TB"

            mutate(activity)

            val widthPx = px(widthDp)
            fun layOut() {
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(px(640), View.MeasureSpec.EXACTLY),
                )
                root.layout(0, 0, widthPx, px(640))
            }

            val oneLiners = mutableListOf<String>()
            val truncated = mutableListOf<String>()
            for (ref in mutation.refs) {
                bGoto.text = ref
                layOut(); layOut()
                val layout = bGoto.layout
                if (layout != null && (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }) {
                    truncated += ref
                } else if ((layout?.lineCount ?: 0) <= 1) {
                    oneLiners += ref
                }
            }

            bGoto.text = mutation.sample
            layOut(); layOut()
            val bitmap = Bitmap.createBitmap(toolbar.width, toolbar.height, Bitmap.Config.ARGB_8888)
            toolbar.draw(Canvas(bitmap))

            Variant(
                name,
                bitmap,
                bGoto.width - bGoto.paddingLeft - bGoto.paddingRight,
                mutation.refs,
                oneLiners,
                truncated,
                mutation.sample,
                collectSlots(
                    activity,
                    activity.resources.getDimensionPixelSize(R.dimen.nav_prevnext_width) -
                        activity.resources.getDimensionPixelSize(R.dimen.nav_goto_side_margin),
                ),
            )
        }
    }

    /** Widths the version changer would take if it were sized to its content. */
    private fun measureVersionChangerContentWidths(widthDp: Int): List<Pair<String, Int>> {
        RuntimeEnvironment.setQualifiers("sw${widthDp}dp-w${widthDp}dp-h640dp-port-xxhdpi")
        val activity = buildHost()
        val root = activity.findViewById<ViewGroup>(R.id.root)
        val bVersion = activity.findViewById<TextView>(R.id.bVersion)
        activity.findViewById<GotoButton>(R.id.bGoto).text = "Kejadian 1"
        bVersion.layoutParams = bVersion.layoutParams.apply { width = ViewGroup.LayoutParams.WRAP_CONTENT }

        return VERSION_INITIALS.map { initials ->
            bVersion.text = initials
            repeat(2) {
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(px(widthDp), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(px(640), View.MeasureSpec.EXACTLY),
                )
                root.layout(0, 0, px(widthDp), px(640))
            }
            initials to bVersion.width
        }
    }

    private fun shrinkVersion(a: ToolbarAuditHostActivity, dpWidth: Int) {
        val v = a.findViewById<TextView>(R.id.bVersion)
        v.layoutParams = v.layoutParams.apply { width = px(dpWidth) }
    }

    private fun resizeArrows(a: ToolbarAuditHostActivity, arrowDp: Int, marginDp: Int) {
        for (id in intArrayOf(R.id.bLeft, R.id.bRight)) {
            val v = a.findViewById<View>(id)
            v.layoutParams = v.layoutParams.apply { width = px(arrowDp) }
        }
        val goto = a.findViewById<GotoButton>(R.id.bGoto)
        goto.layoutParams = (goto.layoutParams as ViewGroup.MarginLayoutParams).apply {
            marginStart = px(marginDp)
            marginEnd = px(marginDp)
        }
    }

    private fun dropArrows(a: ToolbarAuditHostActivity) {
        a.findViewById<View>(R.id.bLeft).visibility = View.GONE
        a.findViewById<View>(R.id.bRight).visibility = View.GONE
        val goto = a.findViewById<GotoButton>(R.id.bGoto)
        goto.layoutParams = (goto.layoutParams as ViewGroup.MarginLayoutParams).apply {
            marginStart = 0
            marginEnd = 0
        }
    }

    private fun hideMenuItem(a: ToolbarAuditHostActivity, itemId: Int) {
        a.findViewById<Toolbar>(R.id.toolbar).menu.findItem(itemId)?.isVisible = false
    }

    // --- Redraws and resizes that keep every control -----------------------

    /**
     * Pushes the chapter chevrons into the outer strip of their own boxes and
     * lets the reference span what is left. The arrows keep their full
     * rectangles, so nothing moves for touch: only the glyph and the text box
     * change.
     */
    private fun chevronsFlush(a: ToolbarAuditHostActivity, marginDp: Int) {
        val arrowPx = a.findViewById<View>(R.id.bLeft).layoutParams.width
        val glyphStripPx = arrowPx - px(marginDp)

        val goto = a.findViewById<GotoButton>(R.id.bGoto)
        goto.layoutParams = (goto.layoutParams as ViewGroup.MarginLayoutParams).apply {
            marginStart = px(marginDp)
            marginEnd = px(marginDp)
        }
        a.findViewById<ImageButton>(R.id.bLeft).setPadding(0, 0, glyphStripPx, 0)
        a.findViewById<ImageButton>(R.id.bRight).setPadding(glyphStripPx, 0, 0, 0)
    }

    /** A 48dp drawer button in place of the action bar's 56dp one. */
    private fun compactNavButton(a: ToolbarAuditHostActivity, dpWidth: Int) {
        val toolbar = a.findViewById<Toolbar>(R.id.toolbar)
        val nav = toolbar.children.filterIsInstance<ImageButton>().first()
        nav.minimumWidth = px(dpWidth)
        nav.layoutParams = nav.layoutParams.apply { width = px(dpWidth) }
        toolbar.contentInsetStartWithNavigation = px(dpWidth)
        toolbar.setContentInsetsRelative(px(dpWidth), toolbar.contentInsetEnd)
    }

    /** Folds the version changer into the reference as an inline chip. */
    private fun foldVersionIntoReference(a: ToolbarAuditHostActivity) {
        a.findViewById<TextView>(R.id.bVersion).visibility = View.GONE
    }

    /**
     * Redraws the search icon at [dpSize] so the action item stops being wider
     * than its 48dp slot. The asset that ships is 32dp, which is where the
     * item's extra 8dp comes from.
     */
    private fun resizeSearchIcon(a: ToolbarAuditHostActivity, dpSize: Int) {
        val item = a.findViewById<Toolbar>(R.id.toolbar).menu.findItem(R.id.menuSearch) ?: return
        val src = ContextCompat.getDrawable(a, R.drawable.ic_menu_search) ?: return
        val full = Bitmap.createBitmap(src.intrinsicWidth, src.intrinsicHeight, Bitmap.Config.ARGB_8888)
        src.setBounds(0, 0, src.intrinsicWidth, src.intrinsicHeight)
        src.draw(Canvas(full))
        val scaled = Bitmap.createScaledBitmap(full, px(dpSize), px(dpSize), true).apply {
            density = a.resources.displayMetrics.densityDpi
        }
        item.icon = BitmapDrawable(a.resources, scaled)
    }

    private fun condensedReference(a: ToolbarAuditHostActivity) {
        a.findViewById<GotoButton>(R.id.bGoto).typeface =
            Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    }

    private fun spaceSavingPackage(a: ToolbarAuditHostActivity) {
        chevronsFlush(a, 24)
        compactNavButton(a, 48)
        shrinkVersion(a, 48)
        resizeSearchIcon(a, 24)
    }

    /**
     * The narrowest exact width the action menu can be given while every item
     * still measures at least [MIN_TOUCH_DP], reported with the width the menu
     * takes when left alone.
     */
    private fun probeMenuWidths(widthDp: Int): Pair<Int, Int> {
        fun menuAt(forcedDp: Int?): Pair<Int, Int> {
            RuntimeEnvironment.setQualifiers("sw${widthDp}dp-w${widthDp}dp-h640dp-port-xxhdpi")
            val activity = buildHost()
            val root = activity.findViewById<ViewGroup>(R.id.root)
            val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
            activity.findViewById<GotoButton>(R.id.bGoto).text = "Kejadian 1"
            if (forcedDp != null) {
                val menuView = toolbar.children.filterIsInstance<ActionMenuView>().first()
                menuView.layoutParams = menuView.layoutParams.apply { width = px(forcedDp) }
            }
            repeat(2) {
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(px(widthDp), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(px(640), View.MeasureSpec.EXACTLY),
                )
                root.layout(0, 0, px(widthDp), px(640))
            }
            val menuView = toolbar.children.filterIsInstance<ActionMenuView>().first()
            val items = menuView.children.filter { it.visibility != View.GONE }.toList()
            val smallest = items.minOfOrNull { it.width } ?: 0
            return menuView.width to smallest
        }

        val (naturalWidth, _) = menuAt(null)
        var narrowest = naturalWidth
        for (candidate in 88..naturalWidth / px(1) step 4) {
            val (_, smallest) = menuAt(candidate)
            if (dp(smallest) >= MIN_TOUCH_DP) {
                narrowest = px(candidate)
                break
            }
        }
        return naturalWidth to narrowest
    }

    private fun redrawMutations() = listOf(
        Mutation("as shipped") {},
        Mutation("chevrons drawn flush outward (margin 24dp)") { a -> chevronsFlush(a, 24) },
        Mutation("drawer button 48dp instead of 56dp") { a -> compactNavButton(a, 48) },
        Mutation("version changer sized to its content") { a -> shrinkVersion(a, 48) },
        Mutation("search icon redrawn at 24dp like the rest") { a -> resizeSearchIcon(a, 24) },
        Mutation("all four together") { a -> spaceSavingPackage(a) },
        Mutation("all four, reference in a condensed face") { a ->
            spaceSavingPackage(a); condensedReference(a)
        },
        Mutation("version folded into the reference as a chip", refs = MERGED, sample = "Kejadian 1 · TB") { a ->
            spaceSavingPackage(a); foldVersionIntoReference(a)
        },
    )

    private fun measureRedraws(widthDp: Int, mutations: List<Mutation>): List<Variant> =
        measureVariants(widthDp, mutations)

    /**
     * The smallest type size at which each reference still fits on one line in
     * the given box, floored at [minSp] so the answer stays legible.
     */
    private fun typeSizeToFitOneLine(widthDp: Int, mutate: (ToolbarAuditHostActivity) -> Unit): List<Triple<String, Float, Boolean>> {
        RuntimeEnvironment.setQualifiers("sw${widthDp}dp-w${widthDp}dp-h640dp-port-xxhdpi")
        val activity = buildHost()
        val root = activity.findViewById<ViewGroup>(R.id.root)
        val bGoto = activity.findViewById<GotoButton>(R.id.bGoto)
        activity.findViewById<TextView>(R.id.bVersion).text = "TB"
        mutate(activity)

        fun layOut() {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(px(widthDp), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(px(640), View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, px(widthDp), px(640))
        }

        val minSp = 11f
        val baseSp = 16f
        return REFERENCES.map { ref ->
            bGoto.text = ref
            layOut(); layOut()
            val avail = (bGoto.width - bGoto.paddingLeft - bGoto.paddingRight).toFloat()
            val natural = bGoto.paint.measureText(ref)
            val neededSp = if (natural <= avail) baseSp else baseSp * avail / natural
            Triple(ref, neededSp, neededSp >= minSp)
        }
    }

    @Test
    fun `explain why the drawer and search buttons are wider than the rest`() {
        val outputDir = resolveOutputDir()
        outputDir.mkdirs()

        RuntimeEnvironment.setQualifiers("sw360dp-w360dp-h640dp-port-xxhdpi")
        val activity = buildHost()
        val root = activity.findViewById<ViewGroup>(R.id.root)
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        activity.findViewById<GotoButton>(R.id.bGoto).text = "Kejadian 1"
        repeat(2) {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(px(360), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(px(640), View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, px(360), px(640))
        }

        val navButton = toolbar.children.filterIsInstance<ImageButton>().first()
        val menuView = toolbar.children.filterIsInstance<ActionMenuView>().first()
        val items = menuView.children.filter { it.visibility != View.GONE }.toList()

        val md = buildString {
            appendLine("# Why the drawer and search buttons are wider than the rest")
            appendLine()
            appendLine("Measured at 360 dp, xxhdpi.")
            appendLine()
            appendLine("## The drawer button")
            appendLine()
            appendLine(
                "It is ${dpStr(navButton.width)} dp wide around a ${dpStr(toolbar.navigationIcon?.intrinsicWidth ?: 0)} dp icon, " +
                    "because `Base.Widget.AppCompat.Toolbar.Button.Navigation` sets `android:minWidth` to a literal 56dp. " +
                    "That is not `?actionBarSize`: on an sw600dp screen the bar is ${dpStr(px(64))} dp tall and the button is " +
                    "still 56 dp. Nothing about the icon or the bar height asks for it."
            )
            appendLine()
            appendLine("## The action menu items")
            appendLine()
            appendLine(
                "`Widget.AppCompat.ActionButton` sets `minWidth` to 48dp and 12dp of padding on each side, so an item is " +
                    "`max(48dp, icon + 24dp)`. An item is only wider than 48 dp when its icon asset is wider than 24 dp."
            )
            appendLine()
            appendLine("| item | icon | padding | width | 48dp + what |")
            appendLine("| --- | ---: | ---: | ---: | --- |")
            for (item in items) {
                val icon = iconWidthOf(item)
                val pad = item.paddingLeft + item.paddingRight
                val why = if (item.width > px(48)) "icon is ${dpStr(icon)}dp, over the 24dp the slot is built for" else "icon fits the 48dp minimum"
                appendLine(
                    "| ${(item as? MenuView.ItemView)?.itemData?.title} | ${dpStr(icon)} dp | ${dpStr(pad)} dp " +
                        "| ${dpStr(item.width)} dp | $why |"
                )
            }
        }
        File(outputDir, "control-widths.md").writeText(md)
        println(md)

        assertEquals(
            "the drawer button is sized by AppCompat's minWidth, not by its icon",
            px(56),
            navButton.width,
        )
        for (item in items) {
            assertEquals(
                "an action item is max(48dp, icon + horizontal padding)",
                max(px(48), iconWidthOf(item) + item.paddingLeft + item.paddingRight),
                item.width,
            )
        }
    }

    @Test
    fun `measure the redraws and resizes that keep every control in the bar`() {
        val outputDir = resolveOutputDir()
        outputDir.mkdirs()

        val variants = measureRedraws(360, redrawMutations())
        val baseline = variants.first().referenceBoxPx
        val narrow = measureRedraws(320, listOf(
            Mutation("as shipped (320dp)") {},
            Mutation("all four together") { a -> spaceSavingPackage(a) },
            Mutation("version folded into the reference as a chip", refs = MERGED, sample = "Kejadian 1 · TB") { a ->
                spaceSavingPackage(a); foldVersionIntoReference(a)
            },
        ))
        val narrowBaseline = narrow.first().referenceBoxPx

        val (naturalMenuPx, narrowestMenuPx) = probeMenuWidths(360)
        println("action menu: natural ${dpStr(naturalMenuPx)}dp, narrowest keeping 48dp items ${dpStr(narrowestMenuPx)}dp")

        val shippedTypeSizes = typeSizeToFitOneLine(360) {}
        val packagedTypeSizes = typeSizeToFitOneLine(360) { a -> spaceSavingPackage(a) }

        val margin = 90f
        val canvasWidth = 1980

        fun drawRow(c: Canvas, v: Variant, base: Int, yIn: Float): Float {
            var y = yIn
            val gain = v.referenceBoxPx - base
            val gainText = when {
                gain > 0 -> "+${dpStr(gain)}dp"
                gain < 0 -> "${dpStr(gain)}dp"
                else -> "baseline"
            }
            c.drawText(v.name, margin, y, paint(INK, textSize = 26f, face = monoBold))
            c.drawText(
                "text box ${dpStr(v.referenceBoxPx)}dp   ($gainText)",
                margin + 1100f, y, paint(if (gain > 0) OK else INK_DIM, textSize = 24f, face = monoBold),
            )
            y += 16f
            c.drawBitmap(v.toolbar, margin, y, null)
            c.drawRect(
                margin - 1f, y - 1f, margin + v.toolbar.width + 1f, y + v.toolbar.height + 1f,
                paint(INK, stroke = 2f),
            )
            val refSlot = v.slots.first { it.key == "reference" }
            val others = v.slots.filter { it.key != "reference" }
            val smallest = others.minByOrNull { it.touchWidth }
            c.drawText(
                "${v.oneLiners.size}/${v.refs.size} fit on one line" +
                    (if (v.truncated.isEmpty()) ", nothing truncated" else ", truncated ${v.truncated.size}/${v.refs.size}"),
                margin + 1100f, y + 30f,
                paint(if (v.truncated.isEmpty()) OK else ALERT, textSize = 20f),
            )
            c.drawText(
                "reference tap ${dpStr(refSlot.touchWidth)}dp",
                margin + 1100f, y + 56f, paint(INK_DIM, textSize = 20f),
            )
            c.drawText(
                "smallest other control ${dpStr(smallest?.touchWidth ?: 0)}dp (${smallest?.short})",
                margin + 1100f, y + 82f,
                paint(if (dp(smallest?.touchWidth ?: 0) >= MIN_TOUCH_DP) OK else ALERT, textSize = 20f),
            )
            y += v.toolbar.height + 24f
            c.drawLine(margin, y, canvasWidth - margin, y, dashed(GRID_MAJOR, 2f))
            return y + 40f
        }

        fun render(c: Canvas): Float {
            var y = 110f
            c.drawText("REDRAWING AND RESIZING, WITH EVERY CONTROL KEPT", margin, y, paint(INK, textSize = 40f, face = monoBold))
            c.drawText(
                "360dp wide. No control is removed and no text is shortened; only the drawn boxes move.",
                margin, y + 32f, paint(INK_DIM, textSize = 20f),
            )
            c.drawLine(margin, y + 58f, canvasWidth - margin, y + 58f, paint(GRID_MAJOR, stroke = 3f))
            y += 110f

            for (v in variants) y = drawRow(c, v, baseline, y)

            y += 16f
            c.drawText("THE SAME AT 320 dp", margin, y, paint(INK, textSize = 30f, face = monoBold))
            y += 40f
            for (v in narrow) y = drawRow(c, v, narrowBaseline, y)

            y += 16f
            c.drawText(
                "TYPE SIZE NEEDED TO KEEP A REFERENCE ON ONE LINE (16sp today, 11sp floor)",
                margin, y, paint(INK, textSize = 26f, face = monoBold),
            )
            y += 32f
            for (i in REFERENCES.indices) {
                val (ref, shippedSp, shippedOk) = shippedTypeSizes[i]
                val (_, packedSp, packedOk) = packagedTypeSizes[i]
                c.drawText(
                    "  \"$ref\"".padEnd(26) +
                        "as shipped ${String.format("%4.1f", shippedSp)}sp ${if (shippedOk) " " else "(too small)"}".padEnd(30) +
                        "redrawn ${String.format("%4.1f", packedSp)}sp ${if (packedOk) "" else "(too small)"}",
                    margin, y, paint(if (packedOk) OK else ALERT, textSize = 20f),
                )
                y += 26f
            }
            return y
        }

        val probe = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        val height = (render(probe) + 40f).toInt()
        val sheet = Bitmap.createBitmap(canvasWidth, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        drawGrid(c, canvasWidth, height)
        render(c)

        File(outputDir, "redraws.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val md = buildString {
            appendLine("# Redraws and resizes that keep every control")
            appendLine()
            appendLine("360 dp wide. No control is removed and no text is shortened.")
            appendLine()
            appendLine("| change | reference text box | vs shipped | reference tap | smallest other tap | fit on one line | truncated |")
            appendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: |")
            for (v in variants) {
                val gain = v.referenceBoxPx - baseline
                val refSlot = v.slots.first { it.key == "reference" }
                val smallest = v.slots.filter { it.key != "reference" }.minByOrNull { it.touchWidth }
                appendLine(
                    "| ${v.name} | ${dpStr(v.referenceBoxPx)} dp | ${if (gain > 0) "+" else ""}${dpStr(gain)} dp " +
                        "| ${dpStr(refSlot.touchWidth)} dp | ${dpStr(smallest?.touchWidth ?: 0)} dp (${smallest?.short}) " +
                        "| ${v.oneLiners.size}/${v.refs.size} | ${v.truncated.size}/${v.refs.size} |"
                )
            }
            appendLine()
            appendLine("## The same at 320 dp")
            appendLine()
            appendLine("| change | reference text box | vs shipped | fit on one line | truncated |")
            appendLine("| --- | ---: | ---: | ---: | ---: |")
            for (v in narrow) {
                val gain = v.referenceBoxPx - narrowBaseline
                appendLine(
                    "| ${v.name} | ${dpStr(v.referenceBoxPx)} dp | ${if (gain > 0) "+" else ""}${dpStr(gain)} dp " +
                        "| ${v.oneLiners.size}/${v.refs.size} | ${v.truncated.size}/${v.refs.size} |"
                )
            }
            appendLine()
            appendLine("## The action menu's floor")
            appendLine()
            appendLine(
                "The action menu takes ${dpStr(naturalMenuPx)} dp for its two items, and forcing " +
                    "`ActionMenuView` narrower demotes an item rather than tightening it: the narrowest exact " +
                    "width that still leaves every item at ${MIN_TOUCH_DP.toInt()} dp is ${dpStr(narrowestMenuPx)} dp. " +
                    "That floor is set by the icons, not by the menu. `Widget.AppCompat.ActionButton` makes an item " +
                    "`max(48dp, icon + 24dp)`, and the search asset is 32dp where the audio asset is 24dp. Redrawing " +
                    "the search icon at 24dp moves the floor to 96 dp; see control-widths.md."
            )
            appendLine()
            appendLine("## Type size needed to keep a reference on one line")
            appendLine()
            appendLine("16 sp today. Anything under 11 sp is treated as too small to ship.")
            appendLine()
            appendLine("| reference | as shipped | after the redraws |")
            appendLine("| --- | ---: | ---: |")
            for (i in REFERENCES.indices) {
                val (ref, shippedSp, shippedOk) = shippedTypeSizes[i]
                val (_, packedSp, packedOk) = packagedTypeSizes[i]
                appendLine(
                    "| $ref | ${String.format("%.1f", shippedSp)} sp${if (shippedOk) "" else " (too small)"} " +
                        "| ${String.format("%.1f", packedSp)} sp${if (packedOk) "" else " (too small)"} |"
                )
            }
        }
        File(outputDir, "redraws.md").writeText(md)
        println(md)

        val packaged = variants.first { it.name == "all four together" }
        assertTrue(
            "redrawing alone must widen the reference",
            packaged.referenceBoxPx > baseline,
        )
        assertTrue(
            "no control may drop below the minimum touch target",
            packaged.slots.filter { it.key != "reference" }.all { dp(it.touchWidth) >= MIN_TOUCH_DP },
        )
    }

    @Test
    fun `measure what each candidate change buys the verse reference at 360dp`() {
        val outputDir = resolveOutputDir()
        outputDir.mkdirs()

        val variants = measureVariants(360, mutationsAt360())
        val baseline = variants.first().referenceBoxPx
        val narrowVariants = measureVariants(320, mutationsAt320())
        val narrowBaseline = narrowVariants.first().referenceBoxPx
        val versionWidths = measureVersionChangerContentWidths(360)

        val margin = 90f
        val canvasWidth = 1980

        fun render(c: Canvas): Float {
            var y = 110f
            c.drawText("WHAT EACH CHANGE BUYS THE VERSE REFERENCE", margin, y, paint(INK, textSize = 40f, face = monoBold))
            c.drawText(
                "same measurement harness, 360dp wide, reference \"Kejadian 1\" - the figure is the text box inside the reference button",
                margin, y + 32f, paint(INK_DIM, textSize = 20f),
            )
            c.drawLine(margin, y + 58f, canvasWidth - margin, y + 58f, paint(GRID_MAJOR, stroke = 3f))
            y += 110f

            for (v in variants) {
                val gain = v.referenceBoxPx - baseline
                val gainText = when {
                    gain > 0 -> "+${dpStr(gain)}dp"
                    gain < 0 -> "${dpStr(gain)}dp"
                    else -> "baseline"
                }
                c.drawText(v.name, margin, y, paint(INK, textSize = 26f, face = monoBold))
                c.drawText(
                    "text box ${dpStr(v.referenceBoxPx)}dp   ($gainText)",
                    margin + 1100f, y, paint(if (gain > 0) OK else INK_DIM, textSize = 24f, face = monoBold),
                )
                y += 16f
                c.drawBitmap(v.toolbar, margin, y, null)
                c.drawRect(
                    margin - 1f, y - 1f, margin + v.toolbar.width + 1f, y + v.toolbar.height + 1f,
                    paint(INK, stroke = 2f),
                )
                val notesX = margin + 1100f
                c.drawText(
                    "${v.oneLiners.size}/${v.refs.size} sample references fit on one line",
                    notesX, y + 34f, paint(INK_DIM, textSize = 20f),
                )
                c.drawText(
                    if (v.truncated.isEmpty()) "nothing truncated" else "truncated: ${v.truncated.size}/${v.refs.size}",
                    notesX, y + 62f, paint(if (v.truncated.isEmpty()) OK else ALERT, textSize = 20f),
                )
                y += v.toolbar.height + 24f
                c.drawLine(margin, y, canvasWidth - margin, y, dashed(GRID_MAJOR, 2f))
                y += 40f
            }

            y += 16f
            c.drawText("AND AT 320 dp, WHERE THE ARROWS SHRINK TO 32dp", margin, y, paint(INK, textSize = 30f, face = monoBold))
            y += 40f
            for (v in narrowVariants) {
                val gain = v.referenceBoxPx - narrowBaseline
                val gainText = when {
                    gain > 0 -> "+${dpStr(gain)}dp"
                    gain < 0 -> "${dpStr(gain)}dp"
                    else -> "baseline"
                }
                c.drawText(v.name, margin, y, paint(INK, textSize = 26f, face = monoBold))
                c.drawText(
                    "text box ${dpStr(v.referenceBoxPx)}dp   ($gainText)",
                    margin + 1100f, y, paint(if (gain > 0) OK else if (gain < 0) ALERT else INK_DIM, textSize = 24f, face = monoBold),
                )
                y += 16f
                c.drawBitmap(v.toolbar, margin, y, null)
                c.drawRect(
                    margin - 1f, y - 1f, margin + v.toolbar.width + 1f, y + v.toolbar.height + 1f,
                    paint(INK, stroke = 2f),
                )
                c.drawText(
                    "${v.oneLiners.size}/${v.refs.size} sample references fit on one line",
                    margin + 1100f, y + 34f, paint(INK_DIM, textSize = 20f),
                )
                c.drawText(
                    if (v.truncated.isEmpty()) "nothing truncated" else "truncated: ${v.truncated.size}/${v.refs.size}",
                    margin + 1100f, y + 62f, paint(if (v.truncated.isEmpty()) OK else ALERT, textSize = 20f),
                )
                y += v.toolbar.height + 24f
                c.drawLine(margin, y, canvasWidth - margin, y, dashed(GRID_MAJOR, 2f))
                y += 40f
            }

            y += 16f
            c.drawText(
                "VERSION CHANGER SIZED TO ITS CONTENT (it is a fixed 72dp today)",
                margin, y, paint(INK, textSize = 26f, face = monoBold),
            )
            y += 32f
            for ((initials, wPx) in versionWidths) {
                val saved = px(72) - wPx
                c.drawText(
                    "  \"$initials\"".padEnd(14) + "wants ${dpStr(wPx)}dp" +
                        (if (saved > 0) "   frees ${dpStr(saved)}dp" else "   needs ${dpStr(-saved)}dp more"),
                    margin, y, paint(if (saved > 0) OK else ALERT, textSize = 20f),
                )
                y += 26f
            }
            return y
        }

        val probe = Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        val height = (render(probe) + 40f).toInt()
        val sheet = Bitmap.createBitmap(canvasWidth, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        drawGrid(c, canvasWidth, height)
        render(c)

        val file = File(outputDir, "options.png")
        file.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val md = buildString {
            appendLine("# What each candidate change buys at 360 dp")
            appendLine()
            appendLine("| change | reference text box | vs shipped | fit on one line | truncated |")
            appendLine("| --- | ---: | ---: | ---: | ---: |")
            for (v in variants) {
                val gain = v.referenceBoxPx - baseline
                appendLine(
                    "| ${v.name} | ${dpStr(v.referenceBoxPx)} dp | ${if (gain > 0) "+" else ""}${dpStr(gain)} dp " +
                        "| ${v.oneLiners.size}/${v.refs.size} | ${v.truncated.size}/${v.refs.size} |"
                )
            }
            appendLine()
            appendLine("## At 320 dp, where the chapter arrows shrink to 32 dp")
            appendLine()
            appendLine("| change | reference text box | vs shipped | fit on one line | truncated |")
            appendLine("| --- | ---: | ---: | ---: | ---: |")
            for (v in narrowVariants) {
                val gain = v.referenceBoxPx - narrowBaseline
                appendLine(
                    "| ${v.name} | ${dpStr(v.referenceBoxPx)} dp | ${if (gain > 0) "+" else ""}${dpStr(gain)} dp " +
                        "| ${v.oneLiners.size}/${v.refs.size} | ${v.truncated.size}/${v.refs.size} |"
                )
            }
            appendLine()
            appendLine("## Version changer sized to its content (a fixed 72 dp today)")
            appendLine()
            appendLine("| initials | wrap_content width | frees |")
            appendLine("| --- | ---: | ---: |")
            for ((initials, wPx) in versionWidths) {
                appendLine("| $initials | ${dpStr(wPx)} dp | ${dpStr(px(72) - wPx)} dp |")
            }
        }
        File(outputDir, "options.md").writeText(md)
        println(md)

        assertTrue(
            "shrinking the fixed chrome must widen the reference button",
            variants.last().referenceBoxPx > baseline,
        )
    }
}
