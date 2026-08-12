package yuku.alkitab.base.audio.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import yuku.alkitab.base.audio.AudioLogEntry

/**
 * Visual snapshot of the audio bar's load-status UI: the status line above the
 * play button, and the log bottom sheet it opens. Rendered through
 * Robolectric's native graphics pipeline to real PNGs under
 * `build/snapshots/audio-bar/`, the same technique as
 * [yuku.alkitab.base.verses.VerseItemSideBySideSnapshotTest]. Not a pixel-diff
 * regression test; the PNGs are written for a human to eyeball, which is how
 * the status line's width cap and its error-vs-last-log-entry precedence were
 * caught.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AudioBarScreenshotTest {

    private val VIEWPORT_WIDTH_PX = 400

    @Test
    fun `capture audio bar load-status states`() {
        val outputDir = resolveSnapshotDir()
        outputDir.mkdirs()

        save(outputDir, "01-error-status-line", errorState())
        save(outputDir, "02-slow-load-status-line", slowLoadState(), inspectionMode = true)
        save(outputDir, "03-log-bottom-sheet", errorState().copy(showLogSheet = true), settleAnimations = true)
    }

    private fun save(
        outputDir: File,
        name: String,
        state: AudioBarUiState,
        inspectionMode: Boolean = false,
        settleAnimations: Boolean = false,
    ) {
        val activity = buildActivity()
        val composeView = ComposeView(activity)
        attachAndDoFirstLayout(activity, composeView)
        composeView.setContent {
            if (inspectionMode) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    AudioBar(state = state, onCommand = {}, modifier = Modifier)
                }
            } else {
                AudioBar(state = state, onCommand = {}, modifier = Modifier)
            }
        }
        idleLoopers()
        if (settleAnimations) {
            repeat(30) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32)) }
        }
        // ModalBottomSheet renders into its own android.app.Dialog window, not
        // into composeView's own hierarchy. Draw that window's decor view
        // instead when one is showing, or the sheet would be invisible in the
        // captured bitmap. Its Compose content is anchored to the window size
        // it was actually shown at (via `dialog.show()`'s real WindowManager
        // layout pass), so re-measuring it at our own viewport width like
        // measureAndDraw does would shift the sheet's computed offset. Draw it
        // at its already-laid-out size instead.
        val dialog = ShadowDialog.getLatestDialog()
        val bitmap = if (dialog != null && dialog.isShowing) {
            drawAsLaidOut(dialog.window!!.decorView)
        } else {
            measureAndDraw(composeView)
        }
        FileOutputStream(File(outputDir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun errorState(): AudioBarUiState = AudioBarUiState.HIDDEN.copy(
        visible = true,
        isPlaying = false,
        preparing = false,
        positionMs = 12_000L,
        durationMs = 60_000L,
        error = "IO_NETWORK_CONNECTION_FAILED",
        logs = listOf(
            AudioLogEntry(0L, "User pressed play/pause"),
            AudioLogEntry(20L, "Loading Genesis 1"),
            AudioLogEntry(60L, "Bar state: paused to preparing"),
            AudioLogEntry(1_000L, "HTTP request started: https://audio.example/gen1.mp3"),
            AudioLogEntry(1_200L, "DNS lookup started (audio.example)"),
            AudioLogEntry(1_350L, "DNS lookup finished: 93.184.216.34"),
            AudioLogEntry(1_400L, "Connecting to 93.184.216.34:443"),
            AudioLogEntry(2_100L, "Response headers received: HTTP 500"),
            AudioLogEntry(2_150L, "Response body: Internal Server Error: upstream timeout"),
            AudioLogEntry(6_500L, "Connect failed: Unable to resolve host"),
            AudioLogEntry(6_600L, "Player error: IO_NETWORK_CONNECTION_FAILED (Unable to connect)"),
            AudioLogEntry(6_650L, "Bar state: preparing to error"),
        ),
        playingVersionId = "preset/in-tb",
    )

    private fun slowLoadState(): AudioBarUiState = AudioBarUiState.HIDDEN.copy(
        visible = true,
        isPlaying = false,
        preparing = true,
        logs = listOf(
            AudioLogEntry(0L, "Loading Genesis 1"),
            AudioLogEntry(1_000L, "HTTP request started: https://audio.example/gen1.mp3"),
            AudioLogEntry(1_200L, "DNS lookup started (audio.example)"),
            AudioLogEntry(1_350L, "DNS lookup finished: 93.184.216.34"),
            AudioLogEntry(1_400L, "Connecting to 93.184.216.34:443"),
            AudioLogEntry(5_600L, "Waiting for response headers"),
        ),
        playingVersionId = "preset/in-tb",
    )

    private fun resolveSnapshotDir(): File {
        val override = System.getenv("AUDIO_BAR_SNAPSHOT_DIR")
        if (!override.isNullOrBlank()) return File(override)
        val moduleDir = File(System.getProperty("user.dir") ?: ".")
        return File(moduleDir, "build/snapshots/audio-bar")
    }

    private fun buildActivity(): AppCompatActivity {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        return activity
    }

    private fun attachAndDoFirstLayout(activity: AppCompatActivity, view: View) {
        val frame = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setBackgroundColor(AndroidColor.WHITE)
        }
        activity.setContentView(frame)
        idleLoopers()
    }

    private fun measureAndDraw(view: View): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight.coerceAtLeast(1))
        idleLoopers()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight.coerceAtLeast(1))

        val w = view.measuredWidth.coerceAtLeast(1)
        val h = view.measuredHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)
        view.draw(canvas)
        return bitmap
    }

    /** Draws [view] at whatever size it was already measured and laid out to. */
    private fun drawAsLaidOut(view: View): Bitmap {
        val w = view.width.coerceAtLeast(1)
        val h = view.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)
        view.draw(canvas)
        return bitmap
    }

    private fun idleLoopers() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}
