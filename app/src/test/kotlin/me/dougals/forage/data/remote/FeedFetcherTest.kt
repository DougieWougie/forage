package me.dougals.forage.data.remote

import kotlinx.coroutines.test.runTest
import me.dougals.forage.data.auth.SessionCookieInterceptor
import me.dougals.forage.data.auth.SessionStore
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedFetcherTest {
    private val rss = """<rss version="2.0"><channel><title>Locked</title>
        <item><title>Paid</title><link>https://lwn.net/a</link></item></channel></rss>"""

    private fun withServer(block: suspend (MockWebServer, FeedFetcher) -> Unit) = runTest {
        val store = object : SessionStore {
            val map = mutableMapOf("localhost" to "sid=abc")
            override fun save(domain: String, cookieHeader: String) { map[domain] = cookieHeader }
            override fun get(domain: String) = map[domain]
            override fun remove(domain: String) { map.remove(domain) }
            override fun domains() = map.keys.sorted()
            override fun findForHost(host: String) = SessionStore.matchHost(map, host)
        }
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(SessionCookieInterceptor(store, allowInsecure = true)).build()
        MockWebServer().use { server ->
            server.start()
            block(server, FeedFetcher(client, FeedParser()))
        }
    }

    @Test fun fetchesParsesAndSendsSessionCookie() = withServer { server, fetcher ->
        server.enqueue(MockResponse.Builder().body(rss).build())
        val feed = fetcher.fetch(server.url("/rss").toString())
        assertEquals("Locked", feed.title)
        assertEquals("sid=abc", server.takeRequest().headers["Cookie"])
    }

    @Test fun httpErrorBecomesFetchException() = withServer { server, fetcher ->
        server.enqueue(MockResponse.Builder().code(403).build())
        val e = assertThrows(FeedFetchException::class.java) {
            kotlinx.coroutines.runBlocking { fetcher.fetch(server.url("/rss").toString()) }
        }
        assertEquals(403, e.httpCode)
    }
}
