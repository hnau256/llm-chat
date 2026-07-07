package org.hnau.llmchat.app.dto

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ApiKey(
    val value: String,
)