package com.example.clean.architecture.test.config

import com.azure.core.http.rest.PagedFlux
import com.azure.core.http.rest.PagedResponse
import com.azure.storage.blob.BlobContainerAsyncClient
import com.azure.storage.blob.BlobServiceAsyncClient
import com.azure.storage.blob.batch.BlobBatchAsyncClient
import com.azure.storage.blob.models.ListBlobsOptions
import io.mockk.every
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Mono

@TestConfiguration
class AzureLocalTestConfiguration {

    @Bean
    fun blobServiceAsyncClient(): BlobServiceAsyncClient = mockk(relaxed = true)

    @Bean
    fun blobContainerAsyncClient(): BlobContainerAsyncClient {
        fun <T> emptyPagedFlux(): PagedFlux<T> = PagedFlux(
            { Mono.empty<PagedResponse<T>>() },
            { _: String -> Mono.empty() }
        )
        val container = mockk<BlobContainerAsyncClient>(relaxed = true)
        // Return empty pages by default so context startup (WireMock mappings load) doesn't hang
        every { container.listBlobs() } returns emptyPagedFlux()
        every { container.listBlobs(any<ListBlobsOptions>()) } returns emptyPagedFlux()
        return container
    }

    @Bean
    fun blobBatchAsyncClient(): BlobBatchAsyncClient {
        fun <T> emptyPagedFlux(): PagedFlux<T> = PagedFlux(
             { Mono.empty<PagedResponse<T>>() },{ Mono.empty() }
        )
        val batch = mockk<BlobBatchAsyncClient>(relaxed = true)
        every { batch.deleteBlobs(any<List<String>>(), any()) } returns emptyPagedFlux()
        return batch
    }

}