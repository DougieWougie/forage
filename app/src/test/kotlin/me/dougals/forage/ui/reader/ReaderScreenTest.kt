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
package me.dougals.forage.ui.reader

import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import me.dougals.forage.domain.model.AppSettings
import me.dougals.forage.domain.model.Article
import me.dougals.forage.domain.model.ReaderFont
import me.dougals.forage.presentation.reader.ReaderFonts
import me.dougals.forage.presentation.reader.ReaderState
import me.dougals.forage.presentation.reader.ReaderViewModel
import me.dougals.forage.testutil.article
import me.dougals.forage.ui.theme.ForageTheme
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val story = article(1).copy(title = "Hello reader", author = "Ann", contentHtml = "<p>Body text</p>")
    private val state = MutableStateFlow(ReaderState())
    private val vm = mockk<ReaderViewModel>(relaxed = true).also { every { it.state } returns state }
    private var backs = 0
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    private fun show(article: Article? = story, settings: AppSettings = AppSettings()) {
        state.value = ReaderState(article, settings)
        rule.setContent { ForageTheme { ReaderScreen(onBack = { backs++ }, vm = vm) } }
        rule.waitForIdle()
    }

    private fun webView(): WebView? {
        fun find(v: View): WebView? = v as? WebView ?: (v as? ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it)) } }
        return find(rule.activity.window.decorView)
    }
    private fun loaded() = shadowOf(webView()!!).lastLoadDataWithBaseURL
    private fun client() = webView()!!.webViewClient
    private fun nextStarted(): Intent? = shadowOf(rule.activity).nextStartedActivity

    private fun request(url: String, method: String = "GET") = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = false
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = method
        override fun getRequestHeaders() = emptyMap<String, String>()
    }

    private fun serving(type: String, body: ByteArray, block: (MockWebServer) -> Unit) = MockWebServer().use { server ->
        server.start()
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", type).body(Buffer().write(body)).build())
        block(server)
    }

    // The toolbar

    @Test fun withoutAnArticleOnlyBackIsShown() {
        show(article = null)
        rule.onNodeWithContentDescription("Share").assertDoesNotExist()
        rule.onNodeWithContentDescription("Typography").assertDoesNotExist()
        assertNull(webView())
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test fun saveButtonTogglesAndReflectsTheSavedState() {
        show()
        rule.onNodeWithContentDescription("Save for offline").performClick()
        verify { vm.toggleSaved() }
        state.value = state.value.copy(article = story.copy(isSaved = true))
        rule.onNodeWithContentDescription("Remove from saved").assertExists()
    }

    @Test fun shareOffersTheArticleThroughTheSystemChooser() {
        show()
        rule.onNodeWithContentDescription("Share").performClick()
        val chooser = nextStarted()!!
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals("Share article", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE))
        @Suppress("DEPRECATION")
        assertEquals(Intent.ACTION_SEND, chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.action)
    }

    @Test fun openInBrowserOnlyForWebAddresses() {
        show()
        rule.onNodeWithContentDescription("Open in browser").performClick()
        val view = nextStarted()!!
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals("https://example.com/1", view.dataString)

        state.value = state.value.copy(article = story.copy(url = "urn:isbn:0451450523"))
        rule.onNodeWithContentDescription("Open in browser").assertDoesNotExist()
    }

    @Test fun typographySheetChangesTheFont() {
        show()
        rule.onNodeWithContentDescription("Typography").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Typeface").assertExists()
        rule.onNodeWithText("100%", substring = true).assertExists()
        rule.onNodeWithText("OpenDyslexic").performClick()
        verify { vm.setFont(ReaderFont.DYSLEXIC) }
    }

    // The page

    @Test fun articleIsLoadedWithItsOwnSiteAsTheBase() {
        show()
        val page = loaded()
        assertEquals("https://example.com/1", page.baseUrl)
        assertEquals("text/html", page.mimeType)
        assertTrue(page.data.contains("Hello reader"))
        assertTrue(page.data.contains("<p>Body text</p>"))
        assertTrue("byline", page.data.contains("Ann"))
    }

    @Test fun changingTheFontReloadsThePage() {
        show()
        assertFalse(loaded().data.contains("OpenDyslexic"))
        state.value = state.value.copy(settings = AppSettings(readerFont = ReaderFont.DYSLEXIC))
        rule.waitForIdle()
        assertTrue(loaded().data.contains("OpenDyslexic"))
    }

    @Test fun theWebViewRunsNoScriptsAndReadsNoFiles() {
        show()
        val s = webView()!!.settings
        assertFalse(s.javaScriptEnabled)
        assertFalse(s.allowFileAccess)
        assertFalse(s.allowContentAccess)
        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, s.mixedContentMode)
    }

    @Test fun tappedLinksOpenInTheBrowserNotTheReader() {
        show()
        val web = webView()!!
        assertTrue(client().shouldOverrideUrlLoading(web, request("https://other.example/story")))
        assertEquals("https://other.example/story", nextStarted()!!.dataString)
        assertTrue("non-web links are swallowed", client().shouldOverrideUrlLoading(web, request("intent://evil#Intent;end")))
        assertNull(nextStarted())
    }

    @Test fun nonWebSubresourcesAreLeftToTheWebView() {
        show()
        assertNull(client().shouldInterceptRequest(webView()!!, request("data:image/png;base64,iVBO")))
    }

    @Test fun theDyslexicFontIsServedFromTheApp() {
        show()
        val font = client().shouldInterceptRequest(webView()!!, request(ReaderFonts.REGULAR_URL))!!
        assertEquals("font/woff2", font.mimeType)
        assertEquals("*", font.responseHeaders["Access-Control-Allow-Origin"])
        assertTrue(font.data.readBytes().isNotEmpty())
    }

    @Test fun imagesOnTheLocalNetworkAreBlockedUnlessAllowed() = serving("image/png", png) { server ->
        show()
        val blocked = client().shouldInterceptRequest(webView()!!, request(server.url("/a.png").toString()))!!
        assertEquals(403, blocked.statusCode)
        assertEquals(0, server.requestCount)

        state.value = state.value.copy(settings = AppSettings(allowLocalNetwork = true))
        rule.waitForIdle()
        val image = client().shouldInterceptRequest(webView()!!, request(server.url("/a.png").toString()))!!
        assertTrue("not blocked", image.statusCode != 403) // this constructor leaves the status at 0, which WebView treats as OK
        assertEquals("image/png", image.mimeType)
        assertArrayEquals(png, image.data.readBytes())
    }

    @Test fun onlyImagesAreLetThrough() = serving("text/html", "<script>".toByteArray()) { server ->
        show(settings = AppSettings(allowLocalNetwork = true))
        val r = client().shouldInterceptRequest(webView()!!, request(server.url("/page").toString()))!!
        assertEquals(403, r.statusCode)
        assertNotNull(server.takeRequest())
    }

    @Test fun nonGetRequestsAreBlockedWithoutBeingSent() = serving("image/png", png) { server ->
        show(settings = AppSettings(allowLocalNetwork = true))
        val r = client().shouldInterceptRequest(webView()!!, request(server.url("/a.png").toString(), method = "POST"))!!
        assertEquals(403, r.statusCode)
        assertEquals(0, server.requestCount)
    }
}
