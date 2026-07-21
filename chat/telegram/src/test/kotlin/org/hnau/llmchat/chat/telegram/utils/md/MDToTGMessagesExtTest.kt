package org.hnau.llmchat.chat.telegram.utils.md

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import kotlin.test.Test
import kotlin.test.assertTrue

class MDToTGMessagesExtTest {

    private data class Case(
        val input: String,
        val output: NonEmptyList<String>?,
        val messageLengthLimit: Int = 4096,
        val comment: String = "",
    )

    @Test
    fun mdToTGMessages() {
        val cases = listOf(
            Case(
                input = "",
                output = null,
                comment = "empty",
            ),

            Case(
                input = "a",
                output = nonEmptyListOf("a"),
                comment = "single char",
            ),
            Case(
                input = "ab",
                output = nonEmptyListOf("ab"),
                comment = "two chars",
            ),
            Case(
                input = "a b",
                output = nonEmptyListOf("a b"),
                comment = "word space word",
            ),
            Case(
                input = "a  b",
                output = nonEmptyListOf("a  b"),
                comment = "word two spaces word",
            ),
            Case(
                input = "a   b",
                output = nonEmptyListOf("a   b"),
                comment = "word three spaces word",
            ),

            Case(
                input = "\n",
                output = null,
                comment = "single newline only",
            ),
            Case(
                input = "\n\n",
                output = null,
                comment = "double newline only",
            ),
            Case(
                input = "\n\n\n",
                output = null,
                comment = "triple newline only",
            ),
            Case(
                input = " \t\n",
                output = null,
                comment = "mixed whitespace only",
            ),

            Case(
                input = "a\nb",
                output = nonEmptyListOf("a\nb"),
                comment = "single newline between words",
            ),
            Case(
                input = "a\n\nb",
                output = nonEmptyListOf("a\nb"),
                comment = "double newline between words",
            ),
            Case(
                input = "a\n\n\nb",
                output = nonEmptyListOf("a\nb"),
                comment = "triple newline between words",
            ),
            Case(
                input = "a\r\nb",
                output = nonEmptyListOf("a\nb"),
                comment = "windows newline between words",
            ),
            Case(
                input = "a\rb",
                output = nonEmptyListOf("a\nb"),
                comment = "CR newline between words",
            ),

            Case(
                input = "a\n\nb\nc",
                output = nonEmptyListOf("a\nb\nc"),
                comment = "mixed newlines",
            ),
            Case(
                input = "a\nb\n\nc",
                output = nonEmptyListOf("a\nb\nc"),
                comment = "single then double newline",
            ),
            Case(
                input = "a\n\nb\n\nc",
                output = nonEmptyListOf("a\nb\nc"),
                comment = "double newlines between each",
            ),

            Case(
                input = "a\tb",
                output = nonEmptyListOf("a\tb"),
                comment = "tab between words",
            ),
            Case(
                input = "a \t b",
                output = nonEmptyListOf("a \t b"),
                comment = "space tab space between words",
            ),
            Case(
                input = "a\u00A0b",
                output = nonEmptyListOf("a\u00A0b"),
                comment = "non-breaking space treated as regular char",
            ),

            Case(
                input = "**b**",
                output = nonEmptyListOf("<b>b</b>"),
                comment = "bold",
            ),
            Case(
                input = "*i*",
                output = nonEmptyListOf("<i>i</i>"),
                comment = "italic",
            ),
            Case(
                input = "`c`",
                output = nonEmptyListOf("<code>c</code>"),
                comment = "inline code",
            ),
            Case(
                input = "~s~",
                output = nonEmptyListOf("~s~"),
                comment = "strikethrough not supported",
            ),
            Case(
                input = "**a *b* c**",
                output = nonEmptyListOf("<b>a <i>b</i> c</b>"),
                comment = "nested bold italic",
            ),
            Case(
                input = "[link](url)",
                output = nonEmptyListOf("<a href=\"url\">link</a>"),
                comment = "link",
            ),
            Case(
                input = "<https://example.com>",
                output = nonEmptyListOf("<a href=\"https://example.com\">https://example.com</a>"),
                comment = "autolink",
            ),
            Case(
                input = "![alt](url)",
                output = nonEmptyListOf("<a href=\"url\">alt</a>"),
                comment = "image",
            ),
            Case(
                input = "![img](http://x.com/a.png)",
                output = nonEmptyListOf("<a href=\"http://x.com/a.png\">img</a>"),
                comment = "image with full url",
            ),

            Case(
                input = "a **b** c",
                output = nonEmptyListOf("a <b>b</b> c"),
                comment = "text bold text",
            ),
            Case(
                input = "**a** *b* `c`",
                output = nonEmptyListOf("<b>a</b> <i>b</i> <code>c</code>"),
                comment = "adjacent html blocks with spaces",
            ),
            Case(
                input = "a\n\n**b**",
                output = nonEmptyListOf("a\n<b>b</b>"),
                comment = "paragraph then bold",
            ),
            Case(
                input = "**a**\n\nb",
                output = nonEmptyListOf("<b>a</b>\nb"),
                comment = "bold then paragraph",
            ),
            Case(
                input = "**a**\n*i*",
                output = nonEmptyListOf("<b>a</b>\n<i>i</i>"),
                comment = "html blocks separated by newline",
            ),

            Case(
                input = "# H",
                output = nonEmptyListOf("<b>H</b>"),
                comment = "heading alone",
            ),
            Case(
                input = "# H\nt",
                output = nonEmptyListOf("<b>H</b>\nt"),
                comment = "heading with text",
            ),
            Case(
                input = "## H2\n\ntext",
                output = nonEmptyListOf("<b>H2</b>\ntext"),
                comment = "h2 heading with text",
            ),
            Case(
                input = "### H3",
                output = nonEmptyListOf("<b>H3</b>"),
                comment = "h3 heading",
            ),

            Case(
                input = "* a\n* b",
                output = nonEmptyListOf("• a\n• b"),
                comment = "bullet list",
            ),
            Case(
                input = "1. a\n2. b",
                output = nonEmptyListOf("1. a\n2. b"),
                comment = "ordered list",
            ),
            Case(
                input = "3. a",
                output = nonEmptyListOf("3. a"),
                comment = "ordered list with custom start",
            ),
            Case(
                input = "> quote",
                output = nonEmptyListOf("<blockquote>quote\n</blockquote>"),
                comment = "blockquote",
            ),
            Case(
                input = "> line1\n> line2",
                output = nonEmptyListOf("<blockquote>line1\nline2\n</blockquote>"),
                comment = "multi-line blockquote",
            ),

            Case(
                input = "---",
                output = nonEmptyListOf("---"),
                comment = "thematic break",
            ),
            Case(
                input = "a\n\n---\n\nb",
                output = nonEmptyListOf("a\n\n---\nb"),
                comment = "thematic break between paragraphs",
            ),

            Case(
                input = "a & b",
                output = nonEmptyListOf("a &amp; b"),
                comment = "ampersand escaping",
            ),
            Case(
                input = "a <b> c",
                output = nonEmptyListOf("a &lt;b&gt; c"),
                comment = "angle brackets escaping",
            ),
            Case(
                input = "<b>raw</b>",
                output = nonEmptyListOf("&lt;b&gt;raw&lt;/b&gt;"),
                comment = "raw html escaped",
            ),
            Case(
                input = "[link](url?x=1&y=\"2\")",
                output = nonEmptyListOf("<a href=\"url?x=1&amp;y=&quot;2&quot;\">link</a>"),
                comment = "link with special chars in url",
            ),

            Case(
                input = "```\ncode\n```",
                output = nonEmptyListOf("<pre><code>code\n</code></pre>"),
                comment = "fenced code block",
            ),
            Case(
                input = "```kotlin\nval x = 1\n```",
                output = nonEmptyListOf("<pre><code class=\"language-kotlin\">val x = 1\n</code></pre>"),
                comment = "fenced code with language",
            ),

            Case(
                input = "a  \nb",
                output = nonEmptyListOf("a\nb"),
                comment = "hard line break same as soft",
            ),

            Case(
                input = "\\*not italic\\*",
                output = nonEmptyListOf("*not italic*"),
                comment = "escaped asterisks render as literal",
            ),
            Case(
                input = "\\`not code\\`",
                output = nonEmptyListOf("`not code`"),
                comment = "escaped backticks render as literal",
            ),

            Case(
                input = "привет мир",
                output = nonEmptyListOf("привет мир"),
                comment = "cyrillic text",
            ),
            Case(
                input = "**жирный** *курсив*",
                output = nonEmptyListOf("<b>жирный</b> <i>курсив</i>"),
                comment = "cyrillic formatting",
            ),

            Case(
                input = "emoji 🎉 test",
                output = nonEmptyListOf("emoji 🎉 test"),
                comment = "emoji in text",
            ),

            Case(
                input = "a",
                output = nonEmptyListOf("a"),
                messageLengthLimit = 1,
                comment = "single char fits limit",
            ),
            Case(
                input = "ab",
                output = nonEmptyListOf("a", "b"),
                messageLengthLimit = 1,
                comment = "two chars split by limit 1",
            ),
            Case(
                input = "abc",
                output = nonEmptyListOf("ab", "c"),
                messageLengthLimit = 2,
                comment = "three chars with limit 2",
            ),
            Case(
                input = "abcde",
                output = nonEmptyListOf("ab", "cd", "e"),
                messageLengthLimit = 2,
                comment = "five chars with limit 2",
            ),
            Case(
                input = "a".repeat(5),
                output = nonEmptyListOf("aa", "aa", "a"),
                messageLengthLimit = 2,
                comment = "repeated char chunking",
            ),
            Case(
                input = "a b c",
                output = nonEmptyListOf("a b", "c"),
                messageLengthLimit = 3,
                comment = "space separated with limit equals chunk",
            ),
            Case(
                input = "ab cd ef",
                output = nonEmptyListOf("ab", "cd", "ef"),
                messageLengthLimit = 2,
                comment = "two-char words split on word boundary",
            ),
            Case(
                input = "abc def",
                output = nonEmptyListOf("abc", "def"),
                messageLengthLimit = 3,
                comment = "three-char words exactly at limit",
            ),
            Case(
                input = "a b",
                output = nonEmptyListOf("a", "b"),
                messageLengthLimit = 1,
                comment = "single char words split by limit 1",
            ),
            Case(
                input = "a\nb\nc",
                output = nonEmptyListOf("a", "b", "c"),
                messageLengthLimit = 2,
                comment = "newline separated words with limit 2",
            ),
        )

        val failures = cases.mapNotNull { case ->
            val actual = case.input.mdToTGMessages(case.messageLengthLimit)
            if (actual != case.output) {
                buildString {
                    appendLine("FAIL [${case.comment}]:")
                    appendLine("  input:    \"${case.input.replace("\n", "\\n").replace("\r", "\\r")}\"")
                    appendLine("  limit:    ${case.messageLengthLimit}")
                    appendLine("  expected: ${case.output}")
                    appendLine("  actual:   ${actual}")
                }
            } else null
        }

        assertTrue(failures.isEmpty(), "\n" + failures.joinToString("\n"))
    }
}
