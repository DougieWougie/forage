package me.dougals.forage.presentation.reader

import android.content.Intent
import me.dougals.forage.domain.model.Article
import me.dougals.forage.domain.model.ReaderFont
import kotlin.math.roundToInt

/** Colours are CSS values (e.g. `#101010`) so the page matches the Material theme. */
data class ReaderStyle(
    val font: ReaderFont,
    val fontScale: Float,
    val background: String,
    val text: String,
    val link: String,
)

object ReaderHtml {
    fun wrap(title: String, byline: String?, bodyHtml: String, style: ReaderStyle): String {
        val family = when (style.font) {
            ReaderFont.SERIF -> "Georgia, 'Noto Serif', serif"
            ReaderFont.SANS -> "system-ui, Roboto, sans-serif"
        }
        val percent = (style.fontScale * 100).roundToInt()
        return """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https: http: data:; style-src 'unsafe-inline'; script-src 'none'">
            <style>
            html { font-size: $percent%; }
            body { margin: 0; padding: 20px 20px 48px; background: ${style.background}; color: ${style.text};
                   font-family: $family; line-height: 1.65; overflow-wrap: anywhere; }
            h1 { font-size: 1.6rem; line-height: 1.2; margin: 0 0 .4em; }
            .byline { font-size: .8rem; opacity: .7; margin: 0 0 1.6em; font-family: system-ui, Roboto, sans-serif; }
            a { color: ${style.link}; }
            img, video { max-width: 100%; height: auto; border-radius: 8px; }
            pre, code { font-family: ui-monospace, monospace; font-size: .85em; }
            pre { overflow-x: auto; padding: 12px; background: rgba(127,127,127,.15); border-radius: 8px; }
            blockquote { margin: 1em 0; padding-left: 1em; border-left: 3px solid rgba(127,127,127,.5); opacity: .9; }
            </style></head><body>
            <h1>${escape(title)}</h1>
            ${byline?.let { "<p class=\"byline\">${escape(it)}</p>" }.orEmpty()}
            $bodyHtml
            </body></html>
        """.trimIndent()
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")
}

object ShareIntents {
    fun send(article: Article): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, article.title)
        putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.url}")
    }
}
