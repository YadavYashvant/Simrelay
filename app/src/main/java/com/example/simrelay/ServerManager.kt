package com.example.simrelay

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

object ServerManager {

    private var server: ApplicationEngine? = null

    val isRunning: Boolean
        get() = server != null

    fun startServer() {
        if (server != null) return

        server = embeddedServer(Netty, port = 3000) {
            install(ContentNegotiation) {
                json()
            }

            routing {
                intercept(io.ktor.server.application.ApplicationCallPipeline.Call) {
                    if (call.request.local.uri == "/health") return@intercept
                    
                    val apiKey = call.request.headers["x-api-key"]
                    if (apiKey != "sk_test_simrelay_8f92") {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid API Key"))
                        finish()
                    }
                }

                post("/send-sms") {
                    try {
                        val body = call.receive<Map<String, String>>()
                        val to = body["to"] ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing 'to'"))
                        val message = body["message"] ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing 'message'"))

                        SmsSender.send(to, message)
                        call.respond(HttpStatusCode.OK, mapOf("status" to "sent"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("status" to "failed", "error" to (e.message ?: "Unknown error")))
                    }
                }

                get("/health") {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
                }
            }
        }.start(wait = false)
    }

    fun stopServer() {
        server?.stop()
        server = null
    }
}