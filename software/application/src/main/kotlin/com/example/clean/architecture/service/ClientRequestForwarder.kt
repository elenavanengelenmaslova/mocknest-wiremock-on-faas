package com.example.clean.architecture.service

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
import org.springframework.stereotype.Component

@Component
class ClientRequestForwarder(private val directCallHttpServer: DirectCallHttpServer) :
    HandleClientRequest {
    override fun invoke(httpRequest: HttpRequest): HttpResponse {
        return forwardToDirectCallHttpServer(
            "client request",
            httpRequest
        ) { httpRequest -> directCallHttpServer.stubRequest(httpRequest) }

    }


}
