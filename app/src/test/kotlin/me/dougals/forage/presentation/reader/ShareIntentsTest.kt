package me.dougals.forage.presentation.reader

import android.content.Intent
import me.dougals.forage.testutil.article
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareIntentsTest {
    @Test fun buildsActionSendWithTitleAndUrl() {
        val a = article(1).copy(title = "Great read", url = "https://example.com/p")
        val i = ShareIntents.send(a)
        assertEquals(Intent.ACTION_SEND, i.action)
        assertEquals("text/plain", i.type)
        assertEquals("Great read", i.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Great read\nhttps://example.com/p", i.getStringExtra(Intent.EXTRA_TEXT))
    }
}
