package com.twinpane.app

object EmmetEngine {

    fun expand(abbreviation: String): String? {
        val abbr = abbreviation.trim()
        if (abbr.isEmpty()) return null

        // Handle multiplier: e.g. ul>li*3
        if (abbr.contains(">") && abbr.contains("*")) {
            val parts = abbr.split(">")
            if ((parts.size == 2) && parts[1].contains("*")) {
                val parent = parseNode(parts[0])
                val childPart = parts[1].split("*")
                val child = parseNode(childPart[0])
                val count = childPart[1].toIntOrNull() ?: 1

                val childrenSb = StringBuilder()
                repeat(count) {
                    childrenSb.append("  ").append(child).append("\n")
                }
                return "<${parent.tag}${parent.attrs()}>\n$childrenSb</${parent.tag}>"
            }
        }

        // Handle single node or simple class/id shorthand
        val node = parseNode(abbr)
        return if (node.tag.isNotBlank()) {
            "<${node.tag}${node.attrs()}></${node.tag}>"
        } else null
    }

    private data class Node(
        val tag: String,
        val id: String = "",
        val classes: List<String> = emptyList(),
    ) {
        fun attrs(): String {
            val sb = StringBuilder()
            if (id.isNotEmpty()) sb.append(" id=\"$id\"")
            if (classes.isNotEmpty()) sb.append(" class=\"${classes.joinToString(" ")}\"")
            return sb.toString()
        }
    }

    private fun parseNode(input: String): Node {
        var tag = "div"
        var id = ""
        val classes = mutableListOf<String>()

        val tokens = Regex("""([.#]?[a-zA-Z0-9_-]+)""").findAll(input).map { it.value }.toList()
        for (token in tokens) {
            when {
                token.startsWith("#") -> id = token.substring(1)
                token.startsWith(".") -> classes.add(token.substring(1))
                else -> tag = token
            }
        }
        return Node(tag, id, classes)
    }
}
