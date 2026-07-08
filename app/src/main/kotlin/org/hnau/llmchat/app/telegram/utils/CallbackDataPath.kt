package org.hnau.llmchat.app.telegram.utils

import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import arrow.core.toNonEmptyListOrThrow
import org.hnau.commons.kotlin.it
import org.hnau.commons.kotlin.mapper.Mapper
import org.hnau.commons.kotlin.mapper.plus
import org.hnau.commons.kotlin.mapper.stringToStringsBySeparator
import org.hnau.commons.kotlin.mapper.toListMapper

data class CallbackDataPath(
    val entries: NonEmptyList<Entry>,
) {

    @JvmInline
    value class Entry(
        val id: String,
    )

    operator fun plus(
        entry: Entry,
    ): CallbackDataPath = CallbackDataPath(
        entries = entries + entry,
    )

    fun tryGoBack(
        count: Int = 1,
    ): CallbackDataPath? = entries
        .dropLast(count)
        .toNonEmptyListOrNull()
        ?.let(::CallbackDataPath)

    fun encode(): String =
        unsafeStringMapper.reverse(this)

    override fun toString(): String = encode()

    companion object {

        fun tryParse(
            string: String,
        ): CallbackDataPath? = runCatching {
            unsafeStringMapper.direct(string)
        }.getOrNull()

        private val unsafeStringMapper: Mapper<String, CallbackDataPath> =
            Mapper.stringToStringsBySeparator('-') +
                    Mapper(
                        direct = ::Entry,
                        reverse = Entry::id
                    ).toListMapper() +
                    Mapper(
                        direct = List<Entry>::toNonEmptyListOrThrow,
                        reverse = ::it,
                    ) +
                    Mapper(
                        direct = ::CallbackDataPath,
                        reverse = CallbackDataPath::entries
                    )
    }
}