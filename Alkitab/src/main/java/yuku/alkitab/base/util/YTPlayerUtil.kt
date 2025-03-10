package yuku.alkitab.base.util

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import yuku.alkitab.base.model.MVideo
import yuku.alkitab.debug.R

class YTPlayerUtil {
    private var currentDialog: Dialog? = null
    private var currentYoutubePlayer: YouTubePlayer? = null

    @RequiresApi(Build.VERSION_CODES.P)
    fun showYoutubePopup(context: Context, video: MVideo) {

        if (currentDialog?.isShowing == true) {
            currentYoutubePlayer?.loadVideo(video.yid, 0f)
            return
        }

        currentDialog = createDialog(context, video).apply { show() }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createDialog(context: Context, video: MVideo): Dialog {
        return Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_youtube_player, null))

            requireViewById<TextView>(R.id.titleTextView).text = video.title

            val youTubePlayerView = requireViewById<YouTubePlayerView>(R.id.youtubePlayerView)
            initializeYouTubePlayer(youTubePlayerView, video.yid)

            requireViewById<ImageView>(R.id.closeButton).setOnClickListener { dismiss() }

            val dropdownButton = requireViewById<ImageView>(R.id.dropdownButton)
            setupDropdownButton(dropdownButton, video)

            val dropdownLayout = requireViewById<RelativeLayout>(R.id.dropdown_layout)
            dropdownLayout.setOnClickListener {
                showVideoSelectionDropdown(context, it, video)
            }

            setOnDismissListener {
                currentDialog = null
                currentYoutubePlayer = null
            }
        }
    }

    private fun setupDropdownButton(dropdownButton: ImageView?, video: MVideo) {
        val relatedVideos = GeneratedVideos.videoList.filter { it.position == video.position }
        dropdownButton?.visibility = if (relatedVideos.size > 1) View.VISIBLE else View.GONE
    }

    fun initializeYouTubePlayer(youTubePlayerView: YouTubePlayerView, videoUrl: String) {
        youTubePlayerView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                currentYoutubePlayer = youTubePlayer
                youTubePlayer.loadVideo(videoUrl, 0f)
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun showVideoIfExists(context: Context, book: String, chapter: Int?) {
        val videoList = GeneratedVideos.videoList
            .filter { video ->
                video.position.split(", ").any { it.startsWith(book) }
            }

        if (videoList.isEmpty()) return

        val parsedVideos = videoList.map { video ->
            val positions = video.position.split(", ")
            positions.mapNotNull { pos ->
                val match = Regex("(.+) (\\d+)").find(pos)
                match?.let {
                    val videoBook = it.groupValues[1]
                    val videoChapter = it.groupValues[2].toIntOrNull()
                    if (videoChapter != null) videoBook to videoChapter to video else null
                }
            }
        }.flatten()

        val exactMatch = parsedVideos.find { it.first.second == chapter }?.second
        val fallbackMatch = parsedVideos
            .filter { it.first.second <= (chapter ?: 0) }
            .maxByOrNull { it.first.second }
            ?.second

        val selectedVideo = exactMatch ?: fallbackMatch ?: return
        showYoutubePopup(context, selectedVideo)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun showVideoSelectionDropdown(context: Context, anchorView: View, currentVideo: MVideo) {
        val videoList = GeneratedVideos.videoList
            .filter { video ->
                video.position.split(", ").any { it.startsWith(currentVideo.position.split(" ")[0]) }
            }

        if (videoList.size <= 1) return

        val listView = ListView(context).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            divider = AppCompatResources.getDrawable(context, android.R.color.darker_gray)
            dividerHeight = 1
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
        }

        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, videoList.map { it.title })
        listView.adapter = adapter

        val popupWindow = PopupWindow(
            listView,
            anchorView.width,
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(AppCompatResources.getDrawable(context, android.R.color.black))
            isOutsideTouchable = true
            isFocusable = true
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedVideo = videoList[position]

            currentDialog?.requireViewById<TextView>(R.id.titleTextView)?.text = selectedVideo.title

            showYoutubePopup(context, selectedVideo)
            popupWindow.dismiss()
        }

        popupWindow.showAsDropDown(anchorView)
    }
}