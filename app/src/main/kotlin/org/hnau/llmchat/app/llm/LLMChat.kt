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
import org.hnau.llmchat.app.db.settings.update
import org.hnau.llmchat.app.dto.UserId
import org.hnau.llmchat.app.telegram.CallbackDataPath
import org.hnau.llmchat.app.telegram.TelegramButton
import org.hnau.llmchat.app.telegram.TelegramChat
import org.hnau.llmchat.app.telegram.TelegramPageMessage
import org.hnau.llmchat.app.telegram.fold

private val logger = Logger.withTag("LLMChat")

fun LLMChat(
    dbAccessor: DBAccessor,
): BehaviourContextReceiver<Unit> = {

    val waitingForAnswerInputs = WaitingForAnswerInputs()

    val createContext: (IdChatIdentifier) -> LLMChatContext = { chatId ->
        LLMChatContext(
            chat = TelegramChat(
                bot = this,
                chatId = chatId,
            ),
            userSettings = UserSettingsRepository(
                db = dbAccessor,
                userId = UserId(chatId.chatId.long.toString())
            ),
        )
    }

    setMyCommands(
        commands.map { command ->
            BotCommand(
                command = command.id.id,
                description = command.text,
            )
        }
    )

    onText { message ->

        val chatId = message.chat.id

        val context = createContext(chatId)

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
                        onInput(context, text)
                        context.afterInput(
                            inputPath = inputToAnswer,
                            waitingForAnswerInputs = waitingForAnswerInputs,
                        )
                    }
                )
                ?: context.chat.sendMessage(
                    text = "Unable handle input to answer",
                )
            return@onText
        }

        text
            .trim()
            .lowercase()
            .removePrefixOrNull("/")
            .foldNullable(
                ifNull = {
                    context.chat.sendMessage(
                        text = "Answer for '$text'",
                    )
                },
                ifNotNull = { command ->

                    val path = context
                        .tryParseEncodedPathOrLogError(
                            encodedPath = command,
                        )
                        ?: return@foldNullable

                    context.handleButtonClick(
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

        val context = createContext(chatId)

        val waitingForAnswerInputs = waitingForAnswerInputs.forChat(
            chatId = chatId,
        )

        if (encodedPath == CancelInputCallbackData) {
            waitingForAnswerInputs
                .consume()
                .foldNullable(
                    ifNull = { logger.w { "No input to cancel" } },
                    ifNotNull = { inputToCancel ->
                        context.afterInput(
                            inputPath = inputToCancel,
                            waitingForAnswerInputs = waitingForAnswerInputs,
                        )
                    }
                )
            answerCallbackQuery(dataCallbackQuery)
            return@onDataCallbackQuery
        }

        val path = context.tryParseEncodedPathOrLogError(
            encodedPath = encodedPath,
        ) ?: return@onDataCallbackQuery

        context.handleButtonClick(
            path = path,
            messageToEdit = message.messageId,
            waitingForAnswerInputs = waitingForAnswerInputs,
        )
        answerCallbackQuery(dataCallbackQuery)
    }

}

private suspend fun LLMChatContext.afterInput(
    inputPath: CallbackDataPath,
    waitingForAnswerInputs: WaitingForAnswerInputs.InChat,
) {
    handleButtonClick(
        path = inputPath.tryDropLast()!!,
        messageToEdit = null,
        waitingForAnswerInputs = waitingForAnswerInputs,
    )
}

private suspend fun LLMChatContext.tryParseEncodedPathOrLogError(
    encodedPath: String,
): CallbackDataPath? = CallbackDataPath
    .tryParse(encodedPath)
    .also { pathOrNull ->
        pathOrNull.ifNull {
            chat.sendMessage(
                text = "Unknown command format '$encodedPath'",
            )
        }
    }

private suspend fun LLMChatContext.handleButtonClick(
    path: CallbackDataPath,
    messageToEdit: MessageId?,
    waitingForAnswerInputs: WaitingForAnswerInputs.InChat,
) {

    val button = findButton(
        buttons = commands,
        path = path,
    ) ?: run {
        chat.sendMessage(
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
                chat.sendMessage(
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

private suspend fun LLMChatContext.openPage(
    messageToEdit: MessageId?,
    path: CallbackDataPath,
    page: TelegramPageMessage,
) {
    chat.sendMessage(
        messageToEdit = messageToEdit,
        text = page.generateText(this),
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
                                    generateText = {
                                        "Base prompt: ${userSettings.get().basePrompt}"
                                    },
                                    buttons = listOf(
                                        TelegramPageMessage.Button(
                                            id = CallbackDataPath.Entry("edit"),
                                            text = "Edit",
                                            type = TelegramPageMessage.Button.Type.Input(
                                                onInput = { input ->
                                                    userSettings.update { copy(basePrompt = input) }
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