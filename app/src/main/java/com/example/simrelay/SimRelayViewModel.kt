package com.example.simrelay

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simrelay.services.SmsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecentLog(
    val code: String,
    val method: String,
    val path: String,
)

data class SimRelayUiState(
    val serverRunning: Boolean = false,
    val serverStarting: Boolean = false,
    val serverPort: Int = 3000,
    val serverHost: String = "192.168.1.5",
    val apiKey: String = "sk_test_••••••••••••8f92",
    val lastActionMessage: String = "Accepting connections",
    val selectedTab: SimRelayTab = SimRelayTab.Console,
    val smsTo: String = "",
    val smsMessage: String = "",
    val smsStatus: String? = null,
    val errorMessage: String? = null,
    val recentLogs: List<RecentLog> = listOf(
        RecentLog("200", "GET", "/api/v1/status"),
        RecentLog("202", "POST", "/api/v1/sms/send"),
    ),
)

enum class SimRelayTab(val title: String, val subtitle: String) {
    Console("Console", "Server overview"),
    Messages("Messages", "Relay form"),
    Devices("Devices", "Connected devices"),
    Logs("Logs", "Live request log")
}

class SimRelayViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SimRelayUiState())
    val uiState: StateFlow<SimRelayUiState> = _uiState.asStateFlow()

    fun selectTab(tab: SimRelayTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setSmsTo(value: String) {
        _uiState.update { it.copy(smsTo = value, smsStatus = null, errorMessage = null) }
    }

    fun setSmsMessage(value: String) {
        _uiState.update { it.copy(smsMessage = value, smsStatus = null, errorMessage = null) }
    }

    fun startServer(context: Context) {
        if (_uiState.value.serverRunning || _uiState.value.serverStarting) return

        _uiState.update { it.copy(serverStarting = true) }

        val intent = Intent(context, SmsService::class.java)
        context.startForegroundService(intent)

        _uiState.update {
            it.copy(
                serverRunning = true,
                serverStarting = false,
                lastActionMessage = "Server running"
            )
        }
    }

    fun stopServer(context: Context) {
        val intent = Intent(context, SmsService::class.java)
        context.stopService(intent)

        _uiState.update {
            it.copy(
                serverRunning = false,
                lastActionMessage = "Server stopped"
            )
        }
    }

    fun sendSms() {
        val state = _uiState.value
        when {
            state.smsTo.isBlank() -> _uiState.update { it.copy(errorMessage = "Recipient phone number is required") }
            state.smsMessage.isBlank() -> _uiState.update { it.copy(errorMessage = "Message cannot be empty") }
            else -> _uiState.update {
                it.copy(
                    smsStatus = "Queued for relay",
                    errorMessage = null,
                    recentLogs = listOf(RecentLog("202", "POST", "/api/v1/sms/send")) + it.recentLogs.take(4),
                )
            }
        }
    }
}
