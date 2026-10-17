/*
 * Forage - an RSS reader for Android
 * Copyright (C) 2026 Dougie Richardson
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version. It is distributed WITHOUT ANY WARRANTY;
 * see the LICENSE file for details.
 */
package me.dougals.forage.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.dougals.forage.data.opml.OpmlTransfer
import me.dougals.forage.domain.model.Feed
import me.dougals.forage.domain.model.Folder
import me.dougals.forage.domain.repository.FeedRepository
import java.io.InputStream
import javax.inject.Inject

/** [folder] is null for the "Uncategorized" group. */
data class CategoryNode(val folder: Folder?, val feeds: List<Feed>)

data class CategoriesState(
    val categories: List<CategoryNode> = emptyList(),
    val message: String? = null,
    val isBusy: Boolean = false,
)

fun buildCategories(folders: List<Folder>, feeds: List<Feed>): List<CategoryNode> {
    val byFolder = feeds.groupBy { it.folderId }
    val nodes = folders.sortedBy { it.name.lowercase() }.map { f ->
        CategoryNode(f, byFolder[f.id].orEmpty().sortedBy { it.title.lowercase() })
    }
    val loose = feeds.filter { f -> f.folderId == null || folders.none { it.id == f.folderId } }
    return if (loose.isEmpty()) nodes else nodes + CategoryNode(null, loose.sortedBy { it.title.lowercase() })
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repo: FeedRepository,
    private val opml: OpmlTransfer,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)

    val state: StateFlow<CategoriesState> = combine(repo.observeFolders(), repo.observeFeeds(), message, busy) { folders, feeds, msg, isBusy ->
        CategoriesState(buildCategories(folders, feeds), msg, isBusy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesState())

    fun addFeed(rawUrl: String, folderId: Long?) {
        val url = rawUrl.trim()
        if (url.isEmpty()) { message.value = "Enter a feed URL"; return }
        val normalized = if ("://" in url) url else "https://$url"
        runBusy {
            repo.addFeed(normalized, folderId)
                .onSuccess { message.value = "Added ${it.title}" }
                .onFailure { message.value = it.message ?: "Could not add feed" }
        }
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        runBusy { repo.createFolder(trimmed).onFailure { message.value = it.message ?: "Could not create folder" } }
    }

    fun deleteFolder(id: Long) = runBusy { repo.deleteFolder(id).onFailure { message.value = it.message ?: "Could not delete folder" } }
    fun removeFeed(id: Long) = runBusy { repo.removeFeed(id).onFailure { message.value = it.message ?: "Could not remove feed" } }
    fun markFeedRead(id: Long) { viewModelScope.launch { repo.markFeedRead(id) } }

    fun importOpml(input: InputStream) = runBusy {
        opml.import(input)
            .onSuccess { message.value = "Imported ${it.added}, skipped ${it.skipped}, failed ${it.failed}" }
            .onFailure { message.value = it.message ?: "Import failed" }
    }

    suspend fun exportOpml(): String = opml.export()

    fun dismissMessage() { message.value = null }

    private fun runBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try { block() } finally { busy.value = false }
        }
    }
}
