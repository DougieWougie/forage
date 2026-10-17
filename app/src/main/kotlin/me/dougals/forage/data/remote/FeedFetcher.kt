package me.dougals.forage.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class FeedFetchException(message: String, val httpCode: Int? = null, cause: Throwable? = null) :
    Exception(message, cause)

fun interface FeedSource {
    suspend fun fetch(url: String): ParsedFeed
}

class FeedFetcher(private val client: OkHttpClient, private val parser: FeedParser) : FeedSource {
    override suspend fun fetch(url: String): ParsedFeed = withContext(Dispatchers.IO) {
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            throw FeedFetchException("Invalid URL: $url", cause = e)
        }
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw FeedFetchException("HTTP ${response.code}", response.code)
                parser.parse(response.body.byteStream(), response.request.url.toString())
            }
        } catch (e: IOException) {
            throw FeedFetchException(e.message ?: "Network error", cause = e)
        }
    }
}
