package me.dougals.forage.ui.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.Article
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.presentation.reader.ReaderHtml
import me.dougals.forage.presentation.reader.ReaderStyle
import me.dougals.forage.presentation.reader.ReaderViewModel
import me.dougals.forage.presentation.reader.ShareIntents
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(onBack: () -> Unit, vm: ReaderViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showType by remember { mutableStateOf(false) }
    val article = state.article

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (article != null) {
                        IconButton({ showType = true }) { Icon(Icons.Filled.TextFormat, "Typography") }
                        IconButton({ vm.toggleSaved() }) {
                            Icon(
                                if (article.isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                if (article.isSaved) "Remove from saved" else "Save for offline",
                            )
                        }
                        IconButton({ context.startActivity(Intent.createChooser(ShareIntents.send(article), "Share article")) }) {
                            Icon(Icons.Filled.Share, "Share")
                        }
                        IconButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url))) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in browser")
                        }
                    }
                },
            )
        },
    ) { inner ->
        if (article != null) {
            ArticleWebView(article, state.settings, Modifier.padding(inner).fillMaxSize())
        }
    }

    if (showType && article != null) {
        TypographySheet(state.settings, vm::setFont, vm::setFontScale, onDismiss = { showType = false })
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ArticleWebView(article: Article, prefs: AppSettings, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val style = ReaderStyle(prefs.readerFont, prefs.fontScale, scheme.background.css(), scheme.onBackground.css(), scheme.primary.css())
    val context = LocalContext.current
    val byline = remember(article.id) {
        listOfNotNull(
            article.author,
            article.publishedAt.takeIf { it > 0 }?.let { DateFormat.getMediumDateFormat(context).format(Date(it)) },
        ).joinToString(" · ").ifEmpty { null }
    }
    val html = remember(article.id, article.readerHtml, style, byline) { ReaderHtml.wrap(article.title, byline, article.readerHtml, style) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false // the page never needs scripts; the CSP forbids them as well
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportZoom(false)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (request.url.scheme in listOf("http", "https")) {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                        }
                        return true
                    }
                }
            }
        },
        update = { web ->
            web.setBackgroundColor(scheme.background.toArgb())
            if (web.tag != html) {
                web.tag = html
                web.loadDataWithBaseURL(article.url, html, "text/html", "utf-8", null)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypographySheet(
    settings: AppSettings,
    onFont: (ReaderFont) -> Unit,
    onScale: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(settings.fontScale) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Typeface", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(settings.readerFont == ReaderFont.SERIF, { onFont(ReaderFont.SERIF) }, label = { Text("Serif") })
                FilterChip(settings.readerFont == ReaderFont.SANS, { onFont(ReaderFont.SANS) }, label = { Text("Sans-serif") })
            }
            Text("Text size  ${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("A", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = scale, onValueChange = { scale = it }, onValueChangeFinished = { onScale(scale) },
                    valueRange = AppSettings.MIN_SCALE..AppSettings.MAX_SCALE, modifier = Modifier.weight(1f),
                )
                Text("A", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

private fun Color.css(): String = String.format("#%06X", toArgb() and 0xFFFFFF)
