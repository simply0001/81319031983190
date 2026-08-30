package com.pocketpass.app.mii

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

object PretendoXml {
    fun parseMappedPid(xml: String): Long? {
        val root = parse(xml) ?: return null
        return root.children("mapped_id").firstNotNullOfOrNull { mapped ->
            mapped.childText("out_id")?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()
        }
    }

    fun parseMii(xml: String): PretendoMiiRecord? {
        val root = parse(xml) ?: return null
        val mii = root.children("mii").firstOrNull() ?: return null
        val data = mii.childText("data")
            ?.filterNot(Char::isWhitespace)
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val pid = mii.childText("pid")?.trim()?.toLongOrNull() ?: return null
        val portrait = mii.children("images").firstOrNull()
            ?.children("image")
            ?.firstOrNull { it.childText("type")?.trim() == "normal_face" }
            ?.let { image -> image.childText("url") ?: image.childText("cached_url") }
            ?.trim()
            ?.takeIf { it.startsWith("https://") }
        return PretendoMiiRecord(
            pid = pid,
            name = mii.childText("name")?.trim().orEmpty(),
            miiDataBase64 = data,
            portraitUrl = portrait,
        )
    }

    fun errorMessage(xml: String): String? {
        val root = parse(xml) ?: return null
        if (root.tagName != "errors") return null
        return root.children("error").firstNotNullOfOrNull { it.childText("message")?.trim() }
            ?: "Pretendo Network returned an error"
    }

    private fun parse(xml: String): Element? = runCatching {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        runCatching { factory.isExpandEntityReferences = false }
        runCatching { factory.isXIncludeAware = false }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
    }.getOrNull()

    private fun Element.children(name: String): List<Element> {
        val nodes = childNodes
        val matches = ArrayList<Element>()
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node is Element && node.tagName == name) matches.add(node)
        }
        return matches
    }

    private fun Element.childText(name: String): String? =
        children(name).firstOrNull()?.textContent
}
