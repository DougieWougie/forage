package me.dougals.forage.data.content

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.dougals.forage.data.local.ForageDatabase
import me.dougals.forage.data.work.ArchiveAction
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

fun interface PageFetcher { suspend fun fetchHtml(url: String): String }

/**
 * Article URLs come from untrusted feeds, so by default this refuses hosts that resolve to loopback,
 * link-local, private, CGNAT or multicast addresses (checked on every redirect hop) and caps body size.
 * Pass a client that carries only the session-cookie interceptor, never Nextcloud credentials.
 */
class OkHttpPageFetcher(
    client: OkHttpClient,
    allowPrivateNetworks: Boolean = false,
    private val maxBytes: Long = 5_000_000,
) : PageFetcher {
    private val client = if (allowPrivateNetworks) client else client.newBuilder().dns(PublicOnlyDns(client.dns)).build()

    override suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val source = r.body.source()
            source.request(maxBytes + 1)
            if (source.buffer.size > maxBytes) throw IOException("Page larger than $maxBytes bytes")
            source.readUtf8()
        }
    }
}

class PublicOnlyDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        if (addresses.any { !it.isPublic() }) throw UnknownHostException("$hostname resolves to a non-public address")
        return addresses
    }

    private fun InetAddress.isPublic(): Boolean {
        if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) return false
        val b = address
        return when (b.size) {
            4 -> !(b[0].toInt() == 100 && (b[1].toInt() and 0xC0) == 0x40) // 100.64.0.0/10 CGNAT
            else -> (b[0].toInt() and 0xFE) != 0xFC // fc00::/7 unique local
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
