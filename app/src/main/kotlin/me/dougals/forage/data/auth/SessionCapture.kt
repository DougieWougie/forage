package me.dougals.forage.data.auth

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Persists the cookies a WebView holds for a login URL.
 * [cookieSource] is `CookieManager.getInstance()::getCookie` in production.
 */
class SessionCapture(private val cookieSource: (String) -> String?, private val store: SessionStore) {
    fun capture(url: String): Boolean {
        val host = url.toHttpUrlOrNull()?.host ?: return false
        val cookies = cookieSource(url)?.takeIf { it.isNotBlank() } ?: return false
        store.save(host, cookies)
        return true
    }
}
