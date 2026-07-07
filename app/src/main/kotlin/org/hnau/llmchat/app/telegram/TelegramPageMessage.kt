package org.hnau.llmchat.app.telegram

data class TelegramPageMessage(
    val generateText: () -> String,
    val buttons: List<Button>,
) {

    data class Button(
        val id: String,
        val text: String,
        val type: Type,
    ) {

        sealed interface Type {

            data class Child(
                val message: TelegramPageMessage,
            ) : Type
        }
    }
}