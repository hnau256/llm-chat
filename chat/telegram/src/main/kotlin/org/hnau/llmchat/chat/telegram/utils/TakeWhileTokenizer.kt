package org.hnau.llmchat.chat.telegram.utils

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import org.hnau.commons.kotlin.ifTrue
import org.hnau.commons.kotlin.tokenize.Tokenizer

fun <I, O> Tokenizer.Companion.takeWhile(
    initialItem: I,
    predicate: (alreadyCollected: NonEmptyList<I>, newItem: I) -> Boolean,
    complete: (NonEmptyList<I>) -> O,
): Tokenizer<I, O> = TakeWhileTokenizer(
    items = nonEmptyListOf(initialItem),
    predicate = predicate,
    complete = complete,
)

fun <I, O> Tokenizer.Companion.takeWhile(
    initialItem: I,
    predicate: (I) -> Boolean,
    complete: (NonEmptyList<I>) -> O,
): Tokenizer<I, O> = takeWhile(
    initialItem = initialItem,
    predicate = { _, item -> predicate(item) },
    complete = complete,
)

private class TakeWhileTokenizer<I, O>(
    private val items: NonEmptyList<I>,
    private val predicate: (alreadyCollected: NonEmptyList<I>, newItem: I) -> Boolean,
    private val complete: (NonEmptyList<I>) -> O,
) : Tokenizer<I, O> {

    override fun collect(): O = complete(items)

    override fun tryConsume(
        nextItem: I,
    ): Tokenizer<I, O>? = predicate(
        items,
        nextItem,
    ).ifTrue {
        TakeWhileTokenizer(
            items = items + nextItem,
            predicate = predicate,
            complete = complete,
        )
    }
}