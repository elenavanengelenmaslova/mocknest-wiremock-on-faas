package com.example.clean.architecture.azure.persistence.config

import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.storage.blob.BlobContainerAsyncClient
import com.azure.storage.blob.BlobServiceAsyncClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.batch.BlobBatchAsyncClient
import com.azure.storage.blob.batch.BlobBatchClientBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

private val logger = KotlinLogging.logger {}

@Configuration
@Profile("!local")
class BlobStorageConfig(
    @Value("\${azure.storage.endpoint}") private val endpoint: String,
    @Value("\${azure.storage.container-name}") private val containerName: String,
) {

    @Bean
    fun blobServiceAsyncClient(): BlobServiceAsyncClient {
        logger.info { "Initializing Async Blob Service Client using managed identity" }
        val credential = DefaultAzureCredentialBuilder().build()
        return BlobServiceClientBuilder()
            .endpoint(endpoint)
            .credential(credential)
            .buildAsyncClient()
    }

    @Bean
    fun blobContainerAsyncClient(service: BlobServiceAsyncClient): BlobContainerAsyncClient =
        service.getBlobContainerAsyncClient(containerName)

    @Bean
    fun blobBatchAsyncClient(service: BlobServiceAsyncClient): BlobBatchAsyncClient =
        BlobBatchClientBuilder(service).buildAsyncClient()
}