package org.hnau.llmchat.app.chat

import org.hnau.commons.gen.fold.annotations.Fold

class ChatPage<out C>(
    val text: String,
    val buttons: List<Button<C>>,
) {

    data class Button<out C>(
        val id: Id,
        val title: String,
        val type: Type<C>,
    ) {

        @JvmInline
        value class Id(
            val id: String,
        )

        @Fold
        sealed interface Type<out C> {

            data class Child<out C>(
                val page: ChatPage<C>,
            ) : Type<C>

            data class Click<C>(
                val onClick: suspend (context: C) -> ButtonResult,
            ) : Type<C>

            data class Input<C>(
                val onInput: suspend (context: C, input: String) -> ButtonResult,
            ) : Type<C>
        }
    }
}