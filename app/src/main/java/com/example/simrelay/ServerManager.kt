package com.example.simrelay

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import java.net.NetworkInterface
import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse(val ok: Boolean, val status: String? = null, val error: String? = null)

@Serializable
data class HealthResponse(val ok: Boolean, val status: String)

@Serializable
data class SendSmsRequest(val to: String, val message: String)

object ServerManager {

    @Volatile
    private var server: ApplicationEngine? = null
    private var nsdHelper: NsdHelper? = null
    private const val PORT = 3000
    
    @Volatile
    private var isStarting = false

    val isRunning: Boolean
        get() = server != null

    fun startServer(context: Context) {
        synchronized(this) {
            if (server != null || isStarting) {
                Log.d("ServerManager", "Server already running or starting")
                return
            }
            isStarting = true
        }

        Thread {
            var retryCount = 0
            val maxRetries = 3
            var success = false

            while (retryCount < maxRetries && !success) {
                try {
                    Log.i("ServerManager", "Starting server on port $PORT (Attempt ${retryCount + 1})...")
                    
                    val engine = embeddedServer(Netty, port = PORT, host = "0.0.0.0") {
                        install(ContentNegotiation) {
                            json()
                        }

                        routing {
                            intercept(ApplicationCallPipeline.Call) {
                                val method = call.request.local.method.value
                                val path = call.request.uri
                                
                                if (path == "/health") return@intercept
                                
                                val apiKey = call.request.headers["x-api-key"]
                                if (apiKey != ConfigManager.getApiKey()) {
                                    LogRepository.addLog(method, path, 401, "Invalid API Key")
                                    call.respond(HttpStatusCode.Unauthorized, ApiResponse(ok = false, error = "Invalid API Key"))
                                    finish()
                                }
                            }

                            post("/send-sms") {
                                val method = call.request.local.method.value
                                val path = call.request.uri
                                try {
                                    val request = call.receive<SendSmsRequest>()
                                    val to = request.to
                                    val message = request.message

                                    if (to.isBlank() || message.isBlank()) {
                                        LogRepository.addLog(method, path, 400, "Missing to or message")
                                        return@post call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "Missing 'to' or 'message'"))
                                    }

                                    if (to.length < 5) {
                                        LogRepository.addLog(method, path, 400, "Invalid phone number")
                                        return@post call.respond(HttpStatusCode.BadRequest, ApiResponse(ok = false, error = "Invalid phone number"))
                                    }

                                    try {
                                        SmsSender.send(context, to, message)
                                        LogRepository.addLog(method, path, 200, "SMS queued for $to")
                                        call.respond(HttpStatusCode.OK, ApiResponse(ok = true, status = "sent"))
                                    } catch (e: Exception) {
                                        LogRepository.addLog(method, path, 500, e.message ?: "SmsSender error")
                                        call.respond(HttpStatusCode.InternalServerError, ApiResponse(ok = false, error = (e.message ?: "Failed to send SMS")))
                                    }
                                } catch (e: Exception) {
                                    LogRepository.addLog(method, path, 500, e.message ?: "Server error")
                                    call.respond(HttpStatusCode.InternalServerError, ApiResponse(ok = false, error = (e.message ?: "Unknown error")))
                                }
                            }

                            get("/health") {
                                call.respond(HttpStatusCode.OK, HealthResponse(ok = true, status = "ok"))
                            }
                        }
                    }

                    engine.start(wait = false)
                    
                    synchronized(this) {
                        server = engine
                        nsdHelper = NsdHelper(context).also {
                            it.registerService(PORT)
                        }
                    }
                    Log.i("ServerManager", "Server successfully started on port $PORT")
                    success = true
                } catch (e: Exception) {
                    val errorMsg = e.toString()
                    Log.e("ServerManager", "Error starting server: $errorMsg")
                    
                    if (errorMsg.contains("BindException") || e.cause?.toString()?.contains("BindException") == true) {
                        Log.e("ServerManager", "Port $PORT is busy, retrying in 2s...")
                        retryCount++
                        Thread.sleep(2000)
                    } else {
                        Log.e("ServerManager", "Non-bind error, aborting: ${e.message}")
                        break
                    }
                }
            }
            
            synchronized(this) {
                isStarting = false
                if (!success) {
                    server = null
                    Log.e("ServerManager", "Failed to start server after $maxRetries attempts")
                }
            }
        }.start()
    }

    fun stopServer() {
        Log.i("ServerManager", "Request to stop server...")
        
        val engineToStop: ApplicationEngine?
        val helperToStop: NsdHelper?
        
        synchronized(this) {
            engineToStop = server
            helperToStop = nsdHelper
            server = null
            nsdHelper = null
            isStarting = false
        }

        if (engineToStop == null && helperToStop == null) {
            Log.d("ServerManager", "Server already stopped")
            return
        }

        Thread {
            try {
                helperToStop?.unregisterService()
                engineToStop?.stop(500, 1000)
                Log.i("ServerManager", "Server and NSD service stopped")
            } catch (e: Exception) {
                Log.e("ServerManager", "Error during server stop", e)
            }
        }.start()
    }

    fun getLocalIpAddress(): String? {
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
            Log.e("ServerManager", "Error getting IP", ex)
        }
        return null
    }
}