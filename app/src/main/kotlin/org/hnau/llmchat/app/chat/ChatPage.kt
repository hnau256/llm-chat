package org.hnau.llmchat.app.chat

import org.hnau.commons.gen.fold.annotations.Fold

class ChatPage<out C>(
    val text: String,
    val buttons: List<Button<C>>,
) {

    data class Button<out C>(
        val title: String,
        val type: Type<C>,
        val id: Id = Id.generate(title),
    ) {

        @JvmInline
        value class Id(
            val id: String,
        ) {

            companion object {

                fun generate(
                    title: String,
                ): Id = title
                    .lowercase()
                    .filter(Char::isLetterOrDigit)
                    .let(::Id)
            }
        }

        @Fold
        sealed interface Type<out C> {

            data class Child<out C>(
                val message: ChatPage<C>,
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