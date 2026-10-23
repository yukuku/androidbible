package yuku.alkitab.base.audio

import io.mockk.every
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class AudioBarControllerReshowTest {
    private val activeSession = BibleAudioService::class.java.getDeclaredField("hasActiveSession").apply { isAccessible = true }
    private var isOriginalSessionActive = false
    private lateinit var fixture: AudioBarControllerFixture

    @Before
    fun setUp() {
        isOriginalSessionActive = activeSession.getBoolean(null)
        activeSession.setBoolean(null, false)
        AudioSetsRepository.resetForTest()
        fixture = AudioBarControllerFixture()
    }

    @After
    fun tearDown() {
        fixture.controller.detach()
        activeSession.setBoolean(null, isOriginalSessionActive)
        AudioSetsRepository.resetForTest()
    }

    @Test
    fun `an idle process does not bind or show the bar`() {
        fixture.controller.reshowIfSessionActive()
        verify(exactly = 0) { fixture.context.bindService(any(), any<android.content.ServiceConnection>(), any<Int>()) }
        assertFalse(fixture.controller.isBarVisible)
    }

    @Test
    fun `restoration waits for an active snapshot before showing the bar`() {
        activeSession.setBoolean(null, true)
        fixture.controller.reshowIfSessionActive()
        assertFalse(fixture.controller.isBarVisible)
        fixture.connect(fixture.activeState())
        assertTrue(fixture.controller.isBarVisible)
        verify(exactly = 0) { fixture.service.loadChapter(any()) }
    }

    @Test
    fun `a session that ends before connection stays hidden`() {
        activeSession.setBoolean(null, true)
        fixture.controller.reshowIfSessionActive()
        fixture.connect(PlaybackState.IDLE)
        assertFalse(fixture.controller.isBarVisible)
        verify(exactly = 1) { fixture.context.unbindService(fixture.connection) }
    }

    @Test
    fun `repeated restoration calls do not register another binding`() {
        activeSession.setBoolean(null, true)
        fixture.controller.reshowIfSessionActive()
        fixture.controller.reshowIfSessionActive()
        fixture.connect(fixture.activeState())
        fixture.controller.reshowIfSessionActive()
        verify(exactly = 1) { fixture.context.bindService(any(), any<android.content.ServiceConnection>(), any<Int>()) }
    }

    @Test
    fun `an external stop hides the bar and unregisters the binding`() {
        activeSession.setBoolean(null, true)
        fixture.controller.reshowIfSessionActive()
        fixture.connect(fixture.activeState())
        fixture.state.value = PlaybackState.IDLE
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(fixture.controller.isBarVisible)
        verify(exactly = 1) { fixture.context.unbindService(fixture.connection) }
    }

    @Test
    fun `a disconnected service keeps its registered binding until closed`() {
        activeSession.setBoolean(null, true)
        fixture.controller.reshowIfSessionActive()
        fixture.connect(fixture.activeState())
        fixture.connection.onServiceDisconnected(null)
        fixture.controller.reshowIfSessionActive()
        assertFalse(fixture.controller.uiState.value.isPlaying)
        assertTrue(fixture.controller.uiState.value.preparing)
        verify(exactly = 0) { fixture.context.unbindService(any()) }
        fixture.controller.hide()
        verify(exactly = 1) { fixture.context.unbindService(fixture.connection) }
        verify(exactly = 1) { fixture.context.bindService(any(), any<android.content.ServiceConnection>(), any<Int>()) }
    }

    @Test
    fun `a binding that dies is released and the next show can retry`() {
        fixture.controller.show()
        fixture.connection.onBindingDied(null)
        assertFalse(fixture.controller.isBarVisible)
        fixture.controller.show()
        verify(exactly = 2) { fixture.context.bindService(any(), any<android.content.ServiceConnection>(), any<Int>()) }
    }

    @Test
    fun `a late callback after hiding cannot revive the session`() {
        fixture.controller.show()
        fixture.controller.hide()
        fixture.connect(fixture.activeState())
        assertFalse(fixture.controller.isBarVisible)
        assertTrue(fixture.requests.isEmpty())
    }

    @Test
    fun `a null binding releases its registration instead of waiting indefinitely`() {
        fixture.controller.show()
        fixture.connection.onNullBinding(null)
        assertFalse(fixture.controller.isBarVisible)
        verify(exactly = 1) { fixture.context.unbindService(fixture.connection) }
    }

    @Test
    fun `a denied service start leaves no pending or visible session`() {
        every { fixture.context.startService(any()) } throws SecurityException("denied")
        fixture.controller.show()
        assertFalse(fixture.controller.isBarVisible)
        assertFalse(fixture.controller.uiState.value.preparing)
        verify(exactly = 0) { fixture.context.bindService(any(), any<android.content.ServiceConnection>(), any<Int>()) }
    }

    @Test
    fun `detaching clears bindings without stopping background playback`() {
        activeSession.setBoolean(null, true)
        fixture.controller.reshowIfSessionActive()
        fixture.connect(fixture.activeState())
        fixture.controller.detach()
        verify(exactly = 0) { fixture.service.stop() }
        verify(exactly = 1) { fixture.context.unbindService(fixture.connection) }
        assertFalse(fixture.controller.isBarVisible)
    }
}
