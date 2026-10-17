package me.dougals.forage.data.xml

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream

class XmlParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Minimal namespace-stripped element tree; feeds and OPML are small enough to hold in memory. */
class XmlNode(val name: String, val attrs: Map<String, String>) {
    val children = mutableListOf<XmlNode>()
    private val textBuf = StringBuilder()

    val text: String get() = textBuf.toString().trim()

    fun child(name: String): XmlNode? = children.firstOrNull { it.name == name }
    fun children(name: String): List<XmlNode> = children.filter { it.name == name }
    fun childText(name: String): String? = child(name)?.text?.takeIf { it.isNotEmpty() }

    internal fun appendText(s: String) { textBuf.append(s) }

    companion object {
        fun parse(input: InputStream): XmlNode {
            try {
                val p = Xml.newPullParser()
                p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                p.setInput(input, null)
                val stack = ArrayDeque<XmlNode>()
                var root: XmlNode? = null
                while (true) {
                    when (p.next()) {
                        XmlPullParser.START_TAG -> {
                            val attrs = (0 until p.attributeCount).associate { p.getAttributeName(it) to p.getAttributeValue(it) }
                            val node = XmlNode(p.name, attrs)
                            stack.lastOrNull()?.children?.add(node)
                            if (root == null) root = node
                            stack.addLast(node)
                        }
                        XmlPullParser.TEXT -> stack.lastOrNull()?.appendText(p.text)
                        XmlPullParser.END_TAG -> stack.removeLast()
                        XmlPullParser.END_DOCUMENT -> break
                    }
                }
                if (stack.isNotEmpty()) throw XmlParseException("Unexpected end of document")
                return root ?: throw XmlParseException("Empty document")
            } catch (e: XmlPullParserException) {
                throw XmlParseException(e.message ?: "Malformed XML", e)
            } catch (e: IOException) {
                throw XmlParseException(e.message ?: "Unreadable XML", e)
            }
        }
    }
}
