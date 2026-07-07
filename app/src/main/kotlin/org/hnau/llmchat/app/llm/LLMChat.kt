package org.hnau.llmchat.app.llm

import arrow.core.nonEmptyListOf
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
import org.hnau.llmchat.app.telegram.TelegramButton
import org.hnau.llmchat.app.telegram.TelegramChat
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

            val telegramChat = toChat(
                chatId = chatId,
            )

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
                            telegramChat.afterInput(
                                inputPath = inputToAnswer,
                                waitingForAnswerInputs = waitingForAnswerInputs,
                            )
                        }
                    )
                    ?: run {
                        telegramChat.sendMessage(
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
                        telegramChat.sendMessage(
                            text = "Answer for '$text'",
                        )
                    },
                    ifNotNull = { command ->

                        val path = telegramChat
                            .tryParseEncodedPathOrLogError(
                                encodedPath = command,
                            )
                            ?: return@foldNullable

                        telegramChat.handleButtonClick(
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

            val telegramChat = toChat(
                chatId = chatId,
            )

            val waitingForAnswerInputs = waitingForAnswerInputs.forChat(
                chatId = chatId,
            )

            if (encodedPath == CancelInputCallbackData) {
                waitingForAnswerInputs
                    .consume()
                    .foldNullable(
                        ifNull = { logger.w { "No input to cancel" } },
                        ifNotNull = { inputToCancel ->
                            telegramChat.afterInput(
                                inputPath = inputToCancel,
                                waitingForAnswerInputs = waitingForAnswerInputs,
                            )
                        }
                    )
                answerCallbackQuery(dataCallbackQuery)
                return@onDataCallbackQuery
            }

            val path = telegramChat.tryParseEncodedPathOrLogError(
                encodedPath = encodedPath,
            ) ?: return@onDataCallbackQuery

            telegramChat.handleButtonClick(
                path = path,
                messageToEdit = message.messageId,
                waitingForAnswerInputs = waitingForAnswerInputs,
            )
            answerCallbackQuery(dataCallbackQuery)
        }

    }
}

private fun TelegramBot.toChat(
    chatId: IdChatIdentifier,
): TelegramChat = object : TelegramChat {

    override suspend fun sendMessage(
        text: String,
        buttons: List<TelegramButton>,
        messageToEdit: MessageId?
    ) {

        val replyMarkup = InlineKeyboardMarkup(
            keyboard = buttons.map { button ->
                listOf(
                    CallbackDataInlineKeyboardButton(
                        text = button.title,
                        callbackData = button.path.encode(),
                    )
                )
            }
        )

        messageToEdit.foldNullable(
            ifNull = {
                send(
                    chatId = chatId,
                    text = text,
                    replyMarkup = replyMarkup,
                )
            },
            ifNotNull = { messageId ->
                editMessageText(
                    chatId = chatId,
                    messageId = messageId,
                    text = text,
                    replyMarkup = replyMarkup,
                )
            }
        )
    }
}

private suspend fun TelegramChat.afterInput(
    inputPath: CallbackDataPath,
    waitingForAnswerInputs: WaitingForAnswerInputs.InChat,
) {
    handleButtonClick(
        path = inputPath.tryDropLast()!!,
        messageToEdit = null,
        waitingForAnswerInputs = waitingForAnswerInputs,
    )
}

private suspend fun TelegramChat.tryParseEncodedPathOrLogError(
    encodedPath: String,
): CallbackDataPath? = CallbackDataPath
    .tryParse(encodedPath)
    .also { pathOrNull ->
        pathOrNull.ifNull {
            sendMessage(
                text = "Unknown command format '$encodedPath'",
            )
        }
    }

private suspend fun TelegramChat.handleButtonClick(
    path: CallbackDataPath,
    messageToEdit: MessageId?,
    waitingForAnswerInputs: WaitingForAnswerInputs.InChat,
) {

    val button = findButton(
        buttons = commands,
        path = path,
    ) ?: run {
        sendMessage(
            text = "Unknown command '$path'",
        )
        return
    }

    button
        .type
        .fold(
            ifChild = { message ->
                openPage(
                    messageToEdit = messageToEdit,
                    path = path,
                    page = message,
                )
            },
            ifInput = {
                sendMessage(
                    text = "Input '${button.text}",
                    buttons = listOf(
                        TelegramButton(
                            title = "Cancel input",
                            path = CallbackDataPath(
                                entries = nonEmptyListOf(
                                    CallbackDataPath.Entry(
                                        id = CancelInputCallbackData,
                                    )
                                )
                            ),
                        )
                    ),
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

private suspend fun TelegramChat.openPage(
    messageToEdit: MessageId?,
    path: CallbackDataPath,
    page: TelegramPageMessage,
) {
    sendMessage(
        messageToEdit = messageToEdit,
        text = page.generateText(),
        buttons = buildList {
            addAll(
                page.buttons.map { button ->
                    TelegramButton(
                        title = button.text,
                        path = path + button.id,
                    )
                }
            )
            path.tryDropLast()?.let { pathToGoBack ->
                add(
                    TelegramButton(
                        title = "Back",
                        path = pathToGoBack,
                    )
                )
            }
        },
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