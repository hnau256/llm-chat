package org.hnau.llmchat.chat.telegram.utils

import org.commonmark.node.*
import org.commonmark.node.Visitor
import org.commonmark.parser.Parser
import org.hnau.commons.kotlin.foldNullable

internal fun String.convertMDToHtml(): String = HtmlBuilder()
    .apply {
        Parser
            .builder()
            .build()
            .parse(this@convertMDToHtml)
            .accept(this)
    }
    .build()

private val Node.children: Sequence<Node>
    get() = generateSequence(
        seed = firstChild,
        nextFunction = { it.next },
    )

private class HtmlBuilder : Visitor {

    private val result = StringBuilder()

    fun build(): String = result
        .toString()
        .trim()

    private fun append(
        text: String,
    ) {
        result.append(text)
    }

    private fun appendEscaped(
        text: String,
    ) {
        append(
            text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
        )
    }

    private fun appendAttrEscaped(
        text: String,
    ) {
        append(
            text
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
        )
    }

    private fun tag(
        tag: String,
        vararg attrs: Pair<String, String>,
        content: (() -> Unit)? = null,
    ) {
        append("<$tag")
        attrs.forEach { (key, value) ->
            append(" $key=\"")
            appendAttrEscaped(value)
            append("\"")
        }
        append(">")
        content.foldNullable(
            ifNull = {},
            ifNotNull = {
                it()
                append("</$tag>")
            },
        )
    }

    private fun visitChildren(node: Node) {
        node.children.forEach { it.accept(this) }
    }

    override fun visit(blockQuote: BlockQuote) {
        tag("blockquote") { visitChildren(blockQuote) }
    }

    override fun visit(bulletList: BulletList) {
        visitChildren(bulletList)
    }

    override fun visit(code: Code) {
        tag("code") { appendEscaped(code.literal) }
    }

    override fun visit(document: Document) {
        visitChildren(document)
    }

    override fun visit(emphasis: Emphasis) {
        tag("i") { visitChildren(emphasis) }
    }

    override fun visit(fencedCodeBlock: FencedCodeBlock) {
        val language = fencedCodeBlock.info.trim().substringBefore(' ')
        tag("pre") {
            tag(
                tag = "code",
                *listOfNotNull(
                    language.takeIf { it.isNotEmpty() }?.let { "class" to "language-$it" },
                ).toTypedArray(),
            ) { appendEscaped(fencedCodeBlock.literal) }
        }
    }

    override fun visit(hardLineBreak: HardLineBreak) {
        append("\n")
    }

    override fun visit(heading: Heading) {
        tag("b") { visitChildren(heading) }
        append("\n")
    }

    override fun visit(thematicBreak: ThematicBreak) {
        append("\n---\n")
    }

    override fun visit(htmlInline: HtmlInline) {
        appendEscaped(htmlInline.literal)
    }

    override fun visit(htmlBlock: HtmlBlock) {
        appendEscaped(htmlBlock.literal)
    }

    override fun visit(image: Image) {
        tag(
            tag = "a",
            "href" to image.destination,
        ) {
            val altChild: Node? = image.firstChild
            altChild.foldNullable(
                ifNull = { appendAttrEscaped(image.destination) },
                ifNotNull = { visitChildren(image) },
            )
        }
    }

    override fun visit(indentedCodeBlock: IndentedCodeBlock) {
        tag("pre") {
            tag("code") { appendEscaped(indentedCodeBlock.literal) }
        }
    }

    override fun visit(link: Link) {
        tag(
            tag = "a",
            "href" to link.destination,
        ) { visitChildren(link) }
    }

    override fun visit(listItem: ListItem) {
        when (val parent = listItem.parent) {
            is BulletList -> append("• ")
            is OrderedList -> {

                val index = parent
                    .children
                    .indexOfFirst { it === listItem }
                    .plus(parent.markerStartNumber ?: 1)

                append("$index${parent.markerDelimiter ?: "."} ")
            }
        }
        visitChildren(listItem)
        append("\n")
    }

    override fun visit(orderedList: OrderedList) {
        visitChildren(orderedList)
    }

    override fun visit(paragraph: Paragraph) {
        visitChildren(paragraph)
        if (paragraph.parent !is ListItem) {
            append("\n")
        }
    }

    override fun visit(softLineBreak: SoftLineBreak) {
        append("\n")
    }

    override fun visit(strongEmphasis: StrongEmphasis) {
        tag("b") { visitChildren(strongEmphasis) }
    }

    override fun visit(text: Text) {
        appendEscaped(text.literal)
    }

    override fun visit(linkReferenceDefinition: LinkReferenceDefinition) {
    }

    override fun visit(customBlock: CustomBlock) {
    }

    override fun visit(customNode: CustomNode) {
    }
}
