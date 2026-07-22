package org.hnau.llmchat.app.hnauchat.messages

import org.hnau.commons.gen.fold.annotations.Fold
import org.hnau.commons.kotlin.mapper.Mapper
import org.hnau.commons.kotlin.mapper.nameToEnum

@Fold
enum class MessageRole {
    User, Assistant;

    companion object {

        val stringMapper: Mapper<String, MessageRole> =
            Mapper.nameToEnum<MessageRole>()
    }
}