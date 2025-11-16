package com.example.clean.architecture.service

import com.example.clean.architecture.model.HttpRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.InputStreamSource
import com.github.tomakehurst.wiremock.common.Json
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.github.tomakehurst.wiremock.store.BlobStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Base64
import java.util.Optional
import java.util.UUID
import java.util.stream.Stream

class AdminForwarderEndpointsTest {

    private class InMemoryBlobStore : BlobStore {
        val map: MutableMap<String, ByteArray> = LinkedHashMap()
        override fun get(key: String): Optional<ByteArray> = Optional.ofNullable(map[key])
        override fun getStream(key: String): Optional<InputStream> = get(key).map { ByteArrayInputStream(it) }
        override fun getStreamSource(key: String): InputStreamSource =
            InputStreamSource { getStream(key).orElse(ByteArrayInputStream(ByteArray(0))) }
        override fun put(key: String, value: ByteArray) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun getAllKeys(): Stream<String> = map.keys.stream()
        override fun clear() { map.clear() }
    }

    private lateinit var server: WireMockServer
    private lateinit var blobStore: InMemoryBlobStore
    private lateinit var admin: AdminForwarder

    private fun req(method: HttpMethod, body: String? = null) =
        HttpRequest(method, emptyMap(), null, emptyMap(), body)

    @BeforeEach
    fun setUp() {
        server = WireMockServer()
        server.start()
        blobStore = InMemoryBlobStore()
        admin = AdminForwarder(server, blobStore)
    }

    @AfterEach
    fun tearDown() {
        runCatching { server.stop() }
    }

    @Test
    fun `When POST mappings Then creates stub and writes body file`() {
        val id = UUID.randomUUID().toString()
        val mappingJson = """
            {
              "id": "$id",
              "request": { "method": "GET", "url": "/foo" },
              "response": { "status": 200, "body": "hello" }
            }
        """.trimIndent()

        val resp = admin.invoke("mappings", req(HttpMethod.POST, mappingJson))
        assertEquals(HttpStatusCode.valueOf(201), resp.httpStatusCode)

        val mapping = server.listAllStubMappings().mappings.find { it.id == UUID.fromString(id) }
        assertNotNull(mapping)
        assertEquals(UUID.fromString(id), mapping?.id)
        assertEquals("hello", blobStore.map["$id.json"]?.toString(Charsets.UTF_8))
    }

    @Test
    fun `When DELETE mappings Then clears all stubs and files`() {
        // Add a stub directly
        val id = UUID.randomUUID()
        val stubJson = """
            {
              "id": "$id",
              "request": { "method": "GET", "url": "/a" },
              "response": { "status": 200, "body": "A" }
            }
        """.trimIndent()
        val stub: StubMapping = Json.getObjectMapper().readValue(stubJson, StubMapping::class.java)
        server.addStubMapping(stub)
        // Add some files
        blobStore.put("a.json", "1".toByteArray())
        blobStore.put("b.bin", byteArrayOf(2))

        val resp = admin.invoke("mappings", req(HttpMethod.DELETE))
        assertEquals(HttpStatusCode.valueOf(200), resp.httpStatusCode)

        // No stubs
        val stubs = server.listAllStubMappings().mappings
        assertEquals(0, stubs.size)
        // No files
        assertEquals(0, blobStore.map.size)
    }

    @Test
    fun `When GET files Then returns list of keys`() {
        blobStore.put("x.json", "x".toByteArray())
        blobStore.put("dir/y.bin", byteArrayOf(1,2,3))

        val resp = admin.invoke("files", req(HttpMethod.GET))
        assertEquals(HttpStatusCode.valueOf(200), resp.httpStatusCode)

        val body = resp.body as String
        val list: List<String> = jacksonObjectMapper().readValue(body, jacksonObjectMapper().typeFactory.constructCollectionType(List::class.java, String::class.java))
        // Order is not guaranteed, so assert contents
        assert(list.contains("x.json"))
        assert(list.contains("dir/y.bin"))
    }

    @Test
    fun `When DELETE files Then clears all keys`() {
        blobStore.put("one", byteArrayOf(1))
        blobStore.put("two", byteArrayOf(2))

        val resp = admin.invoke("files", req(HttpMethod.DELETE))
        assertEquals(HttpStatusCode.valueOf(200), resp.httpStatusCode)
        assertEquals(0, blobStore.map.size)
    }

    @Test
    fun `When DELETE files path Then removes only that key`() {
        blobStore.put("keep", byteArrayOf(1))
        blobStore.put("remove/me.txt", byteArrayOf(2))

        val resp = admin.invoke("files/remove/me.txt", req(HttpMethod.DELETE))
        assertEquals(HttpStatusCode.valueOf(200), resp.httpStatusCode)
        assertNotNull(blobStore.map["keep"]) // still there
        assertNull(blobStore.map["remove/me.txt"]) // removed
    }

    @Test
    fun `When GET mapping by id Then returns stored mapping`() {
        val id = UUID.randomUUID()
        val stubJson = """
            {
              "id": "$id",
              "request": { "method": "GET", "url": "/get" },
              "response": { "status": 200, "body": "ok" }
            }
        """.trimIndent()
        val stub: StubMapping = Json.getObjectMapper().readValue(stubJson, StubMapping::class.java)
        server.addStubMapping(stub)

        val resp = admin.invoke("mappings/$id", req(HttpMethod.GET))
        assertEquals(HttpStatusCode.valueOf(200), resp.httpStatusCode)
        // Verify via server that mapping with id exists
        val exists = server.listAllStubMappings().mappings.any { it.id == id }
        assertEquals(true, exists)
    }

    @Test
    fun `When PUT mapping by id Then updates stub and writes body file`() {
        val id = UUID.randomUUID()
        // Create existing mapping
        val stubJson = """
            {
              "id": "$id",
              "request": { "method": "GET", "url": "/put" },
              "response": { "status": 200, "body": "old" }
            }
        """.trimIndent()
        val stub: StubMapping = Json.getObjectMapper().readValue(stubJson, StubMapping::class.java)
        server.addStubMapping(stub)

        // Now update via admin with new body and status
        val updateJson = """
            {
              "id": "$id",
              "request": { "method": "GET", "url": "/put" },
              "response": { "status": 201, "body": "new" }
            }
        """.trimIndent()

        val resp = admin.invoke("mappings/$id", req(HttpMethod.PUT, updateJson))
        assertEquals(HttpStatusCode.valueOf(200), resp.httpStatusCode)

        val updated = server.listAllStubMappings().mappings.find { it.id == id }
        assertNotNull(updated)
        assertEquals(201, updated!!.response.status)
        assertEquals("new", blobStore.map["$id.json"]?.toString(Charsets.UTF_8))
    }

    @Test
    fun `When POST mappings with base64Body Then writes bin file`() {
        val id = UUID.randomUUID().toString()
        val bytes = "BIN".toByteArray()
        val b64 = Base64.getEncoder().encodeToString(bytes)
        val mappingJson = """
            {
              "id": "$id",
              "request": { "method": "GET", "url": "/bin" },
              "response": { "status": 200, "base64Body": "$b64" }
            }
        """.trimIndent()

        val resp = admin.invoke("mappings", req(HttpMethod.POST, mappingJson))
        assertEquals(HttpStatusCode.valueOf(201), resp.httpStatusCode)
        assertEquals("BIN", blobStore.map["$id.bin"]?.toString(Charsets.UTF_8))
    }
}
