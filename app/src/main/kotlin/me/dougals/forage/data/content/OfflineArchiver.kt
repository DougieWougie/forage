package me.dougals.forage.data.content

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.dougals.forage.data.local.ForageDatabase
import me.dougals.forage.data.work.ArchiveAction
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException

fun interface PageFetcher { suspend fun fetchHtml(url: String): String }

/**
 * Article URLs come from untrusted feeds, so by default every hop must resolve to a globally routable address:
 * names are checked in [PublicOnlyDns] (which also covers odd forms like `2130706433`), IP literals (which OkHttp
 * never sends through Dns) are checked here before connecting, and redirects are followed manually so each hop gets
 * both checks. Proxies are disabled so the checked address is the one actually dialled. Body size is capped.
 * Pass a client that carries only the session-cookie interceptor, never Nextcloud credentials.
 */
class OkHttpPageFetcher(
    client: OkHttpClient,
    allowPrivateNetworks: Boolean = false,
    private val maxBytes: Long = 5_000_000,
    private val addressPolicy: (InetAddress) -> Boolean = ::isPublicAddress,
) : PageFetcher {
    private val enforce = !allowPrivateNetworks
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .apply { if (enforce) proxy(Proxy.NO_PROXY).dns(PublicOnlyDns(client.dns, addressPolicy)) }
        .build()

    override suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        var current = url.toHttpUrlOrNull() ?: throw IOException("Invalid URL: $url")
        repeat(MAX_REDIRECTS + 1) {
            if (enforce) requirePublicLiteral(current.host)
            client.newCall(Request.Builder().url(current).build()).execute().use { r ->
                if (r.isRedirect) {
                    val location = r.header("Location") ?: throw IOException("Redirect without Location")
                    current = r.request.url.resolve(location) ?: throw IOException("Unsupported redirect: $location")
                    return@repeat
                }
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val source = r.body.source()
                source.request(maxBytes + 1)
                if (source.buffer.size > maxBytes) throw IOException("Page larger than $maxBytes bytes")
                return@withContext source.readUtf8()
            }
        }
        throw IOException("Too many redirects")
    }

    private fun requirePublicLiteral(host: String) {
        val isLiteral = host.contains(':') || host.all { it.isDigit() || it == '.' }
        // Literals are parsed locally by getByName (no lookup happens).
        if (isLiteral && !addressPolicy(InetAddress.getByName(host))) throw IOException("$host is a non-public address")
    }

    private companion object { const val MAX_REDIRECTS = 10 }
}

class PublicOnlyDns(
    private val delegate: Dns = Dns.SYSTEM,
    private val policy: (InetAddress) -> Boolean = ::isPublicAddress,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        if (addresses.any { !policy(it) }) throw UnknownHostException("$hostname resolves to a non-public address")
        return addresses
    }
}

/** Strict allowlist of globally routable unicast space; everything special-purpose is rejected. */
fun isPublicAddress(address: InetAddress): Boolean {
    val b = address.address.map { it.toInt() and 0xFF }
    return if (b.size == 4) isPublicV4(b) else isPublicV6(b)
}

private fun isPublicV4(b: List<Int>): Boolean {
    val (a, o2, o3) = Triple(b[0], b[1], b[2])
    return when {
        a == 0 || a == 10 || a == 127 || a >= 224 -> false             // this-net, private, loopback, multicast/reserved/broadcast
        a == 100 && o2 in 64..127 -> false                             // CGNAT
        a == 169 && o2 == 254 -> false                                 // link-local
        a == 172 && o2 in 16..31 -> false
        a == 192 && o2 == 168 -> false
        a == 192 && o2 == 0 && (o3 == 0 || o3 == 2) -> false           // IETF protocol assignments, TEST-NET-1
        a == 198 && o2 in 18..19 -> false                              // benchmarking
        a == 198 && o2 == 51 && o3 == 100 -> false                     // TEST-NET-2
        a == 203 && o2 == 0 && o3 == 113 -> false                      // TEST-NET-3
        else -> true
    }
}

private fun isPublicV6(b: List<Int>): Boolean {
    if ((b[0] and 0xE0) != 0x20) return false                          // only 2000::/3 is global unicast
    if (b[0] == 0x20 && b[1] == 0x01 && b[2] == 0 && b[3] == 0) return false          // 2001::/32 Teredo
    if (b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0d && b[3] == 0xb8) return false    // 2001:db8::/32 documentation
    if (b[0] == 0x20 && b[1] == 0x02) return false                                    // 2002::/16 6to4 (embeds IPv4)
    return true
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
