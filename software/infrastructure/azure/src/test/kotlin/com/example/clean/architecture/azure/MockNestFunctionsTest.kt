package com.example.clean.architecture.azure


import com.azure.core.http.HttpHeaders
import com.azure.core.http.HttpRequest
import com.azure.core.http.rest.*
import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobAsyncClient
import com.azure.storage.blob.BlobContainerAsyncClient
import com.azure.storage.blob.batch.BlobBatchAsyncClient
import com.azure.storage.blob.models.BlobItem
import com.azure.storage.blob.models.BlockBlobItem
import com.azure.storage.blob.models.DeleteSnapshotsOptionType
import com.azure.storage.blob.models.ListBlobsOptions
import com.example.clean.architecture.persistence.ObjectStorageInterface
import com.example.clean.architecture.test.config.AzureLocalTestConfiguration
import com.github.tomakehurst.wiremock.WireMockServer
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.HttpMethod
import com.microsoft.azure.functions.HttpRequestMessage
import com.microsoft.azure.functions.HttpStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import com.azure.core.http.HttpMethod as AzureHttpMethod

@SpringBootTest
@ActiveProfiles("local")
@Import(AzureLocalTestConfiguration::class)
class MockNestFunctionsIntegrationTest {

    @Autowired
    private lateinit var mockNestFunctions: MockNestFunctions
    @Autowired
    private lateinit var storage: ObjectStorageInterface
    @Autowired
    private lateinit var wireMockServer: WireMockServer
    @Autowired
    private lateinit var container: BlobContainerAsyncClient
    @Autowired
    private lateinit var batch: BlobBatchAsyncClient

    private val store = ConcurrentHashMap<String, String>()
    private val containerUrl = "https://test.blob.core.windows.net/test-container"

    private val context = mockk<ExecutionContext>()
    val request =
        mockk<HttpRequestMessage<String>>(relaxed = true)

    @BeforeEach
    fun setup() {
        // Set container expectations per test run
        every { container.blobContainerUrl } returns containerUrl
        every { container.getBlobAsyncClient(any()) } answers {
            val name = arg<String>(0)
            mockBlobAsyncClient(name)
        }
        every { container.listBlobs() } answers { pagedFluxFor(store.keys.map { it }) }
        every { container.listBlobs(any<ListBlobsOptions>()) } answers {
            val options = arg<ListBlobsOptions>(0)
            val prefix = options.prefix ?: ""
            pagedFluxFor(store.keys.filter { it.startsWith(prefix) })
        }
        every { batch.deleteBlobs(any<List<String>>(), any<DeleteSnapshotsOptionType>()) } answers {
            val urls = arg<List<String>>(0)
            urls.forEach { url ->
                val key = url.removePrefix("$containerUrl/")
                store.remove(key)
            }
            val req = HttpRequest(AzureHttpMethod.DELETE, containerUrl)
            val headers = HttpHeaders()
            val responses = urls.map { SimpleResponse<Void>(req, 202, headers, null) as Response<Void> }
            val page: Mono<PagedResponse<Response<Void>>> = Mono.just(
                PagedResponseBase(req, 202, headers, responses, null, null)
            )
            PagedFlux({ page }, { _: String -> Mono.empty() })
        }

        // Clear storage before each test
        runBlocking {
            storage.list().toList().forEach { storage.delete(it) }
        }
        wireMockServer.resetAll()
    }

    private fun mockBlobAsyncClient(name: String): BlobAsyncClient {
        val blob = mockk<BlobAsyncClient>(relaxed = true)
        every { blob.blobUrl } returns "$containerUrl/$name"
        every { blob.exists() } answers { Mono.just(store.containsKey(name)) }
        every { blob.upload(any<BinaryData>(), any()) } answers {
            val data = arg<BinaryData>(0)
            store[name] = data.toString()
            Mono.just(mockk<BlockBlobItem>(relaxed = true))
        }
        every { blob.downloadContent() } answers {
            val value = store[name] ?: return@answers Mono.empty()
            Mono.just(BinaryData.fromString(value))
        }
        every { blob.delete() } answers {
            store.remove(name)
            Mono.empty<Void>()
        }
        return blob
    }

    private fun pagedFluxFor(keys: List<String>): PagedFlux<BlobItem> {
        val items = keys.map { key ->
            val item = mockk<BlobItem>(relaxed = true)
            every { item.name } returns key
            item
        }
        val firstPage: Mono<PagedResponse<BlobItem>> = Mono.just(
            PagedResponseBase(
                HttpRequest(AzureHttpMethod.GET, containerUrl),
                200,
                HttpHeaders(),
                items,
                null,
                null
            )
        )
        return PagedFlux({ firstPage }, { _: String -> Mono.empty() })
    }
    @Test
    fun `When match request Then maps to a success response`() {
        every { request.httpMethod } returns HttpMethod.GET

        mockNestFunctions.forwardClientRequest(request, "health", context)

        verify {
            request
                .createResponseBuilder(HttpStatus.valueOf(200))
        }
    }

    @Test
    fun `When deleting a non existent MockNest mapping Then returns 404 status code`() {
        every { request.httpMethod } returns HttpMethod.DELETE

        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings/8c5db8b0-2db4-4ad7-a99f-38c9b00da3f7",
            context
        )

        verify {
            request
                .createResponseBuilder(
                    HttpStatus.valueOf(
                        404
                    )
                )
        }
    }

    @Test
    fun `When deleting an existent MockNest mapping Then returns 200 status code`() {
        every { request.httpMethod } returns HttpMethod.DELETE

        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings/76ada7b0-55ae-4229-91c4-396a36f19999",
            context
        )

        verify {
            request
                .createResponseBuilder(
                    HttpStatus.valueOf(
                        200
                    )
                )
        }
    }

    @Test
    fun `When retrieving near misses Then returns 200 status code`() {
        every { request.httpMethod } returns HttpMethod.GET

        mockNestFunctions.forwardAdminRequest(
            request,
            "requests/unmatched/near-misses",
            context
        )

        verify {
            request
                .createResponseBuilder(
                    HttpStatus.valueOf(
                        200
                    )
                )
        }
    }

    @Test
    fun `When resetting MockNest mappings Then returns 200 status code`() {
        every { request.httpMethod } returns HttpMethod.POST

        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings/reset",
            context
        )

        verify {
            request
                .createResponseBuilder(
                    HttpStatus.valueOf(
                        200
                    )
                )
        }
    }

    @Test
    fun `When retrieving all mappings Then returns 200 status code`() {
        every { request.httpMethod } returns HttpMethod.GET

        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings",
            context
        )

        verify {
            request.createResponseBuilder(HttpStatus.valueOf(200))
        }
    }

    @Test
    fun `When retrieving all requests Then returns 200 status code`() {
        every { request.httpMethod } returns HttpMethod.GET

        mockNestFunctions.forwardAdminRequest(
            request,
            "requests",
            context
        )

        verify {
            request.createResponseBuilder(HttpStatus.valueOf(200))
        }
    }

    @Test
    fun `When clearing request journal Then returns 200 status code`() {
        every { request.httpMethod } returns HttpMethod.DELETE

        mockNestFunctions.forwardAdminRequest(
            request,
            "requests",
            context
        )

        verify {
            request.createResponseBuilder(HttpStatus.valueOf(200))
        }
    }

    @Test
    fun `When retrieving unmatched requests Then returns 200 status code`() {
        every { request.httpMethod } returns HttpMethod.GET

        mockNestFunctions.forwardAdminRequest(
            request,
            "requests/unmatched",
            context
        )

        verify {
            request.createResponseBuilder(HttpStatus.valueOf(200))
        }
    }

    @Test
    fun `When creating a new WireMock mapping with POST then normalize body to __files and returns 201 status code`() {
        every { request.httpMethod } returns HttpMethod.POST
        every { request.body } returns """
        {
          "request": {
            "method": "GET",
            "url": "/test/endpoint"
          },
          "response": {
            "status": 200,
            "body": "Test response body",
            "headers": {
              "Content-Type": "application/json"
            }
          },
          "persistent": true
        }
    """.trimIndent()
        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings",
            context
        )
        // Assert storage received one __files and one mappings entry
        runBlocking {
            val keys = storage.list().toList()
            val files = keys.filter { it.startsWith("__files/") }
            val mappings = keys.filter { it.startsWith("mappings/") }
            assertEquals(1, files.size)
            assertEquals(1, mappings.size)
        }

        verify {
            request
                .createResponseBuilder(HttpStatus.valueOf(201))
        }
    }

    @Test
    fun `When updating a WireMock mapping with PUT then normalize body to __files returns 200 status code`() {
        every { request.httpMethod } returns HttpMethod.PUT
        every { request.body } returns """
        {
          "id": "76ada7b0-55ae-4229-91c4-396a36f19999",
          "request": {
            "method": "GET",
            "url": "/test/endpoint/updated"
          },
          "response": {
            "status": 200,
            "body": "Updated response body",
            "headers": {
              "Content-Type": "application/json"
            }
          },
          "persistent": true
        }
    """.trimIndent()
        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings/76ada7b0-55ae-4229-91c4-396a36f19999",
            context
        )
        runBlocking {
            val keys = storage.list().toList()
            val files = keys.filter { it.startsWith("__files/") }
            val mappings = keys.filter { it.startsWith("mappings/") }
            assertEquals(1, files.size)
            assertEquals(1, mappings.size)
        }

        verify {
            request
                .createResponseBuilder(HttpStatus.valueOf(200))
        }
    }

    @Test
    fun `When creating a mapping with bodyFileName then do not normalizes to __files`() {
        val mapping = """
        {
          "request": {
            "method": "POST",
            "urlPath": "/api/resource"
          },
          "response": {
            "status": 200,
            "bodyFileName": "responses/test-response.json",
            "headers": {
              "Content-Type": "application/json"
            }
          },
          "persistent": true
        }
    """.trimIndent()
        every { request.httpMethod } returns HttpMethod.POST
        every { request.body } returns mapping
        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings",
            context
        )
        runBlocking {
            val keys = storage.list().toList()
            val files = keys.filter { it.startsWith("__files/") }
            val mappings = keys.filter { it.startsWith("mappings/") }
            assertEquals(0, files.size)
            assertEquals(1, mappings.size)
        }

        verify {
            request
                .createResponseBuilder(HttpStatus.valueOf(201))
        }
    }

    @Test
    fun `When creating a new WireMock transient mapping with Then do not save to blob storage`() {
        every { request.httpMethod } returns HttpMethod.POST
        every { request.body } returns """
        {
          "request": {
            "method": "GET",
            "url": "/test/endpoint"
          },
          "response": {
            "status": 200,
            "body": "Test response body",
            "headers": {
              "Content-Type": "application/json"
            }
          },
          "persistent": false
        }
    """.trimIndent()

        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings",
            context
        )

        runBlocking {
            val keys = storage.list().toList()
            // Transient mapping should not persist anything
            assertTrue(keys.isEmpty())
        }

        verify {
            request
                .createResponseBuilder(HttpStatus.valueOf(201))
        }

    }

    @Test
    fun `When updating a WireMock mapping with transient mapping Then do not update blob storage`() {
        every { request.httpMethod } returns HttpMethod.PUT
        every { request.body } returns """
        {
          "id": "76ada7b0-55ae-4229-91c4-396a36f19999",
          "request": {
            "method": "GET",
            "url": "/test/endpoint/updated"
          },
          "response": {
            "status": 200,
            "body": "Updated response body",
            "headers": {
              "Content-Type": "application/json"
            }
          }
        }
    """.trimIndent()

        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings/76ada7b0-55ae-4229-91c4-396a36f19999",
            context
        )

        runBlocking {
            val keys = storage.list().toList()
            assertTrue(keys.isEmpty())
        }

        verify {
            request
                .createResponseBuilder(HttpStatus.valueOf(200))
        }
    }

    @Test
    fun `When deleting all WireMock mappings then returns 200 and clears files`() {
        every { request.httpMethod } returns HttpMethod.DELETE

        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings",
            context
        )

        verify {
            request
                .createResponseBuilder(HttpStatus.valueOf(200))
        }
    }
}
