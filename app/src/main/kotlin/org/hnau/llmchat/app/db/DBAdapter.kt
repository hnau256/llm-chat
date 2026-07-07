package org.hnau.llmchat.app.db

import java.sql.Connection

interface DBAdapter {

    val jdbcUrl: String

    fun getConnection(): Connection

    companion object
}