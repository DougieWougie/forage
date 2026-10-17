package me.dougals.forage.data.content

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist

data class ExtractedArticle(val title: String, val contentHtml: String)

/** Readability-style: drop page chrome, pick the main content block, sanitise, absolutise URLs. */
class ArticleExtractor {
    fun extract(html: String, baseUrl: String): ExtractedArticle {
        val doc = Jsoup.parse(html, baseUrl)
        doc.select("script, style, noscript, nav, aside, footer, form, iframe, svg").remove()
        val main = doc.selectFirst("article") ?: doc.selectFirst("main, [role=main]") ?: densestBlock(doc.body())
        val clean = Jsoup.clean(main.html(), baseUrl, Safelist.relaxed())
        return ExtractedArticle(doc.title(), clean)
    }

    private fun densestBlock(body: Element): Element {
        val scores = HashMap<Element, Int>()
        for (p in body.select("p")) {
            val len = p.text().length
            if (len < MIN_PARAGRAPH) continue
            p.parent()?.let { scores.merge(it, len, Int::plus) }
            p.parent()?.parent()?.let { scores.merge(it, len / 2, Int::plus) }
        }
        return scores.maxByOrNull { it.value }?.key ?: body
    }

    private companion object { const val MIN_PARAGRAPH = 25 }
}
