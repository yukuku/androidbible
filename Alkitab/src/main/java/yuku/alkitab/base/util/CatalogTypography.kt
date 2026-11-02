package yuku.alkitab.base.util

import android.net.Uri
import android.webkit.WebView
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import yuku.alkitab.debug.BuildConfig

object CatalogTypography {
    @JvmStatic
    fun apply(view: WebView, url: String) {
        val uri = Uri.parse(url)
        val server = Uri.parse(BuildConfig.SERVER_HOST)
        if (uri.scheme != server.scheme || uri.authority != server.authority) return
        if (uri.path !in setOf("/rp/downloads", "/songs/downloads")) return

        fun css(role: UiTextRole): String = "font-size:${UiTypography.nominalSp(view.resources, role.size)}px;" +
            "line-height:${UiTypography.nominalSp(view.resources, role.lineHeight)}px;font-weight:${role.weight};letter-spacing:0;"

        val stylesheet = "*{text-transform:none!important}body{font-family:Roboto,Noto,sans-serif;${css(UiTextRole.PRIMARY)}}" +
            ".tab{${css(UiTextRole.LABEL)}}" +
            ".book_name{${css(UiTextRole.PRIMARY)}}" +
            ".book_title,.book_copyright,small{${css(UiTextRole.SUPPORTING)}}" +
            ".list b,.section{${css(UiTextRole.EMPHASIS)}}"
        val encoded = Json.encodeToString(stylesheet)
        val locale = Json.encodeToString(view.resources.configuration.locales[0].toLanguageTag())
        view.evaluateJavascript(
            """
            (() => {
                let s = document.getElementById('alkitab-ui-typography');
                if (!s) {
                    s = document.createElement('style');
                    s.id = 'alkitab-ui-typography';
                    document.head.appendChild(s);
                }
                s.textContent = $encoded;
                const locale = $locale;
                const normalize = text => {
                    if (text !== text.toLocaleUpperCase(locale)) return text;
                    return text.toLocaleLowerCase(locale).split(/(\s+)/)
                        .map(word => /^(id|json|url|pdf|zip|html|txt|pdb|yes)$/i.test(word) ? word.toUpperCase() : word)
                        .join('').replace(/^(\s*)(\S)/, (_, space, first) => space + first.toLocaleUpperCase(locale));
                };
                document.querySelectorAll('.tab,.download,.upload,.upload-bg,button,input[type=button],input[type=submit]').forEach(element => {
                    if (element.tagName === 'INPUT') {
                        element.value = normalize(element.value);
                    } else {
                        const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
                        while (walker.nextNode()) walker.currentNode.textContent = normalize(walker.currentNode.textContent);
                    }
                });
            })()
            """.trimIndent(),
            null,
        )
    }
}
