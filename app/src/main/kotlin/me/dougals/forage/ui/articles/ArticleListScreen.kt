package me.dougals.forage.ui.articles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.dougals.forage.presentation.articles.ArticleListViewModel
import me.dougals.forage.presentation.articles.ListSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    onOpenArticle: (Long) -> Unit,
    onBack: (() -> Unit)?,
    vm: ArticleListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.dismissError() }
    }

    val title = when (val s = vm.source) {
        ListSource.Unread -> "Unread"
        ListSource.Saved -> "Saved"
        is ListSource.Feed -> state.feedTitles[s.feedId] ?: "Feed"
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (vm.source != ListSource.Saved && state.articles.isNotEmpty()) {
                        IconButton(vm::markAllRead) { Icon(Icons.Filled.DoneAll, "Mark all as read") }
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.padding(inner).fillMaxSize(),
        ) {
            if (state.articles.isEmpty()) {
                EmptyState(vm.source)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.articles, key = { it.id }) { article ->
                        SwipeableArticleCard(
                            article = article,
                            feedTitle = state.feedTitles[article.feedId],
                            onClick = { onOpenArticle(article.id) },
                            onToggleRead = { vm.toggleRead(article) },
                            onToggleSaved = { vm.toggleSaved(article) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(source: ListSource) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (source) {
                    ListSource.Unread -> "All caught up"
                    ListSource.Saved -> "Nothing saved yet"
                    is ListSource.Feed -> "No articles"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                when (source) {
                    ListSource.Unread -> "Pull down to refresh, or add feeds under Categories."
                    ListSource.Saved -> "Swipe an article left to save it for offline reading."
                    is ListSource.Feed -> "Pull down to refresh."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
