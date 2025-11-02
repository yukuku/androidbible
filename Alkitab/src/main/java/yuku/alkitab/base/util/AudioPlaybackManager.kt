package yuku.alkitab.base.util

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.lifecycle.LifecycleCoroutineScope
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import yuku.alkitab.base.model.MTiming
import yuku.alkitab.model.Book
import yuku.alkitab.model.Version
import yuku.alkitab.songs.ExoplayerController
import yuku.alkitab.songs.MediaController

class AudioPlaybackManager(
    private val scope: LifecycleCoroutineScope,
    private val controller0: ExoplayerController,
    private val controller1: ExoplayerController,
    private val timing0: TimingUtil,
    private val timing1: TimingUtil,
    private val isSplitMode: () -> Boolean,
    private val getBookName: () -> Book,
    private val getVersion: () -> Version,
    private val getChapterNumber: () -> Int,
    private val displayChapter: (Book, Int) -> Unit,
    private val buildAudioForChapter: () -> Unit,
    private val onHighlight: (verse: Int, color: Int) -> Unit,
    private val onScroll: (verse: Int) -> Unit,
    private val onPlayerStateChanged: (isPlaying: Boolean) -> Unit,
) {
    private var dualJob: Job? = null
    private var currentSegmentIndex = -1
    private var isAudioVisible = false
    private var isRepeatMode = false

    // === AUDIO BAR TOGGLE ===
    fun toggleAudioBar(audioBar: View, panelBackForwardList: LinearLayout) {
        isAudioVisible = !isAudioVisible
        audioBar.visibility = if (isAudioVisible) View.VISIBLE else View.GONE
        val params = panelBackForwardList.layoutParams as ViewGroup.MarginLayoutParams
        params.bottomMargin = if (isAudioVisible) dpToPx(75) else 0
        panelBackForwardList.layoutParams = params
    }

    fun hideAudioBar(audioBar: View, panelBackForwardList: LinearLayout) {
        if (!isAudioVisible) return

        isAudioVisible = false
        audioBar.visibility = View.GONE

        val params = panelBackForwardList.layoutParams as ViewGroup.MarginLayoutParams
        params.bottomMargin = 0
        panelBackForwardList.layoutParams = params
    }

    private fun dpToPx(dp: Int): Int = (dp * Resources.getSystem().displayMetrics.density).toInt()

    // === STOP & RESET ===
    fun stopAllAudio() {
        dualJob?.cancel()
        dualJob = null
        controller0.stopSegment()
        controller1.stopSegment()
        controller0.playOrPause(false)
        controller1.playOrPause(false)
        timing0.resetHighlight()
        timing1.resetHighlight()
    }

    // === PLAYBACK: SINGLE MODE ===
    fun togglePlayPauseSingle() {
        val version = getVersion()
        val book = getBookName()
        val chapter = getChapterNumber().toString()

        if (timing0.timingList.isNotEmpty()) {
            if (controller0.state == MediaController.State.playing) {
                controller0.playOrPause(false)
                timing0.resetHighlight()
                onPlayerStateChanged(false)
            } else {
                timing0.startHighlightingVerses()
                controller0.playOrPause(true)
                onPlayerStateChanged(true)
            }
        } else {
            timing0.loadTimingFile(book.shortName, chapter, version.shortName,
                onSuccess = {
                    timing0.startHighlightingVerses()
                    controller0.playOrPause(true)
                    onPlayerStateChanged(true)
                },
                onError = {
                    AppLog.e("AudioPlaybackManager", "Gagal memuat timing: $it")
                }
            )
        }
    }

    // === PLAYBACK: DUAL MODE ===
    fun togglePlayPauseDual() {
        val version = getVersion()
        val book = getBookName()
        val chapter = getChapterNumber().toString()

        if (timing0.timingList.isEmpty() || timing1.timingList.isEmpty()) {
            // load timing dulu
            timing0.loadTimingFile(book.shortName, chapter, version.shortName, onSuccess = {
                timing1.loadTimingFile(book.shortName, chapter, version.shortName, onSuccess = {
                    playAlternating(controller0, controller1, timing0.timingList, timing1.timingList)
                })
            })
            return
        }

        if (controller0.state == MediaController.State.playing || controller1.state == MediaController.State.playing) {
            stopAllAudio()
            onPlayerStateChanged(false)
        } else {
            playAlternating(controller0, controller1, timing0.timingList, timing1.timingList, currentSegmentIndex)
            onPlayerStateChanged(true)
        }
    }

    // === NAVIGASI AYAT ===
    fun navigateVerse(isNext: Boolean) {
        if (isSplitMode()) navigateVerseDual(isNext)
        else navigateVerseSingle(isNext)
    }

    private fun navigateVerseSingle(isNext: Boolean) {
        val index = timing0.currentVerseIndex
        if (index == -1) return

        val targetIndex = if (isNext) index + 1 else index - 1
        if (targetIndex in timing0.timingList.indices) {
            val target = timing0.timingList[targetIndex]
            timing0.highlightVerse(target.verseNumber)
            controller0.seekTo(target.startTime)
            controller0.playFromVerse(target.verseNumber, timing0.timingList)
        } else {
            if (isNext) navigateChapter(true, toFirstVerse = true)
            else navigateChapter(false, toLastVerse = true)
        }
    }

    private fun navigateVerseDual(isNext: Boolean) {
        val index = currentSegmentIndex
        if (index == -1) return

        val targetIndex = if (isNext) index + 1 else index - 1
        if (targetIndex in timing0.timingList.indices && targetIndex in timing1.timingList.indices) {
            val v0 = timing0.timingList[targetIndex]
            val v1 = timing1.timingList[targetIndex]
            timing0.highlightVerse(v0.verseNumber)
            timing1.highlightVerse(v1.verseNumber)
            playAlternating(controller0, controller1, timing0.timingList, timing1.timingList, targetIndex)
        } else {
            if (isNext) navigateChapter(true, toFirstVerse = true)
            else navigateChapter(false, toLastVerse = true)
        }
    }

    // === NAVIGASI PASAL ===
    fun navigateChapter(isNext: Boolean, toFirstVerse: Boolean = false, toLastVerse: Boolean = false) {
        val target = getNextOrPreviousChapter(isNext) ?: return
        val (book, chapter) = target

        // ubah state buku & tampilkan
        displayChapter(book, chapter)
        buildAudioForChapter()

        // atur highlight
        if (isSplitMode()) {
            when {
                toFirstVerse -> {
                    timing0.highlightVerse(1)
                    timing1.highlightVerse(1)
                }
                toLastVerse -> {
                    val lastVerse = min(
                        timing0.timingList.lastOrNull()?.verseNumber ?: 1,
                        timing1.timingList.lastOrNull()?.verseNumber ?: 1
                    )
                    timing0.highlightVerse(lastVerse)
                    timing1.highlightVerse(lastVerse)
                }
            }
        } else {
            when {
                toFirstVerse -> timing0.highlightVerse(1)
                toLastVerse -> {
                    val lastVerse = timing0.timingList.lastOrNull()?.verseNumber ?: 1
                    timing0.highlightVerse(lastVerse)
                }
            }
        }
    }

    private fun getNextOrPreviousChapter(isNext: Boolean): Pair<Book, Int>? {
        val currentBook = getBookName()
        val currentChapter = getChapterNumber()
        val version = getVersion()

        return if (isNext) {
            if (currentChapter >= currentBook.chapter_count) {
                var nextBookId = currentBook.bookId + 1
                while (nextBookId < version.maxBookIdPlusOne) {
                    version.getBook(nextBookId)?.let { return it to 1 }
                    nextBookId++
                }
                null
            } else {
                currentBook to (currentChapter + 1)
            }
        } else {
            if (currentChapter == 1) {
                var prevBookId = currentBook.bookId - 1
                while (prevBookId >= 0) {
                    version.getBook(prevBookId)?.let { return it to it.chapter_count }
                    prevBookId--
                }
                null
            } else {
                currentBook to (currentChapter - 1)
            }
        }
    }

    // === PLAY ALTERNATING (Dual Mode) ===
    fun playAlternating(
        c0: ExoplayerController,
        c1: ExoplayerController,
        list0: List<MTiming>,
        list1: List<MTiming>,
        startIndex: Int = 0
    ) {
        dualJob?.cancel()
        dualJob = scope.launch {
            try {
                val size = min(list0.size, list1.size)
                for (i in startIndex until size) {
                    currentSegmentIndex = i
                    val (start0, end0, _) = list0[i]
                    val (start1, end1, _) = list1[i]

                    c0.stopSegment(); c1.stopSegment()

                    suspendCancellableCoroutine { cont ->
                        c0.playSegment(start0, end0) {
                            if (cont.isActive) cont.resume(Unit) {}
                        }
                    }

                    ensureActive()

                    suspendCancellableCoroutine { cont ->
                        c1.playSegment(start1, end1) {
                            if (cont.isActive) cont.resume(Unit) {}
                        }
                    }
                }
            } catch (e: CancellationException) {
                c0.stopSegment(); c1.stopSegment()
            }
        }
    }

    // === PLAYBACK SPEED MENU ===
    fun showSpeedMenu(anchor: View) {
        val popup = PopupMenu(anchor.context, anchor)
        val options = listOf(
            0.5f to "0.5×", 0.8f to "0.8×", 1.0f to "Normal",
            1.1f to "1.1×", 1.25f to "1.25×", 1.5f to "1.5×", 1.75f to "1.75×", 2.0f to "2.0×"
        )

        options.forEachIndexed { idx, (_, label) -> popup.menu.add(0, idx, 0, label) }
        popup.menu.setGroupCheckable(0, true, true)

        val currentSpeed = controller0.getPlaybackSpeed()
        val currentIndex = options.indexOfFirst { it.first == currentSpeed }
        if (currentIndex != -1) popup.menu.findItem(currentIndex)?.isChecked = true

        popup.setOnMenuItemClickListener { item ->
            val speed = options.getOrNull(item.itemId)?.first ?: return@setOnMenuItemClickListener true
            controller0.setPlaybackSpeed(speed)
            if (isSplitMode()) controller1.setPlaybackSpeed(speed)
            true
        }

        popup.show()
    }

    // === CALLBACK DARI EXOPLAYER ===
    fun onAudioEnded() {
        AppLog.d("AudioPlaybackManager", "onAudioEnded called. repeat=$isRepeatMode")

        if (isRepeatMode) {
            AppLog.d("AudioPlaybackManager", "Repeat ON → restart current chapter")
            //onNavigatePrevChapter()
        } else {
            AppLog.d("AudioPlaybackManager", "Repeat OFF → next chapter")
            //onNavigateNextChapter()
        }

        timing0.startHighlightingVerses()
        timing0.highlightVerse(1)
        if (isSplitMode()) timing1.highlightVerse(1)
    }

    // === REPEAT TOGGLE ===
    fun toggleRepeat(): Boolean {
        isRepeatMode = !isRepeatMode
        return isRepeatMode
    }

}