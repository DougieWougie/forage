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
package me.dougals.forage.ui.login

import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.dougals.forage.ui.theme.ForageTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class LoginScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val vm = mockk<LoginViewModel>(relaxed = true)
    private val cookies get() = CookieManager.getInstance()
    private var done = 0
    private var visible by mutableStateOf(true)

    private fun show(startUrl: String = "https://lwn.net/login") {
        rule.setContent { ForageTheme { if (visible) LoginScreen(startUrl, onDone = { done++ }, vm = vm) } }
        rule.waitForIdle()
    }

    private fun webView(): WebView? {
        fun find(v: View): WebView? = v as? WebView ?: (v as? ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it)) } }
        return find(rule.activity.window.decorView)
    }

    @Test fun loginFormsGetScriptsButNoFileAccess() {
        show()
        val s = webView()!!.settings
        assertTrue(s.javaScriptEnabled)
        assertTrue(s.domStorageEnabled)
        assertFalse(s.allowFileAccess)
        assertFalse(s.allowContentAccess)
        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, s.mixedContentMode)
    }

    @Test fun oldCookiesAreWipedBeforeTheLoginPageLoads() {
        cookies.setCookie("https://lwn.net", "sid=stale")
        show()
        assertNull(cookies.getCookie("https://lwn.net"))
        assertEquals("https://lwn.net/login", shadowOf(webView()!!).lastLoadedUrl)
    }

    @Test fun onlyHttpsPagesAreLoaded() {
        show(startUrl = "http://lwn.net/login")
        assertNull(shadowOf(webView()!!).lastLoadedUrl)
    }

    @Test fun savingCapturesTheCurrentPageAndCloses() {
        every { vm.capture(any()) } returns true
        show()
        rule.onNodeWithText("Save session").performClick()
        verify { vm.capture("https://lwn.net/login") }
        assertEquals(1, done)
    }

    @Test fun savingWithNoSessionYetExplainsAndStays() {
        every { vm.capture(any()) } returns false
        show()
        rule.onNodeWithText("Save session").performClick()
        rule.onNodeWithText("No session cookies found yet — finish signing in first.").assertExists()
        assertEquals(0, done)
    }

    @Test fun savingBeforeAnyPageLoadsCapturesNothing() {
        show(startUrl = "http://lwn.net/login")
        rule.onNodeWithText("Save session").performClick()
        verify(exactly = 0) { vm.capture(any()) }
        rule.onNodeWithText("No session cookies found yet — finish signing in first.").assertExists()
    }

    @Test fun cancelClosesWithoutCapturing() {
        show()
        rule.onNodeWithContentDescription("Cancel").performClick()
        assertEquals(1, done)
        verify(exactly = 0) { vm.capture(any()) }
    }

    @Test fun leavingTheScreenWipesTheWebViewCookies() {
        show()
        cookies.setCookie("https://lwn.net", "sid=fresh")
        visible = false
        rule.waitForIdle()
        assertNull(webView())
        assertNull(cookies.getCookie("https://lwn.net"))
    }
}
