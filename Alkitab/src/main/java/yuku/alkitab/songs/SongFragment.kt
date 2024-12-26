package yuku.alkitab.songs

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.postDelayed
import yuku.alkitab.base.fr.base.BaseFragment
import yuku.alkitab.debug.R
import yuku.kpri.model.Song
import yuku.kpri.model.VerseKind

class SongFragment : BaseFragment() {
    private lateinit var webview: WebView

    private val args by lazy { requireArguments() }
    private val song: Song by lazy { args.getParcelable(ARG_song)!! }
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
            var template = resources.assets.open("templates/song.html").use { input ->
                input.reader().readText()
            }

            for (key in customVars.keySet()) {
                template = templateVarReplace(template, key, customVars[key])
            }

            template = templateDivReplace(template, "code", song.code)
            template = templateDivReplace(template, "title", song.title)
            template = templateDivReplace(template, "title_original", song.title_original)
            template = templateDivReplace(template, "tune", song.tune)
            template = templateDivReplace(template, "keySignature", song.keySignature)
            template = templateDivReplace(template, "timeSignature", song.timeSignature)
            template = templateDivReplace(template, "authors_lyric", song.authors_lyric)
            template = templateDivReplace(template, "authors_music", song.authors_music)
            template = templateDivReplace(template, "lyrics", songToHtml(song, false))
            webview.loadDataWithBaseURL("file:///android_asset/templates/song.html", template, "text/html", "utf-8", null)
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

    private fun templateDivReplace(template: String, name: String, value: String?): String {
        return template.replace("{{div:$name}}", if (value == null) "" else "<div class='$name'>$value</div>")
    }

    private fun templateDivReplace(template: String, name: String, value: List<String>?): String {
        return templateDivReplace(template, name, if (value == null) null else TextUtils.join("; ", value.toTypedArray()))
    }

    private fun templateVarReplace(template: String, name: String, value: Any?): String {
        return template.replace("{{$$name}}", value?.toString() ?: "")
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

        fun songToHtml(song: Song, forPatchText: Boolean): String {
            val sb = StringBuilder()
            for (i in song.lyrics.indices) {
                val lyric = song.lyrics[i] ?: continue

                sb.append("<div class='lyric'>")
                if (song.lyrics.size > 1 || lyric.caption != null) { // otherwise, only lyric and has no name
                    if (lyric.caption != null) {
                        sb.append("<div class='lyric_caption'>").append(lyric.caption).append("</div>")
                    } else {
                        sb.append("<div class='lyric_caption'>Versi ").append(i + 1).append("</div>")
                    }
                }

                var verseNumberNormal = 0
                var verseNumberReff = 0
                for (verse in lyric.verses) {
                    sb.append("<div class='verse").append(if (verse.kind == VerseKind.REFRAIN) " refrain" else "").append("'>")
                    run {
                        when (verse.kind) {
                            VerseKind.REFRAIN -> verseNumberReff++
                            VerseKind.NORMAL -> verseNumberNormal++
                            else -> {}
                        }
                        if (forPatchText) {
                            when (verse.kind) {
                                VerseKind.REFRAIN -> sb.append("reff ").append(verseNumberReff)
                                VerseKind.NORMAL -> sb.append(verseNumberNormal)
                                else -> {}
                            }
                        } else {
                            when (verse.kind) {
                                VerseKind.REFRAIN -> sb.append("<div class='verse_ordering'>").append(verseNumberReff).append("</div>")
                                VerseKind.NORMAL -> sb.append("<div class='verse_ordering'>").append(verseNumberNormal).append("</div>")
                                else -> {}
                            }
                        }
                        sb.append("<div class='verse_content'>")
                        for (line in verse.lines) {
                            if (forPatchText) {
                                sb.append(line).append("<br/>")
                            } else {
                                sb.append("<p class='line'>").append(line).append("</p>")
                            }
                        }
                        sb.append("</div>")
                    }
                    sb.append("</div>")
                }
                sb.append("</div>")
            }
            return sb.toString()
        }
    }
}