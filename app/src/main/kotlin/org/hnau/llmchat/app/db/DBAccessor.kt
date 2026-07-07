package org.hnau.llmchat.app.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.measureTimedValue
import org.flywaydb.core.Flyway
import org.hnau.commons.gen.loggable.annotations.Loggable
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
            migrateDatabase(adapter)
            return DBAccessor(adapter)
        }

        private suspend fun migrateDatabase(
            adapter: DBAdapter,
        ) {

            val flyWay = Flyway
                .configure()
                .dataSource(
                    /* url = */ adapter.jdbcUrl,
                    /* user = */ null,
                    /* password = */ null,
                )
                .load()

            logger.i { "Running database migrations..." }

            val (result, duration) = measureTimedValue {
                withContext(Dispatchers.IO) {
                    flyWay.migrate()
                }
            }

            logger.i { "Database migrations complete. ${result.migrationsExecuted} migration(s) applied in $duration" }
        }
    }
}
