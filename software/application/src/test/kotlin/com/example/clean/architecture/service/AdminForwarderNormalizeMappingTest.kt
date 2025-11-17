package com.example.clean.architecture.service

import com.example.clean.architecture.service.config.MockNestConfig
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.InputStreamSource
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
import com.github.tomakehurst.wiremock.store.BlobStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*
import java.util.stream.Stream

@Disabled("WIP")
class AdminForwarderNormalizeMappingTest {

    private lateinit var directCallHttpServer: DirectCallHttpServer
    private lateinit var server: WireMockServer
    private lateinit var blobStore: InMemoryBlobStore
    private lateinit var adminForwarder: AdminForwarder

    @BeforeEach
    internal fun setUp() {
        val server = WireMockServer() // not started; we don't use it in normalize
        val blobStore = InMemoryBlobStore()
        val config = MockNestConfig()
        val factory = config.directCallHttpServerFactory()
        directCallHttpServer = config.directCallHttpServer(factory)
        adminForwarder = AdminForwarder(server, blobStore, directCallHttpServer)
    }

    @Test
    fun `When response has text body Then externalizes to file removes body adds default content-type and preserves headers`() {
        val id = "11111111-1111-1111-1111-111111111111"
        val mappingJson = """
            {
              "id": "$id",
              "request": { "method": "GET", "url": "/foo" },
              "response": {
                "status": 200,
                "headers": { "X-Foo": "bar" },
                "body": "hello"
              }
            }
        """.trimIndent()

        val result = adminForwarder.normalizeMappingToBodyFile(mappingJson)

        val tree = jacksonObjectMapper().readTree(result)
        val response = tree.get("response")
        assertNotNull(response)
        // body is removed
        assertNull(response.get("body"))
        assertNull(response.get("base64Body"))
        // bodyFileName is present and matches id.json
        assertEquals("$id.json", response.get("bodyFileName").asText())
        // headers preserved and default content-type added
        val headers = response.get("headers")
        assertEquals("bar", headers.get("X-Foo").asText())
        assertEquals("application/json", headers.get("Content-Type").asText())

        // blob store contains the file with expected content
        val bytes = blobStore.map["$id.json"]
        assertNotNull(bytes)
        assertEquals("hello", bytes?.toString(Charsets.UTF_8))
    }

    @Test
    fun `When response has base64Body Then externalizes to file and adds default octet stream when missing`() {
        val id = "22222222-2222-2222-2222-222222222222"
        val payload = "hello"
        val b64 = Base64.getEncoder().encodeToString(payload.toByteArray())
        val mappingJson = """
            {
              "id": "$id",
              "request": { "method": "GET", "url": "/bin" },
              "response": {
                "status": 200,
                "base64Body": "$b64"
              }
            }
        """.trimIndent()

        val result = adminForwarder.normalizeMappingToBodyFile(mappingJson)
        val tree = jacksonObjectMapper().readTree(result)
        val response = tree.get("response")
        assertNull(response.get("base64Body"))
        assertEquals("$id.bin", response.get("bodyFileName").asText())
        val headers = response.get("headers")
        assertEquals("application/octet-stream", headers.get("Content-Type").asText())

        val bytes = blobStore.map["$id.bin"]
        assertNotNull(bytes)
        assertArrayEquals(payload.toByteArray(), bytes)
    }

    @Test
    fun `When mapping has existing bodyFileName Then is left unchanged and no file is written`() {
        val mappingJson = """
            {
              "id": "33333333-3333-3333-3333-333333333333",
              "request": { "method": "GET", "url": "/already" },
              "response": {
                "status": 200,
                "bodyFileName": "existing.json",
                "headers": { "Content-Type": "text/plain" },
                "body": "should-not-be-touched"
              }
            }
        """.trimIndent()

        val result = adminForwarder.normalizeMappingToBodyFile(mappingJson)
        // Should be exactly the same string because method returns early
        assertEquals(mappingJson, result)
        assertTrue(blobStore.map.isEmpty())
    }
    private class InMemoryBlobStore : BlobStore {
        val map: MutableMap<String, ByteArray> = LinkedHashMap()

        override fun get(key: String): Optional<ByteArray> = Optional.ofNullable(map[key])

        override fun getStream(key: String): Optional<InputStream> =
            get(key).map { ByteArrayInputStream(it) }

        override fun getStreamSource(key: String): InputStreamSource =
            InputStreamSource { getStream(key).orElse(ByteArrayInputStream(ByteArray(0))) }

        override fun put(key: String, value: ByteArray) {
            map[key] = value
        }

        override fun remove(key: String) {
            map.remove(key)
        }

        override fun getAllKeys(): Stream<String> = map.keys.stream()

        override fun clear() {
            map.clear()
        }
    }
}
