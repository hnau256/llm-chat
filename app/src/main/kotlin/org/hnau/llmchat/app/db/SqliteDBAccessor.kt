package org.hnau.llmchat.app.db

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

fun DBAdapter.Companion.sqlite(
    databaseFile: File,
): DBAdapter = object : DBAdapter {

    override val jdbcUrl: String =
        "jdbc:sqlite:${databaseFile.path}"

    override fun getConnection(): Connection = DriverManager
        .getConnection(jdbcUrl)
}