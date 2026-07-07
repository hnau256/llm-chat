package org.hnau.llmchat.app.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hnau.commons.gen.loggable.annotations.Loggable
import org.hnau.llmchat.app.db.migration.migrate
import java.sql.Connection

@Loggable
class DBAccessor private constructor(
    private val adapter: DBAdapter,
) {

    suspend fun <T> withConnection(
        block: suspend (Connection) -> T,
    ): T = withContext(Dispatchers.IO) {
        adapter
            .getConnection()
            .use { connection -> block(connection) }
    }

    companion object {

        suspend fun create(
            adapter: DBAdapter,
        ): DBAccessor {
            adapter.migrate()
            return DBAccessor(adapter)
        }
    }
}
