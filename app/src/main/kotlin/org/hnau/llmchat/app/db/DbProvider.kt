package org.hnau.llmchat.app.db

import arrow.core.right
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hnau.llmchat.app.utils.getRequiredEnv
import java.sql.Connection
import java.sql.DriverManager

object DbProvider {

    private val dbPath: String by lazy {
        getRequiredEnv(
            name = "DB_PATH",
            parser = { it.right() },
        )
    }

    suspend fun <T> withConnection(
        block: suspend (Connection) -> T,
    ): T = withContext(Dispatchers.IO) {
        DriverManager
            .getConnection("jdbc:sqlite:$dbPath")
            .apply { createStatement().execute("PRAGMA journal_mode=WAL") }
            .use { connection -> block(connection) }
    }
}
