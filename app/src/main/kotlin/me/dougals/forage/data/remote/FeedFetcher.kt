package me.dougals.forage.data.remote

import me.dougals.forage.data.net.GuardedHttp
import me.dougals.forage.data.net.HttpStatusException
import me.dougals.forage.data.net.isPublicAddress
import me.dougals.forage.data.net.readBytesBounded
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetAddress

class FeedFetchException(message: String, val httpCode: Int? = null, cause: Throwable? = null) :
    Exception(message, cause)

fun interface FeedSource {
    suspend fun fetch(url: String): ParsedFeed
}

/**
 * Feed URLs arrive via OPML files, user input and redirects, so they get the same SSRF guard as article pages
 * unless the user has opted in to local-network feeds ([allowLocalNetwork]).
 */
class FeedFetcher(
    client: OkHttpClient,
    private val parser: FeedParser,
    private val allowLocalNetwork: suspend () -> Boolean = { false },
    private val maxBytes: Long = 10_000_000,
    addressPolicy: (InetAddress) -> Boolean = ::isPublicAddress,
) : FeedSource {
    private val http = GuardedHttp(client, addressPolicy)

    override suspend fun fetch(url: String): ParsedFeed = try {
        http.get(url, allowLocalNetwork()) { r ->
            parser.parse(ByteArrayInputStream(r.readBytesBounded(maxBytes)), r.request.url.toString())
        }
    } catch (e: HttpStatusException) {
        throw FeedFetchException("HTTP ${e.code}", e.code, e)
    } catch (e: IOException) {
        throw FeedFetchException(e.message ?: "Network error", cause = e)
    }
}
