package yuku.alkitab.base

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import yuku.alkitab.base.audio.AudioBarController
import yuku.alkitab.base.audio.AudioHttp
import yuku.alkitab.base.audio.AudioSetsRepository
import yuku.alkitab.base.audio.BibleAudioService
import yuku.alkitab.base.audio.PlaybackState
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.util.History

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [34])
class IsiActivityAudioNavigationTest {
    private val versionId = MVersionInternal.getVersionInternalId()
    private val activeSession = BibleAudioService::class.java.getDeclaredField("hasActiveSession")
        .apply { isAccessible = true }
    private var isOriginalSessionActive = false
    private var originalHistory = emptyList<History.Entry>()

    @Before
    fun setUp() {
        isOriginalSessionActive = activeSession.getBoolean(null)
        activeSession.setBoolean(null, false)
        originalHistory = History.listAllEntries()
        AudioSetsRepository.resetForTest()
        AudioSetsRepository.presetNameResolver = AudioSetsRepository.PresetNameResolver { "navigation" }
        AudioSetsRepository.http = AudioHttp {
            """{"schema":1,"preset":"navigation","sets":[{
                "audioId":"recording","title":"Recording","hasTiming":false,
                "books_1":[1],"mp3UrlTemplate":"/audio/{book_1}/{chapter_1}.mp3",
                "timingUrlTemplate":null
            }]}""".trimIndent()
        }
        runBlocking { AudioSetsRepository.setsFor(versionId) }
    }

    @After
    fun tearDown() {
        activeSession.setBoolean(null, isOriginalSessionActive)
        History.replaceAllEntries(originalHistory)
        AudioSetsRepository.resetForTest()
    }

    @Test
    fun `opening an ari target replaces existing audio without losing the destination`() {
        verifyOpeningTarget(Intent("yuku.alkitab.action.VIEW").putExtra("ari", 0x000203))
    }

    @Test
    fun `opening a lid target replaces existing audio without losing the destination`() {
        verifyOpeningTarget(Intent("yuku.alkitab.action.VIEW").putExtra("lid", 34))
    }

    @Test
    fun `a new ari intent pauses audio and prepares its destination`() {
        verifyNewTarget(Intent("yuku.alkitab.action.VIEW").putExtra("ari", 0x000203), 2, 3)
    }

    @Test
    fun `a new lid intent pauses audio and prepares its destination`() {
        verifyNewTarget(Intent("yuku.alkitab.action.VIEW").putExtra("lid", 34), 2, 3)
    }

    @Test
    fun `a target in the playing chapter also pauses and resets audio`() {
        verifyNewTarget(Intent("yuku.alkitab.action.VIEW").putExtra("ari", 0x000109), 1, 9)
    }

    @Test
    fun `a normal launch reconnects without pausing or replacing the session`() {
        verifyRestoredSession(Intent(Intent.ACTION_MAIN), null)
    }

    @Test
    fun `recreation does not replay the original passage navigation`() {
        verifyRestoredSession(
            Intent("yuku.alkitab.action.VIEW").putExtra("ari", 0x000203),
            Bundle().apply { putInt("ari", 0x000203) })
    }

    @Test
    fun `invalid new targets do not interrupt playback`() {
        val controller = Robolectric.buildActivity(
            IsiActivity::class.java,
            Intent("yuku.alkitab.action.VIEW").putExtra("ari", 0x000101)
        ).create()
        try {
            val fixture = AudioSession()
            fixture.connect(controller.get())
            controller.newIntent(Intent("yuku.alkitab.action.VIEW").putExtra("ari", 0))
            controller.newIntent(Intent("yuku.alkitab.action.VIEW").putExtra("lid", 0))
            assertTrue(fixture.state.value.isPlaying)
            verify(exactly = 0) { fixture.service.pause() }
            verify(exactly = 0) { fixture.service.loadChapter(any()) }
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `navigation within the reader retains chapter autoplay`() {
        val controller = Robolectric.buildActivity(
            IsiActivity::class.java,
            Intent("yuku.alkitab.action.VIEW").putExtra("ari", 0x000101)
        ).create()
        try {
            val activity = controller.get()
            val fixture = AudioSession()
            fixture.connect(activity)
            activity.display(2, 3)
            assertEquals(2, fixture.requests.single().chapter_1)
            assertTrue(fixture.requests.single().isAutoPlay)
            verify(exactly = 0) { fixture.service.pause() }
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `a service chapter change still navigates the reader`() {
        val controller = Robolectric.buildActivity(
            IsiActivity::class.java,
            Intent("yuku.alkitab.action.VIEW").putExtra("ari", 0x000101)
        ).create()
        try {
            val activity = controller.get()
            val fixture = AudioSession()
            fixture.connect(activity)
            fixture.state.value = fixture.state.value.copy(chapter_1 = 2)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(2, activity.chapter_1)
            verify(exactly = 0) { fixture.service.pause() }
            assertTrue(fixture.requests.isEmpty())
        } finally {
            controller.destroy()
        }
    }

    private fun verifyOpeningTarget(intent: Intent) {
        val fixture = AudioSession()
        activeSession.setBoolean(null, true)
        val controller = Robolectric.buildActivity(IsiActivity::class.java, intent).create()
        try {
            val activity = controller.get()
            assertEquals(2, activity.chapter_1)
            activity.lsSplit0 = mockk(relaxed = true)
            activity.lsSplit1 = mockk(relaxed = true)
            fixture.connect(activity)
            assertEquals(2, activity.chapter_1)
            verify { fixture.service.pause() }
            assertEquals(2, fixture.requests.single().chapter_1)
            assertFalse(fixture.requests.single().isAutoPlay)
            assertFalse(fixture.state.value.isPlaying)
            assertTrue(fixture.binder(activity).uiState.value.visible)
            assertFalse(fixture.binder(activity).uiState.value.isPlaying)
            verify(exactly = 0) { activity.lsSplit0.scrollToVerse(1) }
        } finally {
            controller.destroy()
        }
    }

    private fun verifyNewTarget(intent: Intent, chapter1: Int, verse1: Int) {
        val controller = Robolectric.buildActivity(
            IsiActivity::class.java,
            Intent("yuku.alkitab.action.VIEW").putExtra("ari", 0x000101)
        ).create()
        try {
            val activity = controller.get()
            val fixture = AudioSession()
            fixture.connect(activity)
            activity.lsSplit0 = mockk(relaxed = true)
            activity.lsSplit1 = mockk(relaxed = true)
            controller.newIntent(intent)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(chapter1, activity.chapter_1)
            verify { activity.lsSplit0.scrollToVerse(verse1) }
            verify { fixture.service.pause() }
            assertEquals(chapter1, fixture.requests.single().chapter_1)
            assertFalse(fixture.requests.single().isAutoPlay)
            assertFalse(fixture.state.value.isPlaying)
            assertTrue(fixture.binder(activity).uiState.value.visible)
            assertFalse(fixture.binder(activity).uiState.value.isPlaying)
        } finally {
            controller.destroy()
        }
    }

    private fun verifyRestoredSession(intent: Intent, savedState: Bundle?) {
        val fixture = AudioSession()
        activeSession.setBoolean(null, true)
        val controller = Robolectric.buildActivity(IsiActivity::class.java, intent).create(savedState)
        try {
            fixture.connect(controller.get())
            assertTrue(fixture.state.value.isPlaying)
            verify(exactly = 0) { fixture.service.pause() }
            verify(exactly = 0) { fixture.service.loadChapter(any()) }
        } finally {
            controller.destroy()
        }
    }

    private inner class AudioSession {
        val state = MutableStateFlow(
            PlaybackState.IDLE.copy(
                versionId = versionId, audioId = "recording", bookId = 0,
                chapter_1 = 1, verse_1 = 0, isPlaying = true,
            )
        )
        val service = mockk<BibleAudioService>(relaxed = true)
        val requests = mutableListOf<BibleAudioService.AudioRequest>()

        init {
            val application = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(application).setComponentNameAndServiceForBindService(
                ComponentName(application, BibleAudioService::class.java), BibleAudioService.LocalBinder(service),
            )
            every { service.playbackState } returns state
            every { service.pause() } answers {
                state.value = state.value.copy(isPlaying = false)
            }
            every { service.loadChapter(any()) } answers {
                val request = firstArg<BibleAudioService.AudioRequest>()
                requests += request
                state.value = state.value.copy(
                    bookId = request.bookId, chapter_1 = request.chapter_1,
                    verse_1 = 0, isPlaying = false, preparing = true
                )
            }
        }

        fun binder(activity: IsiActivity): AudioBarController =
            IsiActivity::class.java.getDeclaredMethod("getAudioBinder").apply { isAccessible = true }
                .invoke(activity) as AudioBarController

        fun connect(activity: IsiActivity) {
            activeSession.setBoolean(null, true)
            val binder = binder(activity)
            binder.reshowIfSessionActive()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
