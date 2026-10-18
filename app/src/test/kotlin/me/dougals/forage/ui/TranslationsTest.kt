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
package me.dougals.forage.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element

class TranslationsTest {
    private val res = File("src/main/res")
    private val languages = listOf("de", "fr", "es")

    /** Resource name to its text, for translatable strings; plurals map each quantity to `name#quantity`. */
    private fun load(dir: String, translatableOnly: Boolean = false): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$dir/strings.xml"))
        val out = linkedMapOf<String, String>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            if (translatableOnly && e.getAttribute("translatable") == "false") continue
            val name = e.getAttribute("name")
            when (e.tagName) {
                "string" -> out[name] = e.textContent
                "plurals" -> {
                    val items = e.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val item = items.item(j) as Element
                        out["$name#${item.getAttribute("quantity")}"] = item.textContent
                    }
                }
            }
        }
        return out
    }

    private fun placeholders(text: String) = Regex("%(\\d+\\$)?[ds]").findAll(text).map { it.value.removePrefix("%").substringBefore('$') }.toList().sorted()

    @Test
    fun everyTranslatableStringIsTranslated() {
        val source = load("values", translatableOnly = true).keys.map { it.substringBefore('#') }.toSet()
        for (lang in languages) {
            val translated = load("values-$lang").keys.map { it.substringBefore('#') }.toSet()
            assertEquals(emptySet<String>(), source - translated, "$lang is missing strings")
            assertEquals(emptySet<String>(), translated - source, "$lang has strings English does not")
        }
    }

    @Test
    fun translationsKeepTheEnglishPlaceholders() {
        val english = load("values", translatableOnly = true).filterKeys { !it.contains('#') }
        for (lang in languages) {
            val translated = load("values-$lang")
            for ((name, text) in english) {
                assertEquals(placeholders(text), placeholders(translated.getValue(name)), "$lang/$name")
            }
        }
    }

    @Test
    fun everyLanguageIsDeclaredForTheLanguagePicker() {
        assertTrue(File(res, "resources.properties").readText().contains("unqualifiedResLocale=en-US"))
        languages.forEach { assertTrue(File(res, "values-$it/strings.xml").exists(), it) }
    }
}
