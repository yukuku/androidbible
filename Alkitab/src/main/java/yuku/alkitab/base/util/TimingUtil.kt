package yuku.alkitab.base.util

import android.util.Log
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yuku.alkitab.base.model.MTiming
import yuku.alkitab.songs.ExoplayerController

private const val TAG = "TimingUtil"
private const val HIGHLIGHT_DELAY_MS = 100L

class TimingUtil(
    private val exoplayerController: ExoplayerController,
    private val highlightListener: HighlightListener
) {
    var timingList: List<Triple<Long, Long, Int>> = emptyList()
    var currentVerseIndex = -1
    var highlightedVerse: Int? = null
    private var isHighlightingActive = false

    /**
     * Timing File Function
     */

    fun loadTimingFile(bookName: String, chapter: String, version: String, onSuccess: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        AppLog.d(TAG, "loadTimingFile 1 called - bookName: $bookName, chapter: $chapter, version: $version")

        timingList = emptyList()

        val modifiedVersion = if (version == "TB") "tbsuara" else version

        val url = "https://karaoke.sabda.org/api/timming.php?book=$bookName&chapter=$chapter&version=$modifiedVersion"
        AppLog.d(TAG, "loadTimingFile called - url: $url")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonText = URL(url).readText()

                // Parsing JSON dilakukan di MTiming
                timingList = MTiming.fromJsonArray(jsonText)

                AppLog.d(TAG, "loadTimingFile 2 called - bookName: $bookName, chapter: $chapter, version: $version\n timingList: $timingList")

                withContext(Dispatchers.Main) { onSuccess?.invoke() }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading timing file: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun startHighlightingVerses() {
        if (isHighlightingActive) {
            Log.d(TAG, "Highlighting already active, skipping restart")
            return
        }

        isHighlightingActive = true
        Log.d(TAG, "Starting verse highlighting")

        CoroutineScope(Dispatchers.Main).launch {
            while (isHighlightingActive) {
                val currentPosition = exoplayerController.getProgress().getOrNull(0) ?: break

                val newVerseIndex = timingList.indexOfFirst { currentPosition in it.first..it.second }

                if (newVerseIndex != -1 && newVerseIndex != currentVerseIndex) {
                    currentVerseIndex = newVerseIndex
                    highlightVerse(timingList[newVerseIndex].third)
                }

                delay(HIGHLIGHT_DELAY_MS)
            }
        }
    }

    fun highlightVerse(verseNumber: Int) {
        val adjustedVerseNumber = verseNumber - 1 // Dikurangi 1

        if (highlightedVerse != adjustedVerseNumber) {
            Log.d(TAG, "Highlighting verse: $adjustedVerseNumber")
            clearPreviousHighlight()
            highlightedVerse = adjustedVerseNumber
            highlightListener.applyHighlight(adjustedVerseNumber, -1)
        }
    }

    private fun clearPreviousHighlight() {
        highlightedVerse?.let {
            Log.d(TAG, "Clearing highlight for verse: $it")
            highlightListener.applyHighlight(it, 0)
            highlightedVerse = null
        }
    }

    fun resetHighlight() {
        isHighlightingActive = false
        clearPreviousHighlight()
        currentVerseIndex = -1
    }

    fun clearTimingList() {
        timingList = emptyList()
        AppLog.d("TimingUtil", "timingList telah dihapus sebelum memuat ulang data baru.")
    }

    interface HighlightListener {
        fun applyHighlight(verseNumber: Int, color: Int)
    }

}