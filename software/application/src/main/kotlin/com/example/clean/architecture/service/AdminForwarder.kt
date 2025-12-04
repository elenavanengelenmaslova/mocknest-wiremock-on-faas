package com.example.clean.architecture.service

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}
val mapper = jacksonObjectMapper()

@Component
class AdminForwarder(
    private val directCallHttpServer: DirectCallHttpServer,
) : HandleAdminRequest {

    override fun invoke(
        path: String,
        httpRequest: HttpRequest,
    ): HttpResponse {
        logger.info { "Handling admin request ${httpRequest.method} ${httpRequest.path} " }
        return forwardToDirectCallHttpServer("admin", httpRequest) { httpRequest ->
            directCallHttpServer.adminRequest(
                httpRequest
            )
        }
    }
}
