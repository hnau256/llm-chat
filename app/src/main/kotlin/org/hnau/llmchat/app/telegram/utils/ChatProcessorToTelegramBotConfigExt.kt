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
import dev.inmo.tgbotapi.extensions.api.send.setMessageReaction
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
import org.hnau.llmchat.app.chat.ButtonIcon
import org.hnau.llmchat.app.chat.ButtonResult
import org.hnau.llmchat.app.chat.ChatId
import org.hnau.llmchat.app.chat.ChatPage
import org.hnau.llmchat.app.chat.ChatProcessor
import org.hnau.llmchat.app.chat.ChatRootPage
import org.hnau.llmchat.app.chat.createButtonTitle
import org.hnau.llmchat.app.chat.fold
import org.hnau.llmchat.app.llm.WaitingForAnswerInputs

private val logger = Logger.withTag("ChatProcessorToTelegramBotConfigExt")

fun <C> ChatProcessor<C>.toTelegramBotConfig(): BehaviourContextReceiver<Unit> = {

    val waitingForAnswerInputs = WaitingForAnswerInputs()

    val createContext: suspend (IdChatIdentifier) -> ExtendedContext<C> = { idChatIdentifier ->
        val chatId = ChatId(idChatIdentifier.chatId.long.toString())
        val context = buildContext(chatId)
        ExtendedContext(
            context = context,
            bot = this,
            waitingForAnswerInputs = waitingForAnswerInputs.forChat(idChatIdentifier),
            chatId = idChatIdentifier,
            rootPages = rootPages,
        )
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

        val context = createContext(message.chat.id)

        val text = message.content.text

        val handledByPages = tryHandleText(
            context = context,
            text = text,
        )

        if (handledByPages) {
            return@onText
        }


        val chat = TelegramChat(
            bot = this,
            chatId = context.chatId,
        )

        setMessageReaction(
            message = message,
            emoji = "👀",
        )

        chat.handleMessage(
            context = context.context,
            replayFor = message.replyTo?.messageId?.toMessageId(),
            message = text,
        )
    }

    onDataCallbackQuery { dataCallbackQuery ->

        dataCallbackQuery.message?.let { message ->
            val chatId = message.chat.id
            handleCallback(
                context = createContext(chatId),
                callback = dataCallbackQuery,
            )
        }

        answerCallbackQuery(dataCallbackQuery)
    }

}

private suspend fun <C> tryHandleText(
    context: ExtendedContext<C>,
    text: String,
): Boolean {

    return context.waitingForAnswerInputs.consume().foldNullable(
        ifNotNull = { waitingInput ->
            findButton(
                buttons = context.pages.get(),
                path = waitingInput.path,
            )
                ?.type
                ?.fold(
                    ifChild = { null },
                    ifClick = { null },
                    ifInput = { onInput ->
                        val result = onInput(text)
                        handleButtonResult(
                            context = context,
                            buttonResult = result,
                            buttonPath = waitingInput.path,
                            messageToEdit = null,
                        )
                    },
                )
                .foldNullable(
                    ifNotNull = { true },
                    ifNull = {
                        context.bot.sendMessage(
                            chatId = context.chatId,
                            text = "${ButtonIcon.warning} Unable handle input to answer",
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
                            context = context,
                            encodedPath = command,
                        )
                            ?: return@foldNullable true

                        handleButtonClick(
                            context = context,
                            path = path,
                            messageToEdit = null,
                        )

                        true
                    }
                )
        },
    )
}

private suspend fun <C> handleCallback(
    context: ExtendedContext<C>,
    callback: DataCallbackQuery,
) {
    val message = callback.message ?: return

    val encodedPath = callback.data

    if (encodedPath == CancelInputCallbackData) {
        context
            .waitingForAnswerInputs
            .consume()
            .foldNullable(
                ifNull = { logger.w { "No input to cancel" } },
                ifNotNull = { waitingInput ->
                    context.bot.deleteMessage(
                        chatId = context.chatId,
                        messageId = waitingInput.promptMessageId,
                    )
                },
            )
        return
    }

    val path = tryParseEncodedPathOrLogError(
        context = context,
        encodedPath = encodedPath,
    ) ?: return

    handleButtonClick(
        context = context,
        path = path,
        messageToEdit = message.messageId,
    )
}

private suspend fun <C> handleButtonResult(
    context: ExtendedContext<C>,
    buttonResult: ButtonResult,
    buttonPath: CallbackDataPath,
    messageToEdit: MessageId?,
) {
    buttonPath
        .tryGoBack(
            count = buttonResult.navigateBackCount + 1,
        )
        .foldNullable(
            ifNull = {
                context.bot.sendMessage(
                    chatId = context.chatId,
                    text = "${ButtonIcon.warning} Unable handle button click",
                )
            },
            ifNotNull = { newPath ->
                context.resetPages()
                handleButtonClick(
                    context = context,
                    path = newPath,
                    messageToEdit = messageToEdit,
                )
            }
        )
}

private suspend fun <C> tryParseEncodedPathOrLogError(
    context: ExtendedContext<C>,
    encodedPath: String,
): CallbackDataPath? = CallbackDataPath
    .tryParse(encodedPath)
    .also { pathOrNull ->
        pathOrNull.ifNull {
            context.bot.sendMessage(
                chatId = context.chatId,
                text = "${ButtonIcon.warning} Unknown command format '$encodedPath'",
            )
        }
    }

private suspend fun <C> handleButtonClick(
    context: ExtendedContext<C>,
    path: CallbackDataPath,
    messageToEdit: MessageId?,
) {

    val button = findButton(
        buttons = context.pages.get(),
        path = path,
    ) ?: run {
        context.bot.sendMessage(
            chatId = context.chatId,
            text = "${ButtonIcon.warning} Unknown command '$path'",
        )
        return
    }

    button
        .type
        .fold(
            ifChild = { message ->
                openPage(
                    context = context,
                    messageToEdit = messageToEdit,
                    path = path,
                    page = message,
                )
            },
            ifClick = { onClick ->
                val result = onClick()
                handleButtonResult(
                    context = context,
                    buttonResult = result,
                    buttonPath = path,
                    messageToEdit = messageToEdit,
                )
            },
            ifInput = {
                val sentMessageId = context
                    .bot
                    .sendMessage(
                        chatId = context.chatId,
                        text = "${ButtonIcon.edit} Please, enter '${button.title} in next message",
                        replyMarkup = listOf(
                            TelegramButton(
                                title = createButtonTitle(
                                    icon = ButtonIcon.cancel,
                                    title = "Cancel input",
                                ),
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
                context
                    .waitingForAnswerInputs
                    .add(
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
    context: ExtendedContext<C>,
    path: CallbackDataPath,
    page: ChatPage<C>,
    messageToEdit: MessageId?,
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
                    title = createButtonTitle(
                        icon = ButtonIcon.back,
                        title = "Back",
                    ),
                    path = pathToGoBack,
                )
            )
        }
    }.toInlineKeyboardMarkup()
    messageToEdit.foldNullable(
        ifNull = {
            context.bot.sendMessage(
                chatId = context.chatId,
                text = text,
                replyMarkup = replyMarkup,
            )
        },
        ifNotNull = { messageId ->
            context.bot.editMessageText(
                chatId = context.chatId,
                messageId = messageId,
                text = text,
                replyMarkup = replyMarkup,
            )
        }
    )
}

private data class ExtendedContext<C>(
    val context: C,
    val chatId: IdChatIdentifier,
    val bot: TelegramBot,
    val waitingForAnswerInputs: WaitingForAnswerInputs.InChat,
    val rootPages: List<ChatRootPage<C>>,
) {

    var pages: AsyncLazy<List<ChatPage.Button<C>>> = generatePages()
        private set

    fun resetPages() {
        pages = generatePages()
    }

    private fun generatePages(): AsyncLazy<List<ChatPage.Button<C>>> = AsyncLazy {
        coroutineScope {
            rootPages
                .map { rootPage ->
                    async {
                        val message = rootPage.generatePage(context)
                        ChatPage.Button(
                            id = rootPage.id,
                            title = rootPage.title,
                            type = ChatPage.Button.Type.Child(
                                page = message,
                            )
                        )
                    }
                }
                .awaitAll()
        }
    }
}

private data class TelegramButton(
    val title: String,
    val path: CallbackDataPath,
)


private const val CancelInputCallbackData = "cancel_input"