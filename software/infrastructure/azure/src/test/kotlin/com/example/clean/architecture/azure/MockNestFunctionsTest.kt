package com.example.clean.architecture.azure


import com.azure.core.http.rest.PagedIterable
import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.models.BlobItem
import com.example.clean.architecture.test.config.LocalTestConfiguration
import com.github.tomakehurst.wiremock.WireMockServer
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.HttpMethod
import com.microsoft.azure.functions.HttpRequestMessage
import com.microsoft.azure.functions.HttpStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.io.ByteArrayInputStream
import kotlin.test.Test

@SpringBootTest
@ActiveProfiles("local")
@Import(LocalTestConfiguration::class)
class MockNestFunctionsIntegrationTest {

    @Autowired
    private lateinit var mockNestFunctions: MockNestFunctions
    @Autowired
    private lateinit var blobContainerClient: BlobContainerClient
    @Autowired
    private lateinit var wireMockServer: WireMockServer

    private val blobsList: PagedIterable<BlobItem> = mockk(relaxed = true)
    private val blobClient: BlobClient = mockk(relaxed = true)

    private val context = mockk<ExecutionContext>()
    val request =
        mockk<HttpRequestMessage<String>>(relaxed = true)

    @BeforeEach
    fun setup() {
        wireMockServer.resetAll()
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
        every { blobContainerClient.listBlobs() } returns blobsList
        val fileNameList = mutableListOf<String>()
        every { blobContainerClient.getBlobClient(capture(fileNameList)) } returns blobClient
        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings",
            context
        )
        assertEquals(2, fileNameList.size)
        assertTrue(fileNameList[0].startsWith("__files"))
        assertTrue(fileNameList[1].startsWith("mappings"))
        verify {
            blobClient.upload(any<ByteArrayInputStream>(), true)
        }

        verify {
            request
                .createResponseBuilder(HttpStatus.valueOf(201))
        }
    }

    @Test
    fun `When updating a WireMock mapping with PUT then normalize body to __files returns 201 status code`() {
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
        every { blobContainerClient.listBlobs() } returns blobsList
        val fileNameList = mutableListOf<String>()
        every { blobContainerClient.getBlobClient(capture(fileNameList)) } returns blobClient
        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings",
            context
        )
        assertEquals(2, fileNameList.size)
        assertTrue(fileNameList[0].startsWith("__files"))
        assertTrue(fileNameList[1].startsWith("mappings"))
        verify {
            blobClient.upload(any<ByteArrayInputStream>(), true)
        }

        verify {
            request
                .createResponseBuilder(HttpStatus.valueOf(201))
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
        every { blobContainerClient.listBlobs() } returns blobsList
        val fileNameList = mutableListOf<String>()
        every { blobContainerClient.getBlobClient(capture(fileNameList)) } returns blobClient
        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings",
            context
        )
        assertEquals(1, fileNameList.size)
        assertTrue(fileNameList[0].startsWith("mappings"))

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

        every { blobContainerClient.listBlobs() } returns blobsList
        val fileNameList = mutableListOf<String>()
        every { blobContainerClient.getBlobClient(capture(fileNameList)) } returns blobClient
        val payloadList = mutableListOf<ByteArrayInputStream>()
        every {
            blobClient.upload(capture(payloadList), true)
        } returns Unit
        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings",
            context
        )

        assertTrue(payloadList.isEmpty())

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

        every { blobContainerClient.listBlobs() } returns blobsList
        val fileNameList = mutableListOf<String>()
        every { blobContainerClient.getBlobClient(capture(fileNameList)) } returns blobClient
        val payloadList = mutableListOf<ByteArrayInputStream>()
        every {
            blobClient.upload(capture(payloadList), true)
        } returns Unit
        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings",
            context
        )

        assertTrue(payloadList.isEmpty())

        verify {
            request
                .createResponseBuilder(HttpStatus.valueOf(201))
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
