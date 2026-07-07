package org.hnau.llmchat.app.db

import org.flywaydb.core.api.callback.Callback
import org.flywaydb.core.api.callback.Context
import org.flywaydb.core.api.callback.Event
import org.flywaydb.core.api.configuration.FluentConfiguration

inline fun FluentConfiguration.addAfterConnectCallback(
    crossinline afterConnect: (Context) -> Unit,
): FluentConfiguration = callbacks(
    object : Callback {
        override fun supports(
            event: Event,
            context: Context
        ): Boolean = event == Event.AFTER_CONNECT

        override fun canHandleInTransaction(
            event: Event,
            context: Context
        ): Boolean = false

        override fun handle(
            event: Event,
            context: Context
        ) {
            afterConnect(context)
        }

        override fun getCallbackName() = "afterConnect"
    }
)
