package org.hnau.llmchat.app.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

fun DBAccessor.Companion.sqlite(
    databaseFile: File,
): DBAccessor = object : DBAccessor {

    override suspend fun <T> withConnection(
        block: suspend (Connection) -> T,
    ): T = withContext(Dispatchers.IO) {
        DriverManager
            .getConnection("jdbc:sqlite:${databaseFile.path}")
            .apply { createStatement().execute("PRAGMA journal_mode=WAL") }
            .use { connection -> block(connection) }
    }
}