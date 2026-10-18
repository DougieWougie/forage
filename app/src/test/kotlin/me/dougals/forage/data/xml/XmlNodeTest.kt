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
package me.dougals.forage.data.xml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
class XmlNodeTest {
    private fun parse(xml: String) = XmlNode.parse(xml.byteInputStream())

    @Test fun buildsATreeWithoutNamespacePrefixes() {
        val root = parse("""
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <item rdf:about="https://example.com/1"><dc:creator>Ann</dc:creator></item>
              <item rdf:about="https://example.com/2"/>
            </rdf:RDF>
        """.trimIndent())
        assertEquals("RDF", root.name)
        assertEquals(listOf("https://example.com/1", "https://example.com/2"), root.children("item").map { it.attrs["about"] })
        assertEquals("Ann", root.child("item")!!.childText("creator"))
    }

    @Test fun textIsJoinedAcrossCdataAndTrimmed() {
        val root = parse("<a>  one <![CDATA[<b>two</b>]]> three  </a>")
        assertEquals("one <b>two</b> three", root.text)
    }

    @Test fun missingOrBlankChildrenHaveNoText() {
        val root = parse("<a><blank>   </blank></a>")
        assertNull(root.childText("blank"))
        assertNull(root.childText("missing"))
        assertNull(root.child("missing"))
        assertEquals(emptyList<XmlNode>(), root.children("missing"))
    }

    @Test fun emptyDocumentIsRejected() {
        assertThrows(XmlParseException::class.java) { parse("") }
        assertThrows(XmlParseException::class.java) { parse("<?xml version=\"1.0\"?>") }
    }

    @Test fun unclosedElementsAreRejected() {
        assertThrows(XmlParseException::class.java) { parse("<rss><channel><title>t</title>") }
    }

    @Test fun malformedXmlIsRejected() {
        assertThrows(XmlParseException::class.java) { parse("<a><b></a>") }
    }

    @Test fun readErrorsPartWayThroughAreReportedAsParseErrors() {
        // Fails after the start, so the parser is past its encoding sniff and the IOException reaches us unwrapped.
        val failing = object : InputStream() {
            private val start = "<rss><channel><title>".toByteArray()
            private var pos = 0
            override fun read(): Int = if (pos < start.size) start[pos++].toInt() else throw IOException("connection reset")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= start.size) throw IOException("connection reset")
                val n = minOf(len, start.size - pos)
                start.copyInto(b, off, pos, pos + n); pos += n
                return n
            }
        }
        val e = assertThrows(XmlParseException::class.java) { XmlNode.parse(failing) }
        assertTrue(e.cause is IOException)
        assertEquals("connection reset", e.message)
    }
}
