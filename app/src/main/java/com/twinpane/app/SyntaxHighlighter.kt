package com.twinpane.app

import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.graphics.toColorInt

object SyntaxHighlighter {

    // SECURITY: এর চেয়ে বড় code-এ highlight বন্ধ, যাতে UI freeze না হয়
    private const val MAX_LENGTH = 30_000

    private data class Rule(val regex: Regex, val color: Int, val group: Int = 0)

    private val TAG = "#F43F5E".toColorInt()
    private val ATTR = "#F59E0B".toColorInt()
    private val STRING = "#10B981".toColorInt()
    private val NUMBER = "#3B82F6".toColorInt()
    private val FUNC = "#38BDF8".toColorInt()
    private val KEYWORD = "#C084FC".toColorInt()
    private val COMMENT = "#64748B".toColorInt()

    private val STRINGS = Regex("""("[^"\n]*"|'[^'\n]*')""")

    private val htmlRules = listOf(
        Rule(Regex("""</?[a-zA-Z][a-zA-Z0-9-]*|/?>"""), TAG),
        Rule(Regex("""\s([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*="""), ATTR, 1),
        Rule(STRINGS, STRING),
        Rule(Regex("""<!--[\s\S]*?-->"""), COMMENT),
    )

    private val cssRules = listOf(
        Rule(Regex("""[a-zA-Z-]+(?=\s*:)"""), FUNC),
        Rule(Regex("""[^{};\n]+(?=\s*\{)"""), TAG),
        Rule(Regex("""\b\d+(\.\d+)?(px|em|rem|%|vh|vw|ms|s|deg)?"""), NUMBER),
        Rule(Regex("""#[0-9a-fA-F]{3,8}\b"""), NUMBER),
        Rule(STRINGS, STRING),
        Rule(Regex("""/\*[\s\S]*?\*/"""), COMMENT),
    )

    private val jsRules = listOf(
        Rule(Regex("""\b[a-zA-Z_]\w*(?=\s*\()"""), FUNC),
        Rule(Regex("""\b(const|let|var|function|return|if|else|for|while|do|switch|case|break|continue|new|class|extends|import|export|from|try|catch|finally|throw|typeof|instanceof|in|of|await|async|this|null|undefined|true|false)\b"""), KEYWORD),
        Rule(Regex("""\b\d+(\.\d+)?\b"""), NUMBER),
        Rule(Regex("""("(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|`(?:\\.|[^`\\])*`)"""), STRING),
        Rule(Regex("""//[^\n]*|/\*[\s\S]*?\*/"""), COMMENT),
    )

    /** lang: 0 = HTML, 1 = CSS, 2 = JS */
    fun highlight(text: Editable, lang: Int) {
        text.getSpans(0, text.length, ForegroundColorSpan::class.java).forEach { text.removeSpan(it) }
        if (text.length > MAX_LENGTH) return

        val rules = when (lang) { 0 -> htmlRules; 1 -> cssRules; else -> jsRules }
        for (rule in rules) {
            for (match in rule.regex.findAll(text)) {
                val range = match.groups[rule.group]?.range ?: continue
                if (range.isEmpty()) continue
                text.setSpan(
                    ForegroundColorSpan(rule.color),
                    range.first, range.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }
}
