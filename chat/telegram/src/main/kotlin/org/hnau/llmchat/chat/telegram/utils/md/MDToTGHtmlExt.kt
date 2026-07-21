package org.hnau.llmchat.chat.telegram.utils.md

import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.node.Visitor
import org.commonmark.parser.Parser
import org.hnau.commons.gen.fold.annotations.Fold
import org.hnau.commons.kotlin.foldBoolean
import org.hnau.commons.kotlin.foldNullable

internal data class TGHtmlPart(
    val string: String,
    val kind: Kind,
) {

    @Fold
    enum class Kind { Text, Html }
}

internal fun String.mdToTGHtml(): List<TGHtmlPart> = HtmlBuilder()
    .apply {
        Parser
            .builder()
            .build()
            .parse(this@mdToTGHtml)
            .accept(this)
    }
    .result

private val Node.children: Sequence<Node>
    get() = generateSequence(
        seed = firstChild,
        nextFunction = { it.next },
    )

private class HtmlBuilder : Visitor {

    private val _result: MutableList<TGHtmlPart> = ArrayList()

    val result: List<TGHtmlPart>
        get() = _result

    private var htmlDepth: Int = 0

    private fun append(
        string: String,
    ) {
        val targetKind = (htmlDepth > 0).foldBoolean(
            ifTrue = { TGHtmlPart.Kind.Html },
            ifFalse = { TGHtmlPart.Kind.Text },
        )
        val newLastPart = _result
            .lastOrNull()
            ?.takeIf { it.kind == targetKind }
            ?.let { lastPart ->
                lastPart.copy(
                    string = lastPart.string + string,
                )
            }

        if (newLastPart != null) {
            _result[_result.lastIndex] = newLastPart
            return
        }

        _result.add(
            TGHtmlPart(
                string = string,
                kind = targetKind,
            )
        )
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
        htmlDepth++
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
        htmlDepth--
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
            else -> append("  ")
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
