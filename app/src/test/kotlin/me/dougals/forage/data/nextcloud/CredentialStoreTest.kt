package me.dougals.forage.data.nextcloud

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CredentialStoreTest {
    private fun store(): CredentialStore {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("test-nc", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return PrefsCredentialStore(prefs)
    }

    @Test fun emptyByDefault() = assertNull(store().get())

    @Test fun roundTripsAndClears() {
        val s = store()
        val c = NextcloudCredentials("https://cloud.example.com", "dougie", "pw")
        s.save(c)
        assertEquals(c, s.get())
        s.clear()
        assertNull(s.get())
    }
}
