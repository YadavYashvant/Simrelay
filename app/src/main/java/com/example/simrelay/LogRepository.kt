package com.example.simrelay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ApiLog(
    val timestamp: String,
    val method: String,
    val path: String,
    val statusCode: Int,
    val detail: String = ""
)

object LogRepository {
    private val _logs = MutableStateFlow<List<ApiLog>>(emptyList())
    val logs = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun addLog(method: String, path: String, statusCode: Int, detail: String = "") {
        val newLog = ApiLog(
            timestamp = dateFormat.format(Date()),
            method = method,
            path = path,
            statusCode = statusCode,
            detail = detail
        )
        _logs.update { currentLogs ->
            (listOf(newLog) + currentLogs).take(20) // Keep last 20 logs
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
