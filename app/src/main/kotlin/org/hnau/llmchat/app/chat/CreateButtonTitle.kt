package org.hnau.llmchat.app.chat

fun createButtonTitle(
    icon: ButtonIcon? = null,
    title: String,
    additionalInfo: String? = null,
): String = buildString {

    icon?.let { icon ->
        append(icon.emoji)
        append(" ")
    }

    append(title)

    additionalInfo?.let { additionalInfo ->
        append(" [")
        append(additionalInfo)
        append("]")
    }
}

