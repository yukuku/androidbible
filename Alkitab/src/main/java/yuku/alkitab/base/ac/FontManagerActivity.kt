package yuku.alkitab.base.ac

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import com.jakewharton.picasso.OkHttp3Downloader
import com.squareup.picasso.Callback.EmptyCallback
import com.squareup.picasso.Picasso
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import yuku.afw.widget.EasyAdapter
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.sv.DownloadService
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Background
import yuku.alkitab.base.util.FontManager
import yuku.alkitab.base.util.Foreground
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R

private const val TAG = "FontManagerActivity"
private const val URL_fontList = BuildConfig.SERVER_HOST + "addon/fonts/v1/list-v2.txt"
private const val URL_fontData = BuildConfig.SERVER_HOST + "addon/fonts/v1/data/%s.zip"
private const val URL_fontPreview = BuildConfig.SERVER_HOST + "addon/fonts/v1/preview/%s-384x84.png"

class FontManagerActivity : BaseActivity(), DownloadService.DownloadListener {
    private lateinit var lsFont: ListView
    private lateinit var adapter: FontAdapter
    private lateinit var progress: View
    private lateinit var lEmptyError: TextView

    private var dls: DownloadService? = null

    private val picasso: Picasso by lazy {
        Picasso.Builder(this)
            .downloader(OkHttp3Downloader(Connections.okHttp))
            .build()
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            dls = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val dls = (service as DownloadService.DownloadBinder).service
            dls.setDownloadListener(this@FontManagerActivity)
            this@FontManagerActivity.dls = dls
            runOnUiThread { loadFontList() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.willNeedStoragePermission()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_font_manager)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        lsFont = findViewById(R.id.lsFont)
        progress = findViewById(R.id.progress)
        lEmptyError = findViewById(R.id.lEmptyError)
        adapter = FontAdapter()
        lsFont.adapter = adapter

        bindService(Intent(this, DownloadService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onNeededPermissionsGranted(immediatelyGranted: Boolean) {
        super.onNeededPermissionsGranted(immediatelyGranted)
        if (!immediatelyGranted && dls != null) {
            loadFontList()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (dls != null) {
            unbindService(serviceConnection)
        }
    }

    fun loadFontList() = Background.run {
        try {
            val listString = App.downloadString(URL_fontList)
            val list = mutableListOf<FontItem>()
            val lines = listString.lines()
            if (lines.firstOrNull() == "OK") {
                for (line in lines.drop(1)) {
                    if (line.isNotBlank()) {
                        list.add(FontItem(line.trim().split(" ")[0]))
                    }
                }
            }
            Foreground.run {
                adapter.setData(list)
                lEmptyError.visibility = View.GONE
                progress.visibility = View.GONE
            }
        } catch (e: IOException) {
            Foreground.run {
                lEmptyError.visibility = View.VISIBLE
                lEmptyError.text = e.message
                progress.visibility = View.GONE
            }
        }
    }

    fun getFontDownloadKey(name: String?): String {
        return "FontManager/$name"
    }

    private fun getFontNameFromDownloadKey(key: String): String? {
        return if (!key.startsWith("FontManager/")) null else key.substring("FontManager/".length)
    }

    fun getFontDownloadDestination(name: String?): String {
        return File(cacheDir, "download-$name.zip").absolutePath
    }

    data class FontItem(val name: String)

    inner class FontAdapter : EasyAdapter() {
        private val list = mutableListOf<FontItem>()

        fun setData(list: List<FontItem>) {
            this.list.clear()
            this.list.addAll(list)
            notifyDataSetChanged()
        }

        override fun getCount(): Int {
            return list.size
        }

        override fun getItem(position: Int): FontItem {
            return list[position]
        }

        override fun newView(position: Int, parent: ViewGroup): View {
            return layoutInflater.inflate(R.layout.item_font_download, parent, false)
        }

        override fun bindView(view: View, position: Int, parent: ViewGroup) {
            val imgPreview = view.findViewById<ImageView>(R.id.imgPreview)
            val lFontName = view.findViewById<TextView>(R.id.lFontName)
            val bDownload = view.findViewById<View>(R.id.bDownload)
            val bDelete = view.findViewById<View>(R.id.bDelete)
            val progressbar = view.findViewById<ProgressBar>(R.id.progressbar)
            val lErrorMsg = view.findViewById<TextView>(R.id.lErrorMsg)

            val item = getItem(position)
            val dlkey = getFontDownloadKey(item.name)

            lFontName.text = item.name
            lFontName.visibility = View.VISIBLE

            picasso.load(String.format(URL_fontPreview, item.name)).into(imgPreview, object : EmptyCallback() {
                override fun onError(e: Exception) {
                    AppLog.e(TAG, "error loading font preview", e)
                }

                override fun onSuccess() {
                    lFontName.visibility = View.GONE
                }
            })

            imgPreview.contentDescription = item.name
            bDownload.setTag(R.id.TAG_fontItem, item)
            bDownload.setOnClickListener(bDownload_click)
            bDelete.setTag(R.id.TAG_fontItem, item)
            bDelete.setOnClickListener(bDelete_click)
            if (FontManager.isInstalled(item.name)) {
                progressbar.isIndeterminate = false
                progressbar.max = 100
                progressbar.progress = 100
                bDownload.visibility = View.GONE
                bDelete.visibility = View.VISIBLE
                lErrorMsg.visibility = View.GONE
                return
            }

            val entry = dls?.getEntry(dlkey)
            if (entry == null) {
                progressbar.isIndeterminate = false
                progressbar.max = 100
                progressbar.progress = 0
                bDownload.visibility = View.VISIBLE
                bDownload.isEnabled = true
                bDelete.visibility = View.GONE
                lErrorMsg.visibility = View.GONE
                return
            }

            when (entry.state) {
                DownloadService.State.created -> {
                    progressbar.isIndeterminate = true
                    bDownload.visibility = View.VISIBLE
                    bDownload.isEnabled = false
                    bDelete.visibility = View.GONE
                    lErrorMsg.visibility = View.GONE
                }

                DownloadService.State.downloading -> {
                    if (entry.length == -1L) {
                        progressbar.isIndeterminate = true
                    } else {
                        progressbar.isIndeterminate = false
                        progressbar.max = entry.length.toInt()
                        progressbar.progress = entry.progress.toInt()
                    }
                    bDownload.visibility = View.VISIBLE
                    bDownload.isEnabled = false
                    bDelete.visibility = View.GONE
                    lErrorMsg.visibility = View.GONE
                }

                DownloadService.State.finished -> {
                    progressbar.isIndeterminate = false
                    progressbar.max = 100 // consider full
                    progressbar.progress = 100 // consider full
                    bDownload.visibility = View.GONE
                    bDelete.visibility = View.VISIBLE
                    lErrorMsg.visibility = View.GONE
                }

                DownloadService.State.failed -> {
                    progressbar.isIndeterminate = false
                    progressbar.max = 100
                    progressbar.progress = 0
                    bDownload.visibility = View.VISIBLE
                    bDownload.isEnabled = true
                    bDelete.visibility = View.GONE
                    lErrorMsg.visibility = View.VISIBLE
                    lErrorMsg.text = entry.errorMsg
                }

                else -> {
                }
            }
        }

        private val bDownload_click = View.OnClickListener { v ->
            val dls = dls ?: return@OnClickListener
            val item = v.getTag(R.id.TAG_fontItem) as FontItem
            val dlkey = getFontDownloadKey(item.name)
            dls.removeEntry(dlkey)
            if (dls.getEntry(dlkey) == null) {
                dls.startDownload(dlkey, String.format(URL_fontData, item.name), getFontDownloadDestination(item.name))
            }
            notifyDataSetChanged()
        }

        private val bDelete_click = View.OnClickListener { v ->
            val dls = dls ?: return@OnClickListener
            val item = v.getTag(R.id.TAG_fontItem) as FontItem
            MaterialDialog.Builder(this@FontManagerActivity)
                .content(getString(R.string.fm_do_you_want_to_delete, item.name))
                .positiveText(R.string.delete)
                .onPositive { _, _ ->
                    val fontDir = FontManager.getFontDir(item.name)
                    val listFiles = fontDir.listFiles()
                    if (listFiles != null) {
                        for (file in listFiles) {
                            file.delete()
                        }
                    }
                    fontDir.delete()
                    dls.removeEntry(getFontDownloadKey(item.name))
                    notifyDataSetChanged()
                }
                .negativeText(R.string.cancel)
                .show()
        }
    }

    override fun onStateChanged(entry: DownloadService.DownloadEntry, originalState: DownloadService.State) {
        adapter.notifyDataSetChanged()

        if (originalState != DownloadService.State.finished) return
        val fontName = getFontNameFromDownloadKey(entry.key) ?: return // this download doesn't belong to font manager.

        try {
            val downloadedZip = getFontDownloadDestination(fontName)
            val fontDir = FontManager.getFontDir(fontName)
            fontDir.mkdirs()

            AppLog.d(TAG, "Going to unzip $downloadedZip", Throwable().fillInStackTrace())

            ZipInputStream(BufferedInputStream(FileInputStream(downloadedZip))).use { zis ->
                while (true) {
                    val ze: ZipEntry = zis.nextEntry ?: break
                    val zname = ze.name
                    AppLog.d(TAG, "Extracting from zip: $zname")

                    val extractFile = File(fontDir, zname)

                    // https://support.google.com/faqs/answer/9294009
                    val fontDirPath = fontDir.canonicalPath
                    val extractFilePath = extractFile.canonicalPath
                    if (!extractFilePath.startsWith(fontDirPath)) {
                        throw SecurityException("Zip path traversal attack: $fontDirPath, $zname")
                    }

                    FileOutputStream(extractFile).use { fos -> zis.copyTo(fos) }
                }
            }
            File(downloadedZip).delete()

        } catch (e: Exception) {
            MaterialDialog.Builder(this@FontManagerActivity)
                .content(getString(R.string.fm_error_when_extracting_font, fontName, "$e"))
                .positiveText(R.string.ok)
                .show()
        }
    }

    override fun onProgress(entry: DownloadService.DownloadEntry, originalState: DownloadService.State) {
        adapter.notifyDataSetChanged()
    }

    companion object {
        @JvmStatic
        fun createIntent(): Intent {
            return Intent(App.context, FontManagerActivity::class.java)
        }
    }
}
