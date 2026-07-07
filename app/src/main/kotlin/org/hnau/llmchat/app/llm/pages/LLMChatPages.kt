package org.hnau.llmchat.app.llm.pages

import arrow.core.nonEmptyListOf
import arrow.core.tail
import arrow.core.toNonEmptyListOrNull
import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.answers.answerCallbackQuery
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.message
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.queries.callback.DataCallbackQuery
import org.hnau.commons.gen.loggable.annotations.Loggable
import org.hnau.commons.kotlin.foldNullable
import org.hnau.commons.kotlin.ifNull
import org.hnau.commons.kotlin.removePrefixOrNull
import org.hnau.llmchat.app.db.settings.update
import org.hnau.llmchat.app.llm.LLMChatContext
import org.hnau.llmchat.app.llm.WaitingForAnswerInputs
import org.hnau.llmchat.app.llm.logger
import org.hnau.llmchat.app.telegram.CallbackDataPath
import org.hnau.llmchat.app.telegram.TelegramButton
import org.hnau.llmchat.app.telegram.TelegramPageMessage
import org.hnau.llmchat.app.telegram.fold

@Loggable
class LLMChatPages {

    private val waitingForAnswerInputs = WaitingForAnswerInputs()

    suspend fun config(
        bot: TelegramBot,
    ) {
        bot.setMyCommands(
            commands.map { command ->
                BotCommand(
                    command = command.id.id,
                    description = command.text,
                )
            }
        )
    }

    suspend fun tryHandleText(
        context: LLMChatContext,
        text: String,
    ): Boolean {
        val waitingForAnswerInputs = waitingForAnswerInputs.forChat(
            chatId = context.chat.id,
        )

        return waitingForAnswerInputs.consume().foldNullable(
            ifNotNull = { inputToAnswer ->
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
                    .foldNullable(
                        ifNotNull = { true },
                        ifNull = {
                            context.chat.sendMessage(
                                text = "Unable handle input to answer",
                            )
                            false
                        },
                    )
            },
            ifNull = {
                text
                    .trim()
                    .lowercase()
                    .removePrefixOrNull("/")
                    .foldNullable(
                        ifNull = { false },
                        ifNotNull = { command ->

                            val path = context
                                .tryParseEncodedPathOrLogError(
                                    encodedPath = command,
                                )
                                ?: return@foldNullable true

                            context.handleButtonClick(
                                path = path,
                                messageToEdit = null,
                                waitingForAnswerInputs = waitingForAnswerInputs,
                            )

                            true
                        }
                    )
            },
        )
    }

    suspend fun handleCallback(
        context: LLMChatContext,
        callback: DataCallbackQuery,
    ) {
        val message = callback.message ?: return

        val encodedPath = callback.data

        val waitingForAnswerInputs = waitingForAnswerInputs.forChat(
            chatId = context.chat.id,
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
            context.chat.bot.answerCallbackQuery(callback)
            return
        }

        val path = context.tryParseEncodedPathOrLogError(
            encodedPath = encodedPath,
        ) ?: return

        context.handleButtonClick(
            path = path,
            messageToEdit = message.messageId,
            waitingForAnswerInputs = waitingForAnswerInputs,
        )
        context.chat.bot.answerCallbackQuery(callback)
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


    @Suppress("ConstPropertyName")
    companion object {

        private const val CancelInputCallbackData = "cancel_input"
    }
}