package yuku.alkitab.base.util

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import yuku.alkitab.base.model.MVideo
import yuku.alkitab.debug.R

private const val TAG = "VideoPlayerUtil"
private const val BASE_URL = "https://media.sabda.org/video_alkitab/"

class VideoPlayerUtil {
    private var currentDialog: Dialog? = null
    private var currentExoPlayer: ExoPlayer? = null
    private fun formatTitle(video: MVideo) = "${video.title} (${video.label})"
    private fun hasRelatedVideos(video: MVideo) = yuku.alkitab.base.video.MediaList.VIDEOS.count { it.position == video.position } > 1

    private var isFullscreen = false

    private val labelPrefixMap = mapOf(
        "Film Matius" to "film/matius/",
        "Film Lukas" to "film/lukas/",
        "Film Yohanes" to "film/yohanes/",
        "Film Kisah" to "film/kisah/",
        "GNP" to "gnp/",
        "Global" to "global/",
        "KOG" to "kog/",
        "TBP" to "tbp/",
        "Lumo" to "lumo/"
    )

    @RequiresApi(Build.VERSION_CODES.P)
    fun showVideoPopup(context: Context, video: MVideo) {
        Log.d(TAG, "showVideoPopup: video - $video")
        
        if (currentDialog?.isShowing == true) {
            val playerView = currentDialog!!.requireViewById<PlayerView>(R.id.playerView)
            initializeMedia3Player(playerView, video, context)
        } else {
            currentDialog = createDialog(context, video).apply { show() }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createDialog(context: Context, video: MVideo) =
        Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_media3_player, null))
            setupDialogViews(this, context, video)

            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            window?.setLayout((screenWidth * 1), RelativeLayout.LayoutParams.WRAP_CONTENT)

            setOnDismissListener { clearCurrentDialog() }
        }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun setupDialogViews(dialog: Dialog, context: Context, video: MVideo) {
        dialog.requireViewById<TextView>(R.id.titleTextView).text = formatTitle(video)
        dialog.requireViewById<PlayerView>(R.id.playerView).also { initializeMedia3Player(it, video, context) }
        dialog.requireViewById<ImageView>(R.id.dropdownButton).visibility = if (hasRelatedVideos(video)) View.VISIBLE else View.GONE
        dialog.requireViewById<RelativeLayout>(R.id.dropdown_layout).setOnClickListener { showVideoSelectionDropdown(context, it, video) }
        dialog.requireViewById<ImageView>(R.id.closeButton).setOnClickListener {
            if (isFullscreen) {
                val playerView = dialog.requireViewById<PlayerView>(R.id.playerView)
                toggleFullscreen(context, null, playerView)
            }
            dialog.dismiss()
        }
    }

    private fun clearCurrentDialog() {
        val dialog = currentDialog
        if (isFullscreen && dialog != null) {
            val win = dialog.window
            val decor = win?.decorView
            val insetsController = if (win != null && decor != null) {
                WindowCompat.getInsetsController(win, decor)
            } else null

            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            (dialog.context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        currentExoPlayer?.release()
        currentExoPlayer = null
        currentDialog = null
        isFullscreen = false
    }

    private fun getFullVideoUrl(video: MVideo): String {
        if (video.videoId.startsWith("http", ignoreCase = true)) {
            return video.videoId
        }

        val prefix =  labelPrefixMap[video.label] ?: ""
        val fullUrl = BASE_URL + prefix + video.videoId
        Log.d(TAG, "getFullVideoUrl: label=${video.label}, videoId=${video.videoId}, fullUrl=$fullUrl")

        return fullUrl
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun initializeMedia3Player(playerView: PlayerView, video: MVideo, context: Context) {
        currentExoPlayer?.release()

        // 🔹 Buat playlist sesuai videoList filter
        val curPositions = parsePositions(video.position)
        val videoList = yuku.alkitab.base.video.MediaList.VIDEOS.filter { v ->
            val positions = parsePositions(v.position)
            positions.any { pos ->
                curPositions.any { cur ->
                    val curAbbr = getBookAbbr(cur.book) ?: return@any false
                    val posAbbr = getBookAbbr(pos.book) ?: return@any false

                    if (pos.bookEnd != null) {
                        val endAbbr = getBookAbbr(pos.bookEnd) ?: return@any false
                        val inBookRange = isBookInRange(curAbbr, posAbbr, endAbbr)
                        if (inBookRange) return@any true
                    }

                    if (curAbbr == posAbbr) {
                        if (cur.chapterStart != null && pos.chapterStart != null) {
                            val inRange = cur.chapterStart in pos.chapterStart..(pos.chapterEnd ?: pos.chapterStart)
                            if (inRange) return@any true
                        }
                    }
                    false
                }
            }
        }

        Log.d(TAG, "initializeMedia3Player: videolist - $videoList")

        // 🔹 Cari index awal
        val startIndex = videoList.indexOfFirst { it.videoId == video.videoId }.coerceAtLeast(0)

        currentExoPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItems(videoList.map { MediaItem.fromUri(getFullVideoUrl(it)) })
            seekTo(startIndex, 0L)
            prepare()
            playWhenReady = true
        }
        playerView.player = currentExoPlayer

        val btnPlayPause = playerView.findViewById<ImageButton>(R.id.btn_play_pause_custom)
        val btnRew10 = playerView.findViewById<ImageButton>(R.id.rew10)
        val btnFf10 = playerView.findViewById<ImageButton>(R.id.ff10)
        val btnPrev = playerView.findViewById<ImageButton>(R.id.exo_prev)
        val btnNext = playerView.findViewById<ImageButton>(R.id.exo_next)
        val btnFullscreen = playerView.findViewById<ImageButton>(R.id.btn_fullscreen)

        val player = currentExoPlayer!!

        // 🔹 Update tombol berdasarkan index player
        fun updatePrevNextButtons() {
            val idx = player.currentMediaItemIndex
            val total = player.mediaItemCount
            btnPrev?.isEnabled = idx > 0
            btnNext?.isEnabled = idx < total - 1
            Log.d(TAG, "updatePrevNextButtons: idx=$idx total=$total | prev=${btnPrev?.isEnabled}, next=${btnNext?.isEnabled}")
        }

        // 🔹 Listener Media3
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    btnPlayPause?.setImageResource(R.drawable.ic_pause)
                    Log.d(TAG, "▶️ Player mulai jalan")
                } else {
                    btnPlayPause?.setImageResource(R.drawable.ic_play)
                    Log.d(TAG, "⏸️ Player pause/stop")
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updatePrevNextButtons()
                val idx = player.currentMediaItemIndex
                if (idx in videoList.indices) {
                    val newVideo = videoList[idx]
                    currentDialog?.requireViewById<TextView>(R.id.titleTextView)?.text = formatTitle(newVideo)
                    Log.d(TAG, "➡️ Transition ke index=$idx url=${newVideo.videoId}")
                }
            }
        })

        // 🔹 Play/Pause
        btnPlayPause?.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
                btnPlayPause.setImageResource(R.drawable.ic_play)
            } else {
                player.play()
                btnPlayPause.setImageResource(R.drawable.ic_pause)
            }
        }

        // 🔹 Seek -10s
        btnRew10?.setOnClickListener {
            val newPos = (player.currentPosition - 10_000).coerceAtLeast(0)
            player.seekTo(newPos)
        }

        // 🔹 Seek +10s
        btnFf10?.setOnClickListener {
            val duration = if (player.duration != C.TIME_UNSET) player.duration else Long.MAX_VALUE
            val newPos = (player.currentPosition + 10_000).coerceAtMost(duration)
            player.seekTo(newPos)
        }

        // 🔹 Prev
        btnPrev?.setOnClickListener {
            val idx = player.currentMediaItemIndex
            if (idx > 0) {
                player.seekTo(idx - 1, 0L)
                player.playWhenReady = true
                val prevVideo = videoList[idx - 1]
                currentDialog?.requireViewById<TextView>(R.id.titleTextView)?.text = formatTitle(prevVideo)
                Log.d(TAG, "Prev -> index=${idx - 1}, url=${prevVideo.videoId}")
            }
        }

        // 🔹 Next
        btnNext?.setOnClickListener {
            val idx = player.currentMediaItemIndex
            if (idx < player.mediaItemCount - 1) {
                player.seekTo(idx + 1, 0L)
                player.playWhenReady = true
                val nextVideo = videoList[idx + 1]
                currentDialog?.requireViewById<TextView>(R.id.titleTextView)?.text = formatTitle(nextVideo)
                Log.d(TAG, "Next -> index=${idx + 1}, url=${nextVideo.videoId}")
            }
        }

        btnFullscreen.setOnClickListener { toggleFullscreen(context, btnFullscreen, playerView) }

        // 🔹 Set status awal tombol
        updatePrevNextButtons()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun toggleFullscreen(context: Context, btn: ImageButton?, playerView: PlayerView) {
        val dialog = currentDialog ?: return
        val window = dialog.window ?: return
        val decorView = window.decorView
        val insetsController = WindowCompat.getInsetsController(window, decorView)

        if (!isFullscreen) {
            // masuk fullscreen: full size dialog + sembunyikan system bars + lock landscape
            window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            playerView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)

            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

            // set behavior supaya user bisa swipe untuk memunculkan bars sementara
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())

            btn?.setImageResource(R.drawable.ic_fullscreen_exit)
            isFullscreen = true
            Log.d(TAG, "🔲 Masuk fullscreen")
        } else {
            // keluar fullscreen: kembalikan ukuran dialog + show system bars + unlock orientation
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            window.setLayout(screenWidth, LinearLayout.LayoutParams.WRAP_CONTENT)

            val defaultHeight = context.resources.getDimensionPixelSize(R.dimen.player_view_height)

            playerView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, defaultHeight)

            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

            insetsController.show(WindowInsetsCompat.Type.systemBars())

            btn?.setImageResource(R.drawable.ic_fullscreen)
            isFullscreen = false
            Log.d(TAG, "⬜ Keluar fullscreen")
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun showVideoIfExists(context: Context, book: String, chapter: Int?) {
        Log.d(TAG, "showVideoIfExists: book=$book | chapter=$chapter")

        if (chapter == null) return

        val target = listOf(PositionInfo(book, chapter, chapter, null, null))

        val videoList = yuku.alkitab.base.video.MediaList.VIDEOS.filter { video ->
            val positions = parsePositions(video.position)
            positions.any { pos ->
                target.any { cur ->
                    val curAbbr = getBookAbbr(cur.book) ?: return@any false
                    val posAbbr = getBookAbbr(pos.book) ?: return@any false

                    if (pos.bookEnd != null) {
                        val endAbbr = getBookAbbr(pos.bookEnd) ?: return@any false
                        val inBookRange = isBookInRange(curAbbr, posAbbr, endAbbr)
                        if (inBookRange) return@any true
                    }

                    if (curAbbr == posAbbr) {
                        if (cur.chapterStart != null && pos.chapterStart != null) {
                            val inRange = cur.chapterStart in pos.chapterStart..(pos.chapterEnd ?: pos.chapterStart)
                            if (inRange) return@any true
                        }
                    }

                    false
                }
            }
        }

        Log.d(TAG, "showVideoIfExists: candidates=$videoList")

        if (videoList.isEmpty()) return

        // 🔧 pilih yang paling spesifik (rentang terkecil), fallback ke pertama
        val selectedVideo = videoList.minByOrNull {
            val p = parsePositions(it.position)
            val span = p.minOfOrNull { pos -> (pos.chapterEnd ?: pos.chapterStart ?: chapter) - (pos.chapterStart ?: chapter) }
            span ?: Int.MAX_VALUE
        } ?: videoList.first()

        showVideoPopup(context, selectedVideo)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun showVideoSelectionDropdown(context: Context, anchorView: View, currentVideo: MVideo) {
        val curPositions = parsePositions(currentVideo.position)

        val videoList = yuku.alkitab.base.video.MediaList.VIDEOS.filter { video ->
            val positions = parsePositions(video.position)

            positions.any { pos ->
                curPositions.any { cur ->
                    val curAbbr = getBookAbbr(cur.book) ?: return@any false
                    val posAbbr = getBookAbbr(pos.book) ?: return@any false

                    // case: range lintas kitab
                    if (pos.bookEnd != null) {
                        val endAbbr = getBookAbbr(pos.bookEnd) ?: return@any false
                        val inBookRange = isBookInRange(curAbbr, posAbbr, endAbbr)
                        if (inBookRange) {
                            Log.d(TAG, "Lintas kitab match: cur=$curAbbr in $posAbbr..$endAbbr")
                            return@any true
                        }
                    }

                    // case: range dalam 1 kitab
                    if (curAbbr == posAbbr) {
                        if (cur.chapterStart != null && pos.chapterStart != null) {
                            val inRange = cur.chapterStart in pos.chapterStart..(pos.chapterEnd ?: pos.chapterStart)
                            //Log.d(TAG, "Check chapter: cur=${cur.chapterStart}, pos=${pos.chapterStart}-${pos.chapterEnd} => $inRange")
                            return@any inRange
                        }
                    }

                    false
                }
            }
        }

        Log.d(TAG, "showVideoSelectionDropdown: curpositions - $curPositions | videolist - $videoList")

        if (videoList.size <= 1) return

        val listView = createVideoListView(context, videoList)
        val popupWindow = createPopupWindow(context, listView, anchorView)

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedVideo = videoList[position]
            currentDialog?.requireViewById<TextView>(R.id.titleTextView)?.text = formatTitle(selectedVideo)
            showVideoPopup(context, selectedVideo)
            popupWindow.dismiss()
        }

        popupWindow.showAsDropDown(anchorView)
    }

    private fun createVideoListView(context: Context, videoList: List<MVideo>) =
        ListView(context).apply {
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
            divider = AppCompatResources.getDrawable(context, android.R.color.darker_gray)
            dividerHeight = 1
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
            adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, videoList.map { formatTitle(it) })
        }

    private fun createPopupWindow(context: Context, listView: ListView, anchorView: View) =
        PopupWindow(listView, anchorView.width, RelativeLayout.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(AppCompatResources.getDrawable(context, android.R.color.black))
            isOutsideTouchable = true
            isFocusable = true
        }

    fun parsePositions(position: String): List<PositionInfo> {
        return position.split(";").map { it.trim() }.map { parsePosition(it) }
    }

    data class PositionInfo(
        val book: String,
        val chapterStart: Int?,
        val chapterEnd: Int?,
        val verseStart: Int?,
        val verseEnd: Int?,
        val bookEnd: String? = null // tambahan untuk lintas kitab
    )

    fun parsePosition(position: String): PositionInfo {
        val bookRangeSameRegex = Regex("""(.+?)\s+(\d+)-(\d+)""")
        val rangeRegex = Regex("""(.+?)\s+(\d+)\s*-\s*(.+?)\s+(\d+)""")
        val singleRegex = Regex("""(.+?)\s+(\d+)(:(\d+)(-(\d+))?)?""")

        return when {
            position.matches(bookRangeSameRegex) -> {
                val match = bookRangeSameRegex.find(position)!!
                val book = match.groupValues[1].trim()
                val chapStart = match.groupValues[2].toInt()
                val chapEnd = match.groupValues[3].toInt()
                PositionInfo(book, chapStart, chapEnd, null, null)
            }

            position.matches(rangeRegex) -> {
                val match = rangeRegex.find(position)!!
                val bookStart = match.groupValues[1].trim()
                val chapStart = match.groupValues[2].toInt()
                val bookEnd = match.groupValues[3].trim()
                val chapEnd = match.groupValues[4].toInt()

                PositionInfo(bookStart, chapStart, chapEnd, null, null, bookEnd)
            }

            position.matches(singleRegex) -> {
                val match = singleRegex.find(position)!!
                val book = match.groupValues[1].trim()
                val chap = match.groupValues[2].toInt()
                val verseStart = match.groupValues.getOrNull(4)?.takeIf { it.isNotEmpty() }?.toInt()
                val verseEnd = match.groupValues.getOrNull(6)?.takeIf { it.isNotEmpty() }?.toInt()
                    ?: verseStart
                PositionInfo(book, chap, chap, verseStart, verseEnd)
            }

            else -> {
                PositionInfo(position, null, null, null, null)
            }
        }
    }

    private fun getBookAbbr(book: String): String? = BookAbbrManager.bookAbbrMap[book]

    private fun isBookInRange(book: String, startBook: String, endBook: String): Boolean {
        val order = BookAbbrManager.bookAbbrMap.values.toList()
        val idx = order.indexOf(book)
        val startIdx = order.indexOf(startBook)
        val endIdx = order.indexOf(endBook)
        return idx != -1 && startIdx != -1 && endIdx != -1 && idx in startIdx..endIdx
    }
}