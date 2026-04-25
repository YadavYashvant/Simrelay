package com.example.simrelay

import kotlinx.serialization.Serializable

@Serializable
data class SendSmsRequest(
    val to: String,
    val message: String,
    val apiKey: String? = null
)

@Serializable
data class ApiErrorResponse(
    val ok: Boolean = false,
    val error: String
)

@Serializable
data class SendSmsResponse(
    val ok: Boolean = true,
    val status: String = "sent",
    val to: String
)

@Serializable
data class HealthResponse(
    val status: String = "ok",
    val device: String
)