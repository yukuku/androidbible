package yuku.alkitab.songs

import android.os.Looper
import androidx.lifecycle.ViewModelStore
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class SongMediaViewModelTest {
    private val store = ViewModelStore()
    private val empty = SongMedia(emptyList(), emptyList(), false)

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun `a late result from the previous song cannot replace the current song`() {
        val first = CompletableDeferred<SongMedia>()
        val second = CompletableDeferred<SongMedia>()
        val model = SongMediaViewModel { _, code -> if (code == "1") first.await() else second.await() }
        store.put("media", model)
        model.load("KJ", "1", false)
        shadowOf(Looper.getMainLooper()).idle()
        model.load("KJ", "2", false)
        shadowOf(Looper.getMainLooper()).idle()
        second.complete(empty)
        shadowOf(Looper.getMainLooper()).idle()
        first.complete(SongMedia(listOf(SongAudioClip("https://example.com/old.mid")), emptyList(), false))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("2", model.state.value.code)
        assertEquals(empty, model.state.value.media)
        assertFalse(model.state.value.isLoading)
    }

    @Test
    fun `reopening a loaded song keeps its existing media until explicitly retried`() {
        var calls = 0
        val model = SongMediaViewModel { _, _ -> calls++; empty }
        store.put("media", model)
        model.load("KJ", "1", false)
        shadowOf(Looper.getMainLooper()).idle()
        model.load("KJ", "1", false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, calls)
        model.load("KJ", "1", true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, calls)
    }

    @Test
    fun `failed requests can be retried without changing the song`() {
        var isFailing = true
        val model = SongMediaViewModel { _, _ -> if (isFailing) throw IOException("offline") else empty }
        store.put("media", model)
        model.load("KJ", "1", false)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(model.state.value.isError)
        isFailing = false
        model.load("KJ", "1", true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(empty, model.state.value.media)
        assertFalse(model.state.value.isError)
    }
}
