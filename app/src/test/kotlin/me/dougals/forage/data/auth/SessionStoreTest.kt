package me.dougals.forage.data.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionStoreTest {
    private fun store(): SessionStore {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("test-sessions", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return PrefsSessionStore(prefs)
    }

    @Test fun savesAndReadsPerDomain() {
        val s = store()
        s.save("lwn.net", "a=1; b=2")
        s.save("example.com", "c=3")
        assertEquals("a=1; b=2", s.get("lwn.net"))
        assertEquals("c=3", s.get("example.com"))
    }

    @Test fun domainKeysAreCaseInsensitive() {
        val s = store()
        s.save("LWN.net", "a=1")
        assertEquals("a=1", s.get("lwn.NET"))
    }

    @Test fun removeForgetsDomain() {
        val s = store()
        s.save("lwn.net", "a=1")
        s.remove("lwn.net")
        assertNull(s.get("lwn.net"))
    }

    @Test fun listsDomainsWithSessions() {
        val s = store()
        s.save("b.com", "x=1")
        s.save("a.com", "x=1")
        assertEquals(listOf("a.com", "b.com"), s.domains())
    }

    @Test fun findForHostMatchesParentDomainButNotSuffixLookalike() {
        val s = store()
        s.save("lwn.net", "a=1")
        assertEquals("a=1", s.findForHost("lwn.net"))
        assertEquals("a=1", s.findForHost("www.lwn.net"))
        assertNull(s.findForHost("evil-lwn.net"))
        assertNull(s.findForHost("net"))
    }
}
