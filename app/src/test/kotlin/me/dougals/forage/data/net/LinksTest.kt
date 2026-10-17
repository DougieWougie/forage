package me.dougals.forage.data.net

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LinksTest {
    @Test fun nextcloudNewsLinkIsAnHttpsWebUrlOnTheOfficialAppStore() {
        assertTrue(isWebUrl(Links.NEXTCLOUD_NEWS))
        assertTrue(Links.NEXTCLOUD_NEWS.startsWith("https://"))
        assertEquals("https://apps.nextcloud.com/apps/news", Links.NEXTCLOUD_NEWS)
    }
}
