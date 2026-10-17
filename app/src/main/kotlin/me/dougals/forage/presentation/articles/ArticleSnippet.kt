package me.dougals.forage.presentation.articles

import me.dougals.forage.domain.model.Article
import org.jsoup.Jsoup

object ArticleSnippet {
    fun of(article: Article, maxChars: Int = 140): String {
        val html = article.summary ?: article.contentHtml ?: return ""
        val text = Jsoup.parse(html).text().replace(Regex("\\s+"), " ").trim()
        if (text.length <= maxChars) return text
        val cut = text.take(maxChars)
        val lastSpace = cut.lastIndexOf(' ')
        return (if (lastSpace > maxChars / 2) cut.take(lastSpace) else cut).trimEnd() + "…"
    }
}
