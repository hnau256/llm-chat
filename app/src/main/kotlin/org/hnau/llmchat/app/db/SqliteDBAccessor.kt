package org.hnau.llmchat.app.db

import java.io.File

fun DBAdapter.Companion.sqlite(
    databaseFile: File,
): DBAdapter = object : DBAdapter {

    override val startSql: String
        get() = "PRAGMA journal_mode=WAL"

    override val jdbcUrl: String =
        "jdbc:sqlite:${databaseFile.path}"
}