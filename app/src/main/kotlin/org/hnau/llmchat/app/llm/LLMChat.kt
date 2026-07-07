package org.hnau.llmchat.app.llm

import arrow.core.tail
import arrow.core.toNonEmptyListOrNull
import co.touchlab.kermit.Logger
import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.answers.answerCallbackQuery
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextReceiver
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onText
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.message
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import org.hnau.commons.kotlin.foldNullable
import org.hnau.commons.kotlin.ifNull
import org.hnau.commons.kotlin.removePrefixOrNull
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.app.db.settings.UserSettingsRepository
import org.hnau.llmchat.app.telegram.CallbackDataPath
import org.hnau.llmchat.app.telegram.TelegramPageMessage
import org.hnau.llmchat.app.telegram.fold

private val logger = Logger.withTag("LLMChat")

fun LLMChat(
    dbAccessor: DBAccessor,
): BehaviourContextReceiver<Unit> {

    val userSettingsRepository = UserSettingsRepository(dbAccessor)

    val waitingForAnswerInputs = WaitingForAnswerInputs()

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

            val waitingForAnswerInputs = waitingForAnswerInputs.forChat(
                chatId = chatId,
            )

            waitingForAnswerInputs.consume()?.let { inputToAnswer ->
                findButton(
                    buttons = commands,
                    path = inputToAnswer,
                )
                    ?.type
                    ?.fold(
                        ifChild = { null },
                        ifInput = { onInput ->
                            onInput(text)
                            afterInput(
                                chatId = chatId,
                                inputPath = inputToAnswer,
                                waitingForAnswerInputs = waitingForAnswerInputs,
                            )
                        }
                    )
                    ?: run {
                        send(
                            chatId = chatId,
                            text = "Unable handle input to answer",
                        )
                        return@onText
                    }
                return@onText
            }

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

                        val path = tryParseEncodedPathOrLogError(
                            chatId = chatId,
                            encodedPath = command,
                        ) ?: return@foldNullable

                        handleButtonClick(
                            chatId = chatId,
                            path = path,
                            messageToEdit = null,
                            waitingForAnswerInputs = waitingForAnswerInputs,
                        )
                    }
                )
        }

        onDataCallbackQuery { dataCallbackQuery ->
            val message = dataCallbackQuery.message ?: return@onDataCallbackQuery
            val encodedPath = dataCallbackQuery.data

            val chatId = message.chat.id

            val waitingForAnswerInputs = waitingForAnswerInputs.forChat(
                chatId = chatId,
            )

            if (encodedPath == CancelInputCallbackData) {
                waitingForAnswerInputs
                    .consume()
                    .foldNullable(
                        ifNull = { logger.w { "No input to cancel" } },
                        ifNotNull = { inputToCancel ->
                            afterInput(
                                chatId = chatId,
                                inputPath = inputToCancel,
                                waitingForAnswerInputs = waitingForAnswerInputs,
                            )
                        }
                    )
                answerCallbackQuery(dataCallbackQuery)
                return@onDataCallbackQuery
            }

            val path = tryParseEncodedPathOrLogError(
                chatId = chatId,
                encodedPath = encodedPath,
            ) ?: return@onDataCallbackQuery

            handleButtonClick(
                chatId = chatId,
                path = path,
                messageToEdit = message.messageId,
                waitingForAnswerInputs = waitingForAnswerInputs,
            )
            answerCallbackQuery(dataCallbackQuery)
        }

    }
}

private suspend fun TelegramBot.afterInput(
    chatId: IdChatIdentifier,
    inputPath: CallbackDataPath,
    waitingForAnswerInputs: WaitingForAnswerInputs.InChat,
) {
    handleButtonClick(
        chatId = chatId,
        path = inputPath.tryDropLast()!!,
        messageToEdit = null,
        waitingForAnswerInputs = waitingForAnswerInputs,
    )
}

private suspend fun TelegramBot.tryParseEncodedPathOrLogError(
    chatId: IdChatIdentifier,
    encodedPath: String,
): CallbackDataPath? = CallbackDataPath
    .tryParse(encodedPath)
    .also { pathOrNull ->
        pathOrNull.ifNull {
            send(
                chatId = chatId,
                text = "Unknown command format '$encodedPath'",
            )
        }
    }

private suspend fun TelegramBot.handleButtonClick(
    chatId: IdChatIdentifier,
    path: CallbackDataPath,
    messageToEdit: MessageId?,
    waitingForAnswerInputs: WaitingForAnswerInputs.InChat,
) {

    val button = findButton(
        buttons = commands,
        path = path,
    ) ?: run {
        send(
            chatId = chatId,
            text = "Unknown command '$path'",
        )
        return
    }

    button
        .type
        .fold(
            ifChild = { message ->
                openPage(
                    chatId = chatId,
                    messageToEdit = messageToEdit,
                    path = path,
                    page = message,
                )
            },
            ifInput = {
                send(
                    chatId = chatId,
                    text = "Input '${button.text}",
                    replyMarkup = InlineKeyboardMarkup(
                        keyboard = listOf(
                            listOf(
                                CallbackDataInlineKeyboardButton(
                                    text = "Cancel input",
                                    callbackData = CancelInputCallbackData,
                                )
                            )
                        )
                    )
                )
                waitingForAnswerInputs.add(path)
            }
        )
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
                    button
                        .type
                        .fold(
                            ifChild = { message ->
                                findButton(
                                    buttons = message.buttons,
                                    path = CallbackDataPath(tail),
                                )
                            },
                            ifInput = { null },
                        )
                }
            )
    }

private suspend fun TelegramBot.openPage(
    chatId: IdChatIdentifier,
    messageToEdit: MessageId?,
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
                messageId = message,
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
                                    buttons = listOf(
                                        TelegramPageMessage.Button(
                                            id = CallbackDataPath.Entry("edit"),
                                            text = "Edit",
                                            type = TelegramPageMessage.Button.Type.Input(
                                                onInput = { input ->
                                                    println("QWERTY. On input: $input")
                                                }
                                            )
                                        )
                                    ),
                                )
                            )
                        )
                    ),
                )
            )
        )
    )

private const val CancelInputCallbackData = "cancel_input"