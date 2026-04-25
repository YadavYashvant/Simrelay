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

import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import kotlin.concurrent.thread
import org.json.JSONObject

data class RecentLog(
    val code: String,
    val method: String,
    val path: String,
)

data class SimRelayUiState(
    val serverRunning: Boolean = false,
    val serverStarting: Boolean = false,
    val serverPort: Int = 3000,
    val serverHost: String = "localhost",
    val apiKey: String = "sk_test_simrelay_8f92",
    val lastActionMessage: String = "Ready to start",
    val selectedTab: SimRelayTab = SimRelayTab.Console,
    val smsTo: String = "",
    val smsMessage: String = "",
    val smsStatus: String? = null,
    val errorMessage: String? = null,
    val recentLogs: List<RecentLog> = emptyList(),
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

    init {
        updateLocalIp()
    }

    private fun updateLocalIp() {
        val ip = getLocalIpAddress() ?: "localhost"
        _uiState.update { it.copy(serverHost = ip) }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress.indexOf(':') < 0) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

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

        _uiState.update { it.copy(serverStarting = true, lastActionMessage = "Starting service...") }

        thread {
            try {
                val intent = Intent(context, SmsService::class.java)
                context.startForegroundService(intent)
                
                // For simplicity, we assume it starts. 
                // In production, we'd use a broadcast or bound service to confirm.
                _uiState.update {
                    it.copy(
                        serverRunning = true,
                        serverStarting = false,
                        lastActionMessage = "Server running on port ${it.serverPort}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        serverStarting = false,
                        errorMessage = "Failed to start server: ${e.message}"
                    )
                }
            }
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
        if (state.smsTo.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Recipient is required") }
            return
        }
        if (state.smsMessage.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Message is required") }
            return
        }

        _uiState.update { it.copy(smsStatus = "Sending...", errorMessage = null) }

        // Trigger SMS via Local API (Self-test)
        viewModelScope.launch {
            thread {
                try {
                    val url = URL("http://localhost:${state.serverPort}/send-sms")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("x-api-key", state.apiKey)
                    conn.doOutput = true

                    val json = JSONObject().apply {
                        put("to", state.smsTo)
                        put("message", state.smsMessage)
                    }

                    conn.outputStream.use { it.write(json.toString().toByteArray()) }

                    val responseCode = conn.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        _uiState.update {
                            it.copy(
                                smsStatus = "Sent successfully via API",
                                recentLogs = listOf(RecentLog("200", "POST", "/send-sms")) + it.recentLogs.take(9)
                            )
                        }
                    } else {
                        val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                        _uiState.update {
                            it.copy(
                                smsStatus = null,
                                errorMessage = "API Error ($responseCode): $error",
                                recentLogs = listOf(RecentLog(responseCode.toString(), "POST", "/send-sms")) + it.recentLogs.take(9)
                            )
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            smsStatus = null,
                            errorMessage = "Relay failed: ${e.message}"
                        )
                    }
                }
            }
        }
    }
}
