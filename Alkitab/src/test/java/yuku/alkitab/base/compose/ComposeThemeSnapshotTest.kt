package yuku.alkitab.base.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
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
import yuku.alkitab.base.audio.ui.AudioBar
import yuku.alkitab.base.audio.ui.AudioBarUiState
import yuku.alkitab.base.compose.goto.GotoScreen
import yuku.alkitab.base.compose.goto.GotoViewModel
import yuku.alkitab.base.compose.goto.GridStage
import yuku.alkitab.base.compose.goto.GridTab
import yuku.alkitab.base.compose.sync.SyncLoginScreen
import yuku.alkitab.model.Book

/**
 * Screenshot report for the Compose theme: renders the app's real Compose
 * surfaces (Goto screen, grid tab stages, sync login, audio bar) plus a
 * widget gallery, side by side under the dynamic dark scheme (which follows
 * the device wallpaper, so it looks different on every phone) and under
 * [BibleAppTheme]'s branded [BibleAppDarkColorScheme].
 *
 * Like [yuku.alkitab.base.verses.ReaderSideBySideSnapshotTest], this is a
 * report generator for human inspection, not a pixel-diff: it writes PNGs
 * plus a self-contained `index.html` (images embedded as data URIs) under
 * `Alkitab/build/snapshots/compose-theme/`. The dynamic column renders with
 * Robolectric's system tonal palette — one example of the many palettes
 * devices produce.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeThemeSnapshotTest {

    private val WIDTH_PX = 400

    @Before
    fun setUp() {
        AfwApp.initWithAppContext(ApplicationProvider.getApplicationContext())
    }

    private class Case(
        val name: String,
        val description: String,
        val heightPx: Int,
        /**
         * True for surfaces that apply [BibleAppTheme] themselves (the audio
         * bar), where an outer test-supplied theme cannot reach: the dynamic
         * column would misleadingly show the branded palette, so it is left
         * empty.
         */
        val selfThemed: Boolean = false,
        val content: @Composable () -> Unit,
    )

    private fun buildCases(): List<Case> = listOf(
        Case(
            name = "widget-gallery",
            description = "Material 3 widget roundup: buttons, selection controls, slider, chips, text field, tabs, progress, plus the scheme's role swatches.",
            heightPx = 1010,
        ) { ThemeGallery() },
        Case(
            name = "goto-screen-dialer",
            description = "The full Goto screen on its dialer tab: top bar with tabs, book dropdown field, chapter/verse fields, keypad, OK button.",
            heightPx = 720,
        ) {
            GotoScreen(
                initialBookId = 0,
                initialChapter_1 = 1,
                initialVerse_1 = 1,
                onUp = {},
                onGotoFinished = { _, _, _, _ -> },
            )
        },
        Case(
            name = "goto-grid-books",
            description = "Grid tab, book stage: 66 book tiles with per-book colors on the tile surface.",
            heightPx = 620,
        ) {
            ThemedBackground {
                GridTab(
                    askForVerse = true,
                    viewModel = remember { GotoViewModel() },
                    onGotoFinished = { _, _, _, _ -> },
                )
            }
        },
        Case(
            name = "goto-grid-chapters",
            description = "Grid tab, chapter stage: numeric tiles plus the book assist chip.",
            heightPx = 620,
        ) {
            ThemedBackground {
                GridTab(
                    askForVerse = true,
                    viewModel = remember {
                        GotoViewModel().apply { gridStage = GridStage.Chapters(sampleBook()) }
                    },
                    onGotoFinished = { _, _, _, _ -> },
                )
            }
        },
        Case(
            name = "sync-login",
            description = "Sync login screen: top app bar, segmented mode buttons, text fields, primary action button, links.",
            heightPx = 860,
        ) {
            SyncLoginScreen(
                onUp = {},
                onLogin = { _, _, _ -> },
                onRegister = { _, _, _ -> },
                onForgotPassword = { _, _ -> },
                onChangePassword = { _, _, _, _ -> },
                onOpenSyncLog = {},
            )
        },
        Case(
            name = "audio-bar",
            description = "Audio bar while playing: play/pause, verse navigation, speed and recording buttons, slider with time labels.",
            heightPx = 170,
            selfThemed = true,
        ) {
            AudioBar(
                state = AudioBarUiState(
                    visible = true,
                    isPlaying = true,
                    preparing = false,
                    positionMs = 42_000L,
                    durationMs = 195_000L,
                    verse_1 = 7,
                    speed = 1.0f,
                    error = null,
                    timingAvailable = true,
                    logs = emptyList(),
                    showLogSheet = false,
                    playingVersionId = "preset/in-tb",
                    setTitle = "Alkitab Suara",
                    pickerOptions = null,
                    showSpeedSheet = false,
                    setGroups = null,
                ),
                onCommand = {},
                modifier = Modifier,
            )
        },
    )

    private fun sampleBook(): Book = Book().apply {
        bookId = 18 // Psalms, for a distinctly colored assist chip
        shortName = "Psalms"
        chapter_count = 150
        verse_counts = IntArray(150) { 20 }
        abbreviation = "Psa"
    }

    // --- Content helpers ---------------------------------------------------

    /** Paints the scheme background behind tabs that expect a Scaffold around them. */
    @Composable
    private fun ThemedBackground(content: @Composable () -> Unit) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            content()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ThemeGallery() {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Title on background", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Primary text", color = MaterialTheme.colorScheme.primary)
                    Text("Tertiary text", color = MaterialTheme.colorScheme.tertiary)
                    Text("Variant text", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {}) { Text("Filled") }
                    FilledTonalButton(onClick = {}) { Text("Tonal") }
                    OutlinedButton(onClick = {}) { Text("Outlined") }
                    TextButton(onClick = {}) { Text("Text") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = true, onCheckedChange = null)
                    Checkbox(checked = false, onCheckedChange = null)
                    RadioButton(selected = true, onClick = null)
                    RadioButton(selected = false, onClick = null)
                    Switch(checked = true, onCheckedChange = null)
                    Switch(checked = false, onCheckedChange = null)
                }
                Slider(value = 0.4f, onValueChange = {})
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = true, onClick = {}, label = { Text("1.5×") })
                    FilterChip(selected = false, onClick = {}, label = { Text("1×") })
                    AssistChip(onClick = {}, label = { Text("Psalms") })
                }
                OutlinedTextField(value = "Genesis", onValueChange = {}, label = { Text("Book") })
                LinearProgressIndicator(progress = { 0.6f }, modifier = Modifier.fillMaxWidth())
                PrimaryTabRow(selectedTabIndex = 0) {
                    Tab(selected = true, onClick = {}, text = { Text("Dial") })
                    Tab(selected = false, onClick = {}, text = { Text("Type") })
                    Tab(selected = false, onClick = {}, text = { Text("Grid") })
                }
                HorizontalDivider()
                RoleSwatchRow(
                    "primary" to MaterialTheme.colorScheme.primary,
                    "primaryCont" to MaterialTheme.colorScheme.primaryContainer,
                    "secondary" to MaterialTheme.colorScheme.secondary,
                    "secondaryCont" to MaterialTheme.colorScheme.secondaryContainer,
                )
                RoleSwatchRow(
                    "tertiary" to MaterialTheme.colorScheme.tertiary,
                    "surface" to MaterialTheme.colorScheme.surface,
                    "surfContHigh" to MaterialTheme.colorScheme.surfaceContainerHigh,
                    "surfContHighest" to MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
    }

    @Composable
    private fun RoleSwatchRow(vararg swatches: Pair<String, Color>) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((name, color) in swatches) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
                    Text(name, fontSize = 9.sp, color = if (luminance > 0.5f) Color.Black else Color.White)
                }
            }
        }
    }

    /** The scheme [BibleAppTheme] applies when it follows the device's dynamic colors. */
    @Composable
    private fun DynamicDarkTheme(content: @Composable () -> Unit) {
        val ctx = LocalContext.current
        val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(ctx)
        } else {
            darkColorScheme()
        }
        MaterialTheme(colorScheme = colors, content = content)
    }

    // --- Rendering ----------------------------------------------------------

    private fun idleLoopers() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun renderCase(heightPx: Int, themed: @Composable () -> Unit): Bitmap {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val view = ComposeView(activity)
        view.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                themed()
            }
        }
        activity.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        var rounds = 0
        while (true) {
            idleLoopers()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, WIDTH_PX, heightPx)
            idleLoopers()
            rounds++
            if (view.childCount > 0 && rounds >= 3) break
            assertTrue("compose view never composed its content", rounds < 10)
        }

        val bitmap = Bitmap.createBitmap(WIDTH_PX, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)
        view.draw(canvas)
        return bitmap
    }

    // --- Report plumbing ----------------------------------------------------

    private fun toDataUri(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray())
    }

    private fun savePng(dir: File, name: String, bitmap: Bitmap) {
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun resolveSnapshotDir(): File {
        val override = System.getenv("COMPOSE_THEME_SNAPSHOT_DIR")
        return if (override != null) File(override) else File("build/snapshots/compose-theme")
    }

    @Test
    fun `produce side-by-side snapshots of Compose surfaces under dynamic colors and the branded theme`() {
        val outputDir = resolveSnapshotDir()
        outputDir.mkdirs()

        val html = StringBuilder()
        html.append(
            """
            <!doctype html>
            <html><head><meta charset="utf-8"/><title>Compose theme: dynamic vs branded</title>
            <style>
            body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; margin: 24px; }
            table { border-collapse: collapse; }
            td, th { vertical-align: top; padding: 8px 12px; border-bottom: 1px solid #ddd; }
            th { text-align: left; background: #f4f4f4; position: sticky; top: 0; }
            td.label { width: 280px; }
            td.label .name { font-weight: 600; }
            td.label p { margin: 4px 0 0; color: #444; font-size: 13px; }
            td.note { color: #666; font-size: 13px; max-width: 200px; }
            img { display: block; max-width: 400px; image-rendering: pixelated; border: 1px solid #eee; }
            </style></head>
            <body>
            <h1>Compose surfaces: dynamic colors vs the branded BibleAppTheme</h1>
            <p>Left column: the wallpaper-derived dynamic dark scheme (rendered here with Robolectric's
            system palette — every device produces a different one). Right column: the static
            BibleAppDarkColorScheme built from the app's XML palette (light-blue accent, teal escape,
            blue-gray chrome, gray window), identical on every device.</p>
            <table>
              <thead><tr><th>Case</th><th>Dynamic (before, varies per device)</th><th>Branded BibleAppTheme (after)</th></tr></thead>
              <tbody>
            """.trimIndent()
        )

        for (case in buildCases()) {
            val branded = renderCase(case.heightPx) { BibleAppTheme { case.content() } }
            savePng(outputDir, "${case.name}-branded.png", branded)

            val dynamicCell = if (case.selfThemed) {
                "<td class=\"note\">(applies BibleAppTheme internally — an outer theme cannot reach it, so a \"before\" render is not reproducible here)</td>"
            } else {
                val dynamic = renderCase(case.heightPx) { DynamicDarkTheme { case.content() } }
                savePng(outputDir, "${case.name}-dynamic.png", dynamic)
                "<td><img src=\"${toDataUri(dynamic)}\"/></td>"
            }

            html.append(
                "<tr>" +
                    "<td class=\"label\"><div class=\"name\">${escapeHtml(case.name)}</div><p>${escapeHtml(case.description)}</p></td>" +
                    dynamicCell +
                    "<td><img src=\"${toDataUri(branded)}\"/></td>" +
                    "</tr>\n"
            )
        }

        html.append("</tbody></table></body></html>")

        val indexFile = File(outputDir, "index.html")
        indexFile.writeText(html.toString())

        println("Compose theme report written to: ${indexFile.absolutePath}")
        assertTrue("index.html should exist", indexFile.exists())
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
