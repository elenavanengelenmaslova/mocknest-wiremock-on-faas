package com.example.clean.architecture.azure


import com.example.clean.architecture.test.config.LocalTestConfiguration
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.HttpMethod
import com.microsoft.azure.functions.HttpRequestMessage
import com.microsoft.azure.functions.HttpStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Disabled
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import kotlin.test.Test

@SpringBootTest
@ActiveProfiles("local")
@Import(LocalTestConfiguration::class)
class MockNestFunctionsIntegrationTest {

    @Autowired
    private lateinit var mockNestFunctions: MockNestFunctions
    private val context = mockk<ExecutionContext>()
    val request =
        mockk<HttpRequestMessage<String>>(relaxed = true)

    @Test
    @Disabled("Wip")
    fun `When match request then maps to a success response`() {
        every { request.httpMethod } returns HttpMethod.GET

        mockNestFunctions.forwardClientRequest(request, "health", context)
        verify {
            request
                .createResponseBuilder(HttpStatus.valueOf(200))
        }
    }

    @Test
    fun `When deleting a MockNest mapping then returns 200 status code`() {
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
                        200
                    )
                )
        }
    }

    @Test
    fun `When retrieving near misses then returns 200 status code`() {
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
    fun `When resetting MockNest mappings then returns 200 status code`() {
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
    fun `When retrieving all mappings then returns 200 status code`() {
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
    fun `When retrieving all requests then returns 200 status code`() {
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
    fun `When clearing request journal then returns 200 status code`() {
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
    fun `When retrieving unmatched requests then returns 200 status code`() {
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
}
