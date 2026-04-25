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
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

data class SimRelayUiState(
    val serverRunning: Boolean = false,
    val serverStarting: Boolean = false,
    val serverPort: Int = 3000,
    val serverHost: String = "localhost",
    val apiKey: String = "",
    val lastActionMessage: String = "Ready to start",
    val selectedTab: SimRelayTab = SimRelayTab.Console,
    val smsTo: String = "",
    val smsMessage: String = "",
    val smsStatus: String? = null,
    val errorMessage: String? = null,
    val logs: List<ApiLog> = emptyList(),
    val hasSmsPermission: Boolean = false,
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
)

data class DiscoveredDevice(
    val name: String,
    val host: String,
    val port: Int,
    val isLive: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis()
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

    private var nsdHelper: NsdHelper? = null

    init {
        _uiState.update { it.copy(apiKey = ConfigManager.getApiKey()) }
        updateLocalIp()
        
        // Observe logs
        viewModelScope.launch {
            LogRepository.logs.collect { logs ->
                _uiState.update { it.copy(logs = logs) }
            }
        }
        
        // Monitor server state (simple polling or state flow)
        viewModelScope.launch {
            while(true) {
                val running = ServerManager.isRunning
                val ip = ServerManager.getLocalIpAddress() ?: "localhost"
                _uiState.update { it.copy(serverRunning = running, serverHost = ip) }
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    fun initDiscovery(context: Context) {
        if (nsdHelper != null) return
        
        nsdHelper = NsdHelper(context).apply {
            startDiscovery(object : NsdHelper.DiscoveryListener {
                override fun onServiceFound(name: String, host: String, port: Int) {
                    _uiState.update { state ->
                        val device = DiscoveredDevice(name, host, port)
                        val newList = state.discoveredDevices.filter { it.name != name } + device
                        state.copy(discoveredDevices = newList)
                    }
                }

                override fun onServiceLost(name: String) {
                    _uiState.update { state ->
                        val newList = state.discoveredDevices.map { 
                            if (it.name == name) it.copy(isLive = false) else it 
                        }
                        state.copy(discoveredDevices = newList)
                    }
                }
            })
        }
    }

    override fun onCleared() {
        super.onCleared()
        nsdHelper?.stopDiscovery()
    }

    fun updateSmsPermission(granted: Boolean) {
        _uiState.update { it.copy(hasSmsPermission = granted) }
    }

    private fun updateLocalIp() {
        val ip = ServerManager.getLocalIpAddress() ?: "localhost"
        _uiState.update { it.copy(serverHost = ip) }
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

    fun regenerateApiKey() {
        val newKey = ConfigManager.regenerateApiKey()
        _uiState.update { it.copy(apiKey = newKey) }
    }

    fun startServer(context: Context) {
        if (!_uiState.value.hasSmsPermission) {
            _uiState.update { it.copy(errorMessage = "SMS permission required to start server") }
            return
        }
        if (_uiState.value.serverRunning || _uiState.value.serverStarting) return

        _uiState.update { it.copy(serverStarting = true, lastActionMessage = "Starting service...") }

        viewModelScope.launch {
            try {
                ConfigManager.init(context)
                val intent = Intent(context, SmsService::class.java)
                context.startForegroundService(intent)
                
                _uiState.update {
                    it.copy(
                        serverStarting = false,
                        lastActionMessage = "Service started"
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

        _uiState.update { it.copy(smsStatus = "Sending via local API...", errorMessage = null) }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = URL("http://localhost:${state.serverPort}/send-sms")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-api-key", state.apiKey)
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val json = JSONObject().apply {
                    put("to", state.smsTo)
                    put("message", state.smsMessage)
                }

                conn.outputStream.use { it.write(json.toString().toByteArray()) }

                val responseCode = conn.responseCode
                val responseBody = if (responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Error"
                }
                
                val responseJson = JSONObject(responseBody)
                val ok = responseJson.optBoolean("ok", false)

                if (ok) {
                    _uiState.update {
                        it.copy(
                            smsStatus = "Sent successfully via API",
                            errorMessage = null
                        )
                    }
                } else {
                    val error = responseJson.optString("error", "Unknown error")
                    _uiState.update {
                        it.copy(
                            smsStatus = null,
                            errorMessage = "API Error ($responseCode): $error"
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
