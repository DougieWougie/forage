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
package me.dougals.forage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Keeps docs/architecture.md true: `ui` and `presentation` use `domain` interfaces and never `data`, `data` never
 * reaches up into `ui`, `presentation` or the app classes, and `domain` is plain Kotlin. Only `di` and the root
 * package (`ForageApp`, `MainActivity`, ...) may know the concrete classes.
 */
class LayerRulesTest {
    private val base = "me.dougals.forage"

    /** The sub-packages of [base] each layer may refer to (itself included). `R` is the generated resource class. */
    private val mayUse = mapOf(
        "domain" to setOf("domain"),
        "presentation" to setOf("domain", "presentation"),
        "ui" to setOf("domain", "presentation", "ui"),
        "data" to setOf("data", "domain"),
    )

    /** Imports `domain` must not have: it is plain Kotlin, with no Android and no HTTP, database, HTML or DI library. */
    private val notInDomain = listOf("android.", "androidx.", "okhttp3.", "okio.", "retrofit2.", "org.jsoup.", "dagger.", "coil", "kotlinx.serialization.")
    private val allowedRoot = setOf("R")

    private fun code(text: String): String = text
        .replace(Regex("\"\"\".*?\"\"\"", RegexOption.DOT_MATCHES_ALL), "\"\"")   // raw strings
        .replace(Regex("\"(\\\\.|[^\"\\\\\n])*\""), "\"\"")                       // ordinary strings
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")            // block comments, after strings so "image/*" is safe
        .lines().joinToString("\n") { it.substringBefore("//") }

    /** What [layer]'s file [text] reaches that it should not. */
    internal fun violations(layer: String, text: String): List<String> {
        val body = code(text)
        val found = mutableListOf<String>()
        Regex("""\b${Regex.escape(base)}\.([A-Za-z_][A-Za-z0-9_]*)""").findAll(body).forEach { m ->
            val segment = m.groupValues[1]
            val ok = if (segment[0].isUpperCase()) segment in allowedRoot else segment in mayUse.getValue(layer)
            if (!ok) found += m.value
        }
        if (layer == "domain") {
            body.lines().filter { it.trimStart().startsWith("import ") }.map { it.trim().removePrefix("import ") }
                .filter { imp -> notInDomain.any { imp.startsWith(it) } }.forEach { found += it }
        }
        return found.distinct()
    }

    @Test fun noFileCrossesALayerItShouldNot() {
        val root = File("src/main/kotlin/" + base.replace('.', '/'))
        assertTrue(root.isDirectory, "run from the app module: ${root.absolutePath}")
        val problems = mayUse.keys.flatMap { layer ->
            File(root, layer).walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
                violations(layer, file.readText()).map { "${file.relativeTo(root)} -> $it" }
            }.toList()
        }
        assertEquals(emptyList<String>(), problems, "layer rules in docs/architecture.md are broken")
    }

    // The checker itself: it must flag what it claims to, or the test above proves nothing.

    @Test fun uiOrPresentationImportingDataIsFlagged() {
        val src = "package $base.presentation.x\nimport $base.data.content.OfflineArchiver\nclass A"
        assertEquals(listOf("$base.data"), violations("presentation", src))
        assertEquals(listOf("$base.data"), violations("ui", src))
    }

    @Test fun dataReachingUpIntoUiPresentationOrTheAppIsFlagged() {
        val src = "import $base.ui.EXTRA\nimport $base.MainActivity\nimport $base.presentation.UiText\nimport $base.di.AppModule"
        assertEquals(listOf("$base.ui", "$base.MainActivity", "$base.presentation", "$base.di"), violations("data", src))
    }

    @Test fun domainWithAndroidOrOkHttpIsFlagged() {
        val src = "import android.content.Context\nimport okhttp3.HttpUrl\nimport $base.data.X\nimport java.net.URI"
        assertEquals(listOf("$base.data", "android.content.Context", "okhttp3.HttpUrl"), violations("domain", src))
    }

    @Test fun aFullyQualifiedNameWithoutAnImportIsFlaggedToo() {
        assertEquals(listOf("$base.data"), violations("ui", "val x = $base.data.net.GuardedHttp(client)"))
    }

    @Test fun commentsAndAllowedReferencesAreNotFlagged() {
        val src = """
            package $base.ui.thing
            import $base.R
            import $base.domain.model.Article
            import $base.presentation.UiText
            import $base.ui.theme.ForageTheme
            /** Mentions $base.data.Foo in prose. */
            // and $base.di.Bar here
            class A
        """.trimIndent()
        assertEquals(emptyList<String>(), violations("ui", src))
    }

    @Test fun presentationMayNotImportUi() {
        assertEquals(listOf("$base.ui"), violations("presentation", "import $base.ui.FolderRequest"))
    }
}
