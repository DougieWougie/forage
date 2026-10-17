package me.dougals.forage.data.auth

import android.content.SharedPreferences

/** Per-domain session cookie headers ("a=1; b=2"), as captured from a WebView login. */
interface SessionStore {
    fun save(domain: String, cookieHeader: String)
    fun get(domain: String): String?
    fun remove(domain: String)
    fun domains(): List<String>
    /** Cookies for a request host: exact domain or any parent domain with a stored session. */
    fun findForHost(host: String): String?

    companion object {
        fun matchHost(sessions: Map<String, String>, host: String): String? {
            val h = host.lowercase()
            return sessions.entries.firstOrNull { (domain, _) -> h == domain || h.endsWith(".$domain") }?.value
        }
    }
}

/** Pass EncryptedSharedPreferences in production; plain prefs are fine in tests. */
class PrefsSessionStore(private val prefs: SharedPreferences) : SessionStore {
    override fun save(domain: String, cookieHeader: String) {
        prefs.edit().putString(domain.lowercase(), cookieHeader).apply()
    }

    override fun get(domain: String): String? = prefs.getString(domain.lowercase(), null)

    override fun remove(domain: String) {
        prefs.edit().remove(domain.lowercase()).apply()
    }

    override fun domains(): List<String> = prefs.all.keys.sorted()

    override fun findForHost(host: String): String? =
        SessionStore.matchHost(prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap(), host)
}
