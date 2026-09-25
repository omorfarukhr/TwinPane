package com.twinpane.app

object CodeFormatter {

    fun formatHtml(code: String): String {
        if (code.isBlank()) return code
        val lines = code.replace("\r", "").split("\n")
        val sb = StringBuilder()
        var indent = 0
        val voidTags = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr",
        )

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                sb.append("\n")
                continue
            }

            val isClosing = line.startsWith("</")
            if (isClosing && (indent > 0)) indent--

            val prefix = "  ".repeat(indent)
            sb.append(prefix).append(line).append("\n")

            // Count opening vs closing tags in this line
            if (!isClosing && line.startsWith("<") && !line.startsWith("<!") && !line.startsWith("<!--")) {
                val tagMatch = Regex("""<([a-zA-Z0-9]+)""").find(line)
                val tagName = tagMatch?.groupValues?.get(1)?.lowercase()
                val isSelfClosing = line.endsWith("/>") || ((tagName != null) && (tagName in voidTags))
                val hasInlineClose = (tagName != null) && line.contains("</$tagName>")

                if (!isSelfClosing && !hasInlineClose) {
                    indent++
                }
            }
        }
        return sb.toString().trimEnd()
    }

    fun formatCss(code: String): String {
        if (code.isBlank()) return code
        val clean = code.replace("\r", "")
            .replace(Regex("""\s*\{\s*"""), " {\n")
            .replace(Regex("""\s*;\s*"""), ";\n")
            .replace(Regex("""\s*\}\s*"""), "\n}\n")

        val lines = clean.split("\n")
        val sb = StringBuilder()
        var indent = 0

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("}")) {
                if (indent > 0) indent--
            }

            val prefix = "  ".repeat(indent)
            sb.append(prefix).append(line).append("\n")

            if (line.endsWith("{")) {
                indent++
            }
        }
        return sb.toString().trimEnd()
    }

    fun formatJs(code: String): String {
        if (code.isBlank()) return code
        val lines = code.replace("\r", "").split("\n")
        val sb = StringBuilder()
        var indent = 0

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                sb.append("\n")
                continue
            }

            if (line.startsWith("}") || line.startsWith("]") || line.startsWith(")")) {
                if (indent > 0) indent--
            }

            val prefix = "  ".repeat(indent)
            sb.append(prefix).append(line).append("\n")

            val opens = line.count { (it == '{') || (it == '[') }
            val closes = line.count { (it == '}') || (it == ']') }
            val diff = opens - closes
            if (diff > 0) {
                indent += diff
            }
        }
        return sb.toString().trimEnd()
    }

    fun format(code: String, tabIndex: Int): String {
        return when (tabIndex) {
            0 -> formatHtml(code)
            1 -> formatCss(code)
            else -> formatJs(code)
        }
    }
}
