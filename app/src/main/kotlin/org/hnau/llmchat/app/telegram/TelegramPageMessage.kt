package org.hnau.llmchat.app.telegram

import org.hnau.commons.gen.fold.annotations.Fold

data class TelegramPageMessage(
    val generateText: () -> String,
    val buttons: List<Button<Button.Type>>,
) {

    data class Button<T : Button.Type>(
        val id: CallbackDataPath.Entry,
        val text: String,
        val type: T,
    ) {

        @Fold
        sealed interface Type {

            data class Child(
                val message: TelegramPageMessage,
            ) : Type
        }
    }
}