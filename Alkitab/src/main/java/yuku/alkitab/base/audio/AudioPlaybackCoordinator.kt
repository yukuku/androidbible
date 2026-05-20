package yuku.alkitab.base.audio

import androidx.annotation.VisibleForTesting

/**
 * Process-wide arbiter guaranteeing that at most one logical audio session
 * (Bible chapter audio or kidung/hymn audio) is active at a time.
 *
 * OS audio-focus arbitration alone is unreliable here — the legacy hymn player
 * kept playing over Bible audio — so mutual exclusion is enforced in code:
 * whoever [acquire]s last wins, and the previous owner is told to
 * [Session.stopPlayback] (a full stop, not a pause).
 *
 * All calls happen on the main thread (media3's `Player` contract); the lock is
 * cheap insurance for the test harness, which runs off the main thread.
 */
object AudioPlaybackCoordinator {
    interface Session {
        /** Stop playback completely and clear any media notification. */
        fun stopPlayback()
    }

    private val lock = Any()
    private var active: Session? = null

    fun acquire(session: Session) {
        synchronized(lock) {
            val previous = active
            // Set the new owner BEFORE stopping the previous one: stopPlayback()
            // typically calls release() re-entrantly, and that release must not
            // clear the slot we just claimed (it checks active === itself).
            active = session
            if (previous != null && previous !== session) {
                previous.stopPlayback()
            }
        }
    }

    fun release(session: Session) {
        synchronized(lock) {
            if (active === session) {
                active = null
            }
        }
    }

    @VisibleForTesting
    fun activeForTest(): Session? = synchronized(lock) { active }

    @VisibleForTesting
    fun resetForTest() {
        synchronized(lock) { active = null }
    }
}
