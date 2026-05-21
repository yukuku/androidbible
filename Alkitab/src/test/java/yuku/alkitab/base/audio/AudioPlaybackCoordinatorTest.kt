package yuku.alkitab.base.audio

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AudioPlaybackCoordinatorTest {

    private class FakeSession : AudioPlaybackCoordinator.Session {
        var stopCount = 0
        override fun stopPlayback() {
            stopCount++
        }
    }

    @After
    fun tearDown() {
        AudioPlaybackCoordinator.resetForTest()
    }

    @Test
    fun `acquiring a second session stops the first`() {
        val first = FakeSession()
        val second = FakeSession()

        AudioPlaybackCoordinator.acquire(first)
        AudioPlaybackCoordinator.acquire(second)

        assertEquals(1, first.stopCount)
        assertEquals(0, second.stopCount)
        assertSame(second, AudioPlaybackCoordinator.activeForTest())
    }

    @Test
    fun `acquiring the same session twice does not stop it`() {
        val only = FakeSession()

        AudioPlaybackCoordinator.acquire(only)
        AudioPlaybackCoordinator.acquire(only)

        assertEquals(0, only.stopCount)
        assertSame(only, AudioPlaybackCoordinator.activeForTest())
    }

    @Test
    fun `releasing the active session clears the active slot`() {
        val session = FakeSession()

        AudioPlaybackCoordinator.acquire(session)
        AudioPlaybackCoordinator.release(session)

        assertNull(AudioPlaybackCoordinator.activeForTest())
    }

    @Test
    fun `releasing a non-active session is a no-op`() {
        val active = FakeSession()
        val other = FakeSession()

        AudioPlaybackCoordinator.acquire(active)
        AudioPlaybackCoordinator.release(other)

        assertSame(active, AudioPlaybackCoordinator.activeForTest())
        assertEquals(0, active.stopCount)
    }

    @Test
    fun `a session can be re-acquired after release without being stopped again`() {
        val a = FakeSession()
        val b = FakeSession()

        AudioPlaybackCoordinator.acquire(a)
        AudioPlaybackCoordinator.release(a)
        AudioPlaybackCoordinator.acquire(b)

        assertEquals(0, a.stopCount)
        assertSame(b, AudioPlaybackCoordinator.activeForTest())
    }

    @Test
    fun `a stopPlayback callback that re-entrantly releases itself does not clear the new owner`() {
        val newOwner = FakeSession()
        val reentrantReleaser = object : AudioPlaybackCoordinator.Session {
            var stopCount = 0
            override fun stopPlayback() {
                stopCount++
                AudioPlaybackCoordinator.release(this)
            }
        }

        AudioPlaybackCoordinator.acquire(reentrantReleaser)
        AudioPlaybackCoordinator.acquire(newOwner)

        assertEquals(1, reentrantReleaser.stopCount)
        assertSame(newOwner, AudioPlaybackCoordinator.activeForTest())
    }
}
