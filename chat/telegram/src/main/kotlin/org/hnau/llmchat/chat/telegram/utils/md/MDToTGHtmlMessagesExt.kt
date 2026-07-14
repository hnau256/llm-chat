package org.hnau.llmchat.chat.telegram.utils.md

import arrow.core.NonEmptyList
import org.hnau.commons.kotlin.foldBoolean
import org.hnau.commons.kotlin.tokenize.Tokenizer
import org.hnau.commons.kotlin.tokenize.tokenize
import org.hnau.llmchat.chat.telegram.utils.takeWhile


@JvmInline
private value class Token(
    val content: String,
) {

    operator fun plus(
        char: Char,
    ): Token = Token(
        content = content + char,
    )

    enum class Kind(
        val weight: Int,
    ) {
        ParagraphsSeparator(weight = 0),
        WordsSeparator(weight = 1),
        Word(weight = 2),
        Html(weight = 3),
    }

    val kind: Kind
        get() = when {
            content.firstOrNull() == '<' -> Kind.Html

            content.any(Char::isWhitespace) -> content
                .any { it in setOf('\n', '\r') }
                .foldBoolean(
                    ifTrue = { Kind.ParagraphsSeparator },
                    ifFalse = { Kind.WordsSeparator },
                )

            else -> Kind.Word
        }
}

fun String.mdToTGHtmlMessages(
    maxMessageLength: Int,
): List<String> {
    mdToTGHtml()
        .flatMap { part ->
            part
                .kind
                .fold(
                    ifHtml = { listOf(Token(part.string)) },
                    ifText = {
                        part
                            .string
                            .tokenizeText()
                    },
                )
        }
    TODO()
}

private fun String.tokenizeText(): List<Token> = this
    .asSequence()
    .tokenize { char ->

        val complete: (NonEmptyList<Char>) -> Token = { chars ->
            chars
                .joinToString(separator = "")
                .let(::Token)
        }

        when {
            char.isWhitespace() -> Tokenizer.takeWhile(
                initialItem = char,
                predicate = Char::isWhitespace,
                complete = complete,
            )

            else -> Tokenizer.takeWhile(
                initialItem = char,
                predicate = { !it.isWhitespace() && it != '<' },
                complete = complete,
            )
        }
    }
    .toList()