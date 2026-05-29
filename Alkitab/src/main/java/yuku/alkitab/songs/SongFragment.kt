package yuku.alkitab.songs

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.os.BundleCompat
import androidx.core.view.postDelayed
import yuku.alkitab.base.fr.base.BaseFragment
import yuku.alkitab.debug.R
import yuku.alkitab.songs.document.LegacySongConverter
import yuku.alkitab.songs.document.SongDocument
import yuku.alkitab.songs.renderer.SongDocumentRenderer
import yuku.kpri.model.Song

class SongFragment : BaseFragment() {
    private lateinit var webview: WebView

    private val args by lazy { requireArguments() }
    private val song: Song by lazy { BundleCompat.getParcelable(args, ARG_song, Song::class.java)!! }
    private val customVars: Bundle by lazy { args.getBundle(ARG_customVars)!! }

    interface ShouldOverrideUrlLoadingHandler {
        fun shouldOverrideUrlLoading(client: WebViewClient, request: WebResourceRequest): Boolean
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val res = inflater.inflate(R.layout.fragment_song, container, false)

        webview = res.findViewById(R.id.webview)
        webview.setBackgroundColor(0x00000000)
        webview.webViewClient = webViewClient
        webview.settings.apply {
            javaScriptEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // prevent user system-wide display settings (sp scaling) from changing the actual text size inside webview.
            textZoom = 100
        }

        return res
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        renderSong(song)
    }

    private val webViewClient: WebViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val activity: Activity? = activity
            return activity is ShouldOverrideUrlLoadingHandler && activity.shouldOverrideUrlLoading(this, request) || super.shouldOverrideUrlLoading(view, request)
        }

        var pendingResize = false

        override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
            super.onScaleChanged(view, oldScale, newScale)

            // "restore" auto text-wrapping behavior from before KitKat
            if (pendingResize) return
            pendingResize = true

            view.postDelayed(100) {
                val script = "document.getElementsByTagName('body')[0].style.width = window.innerWidth + 'px';"
                view.evaluateJavascript(script, null)
                pendingResize = false
            }
        }
    }

    private fun renderSong(song: Song) {
        try {
            val document = LegacySongConverter.convert(song)
            val html = SongDocumentRenderer.renderToHtml(
                document = document,
                code = song.code ?: "",
                customVars = customVars,
                forPatchText = false
            )
            webview.loadDataWithBaseURL("file:///android_asset/templates/song.html", html, "text/html", "utf-8", null)
        } catch (e: Exception) {
            val errorMessage = buildString {
                // error message, then stack trace
                appendLine("<div style='background: #fff; color: #000; white-space: pre-wrap; word-break: break-word;'>")
                appendLine(getString(R.string.sn_error_rendering_lyrics))
                appendLine()
                appendLine(e.stackTraceToString())
                appendLine("</div>")
            }
            webview.loadDataWithBaseURL(null, errorMessage, "text/html", "utf-8", null)
        }
    }

    var webViewTextZoom: Int
        get() {
            return if (view == null) 0 else webview.settings.textZoom
        }
        set(percent) {
            if (view == null) return
            webview.settings.textZoom = percent
        }

    companion object {
        private const val ARG_song = "song"
        private const val ARG_customVars = "customVars"

        fun create(song: Song, customVars: Bundle) = SongFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_song, song)
                putBundle(ARG_customVars, customVars)
            }
        }

        @JvmStatic
        fun songToHtml(song: Song, forPatchText: Boolean): String {
            val document = LegacySongConverter.convert(song)
            return SongDocumentRenderer.renderToHtml(
                document = document,
                code = song.code ?: "",
                forPatchText = forPatchText
            )
        }
    }
}