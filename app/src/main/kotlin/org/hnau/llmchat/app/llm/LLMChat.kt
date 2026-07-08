package org.hnau.llmchat.app.llm

import dev.inmo.tgbotapi.extensions.api.answers.answerCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextReceiver
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onText
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.message
import dev.inmo.tgbotapi.types.IdChatIdentifier
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.app.db.settings.UserSettingsRepository
import org.hnau.llmchat.app.dto.UserId
import org.hnau.llmchat.app.llm.pages.LLMChatPages
import org.hnau.llmchat.app.telegram.TelegramChat

fun LLMChat(
    dbAccessor: DBAccessor,
): BehaviourContextReceiver<Unit> = {

    val pages = LLMChatPages()

    val createContext: suspend (IdChatIdentifier) -> LLMChatContext = { chatId ->
        LLMChatContext(
            chat = TelegramChat(
                bot = this,
                id = chatId,
            ),
            userSettings = UserSettingsRepository.create(
                db = dbAccessor,
                userId = UserId(chatId.chatId.long.toString())
            ),
        )
    }

    pages.config(
        bot = this,
    )

    onText { message ->

        val context = createContext(message.chat.id)

        val text = message.content.text

        val handledByPages = pages.tryHandleText(
            context = context,
            text = text,
        )

        if (handledByPages) {
            return@onText
        }

        context.chat.sendMessage(
            text = "Answer for '$text'",
        )
    }

    onDataCallbackQuery { dataCallbackQuery ->

        dataCallbackQuery.message?.let { message ->
            pages.handleCallback(
                context = createContext(message.chat.id),
                callback = dataCallbackQuery,
            )
        }

        answerCallbackQuery(dataCallbackQuery)
    }
}