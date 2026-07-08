package org.hnau.llmchat.app.telegram.utils

import arrow.core.nonEmptyListOf
import arrow.core.tail
import arrow.core.toNonEmptyListOrNull
import co.touchlab.kermit.Logger
import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.answers.answerCallbackQuery
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.deleteMessage
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextReceiver
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onText
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.message
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dev.inmo.tgbotapi.types.queries.callback.DataCallbackQuery
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.hnau.commons.kotlin.foldNullable
import org.hnau.commons.kotlin.ifNull
import org.hnau.commons.kotlin.lazy.AsyncLazy
import org.hnau.commons.kotlin.removePrefixOrNull
import org.hnau.llmchat.app.chat.ButtonResult
import org.hnau.llmchat.app.chat.ChatId
import org.hnau.llmchat.app.chat.ChatPage
import org.hnau.llmchat.app.chat.ChatProcessor
import org.hnau.llmchat.app.chat.ChatRootPage
import org.hnau.llmchat.app.chat.fold
import org.hnau.llmchat.app.llm.WaitingForAnswerInputs

private val logger = Logger.withTag("ChatProcessorToTelegramBotConfigExt")

fun <C> ChatProcessor<C>.toTelegramBotConfig(): BehaviourContextReceiver<Unit> = {

    val waitingForAnswerInputs = WaitingForAnswerInputs()

    val createContext: suspend (IdChatIdentifier) -> C = { idChatIdentifier ->
        val chatId = ChatId(idChatIdentifier.chatId.long.toString())
        buildContext(chatId)
    }

    setMyCommands(
        rootPages.map { command ->
            BotCommand(
                command = command.id.id,
                description = command.title,
            )
        }
    )

    onText { message ->

        val chatId = message.chat.id

        val context = createContext(message.chat.id)

        val text = message.content.text

        val handledByPages = tryHandleText(
            context = context,
            text = text,
            waitingForAnswerInputs = waitingForAnswerInputs.forChat(chatId),
            bot = this,
            rootPages = rootPages,
            chatId = chatId,
        )

        if (handledByPages) {
            return@onText
        }

        //TODO replace with handleMessage
        bot.sendMessage(
            chatId = chatId,
            text = "Answer for '$text'",
        )
    }

    onDataCallbackQuery { dataCallbackQuery ->

        dataCallbackQuery.message?.let { message ->
            val chatId = message.chat.id
            handleCallback(
                context = createContext(chatId),
                callback = dataCallbackQuery,
                bot = this,
                rootPages = rootPages,
                chatId = chatId,
                waitingForAnswerInputs = waitingForAnswerInputs.forChat(chatId),
            )
        }

        answerCallbackQuery(dataCallbackQuery)
    }

}

private suspend fun <C> tryHandleText(
    context: C,
    rootPages: List<ChatRootPage<C>>,
    chatId: IdChatIdentifier,
    bot: TelegramBot,
    text: String,
    waitingForAnswerInputs: WaitingForAnswerInputs.InChat,
): Boolean {

    val buttons = generateButtons(
        context = context,
        rootPages = rootPages,
    )

    return waitingForAnswerInputs.consume().foldNullable(
        ifNotNull = { waitingInput ->
            findButton(
                buttons = buttons.get(),
                path = waitingInput.path,
            )
                ?.type
                ?.fold(
                    ifChild = { null },
                    ifClick = { null },
                    ifInput = { onInput ->
                        val result = onInput(context, text)
                        handleButtonResult(
                            context = context,
                            chatId = chatId,
                            bot = bot,
                            buttonResult = result,
                            buttonPath = waitingInput.path,
                            waitingForAnswerInputs = waitingForAnswerInputs,
                            messageToEdit = null,
                            rootPages = rootPages,
                        )
                    },
                )
                .foldNullable(
                    ifNotNull = { true },
                    ifNull = {
                        bot.sendMessage(
                            chatId = chatId,
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

                        val path = tryParseEncodedPathOrLogError(
                            chatId = chatId,
                            bot = bot,
                            encodedPath = command,
                        )
                            ?: return@foldNullable true

                        handleButtonClick(
                            context = context,
                            chatId = chatId,
                            bot = bot,
                            buttons = buttons.get(),
                            path = path,
                            messageToEdit = null,
                            rootPages = rootPages,
                            waitingForAnswerInputs = waitingForAnswerInputs,
                        )

                        true
                    }
                )
        },
    )
}

private suspend fun <C> handleCallback(
    context: C,
    chatId: IdChatIdentifier,
    bot: TelegramBot,
    rootPages: List<ChatRootPage<C>>,
    callback: DataCallbackQuery,
    waitingForAnswerInputs: WaitingForAnswerInputs.InChat,
) {
    val message = callback.message ?: return

    val encodedPath = callback.data

    if (encodedPath == CancelInputCallbackData) {
        waitingForAnswerInputs
            .consume()
            .foldNullable(
                ifNull = { logger.w { "No input to cancel" } },
                ifNotNull = { waitingInput ->
                    bot.deleteMessage(
                        chatId = chatId,
                        messageId = waitingInput.promptMessageId,
                    )
                },
            )
        return
    }

    val path = tryParseEncodedPathOrLogError(
        chatId = chatId,
        bot = bot,
        encodedPath = encodedPath,
    ) ?: return


    val buttons = generateButtons(
        context = context,
        rootPages = rootPages,
    )

    handleButtonClick(
        context = context,
        chatId = chatId,
        bot = bot,
        buttons = buttons.get(),
        path = path,
        messageToEdit = message.messageId,
        rootPages = rootPages,
        waitingForAnswerInputs = waitingForAnswerInputs,
    )
}

private suspend fun <C> handleButtonResult(
    context: C,
    chatId: IdChatIdentifier,
    bot: TelegramBot,
    rootPages: List<ChatRootPage<C>>,
    buttonResult: ButtonResult,
    buttonPath: CallbackDataPath,
    waitingForAnswerInputs: WaitingForAnswerInputs.InChat,
    messageToEdit: MessageId?,
) {
    buttonPath
        .tryGoBack(
            count = buttonResult.navigateBackCount + 1,
        )
        .foldNullable(
            ifNull = {
                bot.sendMessage(
                    chatId = chatId,
                    text = "Unable handle button click",
                )
            },
            ifNotNull = { newPath ->
                handleButtonClick(
                    context = context,
                    chatId = chatId,
                    bot = bot,
                    rootPages = rootPages,
                    buttons = generateButtons(
                        context = context,
                        rootPages = rootPages,
                    ).get(),
                    path = newPath,
                    messageToEdit = messageToEdit,
                    waitingForAnswerInputs = waitingForAnswerInputs,
                )
            }
        )
}

private suspend fun tryParseEncodedPathOrLogError(
    chatId: IdChatIdentifier,
    bot: TelegramBot,
    encodedPath: String,
): CallbackDataPath? = CallbackDataPath
    .tryParse(encodedPath)
    .also { pathOrNull ->
        pathOrNull.ifNull {
            bot.sendMessage(
                chatId = chatId,
                text = "Unknown command format '$encodedPath'",
            )
        }
    }

private suspend fun <C> handleButtonClick(
    context: C,
    chatId: IdChatIdentifier,
    bot: TelegramBot,
    rootPages: List<ChatRootPage<C>>,
    buttons: List<ChatPage.Button<C>>,
    path: CallbackDataPath,
    messageToEdit: MessageId?,
    waitingForAnswerInputs: WaitingForAnswerInputs.InChat,
) {

    val button = findButton(
        buttons = buttons,
        path = path,
    ) ?: run {
        bot.sendMessage(
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
                    bot = bot,
                    chatId = chatId,
                    messageToEdit = messageToEdit,
                    path = path,
                    page = message,
                )
            },
            ifClick = { onClick ->
                val result = onClick(context)
                handleButtonResult(
                    context = context,
                    chatId = chatId,
                    bot = bot,
                    rootPages = rootPages,
                    buttonResult = result,
                    buttonPath = path,
                    waitingForAnswerInputs = waitingForAnswerInputs,
                    messageToEdit = messageToEdit,
                )
            },
            ifInput = {
                val sentMessageId = bot
                    .sendMessage(
                        chatId = chatId,
                        text = "✏\uFE0F Input '${button.title}",
                        replyMarkup = listOf(
                            TelegramButton(
                                title = "❌ Cancel input",
                                path = CallbackDataPath(
                                    entries = nonEmptyListOf(
                                        CallbackDataPath.Entry(
                                            id = CancelInputCallbackData,
                                        )
                                    )
                                ),
                            )
                        ).toInlineKeyboardMarkup(),
                    )
                    .messageId
                waitingForAnswerInputs.add(
                    path,
                    sentMessageId,
                )
            }
        )
}

private val ChatPage.Button.Id.pathEntity: CallbackDataPath.Entry
    get() = CallbackDataPath.Entry(id)

private fun <C> findButton(
    buttons: List<ChatPage.Button<C>>,
    path: CallbackDataPath,
): ChatPage.Button<C>? = buttons
    .find { it.id.pathEntity == path.entries.head }
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
                            ifInput = { null },
                            ifClick = { null },
                            ifChild = { message ->
                                findButton(
                                    buttons = message.buttons,
                                    path = CallbackDataPath(tail),
                                )
                            },
                        )
                }
            )
    }

private fun List<TelegramButton>.toInlineKeyboardMarkup(): InlineKeyboardMarkup =
    InlineKeyboardMarkup(
        map { button ->
            listOf(
                CallbackDataInlineKeyboardButton(
                    text = button.title,
                    callbackData = button.path.encode(),
                )
            )
        }
    )


private suspend fun <C> openPage(
    chatId: IdChatIdentifier,
    messageToEdit: MessageId?,
    bot: TelegramBot,
    path: CallbackDataPath,
    page: ChatPage<C>,
) {
    val text = page.text
    val replyMarkup = buildList {
        addAll(
            page.buttons.map { button ->
                TelegramButton(
                    title = button.title,
                    path = path + button.id.pathEntity,
                )
            }
        )
        path.tryGoBack()?.let { pathToGoBack ->
            add(
                TelegramButton(
                    title = "Back",
                    path = pathToGoBack,
                )
            )
        }
    }.toInlineKeyboardMarkup()
    messageToEdit.foldNullable(
        ifNull = {
            bot.sendMessage(
                chatId = chatId,
                text = text,
                replyMarkup = replyMarkup,
            )
        },
        ifNotNull = { messageId ->
            bot.editMessageText(
                chatId = chatId,
                messageId = messageId,
                text = text,
                replyMarkup = replyMarkup,
            )
        }
    )
}

private fun <C> generateButtons(
    rootPages: List<ChatRootPage<C>>,
    context: C,
): AsyncLazy<List<ChatPage.Button<C>>> = AsyncLazy {
    coroutineScope {
        rootPages.map { rootPage ->
            async {
                val message = rootPage.generatePage(context)
                ChatPage.Button(
                    id = rootPage.id,
                    title = rootPage.title,
                    type = ChatPage.Button.Type.Child(
                        message = message,
                    )
                )
            }
        }
            .awaitAll()
    }
}

private data class TelegramButton(
    val title: String,
    val path: CallbackDataPath,
)


private const val CancelInputCallbackData = "cancel_input"