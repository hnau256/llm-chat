package org.hnau.llmchat.chat.telegram.utils.md

import arrow.core.NonEmptyList

private const val MAX_TG_MESSAGE_LENGTH = 4096

fun String.mdToTGMessages(): NonEmptyList<TextBlock>? = this
    .mdToTGHtml()
    .toTextBlock()
    ?.chunk(MAX_TG_MESSAGE_LENGTH)