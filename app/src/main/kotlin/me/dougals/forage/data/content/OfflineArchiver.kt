package me.dougals.forage.data.content

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.dougals.forage.data.local.ForageDatabase
import me.dougals.forage.data.work.ArchiveAction
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

fun interface PageFetcher { suspend fun fetchHtml(url: String): String }

class OkHttpPageFetcher(private val client: OkHttpClient) : PageFetcher {
    override suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body.string()
        }
    }
}

/** Tier 1 of saving: cache a cleaned copy of the full page so it can be read offline. */
class OfflineArchiver(
    private val db: ForageDatabase,
    private val fetcher: PageFetcher,
    private val extractor: ArticleExtractor = ArticleExtractor(),
) : ArchiveAction {
    override suspend fun archive(articleId: Long): Result<Unit> {
        val article = db.articleDao().getById(articleId)
            ?: return Result.failure(IllegalArgumentException("No article $articleId"))
        if (!article.isSaved) return Result.success(Unit)
        return try {
            val html = fetcher.fetchHtml(article.url)
            val clean = extractor.extract(html, article.url).contentHtml
            if (clean.isBlank()) return Result.failure(IOException("No readable content at ${article.url}"))
            db.articleDao().saveOfflineHtml(articleId, clean)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
