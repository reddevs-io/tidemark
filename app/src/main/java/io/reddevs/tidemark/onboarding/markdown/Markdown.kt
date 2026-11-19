// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding.markdown

/**
 * A small Markdown subset, enough for the bundled privacy policy: ATX headings, paragraphs, bullet
 * lists, bold, and links (`[text](url)`, `<url>` and bare http(s) URLs).
 *
 * Anything else is kept as plain text: an ordered list, a quote or a code fence shows its source
 * characters instead of disappearing.
 */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val content: List<MarkdownSpan>) : MarkdownBlock

    data class Paragraph(val content: List<MarkdownSpan>) : MarkdownBlock

    data class BulletList(val items: List<List<MarkdownSpan>>) : MarkdownBlock
}

/** A run of text. [url] is set for links; only http, https and mailto links are kept. */
data class MarkdownSpan(val text: String, val bold: Boolean = false, val url: String? = null)

object MarkdownParser {
    private val headingPattern = Regex("""^ {0,3}(#{1,6})(?:[ \t]+(.*?))?(?:[ \t]+#+)?[ \t]*$""")
    private val bulletPattern = Regex("""^ *[-*+][ \t]+(.*)$""")
    private val unsupportedBlockPattern = Regex("""^ *(\d+[.)][ \t]|>|```|~~~|\||(?:[-*_][ \t]*){3,}$)""")
    private val allowedSchemes = setOf("http", "https", "mailto")

    fun parse(markdown: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraph = mutableListOf<String>()
        val items = mutableListOf<MutableList<String>>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(parseInline(paragraph.joinToString(" ")))
                paragraph.clear()
            }
        }

        fun flushList() {
            if (items.isNotEmpty()) {
                blocks += MarkdownBlock.BulletList(items.map { parseInline(it.joinToString(" ")) })
                items.clear()
            }
        }

        for (rawLine in markdown.replace("\r\n", "\n").replace('\r', '\n').lines()) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) {
                flushParagraph()
                flushList()
                continue
            }
            val heading = headingPattern.matchEntire(line)
            if (heading != null) {
                flushParagraph()
                flushList()
                val level = heading.groupValues[1].length
                blocks += MarkdownBlock.Heading(level, parseInline(heading.groupValues[2].trim()))
                continue
            }
            if (unsupportedBlockPattern.containsMatchIn(line)) {
                // Ordered lists, quotes, code fences, tables, rules: one plain-text line each.
                flushParagraph()
                flushList()
                blocks += MarkdownBlock.Paragraph(listOf(MarkdownSpan(line.trim())))
                continue
            }
            val bullet = bulletPattern.matchEntire(line)
            if (bullet != null) {
                flushParagraph()
                items += mutableListOf(bullet.groupValues[1].trim())
                continue
            }
            if (items.isNotEmpty() && rawLine.startsWith(" ")) {
                // An indented line continues the current bullet item.
                items.last() += line.trim()
                continue
            }
            flushList()
            paragraph += line.trim()
        }
        flushParagraph()
        flushList()
        return blocks
    }

    /** Parses bold and links inside one block. Unclosed markers stay as literal text. */
    fun parseInline(text: String): List<MarkdownSpan> {
        val out = SpanBuilder()
        parseInline(text, bold = false, out)
        return out.build()
    }

    private fun parseInline(text: String, bold: Boolean, out: SpanBuilder) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length && text[i + 1] in ESCAPABLE -> {
                    out.append(text[i + 1].toString(), bold)
                    i += 2
                }

                !bold && (text.startsWith("**", i) || text.startsWith("__", i)) -> {
                    val marker = text.substring(i, i + 2)
                    val close = text.indexOf(marker, i + 2)
                    if (close > i + 2) {
                        parseInline(text.substring(i + 2, close), bold = true, out)
                        i = close + 2
                    } else {
                        out.append(marker, bold)
                        i += 2
                    }
                }

                c == '[' -> {
                    val link = inlineLink(text, i)
                    if (link != null) {
                        val (label, url, end) = link
                        appendLink(label, url, bold, out)
                        i = end
                    } else {
                        out.append("[", bold)
                        i++
                    }
                }

                c == '<' -> {
                    val close = text.indexOf('>', i + 1)
                    val url = if (close > i) text.substring(i + 1, close) else null
                    if (url != null && isSafeUrl(url) && url.none { it.isWhitespace() }) {
                        out.append(url, bold, url)
                        i = close + 1
                    } else {
                        out.append("<", bold)
                        i++
                    }
                }

                (text.startsWith("https://", i) || text.startsWith("http://", i)) && startsWord(text, i) -> {
                    val end = bareUrlEnd(text, i)
                    val url = text.substring(i, end)
                    out.append(url, bold, url)
                    i = end
                }

                else -> {
                    out.append(c.toString(), bold)
                    i++
                }
            }
        }
    }

    /** `[label](url)` starting at [start]: the label, the url, and the index after `)`. */
    private fun inlineLink(text: String, start: Int): Triple<String, String, Int>? {
        val labelEnd = text.indexOf("](", start + 1)
        if (labelEnd < 0) return null
        val urlEnd = text.indexOf(')', labelEnd + 2)
        if (urlEnd < 0) return null
        val label = text.substring(start + 1, labelEnd)
        val url = text.substring(labelEnd + 2, urlEnd).trim()
        if (label.isEmpty() || '[' in label || url.isEmpty() || url.any { it.isWhitespace() }) return null
        return Triple(label, url, urlEnd + 1)
    }

    private fun appendLink(label: String, url: String, bold: Boolean, out: SpanBuilder) {
        val safeUrl = url.takeIf(::isSafeUrl)
        val labelSpans = SpanBuilder().also { parseInline(label, bold, it) }.build()
        labelSpans.forEach { out.append(it.text, it.bold, safeUrl) }
    }

    private fun startsWord(text: String, index: Int) = index == 0 || !text[index - 1].isLetterOrDigit()

    /** A bare URL ends at whitespace, and trailing sentence punctuation is not part of it. */
    private fun bareUrlEnd(text: String, start: Int): Int {
        var end = start
        while (end < text.length && !text[end].isWhitespace() && text[end] != '<') end++
        while (end > start && text[end - 1] in TRAILING_PUNCTUATION) end--
        return end
    }

    fun isSafeUrl(url: String): Boolean {
        val scheme = url.substringBefore(':', missingDelimiterValue = "").lowercase()
        return scheme in allowedSchemes && url.length > scheme.length + 1
    }

    private const val ESCAPABLE = "\\`*_{}[]()#+-.!<>"
    private const val TRAILING_PUNCTUATION = ".,;:!?)'\""

    /** Merges adjacent characters with the same style into one span. */
    private class SpanBuilder {
        private val spans = mutableListOf<MarkdownSpan>()
        private val current = StringBuilder()
        private var bold = false
        private var url: String? = null

        fun append(text: String, bold: Boolean, url: String? = null) {
            if (current.isNotEmpty() && (bold != this.bold || url != this.url)) flush()
            this.bold = bold
            this.url = url
            current.append(text)
        }

        fun build(): List<MarkdownSpan> {
            flush()
            return spans.toList()
        }

        private fun flush() {
            if (current.isNotEmpty()) {
                spans += MarkdownSpan(current.toString(), bold, url)
                current.clear()
            }
        }
    }
}
