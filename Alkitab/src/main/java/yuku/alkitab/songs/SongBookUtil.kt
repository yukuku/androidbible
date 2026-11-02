package yuku.alkitab.songs

import android.app.Activity
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Menu
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import okhttp3.Response
import yuku.alkitab.base.App
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.storage.SongDb
import yuku.alkitab.base.util.Background
import yuku.alkitab.base.util.Foreground
import yuku.alkitab.base.widget.MaterialDialogJavaHelper
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R
import yuku.alkitab.io.OptionalGzipInputStream
import yuku.alkitab.songs.newdoc.SongDocument
import yuku.alkitab.songs.newdoc.SongDocumentJson
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

object SongBookUtil {
    private const val POPUP_ID_ALL = -1
    private const val POPUP_ID_MORE = -2
    internal const val MAX_RESPONSE_SIZE = 50 * 1024 * 1024L // 50MB

    interface OnSongBookSelectedListener {
        fun onAllSelected()
        fun onSongBookSelected(name: String)
        fun onMoreSelected()
    }

    abstract class DefaultOnSongBookSelectedListener : OnSongBookSelectedListener {
        override fun onAllSelected() {}
        override fun onSongBookSelected(name: String) {}
        override fun onMoreSelected() {}
    }

    interface OnDownloadSongBookListener {
        fun onDownloadedAndInserted(songBookInfo: SongBookInfo)
        fun onFailedOrCancelled(songBookInfo: SongBookInfo, e: Exception?)
    }

    class SongBookInfo {
        @JvmField var name: String? = null
        @JvmField var title: String? = null
        @JvmField var copyright: String? = null
    }

    class NotOkException(val code: Int) : IOException()

    @JvmStatic
    fun isSupportedDataFormatVersion(dataFormatVersion: Int) = dataFormatVersion == SongDocumentJson.DATA_FORMAT_VERSION

    /**
     * For migration
     */
    @JvmStatic
    fun getSongBookInfo(db: SQLiteDatabase, bookName: String): SongBookInfo {
        return SongDb.getSongBookInfo(db, bookName) ?: fallbackSongBookInfo(bookName)
    }

    @JvmStatic
    fun getSongBookInfo(bookName: String): SongBookInfo {
        return App.services.storage.songDb.getSongBookInfo(bookName) ?: fallbackSongBookInfo(bookName)
    }

    internal fun fallbackSongBookInfo(bookName: String): SongBookInfo {
        return SongBookInfo().also {
            it.name = bookName
            it.title = bookName
            it.copyright = null
        }
    }

    @JvmStatic
    fun getSongBookPopupMenu(context: Context, withAll: Boolean, withMore: Boolean, anchor: View): PopupMenu {
        val res = PopupMenu(context, anchor)
        val menu: Menu = res.menu

        if (withAll) {
            val sb = SpannableStringBuilder(context.getString(R.string.sn_bookselector_all) + '\n')
            val sbLen = sb.length
            sb.append(context.getString(R.string.sn_bookselector_all_desc))
            sb.setSpan(RelativeSizeSpan(0.7f), sbLen, sb.length, 0)
            sb.setSpan(ForegroundColorSpan(0xffa0a0a0.toInt()), sbLen, sb.length, 0)
            menu.add(0, POPUP_ID_ALL, 0, sb)
        }

        val infos = App.services.storage.songDb.listSongBookInfos()
        for (i in infos.indices) {
            val info = infos[i]
            val sb = SpannableStringBuilder(escapeSongBookName(info.name))
            sb.append("\n")
            val sbLen = sb.length
            if (!TextUtils.isEmpty(info.title)) {
                sb.append(Html.fromHtml(info.title, Html.FROM_HTML_MODE_LEGACY))
            }
            sb.setSpan(RelativeSizeSpan(0.7f), sbLen, sb.length, 0)
            sb.setSpan(ForegroundColorSpan(0xffa0a0a0.toInt()), sbLen, sb.length, 0)
            menu.add(0, i + 1, 0, sb)
        }

        if (withMore) {
            val sb = SpannableStringBuilder(context.getText(R.string.sn_bookselector_more))
            sb.setSpan(ForegroundColorSpan(ResourcesCompat.getColor(context.resources, R.color.escape, context.theme)), 0, sb.length, 0)
            menu.add(0, POPUP_ID_MORE, 0, sb)
        }

        return res
    }

    @JvmStatic
    fun getCopyright(bookName: String?): String? {
        if (bookName == null) return null
        return getSongBookInfo(bookName).copyright
    }

    @JvmStatic
    fun getSongBookOnMenuItemClickListener(listener: OnSongBookSelectedListener): PopupMenu.OnMenuItemClickListener {
        return PopupMenu.OnMenuItemClickListener { item ->
            when (val itemId = item.itemId) {
                POPUP_ID_ALL -> listener.onAllSelected()
                POPUP_ID_MORE -> listener.onMoreSelected()
                else -> listener.onSongBookSelected(App.services.storage.songDb.listSongBookInfos()[itemId - 1].name ?: "")
            }
            true
        }
    }

    /**
     * Deserializes the song-book download wrapper (`{ dataFormatVersion, songs }`) from an
     * InputStream and returns its songs. The stream may optionally be gzip-compressed. This is a
     * plain JSON parse — no Java deserialization gadget surface.
     *
     * Package-visible for testing.
     */
    @JvmStatic
    fun deserializeSongs(inputStream: InputStream): List<SongDocument> {
        OptionalGzipInputStream(inputStream).use { gzipStream ->
            // Avoid a redundant intermediate ByteArray the size of the whole uncompressed payload:
            // read straight through a Reader instead of readBytes().toString(UTF_8).
            val text = gzipStream.reader(Charsets.UTF_8).use { it.readText() }
            return SongDocumentJson.decodeSongBook(text).songs
        }
    }

    @JvmStatic
    fun downloadSongBook(activity: Activity, songBookInfo: SongBookInfo, dataFormatVersion: Int, listener: OnDownloadSongBookListener) {
        val cancelled = AtomicBoolean()

        val pd: AlertDialog = MaterialDialogJavaHelper.showProgressDialog(
            activity,
            activity.getString(R.string.sn_downloading_ellipsis)
        )

        pd.setOnDismissListener { cancelled.set(true) }

        Background.run {
            try {
                val call = Connections.downloadCall(BuildConfig.SERVER_HOST + "/addon/songs/get_songs?name=" + songBookInfo.name + "&dataFormatVersion=" + dataFormatVersion)

                call.execute().use { response: Response ->
                    if (response.code != 200) throw NotOkException(response.code)

                    val body = response.body ?: throw IOException("Response body is null")

                    val contentLength = body.contentLength()
                    if (contentLength > MAX_RESPONSE_SIZE) {
                        throw IOException("Response too large: $contentLength bytes (max $MAX_RESPONSE_SIZE)")
                    }

                    val songs = deserializeSongs(body.byteStream())

                    if (cancelled.get()) {
                        Foreground.run { listener.onFailedOrCancelled(songBookInfo, null) }
                    } else {
                        App.services.storage.songDb.insertSongBookInfo(songBookInfo)
                        App.services.storage.songDb.storeSongs(songBookInfo.name, songs)
                        Foreground.run { listener.onDownloadedAndInserted(songBookInfo) }
                    }
                }
            } catch (e: Exception) {
                Foreground.run { listener.onFailedOrCancelled(songBookInfo, e) }
            } finally {
                pd.dismiss()
            }
        }
    }

    @JvmStatic
    fun escapeSongBookName(name: String?): CharSequence {
        if (name != null && name.startsWith("_")) {
            val color = ResourcesCompat.getColor(App.context.resources, R.color.escape, App.context.theme)
            val res = SpannableStringBuilder(name.substring(1))
            res.setSpan(ForegroundColorSpan(color), 0, res.length, 0)
            return res
        }
        return name ?: ""
    }
}
