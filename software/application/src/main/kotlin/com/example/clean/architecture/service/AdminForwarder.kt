package com.example.clean.architecture.service

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.InvalidInputException
import com.github.tomakehurst.wiremock.common.Json
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
import com.github.tomakehurst.wiremock.store.BlobStore
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import java.util.*
import kotlin.text.Charsets.UTF_8

private val logger = KotlinLogging.logger {}
val mapper = jacksonObjectMapper()

@Component
class AdminForwarder(
    private val wireMockServer: WireMockServer,
    private val filesStore: BlobStore,
    private val directCallHttpServer: DirectCallHttpServer,
) : HandleAdminRequest {

    override fun invoke(
        path: String,
        httpRequest: HttpRequest,
    ): HttpResponse {
        return when {
            // Normalize body to __files
            path == "mappings" && httpRequest.method in listOf(HttpMethod.POST, HttpMethod.PUT) -> {
                logger.info { "Creating new WireMock stub mapping" }
                wireMockServer.runCatching {
                    val addedMapping = httpRequest.body?.let { body ->
                        val bodyString = body.toString()
                        val normalized = normalizeMappingToBodyFile(bodyString)
                        val mapping = normalized.toStubMapping()
                        if (httpRequest.method == HttpMethod.POST) addStubMapping(mapping)
                        else editStubMapping(mapping)
                        "Mapping ${mapping.id} saved"
                    }
                    HttpResponse(httpStatusCode = HttpStatusCode.valueOf(201), body = addedMapping)
                }.getOrElse { handleAdminException(it) }
            }

            // DELETE /__admin/mappings -> delete all mappings and purge files
            path == "mappings" && httpRequest.method == HttpMethod.DELETE -> {
                logger.info { "Deleting all WireMock stub mappings and files" }
                val response = forwardToDirectCallHttpServer("admin", httpRequest) { httpRequest ->
                    directCallHttpServer.adminRequest(
                        httpRequest
                    )
                }
                // remove __files and reset from mappings from local file system
                wireMockServer.runCatching {
                    filesStore.clear()
                    resetRequests()
                }.onFailure { logger.error { "Unable to reset mappings: $it" } }
                response
            }

            else -> {
                logger.info { "Handling admin request ${httpRequest.method} ${httpRequest.path} " }
                forwardToDirectCallHttpServer("admin", httpRequest) { httpRequest ->
                    directCallHttpServer.adminRequest(
                        httpRequest
                    )
                }
            }
        }
    }

    private fun handleAdminException(exception: Throwable) = when (exception) {
        is InvalidInputException ->
            HttpResponse(
                HttpStatusCode.valueOf(400),
                body = exception.message
            ).also { logger.info { "WireMock error: $exception" } }

        else -> throw exception
    }

    internal fun normalizeMappingToBodyFile(mappingJson: String): String {

        val root = mapper.readTree(mappingJson) as com.fasterxml.jackson.databind.node.ObjectNode
        // Get or create response without overwriting existing one
        val response = (root.get("response") as? com.fasterxml.jackson.databind.node.ObjectNode)
            ?: root.putObject("response")

        // If already bodyFileName, nothing to do
        if (response.has("bodyFileName")) return mappingJson

        val bodyNode = response.remove("body")
        val base64Node = response.remove("base64Body")

        if (bodyNode == null && base64Node == null) return mappingJson

        // Ensure mapping has an id to derive file name
        val mappingId = root.get("id")?.asText() ?: UUID.randomUUID().toString().also { root.put("id", it) }
        val isBinary = base64Node != null
        val fileName = mappingId + if (isBinary) ".bin" else ".json"

        // Persist into FILES store under the relative file name
        if (isBinary) {
            val bytes = Base64.getDecoder().decode(base64Node.asText())
            filesStore.put(fileName, bytes)
        } else {
            val text = bodyNode.asText()
            filesStore.put(fileName, text.toByteArray(UTF_8))
        }

        // Get or create headers without overwriting existing headers
        val headers = (response.get("headers") as? com.fasterxml.jackson.databind.node.ObjectNode)
            ?: response.putObject("headers")

        // Only set default Content-Type when it's missing
        if (!headers.has("Content-Type")) {
            headers.put("Content-Type", if (isBinary) "application/octet-stream" else "application/json")
        }

        response.put("bodyFileName", fileName)
        return mapper.writeValueAsString(root)
    }

    private fun String.toStubMapping(): StubMapping = Json.getObjectMapper().readValue(this, StubMapping::class.java)

}
