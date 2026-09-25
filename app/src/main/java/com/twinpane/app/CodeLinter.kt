package com.twinpane.app

/** tab: 0 = HTML, 1 = CSS, 2 = JS । line 0 মানে line জানা নেই */
data class Problem(
    val tab: Int,
    val line: Int,
    val message: String,
    val isError: Boolean = true
) {
    fun label() = CodeLinter.TAB_NAMES[tab] + if (line > 0) " line $line" else ""
}

object CodeLinter {

    val TAB_NAMES = listOf("HTML", "CSS", "JS")
    const val MAX_PROBLEMS = 50

    // SECURITY: খুব বড় code check করা হয় না, যাতে app আটকে না যায়
    private const val MAX_LENGTH = 100_000

    private val VOID_TAGS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "source", "track", "wbr"
    )
    // এগুলো বন্ধ না করলেও HTML-এ চলে, তাই error দেখানো হয় না
    private val OPTIONAL_CLOSE = setOf(
        "p", "li", "td", "th", "tr", "option", "dt", "dd", "thead", "tbody",
        "tfoot", "colgroup", "optgroup", "rt", "rp", "html", "head", "body"
    )
    private val TAG_REGEX =
        Regex("""<!--[\s\S]*?(?:-->|$)|<(/?)([a-zA-Z][a-zA-Z0-9-]*)([^<>]*?)(/?)>""")
    private val REGEX_PREFIX = "(,=:[!&|?{};+-*%<>~^".toSet()
    private val CLOSE_OF = mapOf('(' to ')', '[' to ']', '{' to '}')
    private val OPEN_OF = mapOf(')' to '(', ']' to '[', '}' to '{')

    fun lintAll(html: String, css: String, js: String): List<Problem> =
        (lintHtml(html) + lintCss(css) + lintJs(js)).take(MAX_PROBLEMS)

    private fun lineAt(s: String, index: Int): Int {
        var n = 1
        for (i in 0 until index.coerceAtMost(s.length)) if (s[i] == '\n') n++
        return n
    }

    // ---------- HTML ----------

    fun lintHtml(src: String): List<Problem> {
        val out = mutableListOf<Problem>()
        if (src.length > MAX_LENGTH) return out
        val stack = ArrayDeque<Pair<String, Int>>() // tag name, position
        var pos = 0

        while (pos < src.length) {
            val m = TAG_REGEX.find(src, pos) ?: break
            pos = m.range.last + 1

            if (m.value.startsWith("<!--")) {
                if (!m.value.endsWith("-->")) {
                    out += Problem(0, lineAt(src, m.range.first), "Comment <!-- is never closed with -->")
                }
                continue
            }

            val closing = m.groupValues[1] == "/"
            val name = m.groupValues[2].lowercase()
            val selfClosing = m.groupValues[4] == "/"

            if (!closing) {
                if (name in VOID_TAGS || selfClosing) continue
                if (name == "script" || name == "style") {
                    val end = src.indexOf("</$name", pos, ignoreCase = true)
                    if (end < 0) {
                        out += Problem(0, lineAt(src, m.range.first), "<$name> is never closed")
                        break
                    }
                    pos = end
                }
                stack.addLast(name to m.range.first)
            } else {
                val line = lineAt(src, m.range.first)
                if (name in VOID_TAGS) {
                    out += Problem(0, line, "</$name> is not needed, <$name> has no closing tag", isError = false)
                    continue
                }
                val idx = stack.indexOfLast { it.first == name }
                if (idx < 0) {
                    out += Problem(0, line, "Closing tag </$name> has no matching <$name>")
                    continue
                }
                while (stack.size - 1 > idx) {
                    val (open, at) = stack.removeLast()
                    if (open !in OPTIONAL_CLOSE) {
                        out += Problem(0, lineAt(src, at), "<$open> is not closed before </$name>")
                    }
                }
                stack.removeLast()
            }
        }
        stack.forEach { (open, at) ->
            if (open !in OPTIONAL_CLOSE) out += Problem(0, lineAt(src, at), "<$open> is never closed")
        }
        return out
    }

    // ---------- CSS ----------

    fun lintCss(src: String): List<Problem> {
        val out = mutableListOf<Problem>()
        if (src.length > MAX_LENGTH) return out
        val open = ArrayDeque<Int>() // '{' এর line
        val seg = StringBuilder()
        var line = 1
        var segLine = 1
        var colons = 0
        var firstColonLine = 1
        var paren = 0
        var i = 0

        fun resetSeg() { seg.setLength(0); colons = 0; paren = 0 }
        fun checkDeclaration() {
            val t = seg.trim()
            if (open.isNotEmpty() && t.isNotEmpty() && !t.startsWith("@")) {
                if (colons == 0) out += Problem(1, segLine, "Missing ':' in \"${t.take(30)}\"")
                else if (colons > 1) out += Problem(1, firstColonLine, "Missing ';' at the end of this line")
            }
            resetSeg()
        }

        while (i < src.length) {
            val c = src[i]
            if (!c.isWhitespace() && seg.isBlank()) segLine = line
            when (c) {
                '\n' -> { line++; seg.append(c) }
                '/' -> if (src.getOrNull(i + 1) == '*') {
                    val end = src.indexOf("*/", i + 2)
                    if (end < 0) {
                        out += Problem(1, line, "Comment /* is never closed with */")
                        return out
                    }
                    for (k in i until end) if (src[k] == '\n') line++
                    i = end + 2
                    continue
                } else seg.append(c)
                '"', '\'' -> {
                    var j = i + 1
                    while (j < src.length && src[j] != c && src[j] != '\n') {
                        if (src[j] == '\\') j++
                        j++
                    }
                    if (j >= src.length || src[j] != c) {
                        out += Problem(1, line, "String is not closed (missing $c)")
                        i = j
                        continue
                    }
                    seg.append("\"\"")
                    i = j + 1
                    continue
                }
                '(' -> { paren++; seg.append(c) }
                ')' -> { paren--; seg.append(c) }
                ':' -> {
                    if (paren <= 0) {
                        colons++
                        if (colons == 1) firstColonLine = line
                    }
                    seg.append(c)
                }
                '{' -> { open.addLast(line); resetSeg() }
                ';' -> checkDeclaration()
                '}' -> {
                    if (open.isEmpty()) {
                        out += Problem(1, line, "Extra '}' without a matching '{'")
                    } else {
                        checkDeclaration()
                        open.removeLast()
                    }
                    resetSeg()
                }
                else -> seg.append(c)
            }
            i++
        }
        open.forEach { out += Problem(1, it, "'{' is never closed with '}'") }
        return out
    }

    // ---------- JS ----------

    fun lintJs(src: String): List<Problem> {
        val out = mutableListOf<Problem>()
        if (src.length > MAX_LENGTH) return out
        val stack = ArrayDeque<Pair<Char, Int>>() // bracket, line
        var line = 1
        var prev = '(' // শেষ অর্থপূর্ণ character
        var i = 0

        while (i < src.length) {
            val c = src[i]
            when {
                c == '\n' -> line++

                c == '/' && src.getOrNull(i + 1) == '/' -> {
                    val end = src.indexOf('\n', i)
                    i = if (end < 0) src.length else end
                    continue
                }

                c == '/' && src.getOrNull(i + 1) == '*' -> {
                    val end = src.indexOf("*/", i + 2)
                    if (end < 0) {
                        out += Problem(2, line, "Comment /* is never closed with */")
                        return out
                    }
                    for (k in i until end) if (src[k] == '\n') line++
                    i = end + 2
                    continue
                }

                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < src.length && src[j] != c && src[j] != '\n') {
                        if (src[j] == '\\') j++
                        j++
                    }
                    prev = c
                    if (j >= src.length || src[j] != c) {
                        out += Problem(2, line, "String is not closed (missing $c)")
                        i = j
                        continue
                    }
                    i = j + 1
                    continue
                }

                c == '`' -> {
                    val startLine = line
                    var j = i + 1
                    while (j < src.length && src[j] != '`') {
                        if (src[j] == '\\') j++ else if (src[j] == '\n') line++
                        j++
                    }
                    if (j >= src.length) {
                        out += Problem(2, startLine, "Template string ` is never closed")
                        return out
                    }
                    prev = '`'
                    i = j + 1
                    continue
                }

                c == '/' && prev in REGEX_PREFIX -> {
                    var j = i + 1
                    var inClass = false
                    while (j < src.length && src[j] != '\n') {
                        val ch = src[j]
                        if (ch == '\\') j++
                        else if (ch == '[') inClass = true
                        else if (ch == ']') inClass = false
                        else if (ch == '/' && !inClass) break
                        j++
                    }
                    prev = 'a'
                    if (j >= src.length || src[j] != '/') {
                        out += Problem(2, line, "Regular expression is not closed")
                        i = j
                        continue
                    }
                    i = j + 1
                    continue
                }

                c == '(' || c == '[' || c == '{' -> {
                    stack.addLast(c to line)
                    prev = c
                }

                c == ')' || c == ']' || c == '}' -> {
                    val want = OPEN_OF.getValue(c)
                    when {
                        stack.isEmpty() ->
                            out += Problem(2, line, "Extra '$c' without a matching '$want'")
                        stack.last().first != want -> {
                            val (o, l) = stack.removeLast()
                            out += Problem(2, line, "Expected '${CLOSE_OF[o]}' to close '$o' from line $l, found '$c'")
                        }
                        else -> stack.removeLast()
                    }
                    prev = c
                }

                !c.isWhitespace() -> prev = c
            }
            i++
        }
        stack.forEach { (o, l) -> out += Problem(2, l, "'$o' is never closed with '${CLOSE_OF[o]}'") }
        return out
    }
}
