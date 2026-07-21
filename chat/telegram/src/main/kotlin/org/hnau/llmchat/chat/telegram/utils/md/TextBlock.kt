package org.hnau.llmchat.chat.telegram.utils.md

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.toNonEmptyListOrNull
import arrow.optics.optics
import org.hnau.commons.gen.fold.annotations.Fold
import org.hnau.commons.kotlin.foldNullable

@optics
@Fold
sealed interface TextBlock {

    @optics
    data class Text(
        val text: String,
    ) : TextBlock {

        companion object
    }

    @optics
    data class Blocks(
        val first: TextBlock,
        val next: List<Next>,
    ) : TextBlock {

        @optics
        data class Next(
            val prefix: String,
            val content: TextBlock,
        ) {

            companion object
        }

        companion object
    }

    companion object
}

private val TextBlock.length: Int
    get() = foldRaw(
        ifBlocks = TextBlock.Blocks::length,
        ifText = TextBlock.Text::length,
    )

private val TextBlock.Blocks.length: Int
    get() = first.length + next.length

private val TextBlock.Text.length: Int
    get() = text.length

private val List<TextBlock.Blocks.Next>.length: Int
    get() = sumOf(TextBlock.Blocks.Next::length)

private val TextBlock.Blocks.Next.length: Int
    get() = prefix.length + content.length

fun TextBlock.chunk(
    length: Int,
): NonEmptyList<TextBlock> = limit(length).let { (first, next) ->
    nonEmptyListOf(
        head = first,
        t = next?.chunk(length).orEmpty().toTypedArray(),
    )
}

private fun TextBlock.limit(
    length: Int,
): Pair<TextBlock, TextBlock?> = foldRaw(
    ifText = { textBlock ->

        val text = textBlock.text

        val first = text
            .take(length)
            .let(TextBlock::Text)

        val second = text
            .drop(length)
            .takeIf(String::isNotEmpty)
            ?.let(TextBlock::Text)

        first to second
    },
    ifBlocks = { items -> items.limit(length) },
)

private operator fun TextBlock.Blocks.plus(
    item: TextBlock.Blocks.Next,
): TextBlock.Blocks = copy(
    next = next + item,
)

private fun TextBlock.Blocks.limit(
    length: Int,
): Pair<TextBlock, TextBlock?> {

    if (first.length >= length) {
        val (first, headNextOrNull) = first.limit(length)

        val second = headNextOrNull.foldNullable(
            ifNull = {
                next
                    .toNonEmptyListOrNull()
                    ?.let { nonEmptyTail ->
                        TextBlock.Blocks(
                            first = nonEmptyTail.head.content,
                            next = nonEmptyTail.tail,
                        )
                    }
            },
            ifNotNull = { headNext ->
                TextBlock.Blocks(
                    first = headNext,
                    next = next,
                )
            }
        )

        return first to second
    }

    return next
        .fold(
            initial = Pair<_, TextBlock.Blocks?>(
                first = TextBlock.Blocks(
                    first = first,
                    next = emptyList(),
                ),
                second = null,
            )
        ) { (head, tail), item ->
            if (tail != null) {
                return@fold head to (tail + item)
            }
            val newHead = head + item
            if (newHead.length > length) {
                return@fold head to TextBlock.Blocks(
                    first = item.content,
                    next = emptyList(),
                )
            }

            newHead to null
        }
}