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
package me.dougals.forage.ui.articles

import me.dougals.forage.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.dougals.forage.domain.model.Article
import me.dougals.forage.domain.model.ThumbnailSide
import me.dougals.forage.presentation.articles.ArticleFreshness
import me.dougals.forage.presentation.articles.ArticleSnippet

/**
 * Swipe right: toggle read/unread. Swipe left: toggle saved (bookmark + offline copy).
 * The card always snaps back after firing; whether it leaves the list is up to the list's own filter.
 * The list offers an undo snackbar for each toggle.
 * [savedOnly] is for the Saved tab: no read/unread swipe or action, and the card is never dimmed.
 */
@Composable
fun SwipeableArticleCard(
    article: Article,
    feedTitle: String?,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier,
    onMarkOlderRead: (() -> Unit)? = null,
    showThumbnail: Boolean = false,
    thumbnailFromCacheOnly: Boolean = false,
    thumbnailSide: ThumbnailSide = ThumbnailSide.END,
    savedOnly: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val currentArticle by rememberUpdatedState(article)
    val readAction by rememberUpdatedState(onToggleRead)
    val saveAction by rememberUpdatedState(onToggleSaved)
    val readLabel = stringResource(if (article.isRead) R.string.action_mark_unread else R.string.action_mark_read)
    val saveLabel = stringResource(if (article.isSaved) R.string.action_remove_saved else R.string.action_save)
    val olderLabel = stringResource(R.string.action_mark_older_read)

    // Side effects must not live in confirmValueChange: it is re-evaluated while the swipe settles and
    // would fire the action several times. currentValue changes exactly once per completed swipe.
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                haptics.performHapticFeedback(if (currentArticle.isRead) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                readAction()
                state.snapTo(SwipeToDismissBoxValue.Settled)
            }
            SwipeToDismissBoxValue.EndToStart -> {
                haptics.performHapticFeedback(if (currentArticle.isSaved) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                saveAction()
                state.snapTo(SwipeToDismissBoxValue.Settled)
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = !savedOnly,
        backgroundContent = { SwipeBackground(state.dismissDirection, state.targetValue, article) },
    ) {
        ArticleCardContent(
            article, feedTitle, onClick, showThumbnail, thumbnailFromCacheOnly, thumbnailSide, onMarkOlderRead, dimWhenRead = !savedOnly,
            Modifier.semantics {
                customActions = listOfNotNull(
                    if (savedOnly) null else CustomAccessibilityAction(readLabel) { onToggleRead(); true },
                    CustomAccessibilityAction(saveLabel) { onToggleSaved(); true },
                    onMarkOlderRead?.let { action -> CustomAccessibilityAction(olderLabel) { action(); true } },
                )
            },
        )
    }
}

/** Solid background and icon colours for a swipe: one pair per action, so each reads differently at a glance. */
internal data class SwipeColors(val container: Color, val content: Color)

internal fun swipeColors(scheme: ColorScheme, direction: SwipeToDismissBoxValue, article: Article, armed: Boolean): SwipeColors {
    // Before the threshold the colour is the softer container; past it, the full role colour confirms the action.
    return when (direction) {
        SwipeToDismissBoxValue.StartToEnd ->
            if (article.isRead) {
                if (armed) SwipeColors(scheme.secondary, scheme.onSecondary) else SwipeColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
            } else {
                if (armed) SwipeColors(scheme.tertiary, scheme.onTertiary) else SwipeColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
            }
        SwipeToDismissBoxValue.EndToStart ->
            if (article.isSaved) {
                if (armed) SwipeColors(scheme.error, scheme.onError) else SwipeColors(scheme.errorContainer, scheme.onErrorContainer)
            } else {
                if (armed) SwipeColors(scheme.primary, scheme.onPrimary) else SwipeColors(scheme.primaryContainer, scheme.onPrimaryContainer)
            }
        SwipeToDismissBoxValue.Settled -> SwipeColors(scheme.surface, scheme.onSurface)
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, target: SwipeToDismissBoxValue, article: Article) {
    val armed = target != SwipeToDismissBoxValue.Settled
    val colors = swipeColors(MaterialTheme.colorScheme, direction, article, armed)
    val container by animateColorAsState(colors.container, label = "swipe-bg")
    val iconScale by animateFloatAsState(if (armed) 1.25f else 0.85f, spring(dampingRatio = 0.5f), label = "swipe-icon")
    val icon: ImageVector? = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> if (article.isRead) Icons.Filled.MarkEmailUnread else Icons.Filled.MarkEmailRead
        SwipeToDismissBoxValue.EndToStart -> if (article.isSaved) Icons.Filled.BookmarkRemove else Icons.Filled.BookmarkAdd
        SwipeToDismissBoxValue.Settled -> null
    }
    Box(
        Modifier.fillMaxSize().background(container).padding(horizontal = 28.dp),
        contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = { (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut()) },
            label = "swipe-icon-swap",
        ) { vector ->
            if (vector != null) {
                Icon(vector, contentDescription = null, modifier = Modifier.size(28.dp).scale(iconScale), tint = colors.content)
            }
        }
    }
}

/** Read articles recede, except where read state is not the point (the Saved tab). */
internal fun cardAlpha(article: Article, dimWhenRead: Boolean): Float = if (article.isRead && dimWhenRead) 0.62f else 1f

@Composable
private fun ArticleCardContent(
    article: Article,
    feedTitle: String?,
    onClick: () -> Unit,
    showThumbnail: Boolean,
    thumbnailFromCacheOnly: Boolean,
    thumbnailSide: ThumbnailSide,
    onMarkOlderRead: (() -> Unit)?,
    dimWhenRead: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = onMarkOlderRead?.let {
                { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menuOpen = true }
            },
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp).alpha(cardAlpha(article, dimWhenRead)),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val image = article.imageUrl.takeIf { showThumbnail }
            if (image != null && thumbnailSide == ThumbnailSide.START) Thumbnail(image, thumbnailFromCacheOnly)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!article.isRead) UnreadMarker(ArticleFreshness.of(article.publishedAt, System.currentTimeMillis()))
                    Text(
                        listOfNotNull(feedTitle?.uppercase(), relativeTime(article.publishedAt)).joinToString("  ·  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.weight(1f))
                    if (article.hasOfflineCopy) {
                        Icon(Icons.Filled.DownloadDone, stringResource(R.string.cd_available_offline), Modifier.size(16.dp), MaterialTheme.colorScheme.outline)
                    }
                    if (article.isSaved) {
                        Icon(Icons.Filled.Bookmark, stringResource(R.string.cd_saved), Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.size(6.dp))
                Text(article.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val snippet = ArticleSnippet.of(article)
                if (snippet.isNotEmpty()) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        snippet, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (image != null && thumbnailSide == ThumbnailSide.END) Thumbnail(image, thumbnailFromCacheOnly)
        }
    }
    if (menuOpen) {
        AlertDialog(
            onDismissRequest = { menuOpen = false },
            title = { Text(stringResource(R.string.action_mark_older_read)) },
            text = { Text(stringResource(R.string.dialog_mark_older_read)) },
            confirmButton = {
                TextButton({ menuOpen = false; onMarkOlderRead?.invoke() }) { Text(stringResource(R.string.action_mark_read)) }
            },
            dismissButton = { TextButton({ menuOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Decorative: the title beside it already says what the article is. Drops out of the layout if the image fails. */
@Composable
private fun Thumbnail(url: String, fromCacheOnly: Boolean) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(url)
            .networkCachePolicy(if (fromCacheOnly) CachePolicy.DISABLED else CachePolicy.ENABLED)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

private fun relativeTime(millis: Long): String? =
    if (millis <= 0) null
    else DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

/** The unread marker: a leaf that wilts then browns with age, or the plain dot when the date is unknown. */
@Composable
private fun UnreadMarker(freshness: ArticleFreshness?) {
    if (freshness == null) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        return
    }
    // Shape differs per stage as well as colour, so the age does not rest on telling green from brown.
    val (icon, tint, description) = when (freshness) {
        ArticleFreshness.FRESH -> Triple(R.drawable.ic_leaf_fresh, R.color.freshness_fresh, R.string.cd_freshness_fresh)
        ArticleFreshness.WILTING -> Triple(R.drawable.ic_leaf_wilting, R.color.freshness_wilting, R.string.cd_freshness_wilting)
        ArticleFreshness.OLD -> Triple(R.drawable.ic_leaf_old, R.color.freshness_old, R.string.cd_freshness_old)
    }
    Icon(painterResource(icon), stringResource(description), Modifier.size(22.dp), colorResource(tint))
}
