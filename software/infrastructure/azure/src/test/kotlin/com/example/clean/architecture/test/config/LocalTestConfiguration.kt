package com.example.clean.architecture.test.config

import com.azure.storage.blob.BlobContainerAsyncClient
import com.azure.storage.blob.BlobServiceAsyncClient
import com.azure.storage.blob.batch.BlobBatchAsyncClient
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class LocalTestConfiguration {


    @Bean
    fun blobServiceAsyncClient(): BlobServiceAsyncClient = mockk(relaxed = true)

    @Bean
    fun blobContainerAsyncClient(): BlobContainerAsyncClient =
        mockk(relaxed = true)

    @Bean
    fun blobBatchAsyncClient(): BlobBatchAsyncClient =
        mockk(relaxed = true)
}