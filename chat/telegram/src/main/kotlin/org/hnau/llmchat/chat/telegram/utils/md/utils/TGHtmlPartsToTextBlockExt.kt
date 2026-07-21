package org.hnau.llmchat.chat.telegram.utils.md.utils

import arrow.core.toNonEmptyListOrNull
import arrow.optics.copy
import org.hnau.commons.kotlin.foldBoolean
import org.hnau.commons.kotlin.foldNullable
import org.hnau.commons.kotlin.it

fun List<TGHtmlPart>.toTextBlock(): TextBlock? = this
    .flatMap { part ->
        part
            .kind
            .fold(
                ifHtml = { listOf(part.string) },
                ifText = {
                    part
                        .string
                        .fold(
                            initial = Pair<List<String>, String>(emptyList(), ""),
                        ) { (acc, buffer), char ->
                            (buffer.isEmpty() || buffer.all(Char::isWhitespace) == char.isWhitespace()).foldBoolean(
                                ifTrue = { acc to buffer + char },
                                ifFalse = { (acc + buffer) to char.toString() },
                            )
                        }
                        .let { (acc, buffer) ->
                            buffer
                                .takeIf(String::isNotEmpty)
                                .foldNullable(
                                    ifNull = { acc },
                                    ifNotNull = { acc + buffer },
                                )
                        }
                }
            )
    }
    .dropLastWhile { part ->
        part.all(Char::isWhitespace)
    }
    .fold(
        initial = Pair<List<TextBlock.Blocks.Next>, String>(emptyList(), ""),
    ) { (acc, separation), item ->
        item.all(Char::isWhitespace).foldBoolean(
            ifTrue = { acc to item },
            ifFalse = {
                val newItem = TextBlock.Blocks.Next(
                    prefix = separation,
                    content = TextBlock.Text(item),
                )
                (acc + newItem) to ""
            }
        )
    }
    .first
    .toNonEmptyListOrNull()
    ?.let { next ->
        TextBlock.Blocks(
            first = next.head.content,
            next = next.tail,
        )
    }
    ?.splitBySeparator { separator -> separator.any(Char::isLineBreak) }
    ?.splitBySeparator { separator -> separator.count(Char::isLineBreak) > 1 }

private val Char.isLineBreak: Boolean
    get() = this == '\n'

private fun TextBlock.Blocks.splitBySeparator(
    splitBy: (separator: String) -> Boolean,
): TextBlock.Blocks = next.fold(
    initial = TextBlock.Blocks(
        first = first,
        next = emptyList(),
    )
) { acc, next ->
    next
        .prefix
        .let(splitBy)
        .foldBoolean(
            ifTrue = {
                acc.copy(
                    next = acc.next + next,
                )
            },
            ifFalse = {
                acc
                    .next
                    .toNonEmptyListOrNull()
                    .foldNullable(
                        ifNull = {
                            acc.copy {
                                TextBlock.Blocks.first transform { first ->
                                    first.addNext(next)
                                }
                            }
                        },
                        ifNotNull = { nextList ->
                            acc.copy(
                                next = nextList.dropLast(1) + nextList.last().copy {
                                    TextBlock.Blocks.Next.content transform { content ->
                                        content.addNext(next)
                                    }
                                }
                            )
                        }
                    )
            }
        )
}

private fun TextBlock.addNext(
    next: TextBlock.Blocks.Next,
): TextBlock.Blocks = this
    .foldRaw(
        ifBlocks = ::it,
        ifText = {
            TextBlock.Blocks(
                first = it,
                next = emptyList(),
            )
        }
    )
    .copy {
        TextBlock.Blocks.next transform { it + next }
    }

