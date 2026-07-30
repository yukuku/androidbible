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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.postDelayed
import kotlin.math.roundToInt
import yuku.alkitab.base.fr.base.BaseFragment
import yuku.alkitab.debug.R
import yuku.alkitab.songs.newdoc.SongDocument
import yuku.alkitab.songs.newdoc.SongDocumentJson
import yuku.alkitab.songs.newdoc.SongDocumentRenderer

class SongFragment : BaseFragment(), SongTextZoomable {
    private lateinit var webview: WebView
    private var bottomInsetDp = 0

    private val args by lazy { requireArguments() }
    private val doc: SongDocument by lazy { SongDocumentJson.decode(args.getString(ARG_songJson)!!) }
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

        ViewCompat.setOnApplyWindowInsetsListener(webview) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            bottomInsetDp = (insets.bottom / v.resources.displayMetrics.density).roundToInt()
            applyBottomInset()
            windowInsets
        }

        return res
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        renderSong(doc)
    }

    /**
     * Gives the page room to scroll its last line clear of the navigation bar
     * it draws behind. The margin belongs to the document rather than to the
     * WebView, whose padding would shorten the viewport and keep the lyrics
     * out of the area behind the bar altogether. CSS pixels are device-
     * independent pixels here, the same unit the template's font sizes use.
     */
    private fun applyBottomInset() {
        webview.evaluateJavascript("document.body.style.marginBottom = '${bottomInsetDp}px';", null)
    }

    private val webViewClient: WebViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val activity: Activity? = activity
            return activity is ShouldOverrideUrlLoadingHandler && activity.shouldOverrideUrlLoading(this, request) || super.shouldOverrideUrlLoading(view, request)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            applyBottomInset()
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

    private fun renderSong(doc: SongDocument) {
        try {
            var template = resources.assets.open("templates/song.html").use { input ->
                input.reader().readText()
            }

            for (key in customVars.keySet()) {
                template = templateVarReplace(template, key, customVars[key])
            }

            template = templateDivReplace(template, "code", doc.code)
            val songContent = SongDocumentRenderer.renderDocument(doc, { osis -> ScriptureReferenceRenderer.render(BIBLE_PROTOCOL, osis) }, false)
            template = templateVarReplace(template, "song_content", songContent)
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

    private fun templateVarReplace(template: String, name: String, value: Any?): String {
        return template.replace("{{$$name}}", value?.toString() ?: "")
    }

    override var songTextZoomPercent: Int
        get() {
            return if (view == null) 0 else webview.settings.textZoom
        }
        set(percent) {
            if (view == null) return
            webview.settings.textZoom = percent
        }

    companion object {
        private const val ARG_songJson = "songJson"
        private const val ARG_customVars = "customVars"

        fun create(doc: SongDocument, customVars: Bundle) = SongFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_songJson, SongDocumentJson.encode(doc))
                putBundle(ARG_customVars, customVars)
            }
        }
    }
}
