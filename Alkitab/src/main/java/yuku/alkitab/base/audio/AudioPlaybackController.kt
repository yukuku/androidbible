package yuku.alkitab.base.audio

import android.content.Context
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import yuku.alkitab.base.model.MAudio
import yuku.alkitab.base.model.MTiming
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.BibleAudioRepository
import yuku.alkitab.debug.R

private val SPEEDS = listOf(0.5f, 0.8f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f)

private const val TAG = "AudioPlaybackController"
private const val HIGHLIGHT_POLL_MS = 100L

/**
 * Orchestrates Bible audio playback for a single chapter, in either single-stream or
 * dual-stream (split-view interleaved) mode.
 *
 * Lifecycle: call [loadChapter] to set up audio for a book/chapter, [release] when done.
 * All methods must be called on the main thread; coroutines are scoped to [lifecycleScope]
 * so there are no memory leaks when the hosting Activity/Fragment is destroyed.
 *
 * @param audioBar  the inflated audio control bar view (activity_audio.xml)
 * @param onVerseHighlight0  called on main thread with the currently active verse_1 for split 0
 * @param onVerseHighlight1  called on main thread with the currently active verse_1 for split 1
 * @param onChapterNavigationRequested  called when the user taps prev/next chapter
 */
class AudioPlaybackController(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    val audioBar: View,
    private val onVerseHighlight0: (verse_1: Int) -> Unit,
    private val onVerseHighlight1: (verse_1: Int) -> Unit,
    private val onChapterNavigationRequested: (isNext: Boolean) -> Unit,
) {
    // -----------------------------------------------------------------------
    // UI references (wired once in init)
    // -----------------------------------------------------------------------
    private val buttonPlay: ImageButton = audioBar.findViewById(R.id.button_play)
    private val buttonPrevVerse: ImageButton = audioBar.findViewById(R.id.button_prev_verse)
    private val buttonNextVerse: ImageButton = audioBar.findViewById(R.id.button_next_verse)
    private val buttonPrevChapter: ImageButton = audioBar.findViewById(R.id.button_prev_chapter)
    private val buttonNextChapter: ImageButton = audioBar.findViewById(R.id.button_next_chapter)
    private val buttonSpeed: ImageButton = audioBar.findViewById(R.id.button_speed)
    private val buttonRepeat: ImageButton = audioBar.findViewById(R.id.button_repeat)
    private val progressBar: ProgressBar = audioBar.findViewById(R.id.audio_progress_bar)

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------
    private var player0: BibleAudioPlayer? = null
    private var player1: BibleAudioPlayer? = null   // non-null only in dual mode

    private var timing0: List<MTiming> = emptyList()
    private var timing1: List<MTiming> = emptyList()

    private var isDual = false
    private var isPlaying = false
    private var repeat = false
    private var speed = 1.0f

    /** Current chapter state – set by [loadChapter]. */
    private var bookId = -1
    private var chapter_1 = 0
    private var audioSpec0: MAudio? = null
    private var audioSpec1: MAudio? = null

    /** Running coroutine job for highlight polling / dual-mode verse alternation. */
    private var playbackJob: Job? = null

    /** Verse currently being tracked for highlight (1-based; 0 = none). */
    private var highlightedVerse0 = 0
    private var highlightedVerse1 = 0

    init {
        buttonPlay.setOnClickListener { togglePlayPause() }
        buttonPrevVerse.setOnClickListener { navigateVerse(isNext = false) }
        buttonNextVerse.setOnClickListener { navigateVerse(isNext = true) }
        buttonPrevChapter.setOnClickListener { onChapterNavigationRequested(false) }
        buttonNextChapter.setOnClickListener { onChapterNavigationRequested(true) }
        buttonSpeed.setOnClickListener { showSpeedMenu(it) }
        buttonRepeat.setOnClickListener { toggleRepeat() }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Load audio for a new book/chapter. Stops any ongoing playback first.
     * Timing data is fetched lazily when the user presses play.
     */
    fun loadChapter(
        bookId: Int,
        chapter_1: Int,
        spec0: MAudio,
        spec1: MAudio? = null,
    ) {
        stopPlayback()
        clearHighlights()

        this.bookId = bookId
        this.chapter_1 = chapter_1
        this.audioSpec0 = spec0
        this.audioSpec1 = spec1
        this.isDual = spec1 != null
        this.timing0 = emptyList()
        this.timing1 = emptyList()

        // Pre-load audio players with the URL so buffering can start.
        val url0 = BibleAudioRepository.buildUrl(bookId, chapter_1, spec0.audioFolder)
        if (url0 != null) {
            ensurePlayer0().load(url0)
        } else {
            AppLog.w(TAG, "No audio URL for bookId=$bookId chapter=$chapter_1 spec=${spec0.versionId}")
        }

        if (spec1 != null) {
            val url1 = BibleAudioRepository.buildUrl(bookId, chapter_1, spec1.audioFolder)
            if (url1 != null) {
                ensurePlayer1().load(url1)
            }
        }

        updatePlayButton()
        AppLog.d(TAG, "loadChapter bookId=$bookId chapter=$chapter_1 dual=$isDual")
    }

    fun togglePlayPause() {
        if (isPlaying) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    /**
     * Seek to the start of [verse_1] in the current timing data (if available).
     * Falls back to a no-op if timing has not yet been loaded.
     *
     * In dual mode the playback loop is restarted from the target verse so that
     * the sequential verse-by-verse iteration stays in sync with the seek.
     */
    fun navigateVerse(isNext: Boolean) {
        val timing = timing0
        if (timing.isEmpty()) return

        val currentVerse = highlightedVerse0.coerceAtLeast(1)
        val targetVerse = if (isNext) currentVerse + 1 else (currentVerse - 1).coerceAtLeast(1)
        val entry = timing.find { it.verseNumber == targetVerse } ?: return

        player0?.seekTo(entry.startMs)
        if (isDual) {
            val entry1 = timing1.find { it.verseNumber == targetVerse }
            if (entry1 != null) player1?.seekTo(entry1.startMs)
            // Restart the dual-mode loop from the target verse so it stays in sync.
            if (isPlaying) {
                highlightedVerse0 = targetVerse
                playbackJob?.cancel()
                playbackJob = lifecycleScope.launch { runDualModePlayback(startVerse = targetVerse) }
            }
        }
        AppLog.d(TAG, "navigateVerse target=$targetVerse")
    }

    fun setSpeed(newSpeed: Float) {
        speed = newSpeed
        player0?.setSpeed(newSpeed)
        player1?.setSpeed(newSpeed)
    }

    fun release() {
        stopPlayback()
        player0?.release(); player0 = null
        player1?.release(); player1 = null
        AppLog.d(TAG, "released")
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun startPlayback() {
        isPlaying = true
        updatePlayButton()
        progressBar.visibility = View.VISIBLE

        playbackJob?.cancel()
        playbackJob = lifecycleScope.launch {
            // Load timing data if not yet available.
            if (timing0.isEmpty()) {
                val spec = audioSpec0 ?: return@launch
                timing0 = BibleAudioRepository.fetchTiming(bookId, chapter_1, spec.timingVersionParam)
                AppLog.d(TAG, "timing0 loaded: ${timing0.size} entries")
            }
            if (isDual && timing1.isEmpty()) {
                val spec = audioSpec1 ?: return@launch
                timing1 = BibleAudioRepository.fetchTiming(bookId, chapter_1, spec.timingVersionParam)
                AppLog.d(TAG, "timing1 loaded: ${timing1.size} entries")
            }

            progressBar.visibility = View.GONE
            player0?.play()
            if (isDual) player1?.pause() // player1 starts muted; dual mode drives it manually

            if (isDual) {
                runDualModePlayback(startVerse = highlightedVerse0.coerceAtLeast(1))
            } else {
                runSingleModeHighlighting()
            }
        }
    }

    private fun pausePlayback() {
        isPlaying = false
        updatePlayButton()
        playbackJob?.cancel()
        player0?.pause()
        player1?.pause()
    }

    private fun stopPlayback() {
        isPlaying = false
        playbackJob?.cancel()
        player0?.pause()
        player1?.pause()
    }

    /**
     * Single mode: poll player0's position and update verse highlight every [HIGHLIGHT_POLL_MS].
     */
    private suspend fun runSingleModeHighlighting() {
        while (isActive) {
            val pos = player0?.currentPositionMs ?: break
            val verse = timing0.findVerseAt(pos)
            if (verse != null && verse != highlightedVerse0) {
                highlightedVerse0 = verse
                onVerseHighlight0(verse)
            }
            delay(HIGHLIGHT_POLL_MS)
        }
    }

    /**
     * Dual mode: interleave playback verse-by-verse between player0 and player1.
     * For each verse: play it in player0 until the verse end time, then in player1.
     *
     * @param startVerse first verse to play; earlier verses are skipped (used after a seek)
     */
    private suspend fun runDualModePlayback(startVerse: Int = 1) {
        val allVerses = (timing0.map { it.verseNumber } + timing1.map { it.verseNumber })
            .distinct()
            .sorted()

        for (verse in allVerses) {
            if (verse < startVerse) continue
            if (!isActive) break

            // Play verse in player0
            val t0 = timing0.find { it.verseNumber == verse }
            if (t0 != null) {
                player0?.seekTo(t0.startMs)
                player0?.play()
                player1?.pause()
                highlightedVerse0 = verse
                onVerseHighlight0(verse)

                while (isActive) {
                    val pos = player0?.currentPositionMs ?: break
                    if (pos >= t0.endMs) break
                    delay(HIGHLIGHT_POLL_MS)
                }
                player0?.pause()
            }

            if (!isActive) break

            // Play verse in player1
            val t1 = timing1.find { it.verseNumber == verse }
            if (t1 != null) {
                player1?.seekTo(t1.startMs)
                player1?.play()
                highlightedVerse1 = verse
                onVerseHighlight1(verse)

                while (isActive) {
                    val pos = player1?.currentPositionMs ?: break
                    if (pos >= t1.endMs) break
                    delay(HIGHLIGHT_POLL_MS)
                }
                player1?.pause()
            }
        }

        // Chapter ended
        if (isActive) {
            isPlaying = false
            updatePlayButton()
            if (repeat) {
                startPlayback()
            } else {
                onChapterNavigationRequested(true)
            }
        }
    }

    private fun toggleRepeat() {
        repeat = !repeat
        buttonRepeat.alpha = if (repeat) 1f else 0.4f
        player0?.setRepeat(repeat && !isDual) // ExoPlayer repeat only in single mode
        AppLog.d(TAG, "repeat=$repeat")
    }

    private fun showSpeedMenu(anchor: View) {
        val popup = PopupMenu(context, anchor)
        SPEEDS.forEachIndexed { index, s ->
            val label = if (s == 1.0f) context.getString(R.string.audio_speed_normal) else "${s}×"
            popup.menu.add(0, index, index, label)
        }
        popup.setOnMenuItemClickListener { item: MenuItem ->
            setSpeed(SPEEDS[item.itemId])
            true
        }
        popup.show()
    }

    private fun updatePlayButton() {
        buttonPlay.setImageResource(
            if (isPlaying) R.drawable.ic_audio_pause else R.drawable.ic_audio_play
        )
    }

    private fun clearHighlights() {
        if (highlightedVerse0 != 0) { onVerseHighlight0(0); highlightedVerse0 = 0 }
        if (highlightedVerse1 != 0) { onVerseHighlight1(0); highlightedVerse1 = 0 }
    }

    private fun ensurePlayer0(): BibleAudioPlayer {
        return player0 ?: BibleAudioPlayer(context).also { p ->
            p.listener = singleModeListener
            player0 = p
        }
    }

    private fun ensurePlayer1(): BibleAudioPlayer {
        return player1 ?: BibleAudioPlayer(context).also { player1 = it }
    }

    private val singleModeListener = object : BibleAudioPlayer.Listener {
        override fun onReady() {}
        override fun onEnded() {
            if (!isDual && isPlaying) {
                isPlaying = false
                updatePlayButton()
                playbackJob?.cancel()
                clearHighlights()
                if (repeat) {
                    startPlayback()
                } else {
                    onChapterNavigationRequested(true)
                }
            }
        }
        override fun onError(message: String) {
            AppLog.e(TAG, "Playback error: $message")
            pausePlayback()
        }
    }
}

// -----------------------------------------------------------------------
// Extension: find which verse covers the given playback position
// -----------------------------------------------------------------------

private fun List<MTiming>.findVerseAt(positionMs: Long): Int? {
    return lastOrNull { it.startMs <= positionMs && positionMs < it.endMs }?.verseNumber
        ?: lastOrNull { positionMs >= it.startMs }?.verseNumber // fallback: last started verse
}
