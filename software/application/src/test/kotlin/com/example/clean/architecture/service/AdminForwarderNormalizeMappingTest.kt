package com.example.clean.architecture.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.InputStreamSource
import com.github.tomakehurst.wiremock.store.BlobStore
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*
import java.util.stream.Stream

class AdminForwarderNormalizeMappingTest {

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

    private fun newAdmin(blobStore: BlobStore = InMemoryBlobStore()): Pair<AdminForwarder, InMemoryBlobStore> {
        val server = WireMockServer() // not started; we don't use it in normalize
        val bs = blobStore as InMemoryBlobStore
        return AdminForwarder(server, bs) to bs
    }

    @Test
    fun `text body is externalized to file, body removed, default content-type added and headers preserved`() {
        val (admin, bs) = newAdmin()
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

        val result = admin.normalizeMappingToBodyFile(mappingJson)

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
        val bytes = bs.map["$id.json"]
        assertNotNull(bytes)
        assertEquals("hello", bytes?.toString(Charsets.UTF_8))
    }

    @Test
    fun `base64Body is externalized to file and default octet-stream added when missing`() {
        val (admin, bs) = newAdmin()
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

        val result = admin.normalizeMappingToBodyFile(mappingJson)
        val tree = jacksonObjectMapper().readTree(result)
        val response = tree.get("response")
        assertNull(response.get("base64Body"))
        assertEquals("$id.bin", response.get("bodyFileName").asText())
        val headers = response.get("headers")
        assertEquals("application/octet-stream", headers.get("Content-Type").asText())

        val bytes = bs.map["$id.bin"]
        assertNotNull(bytes)
        assertArrayEquals(payload.toByteArray(), bytes)
    }

    @Test
    fun `mapping with existing bodyFileName is left unchanged and no file written`() {
        val (admin, bs) = newAdmin()
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

        val result = admin.normalizeMappingToBodyFile(mappingJson)
        // Should be exactly the same string because method returns early
        assertEquals(mappingJson, result)
        assertTrue(bs.map.isEmpty())
    }
}
