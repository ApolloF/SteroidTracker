package com.apollof.protocoltracker.domain.io

/**
 * The privacy policy bundled with the app (Play requires its link or text in the app), read from the small Markdown
 * subset the policy files use: `#`/`##` headings, `- ` bullets and paragraphs. Emphasis marks are dropped.
 */
object PolicyText {
    sealed interface Block {
        val text: String
    }

    /** [level] 1 is the document title, 2 a section. */
    data class Heading(val level: Int, override val text: String) : Block
    data class Paragraph(override val text: String) : Block
    data class Bullet(override val text: String) : Block

    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        fun flush() {
            if (paragraph.isNotEmpty()) blocks += Paragraph(plain(paragraph.joinToString(" ")))
            paragraph.clear()
        }
        for (raw in markdown.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> flush()
                line.startsWith("#") -> {
                    flush()
                    val level = line.takeWhile { it == '#' }.length
                    blocks += Heading(level, plain(line.drop(level)))
                }
                line.startsWith("- ") -> {
                    flush()
                    blocks += Bullet(plain(line.drop(2)))
                }
                // A wrapped bullet line continues the bullet.
                raw.startsWith("  ") && paragraph.isEmpty() && blocks.lastOrNull() is Bullet -> {
                    val last = blocks.removeAt(blocks.lastIndex) as Bullet
                    blocks += Bullet(last.text + " " + plain(line))
                }
                else -> paragraph += line
            }
        }
        flush()
        return blocks
    }

    private fun plain(text: String): String = text.replace("**", "").replace("*", "").trim()
}
