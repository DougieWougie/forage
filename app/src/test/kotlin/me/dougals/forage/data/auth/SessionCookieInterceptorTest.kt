package me.dougals.forage.data.auth

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SessionCookieInterceptorTest {
    private val store = object : SessionStore {
        val map = mutableMapOf<String, String>()
        override fun save(domain: String, cookieHeader: String) { map[domain] = cookieHeader }
        override fun get(domain: String) = map[domain]
        override fun remove(domain: String) { map.remove(domain) }
        override fun domains() = map.keys.sorted()
        override fun findForHost(host: String) = SessionStore.matchHost(map, host)
    }
    private lateinit var server: MockWebServer
    private lateinit var other: MockWebServer

    @BeforeEach fun up() { server = MockWebServer().apply { start() }; other = MockWebServer().apply { start() } }
    @AfterEach fun down() { server.close(); other.close() }

    private fun client(allowInsecure: Boolean = true) = OkHttpClient.Builder()
        .addNetworkInterceptor(SessionCookieInterceptor(store, allowInsecure))
        .build()

    @Test fun attachesStoredCookiesForMatchingHost() {
        store.save("localhost", "sid=abc")
        server.enqueue(MockResponse.Builder().build())
        client().newCall(Request.Builder().url(server.url("/feed")).build()).execute().close()
        assertEquals("sid=abc", server.takeRequest().headers["Cookie"])
    }

    @Test fun noCookieForUnknownHost() {
        store.save("example.org", "sid=abc")
        server.enqueue(MockResponse.Builder().build())
        client().newCall(Request.Builder().url(server.url("/feed")).build()).execute().close()
        assertNull(server.takeRequest().headers["Cookie"])
    }

    @Test fun neverSentOverPlainHttpByDefault() {
        store.save("localhost", "sid=abc")
        server.enqueue(MockResponse.Builder().build())
        client(allowInsecure = false).newCall(Request.Builder().url(server.url("/feed")).build()).execute().close()
        assertNull(server.takeRequest().headers["Cookie"])
    }

    @Test fun doesNotOverrideExplicitCookieHeader() {
        store.save("localhost", "sid=abc")
        server.enqueue(MockResponse.Builder().build())
        client().newCall(Request.Builder().url(server.url("/")).header("Cookie", "mine=1").build()).execute().close()
        assertEquals("mine=1", server.takeRequest().headers["Cookie"])
    }

    @Test fun cookiesAreNotForwardedToADifferentHostOnRedirect() {
        store.save("localhost", "sid=abc")
        val target = other.url("/landing").newBuilder().host("127.0.0.1").build()
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", target.toString()).build())
        other.enqueue(MockResponse.Builder().build())
        client().newCall(Request.Builder().url(server.url("/feed")).build()).execute().close()
        assertEquals("sid=abc", server.takeRequest().headers["Cookie"])
        assertNull(other.takeRequest().headers["Cookie"])
    }
}
