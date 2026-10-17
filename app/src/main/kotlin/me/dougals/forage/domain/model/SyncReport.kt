package me.dougals.forage.domain.model

/** What a refresh actually changed, for telling the user. */
data class SyncReport(val newArticles: Int = 0, val changesSent: Int = 0, val feedsAdded: Int = 0) {
    fun summary(): String {
        val parts = buildList {
            if (feedsAdded > 0) add("$feedsAdded ${if (feedsAdded == 1) "feed" else "feeds"} added to Nextcloud")
            if (newArticles > 0) add("$newArticles new ${if (newArticles == 1) "article" else "articles"}")
            if (changesSent > 0) add("$changesSent ${if (changesSent == 1) "change" else "changes"} sent")
        }
        return if (parts.isEmpty()) "Already up to date" else "Synced · " + parts.joinToString(" · ")
    }
}
