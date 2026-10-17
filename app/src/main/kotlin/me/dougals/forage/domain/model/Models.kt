package me.dougals.forage.domain.model

data class Folder(val id: Long, val name: String)

data class Feed(
    val id: Long,
    val url: String,
    val title: String,
    val siteUrl: String?,
    val folderId: Long?,
    val unreadCount: Int,
)

data class Article(
    val id: Long,
    val feedId: Long,
    val guid: String,
    val title: String,
    val url: String,
    val author: String?,
    val summary: String?,
    val contentHtml: String?,
    val publishedAt: Long,
    val isRead: Boolean,
    val isSaved: Boolean,
    val offlineHtml: String?,
) {
    val hasOfflineCopy: Boolean get() = offlineHtml != null

    /** Best available body for the reader: cleaned offline copy, then feed content, then summary. */
    val readerHtml: String get() = offlineHtml ?: contentHtml ?: summary ?: ""
}

enum class SyncMode { LOCAL, NEXTCLOUD }
