package org.hnau.llmchat.app.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.hnau.commons.gen.loggable.annotations.Loggable
import org.hnau.commons.kotlin.lazy.AsyncLazy
import java.sql.Connection

@Loggable
class DBAccessor(
    private val adapter: DBAdapter,
) {

    private val migrator: AsyncLazy<Unit> = AsyncLazy {
        withContext(Dispatchers.IO) {
            logger.i { "Running database migrations..." }
            val result = Flyway
                .configure()
                .dataSource(
                    /* url = */ adapter.jdbcUrl,
                    /* user = */ null,
                    /* password = */ null,
                )
                .apply { adapter.startSql?.let(::initSql) }
                .load()
                .migrate()
            logger.i { "Database migrations complete. ${result.migrationsExecuted} migration(s) applied" }
        }
    }

    suspend fun <T> withConnection(
        block: suspend (Connection) -> T,
    ): T = withContext(Dispatchers.IO) {
        migrator.get()
        adapter
            .getConnection()
            .apply {
                adapter
                    .startSql
                    ?.let { startSql ->
                        createStatement().execute(startSql)
                    }
            }
            .use { connection -> block(connection) }
    }
}
