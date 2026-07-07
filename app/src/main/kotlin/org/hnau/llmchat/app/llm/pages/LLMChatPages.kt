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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.hnau.commons.gen.loggable.annotations.Loggable
import org.hnau.commons.kotlin.foldNullable
import org.hnau.commons.kotlin.ifNull
import org.hnau.commons.kotlin.lazy.AsyncLazy
import org.hnau.commons.kotlin.removePrefixOrNull
import org.hnau.llmchat.app.db.settings.update
import org.hnau.llmchat.app.llm.LLMChatContext
import org.hnau.llmchat.app.llm.WaitingForAnswerInputs
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

        val buttons = context.generateButtons()

        return waitingForAnswerInputs.consume().foldNullable(
            ifNotNull = { waitingInput ->
                findButton(
                    buttons = buttons.get(),
                    path = waitingInput.path,
                )
                    ?.type
                    ?.fold(
                        ifChild = { null },
                        ifInput = { onInput ->
                            onInput(context, text)
                            context.afterInput(
                                inputPath = waitingInput.path,
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
                                buttons = buttons.get(),
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
                    ifNotNull = { waitingInput ->
                        context.chat.deleteMessage(waitingInput.promptMessageId)
                    },
                )
            context.chat.bot.answerCallbackQuery(callback)
            return
        }

        val path = context.tryParseEncodedPathOrLogError(
            encodedPath = encodedPath,
        ) ?: return


        val buttons = context.generateButtons()

        context.handleButtonClick(
            buttons = buttons.get(),
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
            buttons = generateButtons().get(),
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
        buttons: List<TelegramPageMessage.Button>,
        path: CallbackDataPath,
        messageToEdit: MessageId?,
        waitingForAnswerInputs: WaitingForAnswerInputs.InChat,
    ) {

        val button = findButton(
            buttons = buttons,
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
                    val promptMessageId = chat.sendMessage(
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
                    waitingForAnswerInputs.add(
                        path,
                        promptMessageId,
                    )
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
            text = page.text,
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

    private data class Command(
        val id: CallbackDataPath.Entry,
        val text: String,
        val generateMessage: suspend LLMChatContext.() -> TelegramPageMessage,
    )

    private fun LLMChatContext.generateButtons(): AsyncLazy<List<TelegramPageMessage.Button>> =
        AsyncLazy {
            coroutineScope {
                commands
                    .map { command ->
                        async {
                            val message = command.generateMessage(this@generateButtons)
                            TelegramPageMessage.Button(
                                id = command.id,
                                text = command.text,
                                type = TelegramPageMessage.Button.Type.Child(
                                    message = message,
                                )
                            )
                        }
                    }
                    .awaitAll()
            }
        }

    private val commands: List<Command> = listOf(
        Command(
            id = CallbackDataPath.Entry("settings"),
            text = "Settings",
            generateMessage = {
                TelegramPageMessage(
                    text = "Settings",
                    buttons = listOf(
                        TelegramPageMessage.Button(
                            id = CallbackDataPath.Entry("basePrompt"),
                            text = "Base prompt",
                            type = TelegramPageMessage.Button.Type.Child(
                                TelegramPageMessage(
                                    text = "Base prompt: ${userSettings.settings.basePrompt}",
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
            }
        )
    )


    @Suppress("ConstPropertyName")
    companion object {

        private const val CancelInputCallbackData = "cancel_input"
    }
}