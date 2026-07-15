package org.hnau.llmchat.chat.telegram.utils.md

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.toNonEmptyListOrNull
import org.hnau.commons.gen.fold.annotations.Fold

@Fold
sealed interface TextBlock {

    data class Text(
        val text: String,
    ) : TextBlock

    data class Blocks(
        val items: NonEmptyList<Item>,
    ) : TextBlock {

        data class Item(
            val prefix: String,
            val content: TextBlock,
        )
    }
}

private val TextBlock.length: Int
    get() = fold(
        ifText = String::length,
        ifBlocks = NonEmptyList<TextBlock.Blocks.Item>::length,
    )

private val NonEmptyList<TextBlock.Blocks.Item>.length: Int
    get() = sumOf { it.length }

private val TextBlock.Blocks.Item.length: Int
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
): Pair<TextBlock, TextBlock?> = fold(
    ifText = { text ->

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

private fun NonEmptyList<TextBlock.Blocks.Item>.limit(
    length: Int,
): Pair<TextBlock, TextBlock?> {

    if (head.length >= length) {
        val (first, headNext) = head.content.limit(length)

        val second = buildList {

            headNext
                ?.let {
                    TextBlock.Blocks.Item(
                        prefix = "",
                        content = it,
                    )
                }
                ?.let(::add)

            addAll(tail)
        }
            .toNonEmptyListOrNull()
            ?.let(TextBlock::Blocks)

        return first to second
    }

    val (first, next) = tail
        .fold(
            initial = Pair<_, NonEmptyList<TextBlock.Blocks.Item>?>(
                first = nonEmptyListOf(head),
                second = null,
            )
        ) { (head, tail), item ->
            if (tail != null) {
                return@fold head to (tail + item)
            }
            val newHead = head + item
            if (newHead.length > length) {
                return@fold head to nonEmptyListOf(item)
            }

            newHead to null
        }

    return TextBlock.Blocks(first) to next?.let(TextBlock::Blocks)
}