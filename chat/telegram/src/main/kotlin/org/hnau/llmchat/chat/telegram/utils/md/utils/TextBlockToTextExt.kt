package org.hnau.llmchat.chat.telegram.utils.md.utils


fun TextBlock.toText(): String = foldRaw(
    ifText = TextBlock.Text::text,
    ifBlocks = TextBlock.Blocks::toText,
)

private fun TextBlock.Blocks.toText(): String = next.joinToString(
    prefix = first.toText(),
    separator = "",
) { next ->
    next.prefix + next.content.toText()
}

private fun TextBlock.Blocks.Next.toText(): String = prefix + content.toText()