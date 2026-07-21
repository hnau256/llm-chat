package org.hnau.llmchat.chat.telegram.utils.md

import arrow.core.NonEmptyList
import org.hnau.llmchat.chat.telegram.utils.md.utils.chunk
import org.hnau.llmchat.chat.telegram.utils.md.utils.mdToTGHtml
import org.hnau.llmchat.chat.telegram.utils.md.utils.toText
import org.hnau.llmchat.chat.telegram.utils.md.utils.toTextBlock

private const val MAX_TG_MESSAGE_LENGTH = 4096

fun String.mdToTGMessages(
    maxLength: Int = MAX_TG_MESSAGE_LENGTH,
): NonEmptyList<String>? = this
    .mdToTGHtml()
    .toTextBlock()
    ?.chunk(maxLength)
    ?.map { it.toText() }
