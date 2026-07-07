package org.hnau.llmchat.app.db

import org.flywaydb.core.Flyway

object DbMigrator {

    suspend fun migrate(
        accessor: DBAccessor,
    ): Int = accessor.withConnection { connection ->
        Flyway.configure()
        Flyway.configure()
            .dataSource(
                connection.metaData.url,
                "",
                "",
            )
            .load()
            .migrate()
            .migrationsExecuted
    }
}
