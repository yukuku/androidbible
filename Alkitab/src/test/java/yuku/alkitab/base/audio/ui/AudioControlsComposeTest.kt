package yuku.alkitab.base.audio.ui

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.media3.common.FlagSet
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import yuku.alkitab.debug.R
import yuku.alkitab.songs.MediaController
import yuku.alkitab.songs.SongAudioBar
import yuku.alkitab.songs.SongAudioClip
import yuku.alkitab.songs.SongAudioController
import yuku.alkitab.songs.SongAudioService
import yuku.alkitab.songs.SongPlaybackState

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34], qualifiers = "en-rUS-w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(UnstableApi::class)
class AudioControlsComposeTest {
    @Test
    fun `Bible progress follows paused seeks while transport and long press use existing commands`() {
        val transport = ObservedPlayer()
        val commands = mutableListOf<AudioBarCommand>()
        ComposeHost().use { host ->
            host.show {
                AudioBar(readyBibleState(), commands::add, Modifier, transport.player)
            }
            assertEquals(42_000f, host.slider().config[SemanticsProperties.ProgressBarRangeInfo].current)

            transport.updatePosition(65_000)
            host.settle()
            assertEquals(65_000f, host.slider().config[SemanticsProperties.ProgressBarRangeInfo].current)

            host.click(R.string.audio_bar_play)
            host.click(R.string.audio_bar_prev_verse)
            host.click(R.string.audio_bar_next_verse)
            val playButton = host.described(R.string.audio_bar_play)
            assertTrue(requireNotNull(playButton.config[SemanticsActions.OnLongClick].action).invoke())
            assertEquals(
                listOf(AudioBarCommand.PlayPause, AudioBarCommand.PrevVerse, AudioBarCommand.NextVerse, AudioBarCommand.OpenLogSheet),
                commands,
            )

            assertTrue(requireNotNull(host.slider().config[SemanticsActions.SetProgress].action).invoke(75_000f))
            assertEquals(
                listOf(AudioBarCommand.SeekDrag(75_000L), AudioBarCommand.SeekCommit(75_000L)),
                commands.drop(4),
            )
            verify(exactly = 0) { transport.player.seekTo(any<Long>()) }
            verify(exactly = 0) { transport.player.play() }
            assertTrue(transport.listeners.isNotEmpty())
        }
        assertTrue(transport.listeners.isEmpty())
        verify(exactly = 0) { transport.player.release() }
    }

    @Test
    fun `Bible chapter resolution masks outgoing progress and errors keep retry behavior`() {
        val transport = ObservedPlayer()
        val state = mutableStateOf(readyBibleState())
        val commands = mutableListOf<AudioBarCommand>()
        ComposeHost().use { host ->
            host.show { AudioBar(state.value, commands::add, Modifier, transport.player) }
            state.value = state.value.copy(preparing = true, positionMs = 0L, durationMs = 0L)
            host.settle()
            assertEquals(0f, host.slider().config[SemanticsProperties.ProgressBarRangeInfo].current)
            assertEquals(0f..1f, host.slider().config[SemanticsProperties.ProgressBarRangeInfo].range)
            assertTrue(host.slider().config.contains(SemanticsProperties.Disabled))
            assertTrue(transport.listeners.isEmpty())

            state.value = state.value.copy(preparing = false, error = "no_audio_for_version")
            host.settle()
            host.click(R.string.audio_bar_error_retry)
            assertEquals(listOf(AudioBarCommand.Retry), commands)

            state.value = AudioBarUiState.HIDDEN
            host.settle()
            assertTrue(transport.listeners.isEmpty())
        }
    }

    @Test
    fun `song repeat and playback state update without polling and only the active clip shows progress`() {
        val transport = ObservedPlayer()
        val controller = SongAudioController(mockk(relaxed = true))
        val service = connect(controller, transport.player)
        val clip = SongAudioClip("https://example.com/first.mp3")
        controller.mediaKnownToExist(clip.url)
        var playRequests = 0
        ComposeHost().use { host ->
            host.show { SongAudioBar(clip, controller, { playRequests++ }) }
            assertTrue(host.slider().config.contains(SemanticsProperties.Disabled))
            host.click(R.string.sn_media_repeat)
            host.settle()
            assertTrue(controller.isLooping)
            assertEquals(ToggleableState.On, host.described(R.string.sn_media_repeat).config[SemanticsProperties.ToggleableState])
            host.click(R.string.audio_bar_play)
            assertEquals(1, playRequests)

            controller.setLoop(false)
            host.settle()
            assertEquals(ToggleableState.Off, host.described(R.string.sn_media_repeat).config[SemanticsProperties.ToggleableState])

            controller.state = MediaController.State.paused
            host.settle()
            assertEquals(42_000f, host.slider().config[SemanticsProperties.ProgressBarRangeInfo].current)
            transport.updatePosition(80_000L)
            host.settle()
            assertEquals(80_000f, host.slider().config[SemanticsProperties.ProgressBarRangeInfo].current)

            controller.state = MediaController.State.playing
            host.settle()
            host.click(R.string.audio_bar_pause)
            assertEquals(2, playRequests)

            controller.mediaKnownToExist("https://example.com/second.mp3")
            host.settle()
            assertTrue(host.slider().config.contains(SemanticsProperties.Disabled))
            assertEquals(0f, host.slider().config[SemanticsProperties.ProgressBarRangeInfo].current)
            assertTrue(transport.listeners.isEmpty())
        }
        verify(exactly = 0) { service.stop() }
        verify(exactly = 0) { transport.player.play() }
        controller.reset()
    }

    @Test
    fun `song buffering retains seek controls and service clamping while errors clear progress`() {
        val transport = ObservedPlayer()
        val controller = SongAudioController(mockk(relaxed = true))
        val service = connect(controller, transport.player)
        val clip = SongAudioClip("https://example.com/song.mid")
        controller.mediaKnownToExist(clip.url)
        controller.state = MediaController.State.preparing
        ComposeHost().use { host ->
            host.show { SongAudioBar(clip, controller, {}) }
            assertFalse(host.slider().config.contains(SemanticsProperties.Disabled))
            assertTrue(requireNotNull(host.slider().config[SemanticsActions.SetProgress].action).invoke(90_000f))
            verify(exactly = 1) { service.seekTo(90_000L) }
            verify(exactly = 0) { transport.player.seekTo(any<Long>()) }

            controller.state = MediaController.State.error
            host.settle()
            assertTrue(host.slider().config.contains(SemanticsProperties.Disabled))
            assertEquals(0f, host.slider().config[SemanticsProperties.ProgressBarRangeInfo].current)
            assertTrue(transport.listeners.isEmpty())
        }
        controller.reset()
    }

    private fun readyBibleState() = AudioBarUiState.HIDDEN.copy(
        visible = true,
        durationMs = 120_000L,
        timingAvailable = true,
    )

    private fun connect(controller: SongAudioController, player: Player): SongAudioService {
        val service = mockk<SongAudioService>(relaxed = true)
        every { service.playbackPlayer } returns player
        every { service.playbackState } returns MutableStateFlow(SongPlaybackState.IDLE)
        val connection = SongAudioController::class.java.getDeclaredField("connection").apply { isAccessible = true }
            .get(controller) as ServiceConnection
        connection.onServiceConnected(ComponentName("test", "SongAudioService"), SongAudioService.LocalBinder(service))
        return service
    }

    private class ObservedPlayer {
        val player = mockk<Player>(relaxed = true)
        val listeners = mutableSetOf<Player.Listener>()
        private var positionMs = 42_000L

        init {
            every { player.applicationLooper } returns Looper.getMainLooper()
            every { player.isCommandAvailable(any()) } returns true
            every { player.currentPosition } answers { positionMs }
            every { player.duration } returns 120_000L
            every { player.playbackState } returns Player.STATE_READY
            every { player.addListener(any()) } answers { listeners.add(firstArg()) }
            every { player.removeListener(any()) } answers { listeners.remove(firstArg()) }
        }

        fun updatePosition(positionMs: Long) {
            this.positionMs = positionMs
            val events = Player.Events(FlagSet.Builder().add(Player.EVENT_POSITION_DISCONTINUITY).build())
            listeners.toList().forEach { it.onEvents(player, events) }
        }
    }

    private class ComposeHost : AutoCloseable {
        private val activityController = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        private val activity = activityController.get()
        private val frame = FrameLayout(activity)
        private val view = ComposeView(activity)
        private val clock = BroadcastFrameClock()
        private var frameNumber = 0L

        init {
            activity.setTheme(R.style.Theme_Alkitab)
            activity.setContentView(frame)
            view.setParentCompositionContext(
                frame.createLifecycleAwareWindowRecomposer(Handler(Looper.getMainLooper()).asCoroutineDispatcher() + clock)
            )
            frame.addView(view)
        }

        fun show(content: @Composable () -> Unit) {
            view.setContent(content)
            settle()
        }

        fun settle() {
            repeat(5) {
                Snapshot.sendApplyNotifications()
                shadowOf(Looper.getMainLooper()).idle()
                clock.sendFrame(++frameNumber * 16_000_000L)
                shadowOf(Looper.getMainLooper()).idle()
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, 360, 640)
            }
        }

        private fun nodes() = (view.getChildAt(0) as RootForTest).semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)

        fun slider(): SemanticsNode = nodes().single { it.config.contains(SemanticsActions.SetProgress) }

        fun described(resId: Int): SemanticsNode = nodes().single {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(activity.getString(resId)) == true
        }

        fun click(resId: Int) {
            assertTrue(requireNotNull(described(resId).config[SemanticsActions.OnClick].action).invoke())
        }

        override fun close() {
            frame.removeView(view)
            view.disposeComposition()
            shadowOf(Looper.getMainLooper()).idle()
            activityController.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
