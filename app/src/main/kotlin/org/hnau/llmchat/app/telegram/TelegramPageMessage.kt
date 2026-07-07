package org.hnau.llmchat.app.telegram

import org.hnau.commons.gen.fold.annotations.Fold

data class TelegramPageMessage(
    val generateText: suspend () -> String,
    val buttons: List<Button>,
) {

    data class Button(
        val id: CallbackDataPath.Entry,
        val text: String,
        val type: Type,
    ) {

        @Fold
        sealed interface Type {

            data class Child(
                val message: TelegramPageMessage,
            ) : Type

            data class Input(
                val onInput: suspend (String) -> Unit,
            ) : Type
        }
    }
}