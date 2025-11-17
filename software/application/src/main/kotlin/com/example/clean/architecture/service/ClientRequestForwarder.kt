package com.example.clean.architecture.service

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.ImmutableRequest
import com.github.tomakehurst.wiremock.http.RequestMethod
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import wiremock.org.apache.hc.core5.http.ContentType
import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8
import com.github.tomakehurst.wiremock.http.HttpHeaders as WireMockHttpHeaders

private val logger = KotlinLogging.logger {}

@Component
class ClientRequestForwarder(private val directCallHttpServer: DirectCallHttpServer) :
    HandleClientRequest {
    override fun invoke(httpRequest: HttpRequest): HttpResponse {
        logger.info { "Forwarding request body: ${httpRequest.body} to path: ${httpRequest.path}" }

        val queryString = httpRequest.queryParameters.entries
            .joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, UTF_8)}=${URLEncoder.encode(value, UTF_8)}"
            }
            .takeIf { it.isNotEmpty() }
            ?.let { "?$it" }
            .orEmpty()

        val path = httpRequest.path

        // Create a WireMock request using the WireMock client
        val wireMockRequest =
            ImmutableRequest.create()
                .withAbsoluteUrl("$BASE_URL/$path$queryString")
                .withMethod(
                    RequestMethod.fromString(
                        httpRequest.method.name()
                    )
                )
                .withHeaders(
                    WireMockHttpHeaders(
                        httpRequest.headers.map { header ->
                            HttpHeader(header.key, header.value)
                        }
                    ))
                .withBody(httpRequest.body?.toString().orEmpty().toByteArray())
                .build()

        logger.info { "Calling wiremock with request: ${httpRequest.method} ${httpRequest.path}" }

        // Call stubRequest on the DirectCallHttpServer
        val response = directCallHttpServer.stubRequest(wireMockRequest)

        logger.info { "Wiremock response: ${response.bodyAsString}, code: ${response.status}" }
        val contentType =
            if (response.headers.contentTypeHeader.isPresent) response.headers.contentTypeHeader.firstValue()
            else ContentType.APPLICATION_JSON.toString()
        // Convert the WireMock Response to an HttpResponse
        val responseHeaders = HttpHeaders()

        val headers = response.headers.all().filter { !it.key.equals("Matched-Stub-Id", ignoreCase = true) }
        headers.forEach { header ->
            responseHeaders.add(header.key(), header.firstValue())
        }

        return HttpResponse(
            HttpStatusCode.valueOf(response.status),
            responseHeaders.apply {
                add(HttpHeaders.CONTENT_TYPE, contentType)
            },
            response.bodyAsString
        )

    }


}
