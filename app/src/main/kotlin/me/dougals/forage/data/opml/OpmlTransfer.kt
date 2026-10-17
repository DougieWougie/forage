package me.dougals.forage.data.opml

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.dougals.forage.data.xml.XmlParseException
import me.dougals.forage.domain.repository.FeedRepository
import java.io.InputStream

data class ImportResult(val added: Int, val skipped: Int, val failed: Int)

/** OPML import/export on top of [FeedRepository], so it works in both sync modes. */
class OpmlTransfer(private val repo: FeedRepository) {
    suspend fun import(input: InputStream): Result<ImportResult> {
        val feeds = try {
            withContext(Dispatchers.IO) { Opml.parse(input) }
        } catch (e: XmlParseException) {
            return Result.failure(IllegalArgumentException("Not a valid OPML file", e))
        }

        val folderIds = repo.observeFolders().first().associate { it.name.lowercase() to it.id }.toMutableMap()
        val unusableFolders = mutableSetOf<String>()
        val known = repo.observeFeeds().first().map { it.url }.toMutableSet()
        var added = 0; var skipped = 0; var failed = 0

        for (f in feeds) {
            if (!known.add(f.xmlUrl)) { skipped++; continue }
            val key = f.folder?.lowercase()
            val folderId = when {
                key == null || key in unusableFolders -> null
                else -> folderIds[key] ?: repo.createFolder(f.folder!!).getOrNull()?.id?.also { folderIds[key] = it }
                    ?: run { unusableFolders += key; null }
            }
            if (repo.addFeed(f.xmlUrl, folderId).isSuccess) added++ else failed++
        }
        return Result.success(ImportResult(added, skipped, failed))
    }

    suspend fun export(): String {
        val folders = repo.observeFolders().first().associate { it.id to it.name }
        return Opml.export(repo.observeFeeds().first().map { OpmlFeed(it.title, it.url, it.folderId?.let(folders::get)) })
    }
}
