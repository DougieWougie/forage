/*
 * Forage - an RSS reader for Android
 * Copyright (C) 2026 Dougie Richardson
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version. It is distributed WITHOUT ANY WARRANTY;
 * see the LICENSE file for details.
 */
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

/**
 * OpenDyslexic (SIL OFL 1.1) is bundled in assets. The page names it by a URL on a reserved, unresolvable host
 * and the WebView hands those requests to [assetFor]; nothing else is ever served from assets.
 */
object ReaderFonts {
    const val ORIGIN = "https://forage.invalid"
    const val REGULAR_URL = "$ORIGIN/fonts/opendyslexic-400.woff2"
    const val BOLD_URL = "$ORIGIN/fonts/opendyslexic-700.woff2"

    private val assets = mapOf(REGULAR_URL to "fonts/opendyslexic-400.woff2", BOLD_URL to "fonts/opendyslexic-700.woff2")

    /** The page's origin is the article's site, so the font request is cross-origin and needs CORS. */
    val RESPONSE_HEADERS = mapOf("Access-Control-Allow-Origin" to "*")

    fun assetFor(url: String): String? = assets[url]
}

object ReaderHtml {
    fun wrap(title: String, byline: String?, bodyHtml: String, style: ReaderStyle): String {
        val family = when (style.font) {
            ReaderFont.SERIF -> "Georgia, 'Noto Serif', serif"
            ReaderFont.SANS -> "system-ui, Roboto, sans-serif"
            ReaderFont.DYSLEXIC -> "'OpenDyslexic', system-ui, Roboto, sans-serif"
        }
        val fontFace = if (style.font == ReaderFont.DYSLEXIC) """
            @font-face { font-family: 'OpenDyslexic'; font-weight: 400; src: url('${ReaderFonts.REGULAR_URL}') format('woff2'); }
            @font-face { font-family: 'OpenDyslexic'; font-weight: 700; src: url('${ReaderFonts.BOLD_URL}') format('woff2'); }
        """ else ""
        val percent = (style.fontScale * 100).roundToInt()
        return """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https: http: data:; style-src 'unsafe-inline'; font-src ${ReaderFonts.ORIGIN}; script-src 'none'">
            <style>$fontFace
            html { font-size: $percent%; }
            body { margin: 0; padding: 20px 20px 48px; background: ${style.background}; color: ${style.text};
                   font-family: $family; line-height: 1.65; overflow-wrap: anywhere; }
            /* The reader's typeface wins over inline styles and <font face> that feeds ship with. */
            body * { font-family: inherit !important; }
            h1 { font-size: 1.6rem; line-height: 1.2; margin: 0 0 .4em; }
            .byline { font-size: .8rem; opacity: .7; margin: 0 0 1.6em; font-family: system-ui, Roboto, sans-serif !important; }
            a { color: ${style.link}; }
            img, video { max-width: 100%; height: auto; border-radius: 8px; }
            pre, code { font-family: ui-monospace, monospace !important; font-size: .85em; }
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
