package org.hnau.llmchat.app.db.migration

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.hnau.llmchat.app.db.DBAdapter
import kotlin.time.measureTimedValue

private val logger = Logger.withTag("DBAdapterMigrateExt")

internal suspend fun DBAdapter.migrate() {

    val flyWay = Flyway
        .configure()
        .dataSource(
            /* url = */ jdbcUrl,
            /* user = */ null,
            /* password = */ null,
        )
        .load()

    logger.d { "Running database migrations..." }

    val (result, duration) = measureTimedValue {
        withContext(Dispatchers.IO) {
            flyWay.migrate()
        }
    }

    logger.i { "Database migrations complete. ${result.migrationsExecuted} migration(s) applied in $duration" }
}