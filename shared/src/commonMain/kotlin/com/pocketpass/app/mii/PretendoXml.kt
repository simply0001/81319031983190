package com.pocketpass.app.mii

/**
 * Just enough XML for the Pretendo account API, with the same hardening the
 * old JVM DOM parser had: any DOCTYPE (and thus any external entity) makes
 * the whole document unreadable, unknown entities fail, attributes are
 * ignored.
 */
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

    private fun parse(xml: String): Node.Element? =
        runCatching { Parser(xml).document() }.getOrNull()

    private fun Node.Element.children(name: String): List<Node.Element> =
        content.filterIsInstance<Node.Element>().filter { it.tagName == name }

    private fun Node.Element.childText(name: String): String? =
        children(name).firstOrNull()?.textContent()

    private fun Node.Element.textContent(): String = buildString {
        content.forEach { node ->
            when (node) {
                is Node.Text -> append(node.value)
                is Node.Element -> append(node.textContent())
            }
        }
    }

    private sealed interface Node {
        class Element(val tagName: String) : Node {
            val content = mutableListOf<Node>()
        }

        class Text(val value: String) : Node
    }

    private class Parser(private val source: String) {
        private var position = 0

        fun document(): Node.Element {
            skipMisc()
            val root = element()
            skipMisc()
            check(position >= source.length) { "trailing content" }
            return root
        }

        // Whitespace, the <?xml?> prolog and comments; a DOCTYPE is rejected.
        private fun skipMisc() {
            while (true) {
                while (position < source.length && source[position].isWhitespace()) position++
                when {
                    lookingAt("<?") -> position = source.expect("?>", from = position)
                    lookingAt("<!--") -> position = source.expect("-->", from = position)
                    lookingAt("<!") -> error("declarations are not allowed")
                    else -> return
                }
            }
        }

        private fun element(): Node.Element {
            check(position < source.length && source[position] == '<') { "expected element" }
            position++
            val name = tagName()
            val element = Node.Element(name)
            skipAttributes()
            if (lookingAt("/>")) {
                position += 2
                return element
            }
            check(source[position] == '>') { "malformed start tag" }
            position++
            while (true) {
                check(position < source.length) { "unclosed <$name>" }
                when {
                    lookingAt("</") -> {
                        position += 2
                        val closing = tagName()
                        check(closing == name) { "mismatched closing tag" }
                        while (position < source.length && source[position].isWhitespace()) position++
                        check(position < source.length && source[position] == '>') {
                            "malformed closing tag"
                        }
                        position++
                        return element
                    }

                    lookingAt("<!--") -> position = source.expect("-->", from = position)

                    lookingAt("<![CDATA[") -> {
                        val start = position + "<![CDATA[".length
                        val end = source.indexOf("]]>", start)
                        check(end >= 0) { "unclosed CDATA" }
                        element.content.add(Node.Text(source.substring(start, end)))
                        position = end + 3
                    }

                    lookingAt("<!") || lookingAt("<?") -> error("markup is not allowed here")

                    source[position] == '<' -> element.content.add(element())

                    else -> {
                        val start = position
                        while (position < source.length && source[position] != '<') position++
                        element.content.add(Node.Text(decodeEntities(source.substring(start, position))))
                    }
                }
            }
        }

        private fun tagName(): String {
            val start = position
            while (
                position < source.length &&
                (source[position].isLetterOrDigit() || source[position] in "_-:.")
            ) {
                position++
            }
            check(position > start) { "missing tag name" }
            return source.substring(start, position)
        }

        // Attribute values are skipped, not kept; quotes may contain '>'.
        private fun skipAttributes() {
            while (position < source.length) {
                when (val current = source[position]) {
                    '>' -> return
                    '/' -> if (lookingAt("/>")) return else error("stray slash in tag")
                    '"', '\'' -> {
                        val end = source.indexOf(current, position + 1)
                        check(end >= 0) { "unclosed attribute value" }
                        position = end + 1
                    }

                    '<' -> error("nested angle bracket in tag")
                    else -> position++
                }
            }
            error("unclosed start tag")
        }

        private fun lookingAt(token: String): Boolean = source.startsWith(token, position)

        private fun String.expect(token: String, from: Int): Int {
            val index = indexOf(token, from)
            check(index >= 0) { "expected $token" }
            return index + token.length
        }

        private fun decodeEntities(text: String): String {
            if ('&' !in text) return text
            return buildString(text.length) {
                var index = 0
                while (index < text.length) {
                    val character = text[index]
                    if (character != '&') {
                        append(character)
                        index++
                        continue
                    }
                    val end = text.indexOf(';', index + 1)
                    check(end > index) { "unterminated entity" }
                    val entity = text.substring(index + 1, end)
                    append(
                        when {
                            entity == "amp" -> "&"
                            entity == "lt" -> "<"
                            entity == "gt" -> ">"
                            entity == "quot" -> "\""
                            entity == "apos" -> "'"
                            entity.startsWith("#x") || entity.startsWith("#X") ->
                                entity.drop(2).toInt(16).toChar().toString()

                            entity.startsWith("#") ->
                                entity.drop(1).toInt(10).toChar().toString()

                            else -> error("unknown entity &$entity;")
                        },
                    )
                    index = end + 1
                }
            }
        }
    }
}
