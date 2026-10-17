package me.dougals.forage.data.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionCaptureTest {
    private class MemStore : SessionStore {
        val map = mutableMapOf<String, String>()
        override fun save(domain: String, cookieHeader: String) { map[domain.lowercase()] = cookieHeader }
        override fun get(domain: String) = map[domain.lowercase()]
        override fun remove(domain: String) { map.remove(domain.lowercase()) }
        override fun domains() = map.keys.sorted()
        override fun findForHost(host: String) = SessionStore.matchHost(map, host)
    }

    @Test fun storesCookiesUnderTheHostOfTheLoginUrl() {
        val store = MemStore()
        val capture = SessionCapture({ "sid=abc; theme=dark" }, store)
        assertTrue(capture.capture("https://lwn.net/Login/"))
        assertEquals("sid=abc; theme=dark", store.get("lwn.net"))
    }

    @Test fun noCookiesMeansNothingStored() {
        val store = MemStore()
        assertFalse(SessionCapture({ null }, store).capture("https://lwn.net/"))
        assertFalse(SessionCapture({ "" }, store).capture("https://lwn.net/"))
        assertNull(store.get("lwn.net"))
    }

    @Test fun invalidUrlIsRejected() {
        assertFalse(SessionCapture({ "a=b" }, MemStore()).capture("not a url"))
    }
}
