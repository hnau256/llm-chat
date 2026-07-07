package org.hnau.llmchat.app.llm

import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextReceiver
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onText
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import org.hnau.commons.kotlin.foldNullable
import org.hnau.commons.kotlin.removePrefixOrNull
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.app.db.settings.UserSettingsRepository
import org.hnau.llmchat.app.telegram.TelegramPageMessage

fun LLMChat(
    dbAccessor: DBAccessor,
): BehaviourContextReceiver<Unit> {

    val userSettingsRepository = UserSettingsRepository(dbAccessor)

    return {

        setMyCommands(
            rootPages
                .keys.map { command ->
                    BotCommand(command, command)
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
                        rootPages[command].foldNullable(
                            ifNull = {
                                send(
                                    chatId = chatId,
                                    text = "Unknown command '$text'",
                                )
                            },
                            ifNotNull = { page ->
                                send(
                                    chatId = chatId,
                                    text = page.generateText(),
                                    replyMarkup = InlineKeyboardMarkup(
                                        listOf(
                                            page.buttons.map { button ->
                                                CallbackDataInlineKeyboardButton(
                                                    text = button.text,
                                                    callbackData = command + "-" + button.id,
                                                )
                                            }
                                        )
                                    )
                                )
                            }
                        )
                    }
                )
        }

        onDataCallbackQuery { dataCallbackQuery ->

        }

    }
}

private val rootPages: Map<String, TelegramPageMessage> = mapOf(
    "settings" to TelegramPageMessage(
        generateText = { "Settings" },
        buttons = listOf(
            TelegramPageMessage.Button(
                id = "basePrompt",
                text = "Base prompt",
                type = TelegramPageMessage.Button.Type.Child(
                    TelegramPageMessage(
                        generateText = { "Base prompt: QWERTY" },
                        buttons = emptyList(),
                    )
                )
            )
        )
    )
)