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
import io.ktor.websocket.WebSocketDeflateExtension.Companion.install

object ServerManager {

    private var server: ApplicationEngine? = null

    fun startServer() {

        if (server != null) return

        server = embeddedServer(Netty, port = 3000) {

            install(ContentNegotiation) {
                json()
            }

            routing {

                post("/send-sms") {

                    val body = call.receive<Map<String, String>>()

                    val to = body["to"]
                    val message = body["message"]

                    if (to == null || message == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }

                    try {
                        SmsSender.send(to, message)
                        call.respond(mapOf("status" to "sent"))

                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("status" to "failed")
                        )
                    }
                }

                get("/health") {
                    call.respond(mapOf("status" to "ok"))
                }
            }

        }.start(wait = false)
    }

    fun stopServer() {
        server?.stop()
        server = null
    }
}