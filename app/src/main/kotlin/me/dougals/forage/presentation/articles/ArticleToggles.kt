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
package me.dougals.forage.presentation.articles

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.dougals.forage.domain.model.Article
import me.dougals.forage.domain.repository.FeedRepository

/** A read or saved toggle just made on [articleId], now [value], kept so the snackbar can flip it back. */
data class ToggleUndo(val articleId: Long, val field: Field, val value: Boolean) {
    enum class Field { READ, SAVED }
}

/** Read and saved toggles on article cards, remembering the latest one so it can be undone. */
class ArticleToggles(private val repo: FeedRepository, private val scope: CoroutineScope) {
    private val latest = MutableStateFlow<ToggleUndo?>(null)
    val pending: StateFlow<ToggleUndo?> = latest

    fun toggleRead(article: Article) {
        val now = !article.isRead
        latest.value = ToggleUndo(article.id, ToggleUndo.Field.READ, now)
        scope.launch { repo.setRead(article.id, now) }
    }

    fun toggleSaved(article: Article) {
        val now = !article.isSaved
        latest.value = ToggleUndo(article.id, ToggleUndo.Field.SAVED, now)
        scope.launch { repo.setSaved(article.id, now) }
    }

    fun undo() {
        val undo = latest.value ?: return
        latest.value = null
        scope.launch {
            when (undo.field) {
                ToggleUndo.Field.READ -> repo.setRead(undo.articleId, !undo.value)
                ToggleUndo.Field.SAVED -> repo.setSaved(undo.articleId, !undo.value)
            }
        }
    }

    fun dismiss() { latest.value = null }
}
