package org.hnau.llmchat.app.db

import java.sql.Connection
import java.sql.DriverManager

interface DBAdapter {

    val startSql: String?
        get() = null

    val jdbcUrl: String

    fun getConnection(): Connection = DriverManager
        .getConnection(jdbcUrl)

    companion object
}