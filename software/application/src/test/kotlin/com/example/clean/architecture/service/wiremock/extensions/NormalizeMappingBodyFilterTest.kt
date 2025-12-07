package com.example.clean.architecture.service.wiremock.extensions

import com.example.clean.architecture.persistence.ObjectStorageInterface
import com.example.clean.architecture.service.wiremock.store.adapters.FILES_PREFIX
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.http.ImmutableRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class NormalizeMappingBodyFilterTest {

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
              },
              "persistent": true
            }
        """.trimIndent()

        val storage = InMemoryObjectStorage()
        val filter = NormalizeMappingBodyFilter(storage)
        val result = runBlocking { filter.normalizeMappingToBodyFile(mappingJson) }

        val tree = jacksonObjectMapper().readTree(result)
        val response = tree.get("response")
        assertNotNull(response)
        // body is removed
        assertNull(response.get("body"))
        assertNull(response.get("base64Body"))
        // bodyFileName is present and matches id.json
        val bodyFileName = response.get("bodyFileName").asText()
        assertTrue(bodyFileName.endsWith("$id.json"))
        // headers preserved and default content-type added
        val headers = response.get("headers")
        assertEquals("bar", headers.get("X-Foo").asText())
        assertEquals("application/json", headers.get("Content-Type").asText())

        // blob store contains the file with expected content
        val savedContent = storage.store["$FILES_PREFIX$bodyFileName"]
        assertNotNull(savedContent)
        assertEquals("hello", savedContent)
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
              },
              "persistent": true
            }
        """.trimIndent()

        val storage = InMemoryObjectStorage()
        val filter = NormalizeMappingBodyFilter(storage)
        val result = runBlocking { filter.normalizeMappingToBodyFile(mappingJson) }
        val tree = jacksonObjectMapper().readTree(result)
        val response = tree.get("response")
        assertNull(response.get("base64Body"))
        val bodyFileName = response.get("bodyFileName").asText()
        assertTrue(bodyFileName.endsWith("$id.bin"))
        val headers = response.get("headers")
        assertEquals("application/octet-stream", headers.get("Content-Type").asText())

        val content = storage.store["$FILES_PREFIX$bodyFileName"]
        assertNotNull(content)
        // Binary content is stored as base64 string in ObjectStorage
        assertEquals(b64, content)
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
                "headers": { "Content-Type": "text/plain" }
              },
              "persistent": true
            }
        """.trimIndent()

        val storage = InMemoryObjectStorage()
        val filter = NormalizeMappingBodyFilter(storage)
        val result = runBlocking { filter.normalizeMappingToBodyFile(mappingJson) }
        // Should be exactly the same string because method returns early
        assertEquals(mappingJson, result)
        assertTrue(storage.store.isEmpty())
    }

    @Test
    fun `When mapping is transient Then is left unchanged and no file is written`() {
        val mappingJson = """
            {
              "id": "44444444-4444-4444-4444-444444444444",
              "request": { "method": "GET", "url": "/transient" },
              "response": {
                "status": 200,
                "body": "will-not-be-saved"
              },
              "persistent": false
            }
        """.trimIndent()

        val storage = InMemoryObjectStorage()
        val filter = NormalizeMappingBodyFilter(storage)
        val result = runBlocking { filter.normalizeMappingToBodyFile(mappingJson) }
        // Should be exactly the same string because persistent=false
        assertEquals(mappingJson, result)
        assertTrue(storage.store.isEmpty())
    }

    @Test
    fun `When POST admin mappings Then isSaveMapping returns true`() {
        val req = ImmutableRequest.create()
            .withAbsoluteUrl("http://localhost/__admin/mappings")
            .withMethod(com.github.tomakehurst.wiremock.http.RequestMethod.POST)
            .withProtocol("HTTP/1.1")
            .withClientIp("127.0.0.1")
            .withHeaders(com.github.tomakehurst.wiremock.http.HttpHeaders())
            .withBody(ByteArray(0))
            .build()

        val filter = NormalizeMappingBodyFilter(InMemoryObjectStorage())
        assertTrue(filter.isSaveMapping(req))
    }

    @Test
    fun `When PUT admin mappings with UUID Then isSaveMapping returns true`() {
        val id = UUID.randomUUID().toString()
        val req = ImmutableRequest.create()
            .withAbsoluteUrl("http://localhost/__admin/mappings/$id")
            .withMethod(com.github.tomakehurst.wiremock.http.RequestMethod.PUT)
            .withProtocol("HTTP/1.1")
            .withClientIp("127.0.0.1")
            .withHeaders(com.github.tomakehurst.wiremock.http.HttpHeaders())
            .withBody(ByteArray(0))
            .build()

        val filter = NormalizeMappingBodyFilter(InMemoryObjectStorage())
        assertTrue(filter.isSaveMapping(req))
    }

    @Test
    fun `When GET admin mappings Then isSaveMapping returns false`() {
        val req = ImmutableRequest.create()
            .withAbsoluteUrl("http://localhost/__admin/mappings")
            .withMethod(com.github.tomakehurst.wiremock.http.RequestMethod.GET)
            .withProtocol("HTTP/1.1")
            .withClientIp("127.0.0.1")
            .withHeaders(com.github.tomakehurst.wiremock.http.HttpHeaders())
            .withBody(ByteArray(0))
            .build()

        val filter = NormalizeMappingBodyFilter(InMemoryObjectStorage())
        assertFalse(filter.isSaveMapping(req))
    }

    @Test
    fun `When PUT admin mappings with non-uuid tail Then isSaveMapping returns false`() {
        val req = ImmutableRequest.create()
            .withAbsoluteUrl("http://localhost/__admin/mappings/not-a-uuid")
            .withMethod(com.github.tomakehurst.wiremock.http.RequestMethod.PUT)
            .withProtocol("HTTP/1.1")
            .withClientIp("127.0.0.1")
            .withHeaders(com.github.tomakehurst.wiremock.http.HttpHeaders())
            .withBody(ByteArray(0))
            .build()

        val filter = NormalizeMappingBodyFilter(InMemoryObjectStorage())
        assertFalse(filter.isSaveMapping(req))
    }

    private class InMemoryObjectStorage : ObjectStorageInterface {
        val store: MutableMap<String, String> = LinkedHashMap()

        override suspend fun save(id: String, content: String): String {
            store[id] = content
            return id
        }

        override suspend fun get(id: String): String? = store[id]

        override suspend fun delete(id: String) {
            store.remove(id)
        }

        override fun list(): Flow<String> = flow {
            store.keys.forEach { emit(it) }
        }

        override fun getMany(ids: Flow<String>, concurrency: Int): Flow<Pair<String, String?>> {
            val out = MutableSharedFlow<Pair<String, String?>>(extraBufferCapacity = 64)
            // Simple, sequential collection for tests
            runBlocking {
                ids.collect { id -> out.emit(id to store[id]) }
            }
            return out.asSharedFlow()
        }

        override suspend fun deleteMany(ids: Flow<String>, concurrency: Int) {
            runBlocking {
                ids.collect { id -> store.remove(id) }
            }
        }
    }
}