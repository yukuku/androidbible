package yuku.alkitab.versionmanager

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import kotlinx.coroutines.android.asCoroutineDispatcher
import org.junit.After
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
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.model.MVersionDb
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.model.MVersionPreset
import yuku.alkitab.debug.R
import yuku.afw.App as AfwApp

@RunWith(RobolectricTestRunner::class)
@Config(application = AfwApp::class, sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VersionDetailsSheetTest {
    private val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
    private lateinit var activity: AppCompatActivity
    private lateinit var view: ComposeView
    private val frameClock = BroadcastFrameClock()
    private var frameTime = 0L

    @Before
    fun setUp() {
        controller.get().setTheme(R.style.Theme_Alkitab)
        activity = controller.setup().get()
        val frame = FrameLayout(activity)
        activity.setContentView(frame)
        val recomposer = frame.createLifecycleAwareWindowRecomposer(Handler(Looper.getMainLooper()).asCoroutineDispatcher() + frameClock)
        view = ComposeView(activity)
        view.setParentCompositionContext(recomposer)
        frame.addView(view)
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        idle()
    }

    @Test
    fun `installed version keeps footer actions visible below scrollable metadata and routes each action separately`() {
        val version = MVersionDb().apply {
            shortName = "BIMK"
            longName = "Bahasa Indonesia Masa Kini"
            locale = "in"
            filename = "/storage/emulated/0/Android/data/yuku.alkitab.debug/files/addons/in-bimk.yes"
            description = "Long version description. ".repeat(100)
        }
        var removeCount = 0
        var shareCount = 0
        var updateCount = 0
        render(version, { removeCount++ }, { shareCount++ }, null, { updateCount++ })
        val remove = button(R.string.buang_dari_daftar)
        val share = button(R.string.version_menu_share)
        assertTrue(remove.boundsInRoot.left < share.boundsInRoot.left)
        assertTrue(remove.boundsInRoot.bottom <= 400f)
        assertTrue(share.boundsInRoot.bottom <= 400f)
        assertTrue(remove.config[SemanticsActions.OnClick].action?.invoke() == true)
        assertEquals(1, removeCount)
        assertEquals(0, shareCount)
        assertTrue(share.config[SemanticsActions.OnClick].action?.invoke() == true)
        assertEquals(1, shareCount)
        assertEquals(0, updateCount)
        assertTrue(button(R.string.ed_update_button).config[SemanticsActions.OnClick].action?.invoke() == true)
        assertEquals(1, updateCount)
    }

    @Test
    fun `downloadable version retains download without offering removal or sharing`() {
        val version = MVersionPreset().apply { shortName = "BIMK"; longName = "Bahasa Indonesia Masa Kini"; preset_name = "in-bimk" }
        var downloads = 0
        render(version, null, null, { downloads++ }, null)
        assertTrue(button(R.string.ed_download_button).config[SemanticsActions.OnClick].action?.invoke() == true)
        assertEquals(1, downloads)
        assertFalse(hasButton(R.string.buang_dari_daftar))
        assertFalse(hasButton(R.string.version_menu_share))
    }

    @Test
    fun `built in version exposes its information without destructive or file actions`() {
        val version = MVersionInternal().apply { shortName = "DDD"; longName = "Built in version" }
        render(version, null, null, null, null)
        assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "DDD" } == true })
        assertFalse(hasButton(R.string.buang_dari_daftar))
        assertFalse(hasButton(R.string.version_menu_share))
        assertFalse(hasButton(R.string.ed_download_button))
    }

    private fun render(version: MVersion, onRemove: (() -> Unit)?, onShare: (() -> Unit)?, onDownload: (() -> Unit)?, onUpdate: (() -> Unit)?) {
        view.setContent { BibleAppTheme { VersionDetailsContent(version, onRemove, onShare, onDownload, onUpdate) } }
        repeat(4) {
            idle()
            view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 360, 400)
            idle()
        }
    }

    private fun nodes(): List<SemanticsNode> = (view.getChildAt(0) as RootForTest).semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)

    private fun hasButton(label: Int): Boolean = nodes().any { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null && node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == activity.getString(label) } == true
    }

    private fun button(label: Int): SemanticsNode = nodes().first { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null && node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == activity.getString(label) } == true
    }

    private fun idle() {
        Snapshot.sendApplyNotifications()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        frameTime += 16_000_000L
        frameClock.sendFrame(frameTime)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}
