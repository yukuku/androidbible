package yuku.alkitab.base.util

import android.util.Log
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yuku.alkitab.base.model.MTiming
import yuku.alkitab.songs.ExoplayerController

private const val TAG = "TimingUtil"
private const val HIGHLIGHT_DELAY_MS = 100L

class TimingUtil(
    private val exoplayerController: ExoplayerController,
    private val highlightListener: HighlightListener,
    var mode: Mode = Mode.SINGLE_AUDIO,
    private val id: String
) {
    enum class Mode {
        SINGLE_AUDIO,
        DUAL_AUDIO
    }

    var timingList: List<Triple<Long, Long, Int>> = emptyList()
    var currentVerseIndex = -1
    var highlightedVerse: Int? = null
    private var isHighlightingActive = false
    private var highlightJob: Job? = null
    private var playbackJob: Job? = null

    /**
     * Timing File Function
     */

    fun loadTimingFile(bookName: String, chapter: String, version: String, onSuccess: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        AppLog.d(TAG, "[$id] loadTimingFile called - bookName: $bookName, chapter: $chapter, version: $version")

        timingList = emptyList()
        val modifiedVersion = if (version == "TB") "tbsuara" else version
        val url = "https://karaoke.sabda.org/api/timming.php?book=$bookName&chapter=$chapter&version=$modifiedVersion"
        AppLog.d(TAG, "[$id] loadTimingFile called - url: $url")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonText = URL(url).readText()
                AppLog.d(TAG, "[$id] Timing file berhasil diambil, mulai parsing...")

                // Parsing JSON dilakukan di MTiming
                timingList = MTiming.fromJsonArray(jsonText)

                AppLog.d(TAG, "[$id] Timing file berhasil diparse. Total entries: ${timingList.size}")
                timingList.forEachIndexed { index, triple ->
                    AppLog.d(TAG, "[$id] Entry $index → Start: ${triple.first}, End: ${triple.second}, Verse: ${triple.third}")
                }

                withContext(Dispatchers.Main) { onSuccess?.invoke() }
            } catch (e: Exception) {
                Log.e(TAG, "[$id] Error loading timing file: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke(e.message ?: "Unknown error")
                }
            }
        }
    }

    /**
     * For single-audio use only: auto highlight based on ExoPlayer time
     */
    fun startHighlightingVerses() {
        if (mode != Mode.SINGLE_AUDIO) {
            Log.w(TAG, "[$id] startHighlightingVerses called in DUAL_AUDIO mode – ignored")
            return
        }

        if (isHighlightingActive) {
            Log.d(TAG, "[$id] Highlighting already active, skipping restart")
            return
        }

        isHighlightingActive = true
        Log.d(TAG, "[$id] Starting verse highlighting")

        highlightJob = CoroutineScope(Dispatchers.Main).launch {
            while (isHighlightingActive) {
                val currentPosition = exoplayerController.getProgress().getOrNull(0)

                if (currentPosition == null) {
                    Log.w(TAG, "[$id] getProgress() mengembalikan null — menghentikan highlighting")
                    break
                }

                val newVerseIndex = timingList.indexOfFirst {
                    currentPosition in it.first..it.second
                }

                if (newVerseIndex != -1 && newVerseIndex != currentVerseIndex) {
                    Log.d(TAG, "[$id] Ayat berubah: dari index $currentVerseIndex ke $newVerseIndex")
                    currentVerseIndex = newVerseIndex
                    highlightVerse(timingList[newVerseIndex].third)
                } else if (newVerseIndex == -1) {
                    Log.v(TAG, "[$id] Posisi $currentPosition tidak cocok dengan ayat manapun.")
                }

                delay(HIGHLIGHT_DELAY_MS)
            }

            Log.d(TAG, "[$id] Highlighting loop berakhir.")
        }
    }

    /**
     * For dual-audio mode: manually call this per ayat
     */
    fun highlightVerse(verseNumber: Int) {
        val adjustedVerseNumber = verseNumber - 1
        Log.d(TAG, "[$id] Permintaan highlight ayat: $verseNumber (adjusted=$adjustedVerseNumber)")

        if (highlightedVerse != adjustedVerseNumber) {
            Log.d(TAG, "[$id] Highlighting verse index=$adjustedVerseNumber (previous=${highlightedVerse ?: "none"})")
            clearPreviousHighlight()
            highlightedVerse = adjustedVerseNumber
            highlightListener.applyHighlight(adjustedVerseNumber, -1)
            highlightListener.scrollToHighlightedVerse(adjustedVerseNumber)
        } else {
            Log.d(TAG, "[$id] Ayat $adjustedVerseNumber sudah disorot — diabaikan")
        }
    }

    private fun clearPreviousHighlight() {
        highlightedVerse?.let {
            Log.d(TAG, "[$id] Clearing highlight for verse index=$it")
            highlightListener.applyHighlight(it, 0)
            highlightedVerse = null
        } ?: Log.v(TAG, "[$id] clearPreviousHighlight() — tidak ada highlight aktif.")
    }

    fun resetHighlight() {
        Log.d(TAG, "[$id] resetHighlight() — Menonaktifkan highlighting & reset status")
        isHighlightingActive = false
        clearPreviousHighlight()
        currentVerseIndex = -1
        playbackJob?.cancel()
    }

    fun clearTimingList() {
        timingList = emptyList()
        AppLog.d(TAG, "[$id] timingList dihapus sebelum memuat ulang data baru.")
    }

    interface HighlightListener {
        fun applyHighlight(verseNumber: Int, color: Int)
        fun scrollToHighlightedVerse(verseNumber: Int)

    }
}