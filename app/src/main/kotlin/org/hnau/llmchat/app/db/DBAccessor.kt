package org.hnau.llmchat.app.db

import java.sql.Connection

interface DBAccessor {

    suspend fun <T> withConnection(
        block: suspend (Connection) -> T,
    ): T

    companion object
}