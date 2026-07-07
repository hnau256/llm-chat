package org.hnau.llmchat.app.llm

import arrow.core.tail
import arrow.core.toNonEmptyListOrNull
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextReceiver
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onText
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.message
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dev.inmo.tgbotapi.types.message.abstracts.ContentMessage
import dev.inmo.tgbotapi.types.message.content.MessageContent
import org.hnau.commons.kotlin.foldNullable
import org.hnau.commons.kotlin.removePrefixOrNull
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.app.db.settings.UserSettingsRepository
import org.hnau.llmchat.app.telegram.CallbackDataPath
import org.hnau.llmchat.app.telegram.TelegramPageMessage

fun LLMChat(
    dbAccessor: DBAccessor,
): BehaviourContextReceiver<Unit> {

    val userSettingsRepository = UserSettingsRepository(dbAccessor)

    return {

        setMyCommands(
            commands.map { command ->
                BotCommand(
                    command.id.id,
                    command.text,
                )
            }
        )

        onText { message ->

            val chatId = message.chat.id

            val text = message.content.text
            text
                .trim()
                .lowercase()
                .removePrefixOrNull("/")
                .foldNullable(
                    ifNull = {
                        send(
                            chatId = chatId,
                            text = "Answer for '$text'"
                        )
                    },
                    ifNotNull = { command ->
                        handleButtonClick(
                            chatId = chatId,
                            encodedPath = command,
                            messageToEdit = null,
                        )
                    }
                )
        }

        onDataCallbackQuery { dataCallbackQuery ->
            val message = dataCallbackQuery.message ?: return@onDataCallbackQuery
            handleButtonClick(
                chatId = message.chat.id,
                encodedPath = dataCallbackQuery.data,
                messageToEdit = message,
            )
        }

    }
}

private suspend fun BehaviourContext.handleButtonClick(
    chatId: IdChatIdentifier,
    encodedPath: String,
    messageToEdit: ContentMessage<MessageContent>?,
) {
    val (path, button) = CallbackDataPath
        .tryParse(encodedPath)
        ?.let { path ->
            val page = findButton(
                buttons = commands,
                path = path,
            ) ?: return@let null
            path to page
        }
        ?: run {
            send(
                chatId = chatId,
                text = "Unknown command '$encodedPath'",
            )
            return
        }

    when (val type = button.type) {
        is TelegramPageMessage.Button.Type.Child -> openPage(
            chatId = chatId,
            messageToEdit = messageToEdit,
            path = path,
            page = type.message,
        )

        is TelegramPageMessage.Button.Type.Input -> TODO()
    }
}

private fun findButton(
    buttons: List<TelegramPageMessage.Button>,
    path: CallbackDataPath,
): TelegramPageMessage.Button? = buttons
    .find { it.id == path.entries.head }
    ?.let { button ->
        path
            .entries
            .tail()
            .toNonEmptyListOrNull()
            .foldNullable(
                ifNull = { button },
                ifNotNull = { tail ->
                    when (val type = button.type) {
                        is TelegramPageMessage.Button.Type.Child -> findButton(
                            buttons = type.message.buttons,
                            path = CallbackDataPath(tail),
                        )

                        is TelegramPageMessage.Button.Type.Input -> null
                    }
                }
            )
    }

private suspend fun BehaviourContext.openPage(
    chatId: IdChatIdentifier,
    messageToEdit: ContentMessage<MessageContent>?,
    path: CallbackDataPath,
    page: TelegramPageMessage,
) {
    val text = page.generateText()
    val replyMarkup = InlineKeyboardMarkup(
        listOf(
            buildList {
                addAll(
                    page.buttons.map { button ->
                        Pair(
                            first = button.text,
                            second = path + button.id,
                        )
                    }
                )
                path.tryDropLast()?.let { pathToGoBack ->
                    add(
                        Pair(
                            first = "Back",
                            second = pathToGoBack,
                        )
                    )
                }
            }.map { (text, path) ->
                CallbackDataInlineKeyboardButton(
                    text = text,
                    callbackData = path.encode(),
                )
            }
        )
    )

    messageToEdit.foldNullable(
        ifNull = {
            send(
                chatId = chatId,
                text = text,
                replyMarkup = replyMarkup,
            )
        },
        ifNotNull = { message ->
            editMessageText(
                chatId = chatId,
                messageId = message.messageId,
                text = text,
                replyMarkup = replyMarkup,
            )
        }
    )
}

private val commands: List<TelegramPageMessage.Button> =
    listOf(
        TelegramPageMessage.Button(
            id = CallbackDataPath.Entry("settings"),
            text = "Settings",
            type = TelegramPageMessage.Button.Type.Child(
                TelegramPageMessage(
                    generateText = { "Settings" },
                    buttons = listOf(
                        TelegramPageMessage.Button(
                            id = CallbackDataPath.Entry("basePrompt"),
                            text = "Base prompt",
                            type = TelegramPageMessage.Button.Type.Child(
                                TelegramPageMessage(
                                    generateText = { "Base prompt: QWERTY" },
                                    buttons = emptyList(),
                                )
                            )
                        )
                    ),
                )
            )
        )
    )