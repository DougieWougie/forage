package me.dougals.forage.data.content

import kotlinx.coroutines.CancellationException
import me.dougals.forage.data.local.ForageDatabase
import me.dougals.forage.data.net.GuardedHttp
import me.dougals.forage.data.net.isPublicAddress
import me.dougals.forage.data.net.readBytesBounded
import me.dougals.forage.data.work.ArchiveAction
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetAddress

fun interface PageFetcher { suspend fun fetchHtml(url: String): String }

/**
 * Fetches article pages through [GuardedHttp]; see there for the SSRF rules. Pass a client that carries only the
 * session-cookie interceptor, never Nextcloud credentials.
 */
class OkHttpPageFetcher(
    client: OkHttpClient,
    private val allowPrivateNetworks: suspend () -> Boolean = { false },
    private val maxBytes: Long = 5_000_000,
    addressPolicy: (InetAddress) -> Boolean = ::isPublicAddress,
) : PageFetcher {
    private val http = GuardedHttp(client, addressPolicy)

    override suspend fun fetchHtml(url: String): String =
        http.get(url, allowPrivateNetworks()) { it.readBytesBounded(maxBytes).decodeToString() }
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
