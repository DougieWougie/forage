package me.dougals.forage.data.mapper

import me.dougals.forage.data.local.ArticleEntity
import me.dougals.forage.data.local.FeedEntity
import me.dougals.forage.data.local.FolderEntity
import me.dougals.forage.domain.model.Article
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.Folder

fun FolderEntity.toDomain() = Folder(id, name)

fun FeedEntity.toDomain(unreadCount: Int = 0) = Feed(id, url, title, siteUrl, folderId, unreadCount)

fun ArticleEntity.toDomain() = Article(
    id, feedId, guid, title, url, author, summary, contentHtml, publishedAt, isRead, isSaved, offlineHtml,
)

fun Article.toEntity() = ArticleEntity(
    id = id, feedId = feedId, guid = guid, title = title, url = url, author = author, summary = summary,
    contentHtml = contentHtml, publishedAt = publishedAt, isRead = isRead, isSaved = isSaved,
    offlineHtml = offlineHtml,
)
