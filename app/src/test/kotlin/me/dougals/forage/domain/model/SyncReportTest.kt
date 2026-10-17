package me.dougals.forage.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyncReportTest {
    @Test fun nothingChangedSaysUpToDate() = assertEquals("Already up to date", SyncReport().summary())

    @Test fun singularAndPlural() {
        assertEquals("Synced · 1 new article", SyncReport(newArticles = 1).summary())
        assertEquals("Synced · 1 change sent", SyncReport(changesSent = 1).summary())
        assertEquals("Synced · 1 feed added to Nextcloud", SyncReport(feedsAdded = 1).summary())
        assertEquals("Synced · 2 feeds added to Nextcloud", SyncReport(feedsAdded = 2).summary())
    }

    @Test fun everythingInAStableOrder() {
        assertEquals(
            "Synced · 1 feed added to Nextcloud · 18 new articles · 3 changes sent",
            SyncReport(newArticles = 18, changesSent = 3, feedsAdded = 1).summary(),
        )
    }
}
