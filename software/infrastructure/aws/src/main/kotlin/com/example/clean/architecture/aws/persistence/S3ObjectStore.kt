package com.example.clean.architecture.aws.persistence

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import com.example.clean.architecture.persistence.ObjectStorageInterface
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

@Repository
class S3ObjectStore(
    @Value("\${aws.s3.bucket-name}") private val bucketName: String,
    private val s3Client: S3Client,
) : ObjectStorageInterface {

    override fun save(id: String, content: String): String = runBlocking {
        logger.info { "Saving object with id: $id" }
        runCatching {
            val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
            val byteStream = ByteStream.fromBytes(contentBytes)
            s3Client.putObject(
                PutObjectRequest {
                    bucket = bucketName
                    key = id
                    body = byteStream
                    contentLength = byteStream.contentLength
                    contentType = when {
                        id.endsWith(".json", ignoreCase = true) -> "application/json; charset=UTF-8"
                        id.endsWith(".txt", ignoreCase = true)  -> "text/plain; charset=UTF-8"
                        else -> "application/octet-stream"
                    }
                }
            )
            "s3://$bucketName/$id"
        }.onFailure { e -> logger.error(e) { "Failed to save mapping with id: $id" } }
            .getOrThrow()

    }

    override fun get(id: String): String? = runBlocking {
        logger.info { "Getting object with id: $id" }
        runCatching {
            var content: String? = null
            s3Client.getObject(GetObjectRequest {
                bucket = bucketName
                key = id
            }) { response ->
                content = response.body?.toByteArray()?.toString(StandardCharsets.UTF_8)
            }
            content
        }.onFailure { e ->
            logger.info { "Mapping with id: $id not found: ${e.message}" }
        }.getOrNull()
    }

    override fun delete(id: String): Unit = runBlocking {
        logger.info { "Deleting object with id: $id" }
        runCatching {
            s3Client.deleteObject(DeleteObjectRequest {
                bucket = bucketName
                key = id
            })
        }.onFailure { e -> logger.info { "Error deleting mapping with id: $id: ${e.message}" } }
            .getOrThrow()
    }

    override fun list(): List<String> = runBlocking {
        logger.info { "Listing all object" }
        runCatching {
            s3Client.listObjectsV2(ListObjectsV2Request { bucket = bucketName })
        }.onFailure { e -> logger.error(e) { "Failed to list mappings" } }
            .getOrThrow().contents?.mapNotNull { it.key } ?: emptyList()
    }

    override fun listPrefix(prefix: String): List<String> = runBlocking {
        logger.info { "Listing objects with prefix: $prefix" }
        val result = mutableListOf<String>()
        var token: String? = null
        do {
            val resp = s3Client.listObjectsV2(ListObjectsV2Request {
                bucket = bucketName
                this.prefix = prefix
                continuationToken = token
                maxKeys = 1000
            })
            resp.contents?.forEach { it.key?.let(result::add) }
            token = resp.nextContinuationToken
        } while (!token.isNullOrBlank())
        result
    }

    override suspend fun getMany(ids: Collection<String>, concurrency: Int): Map<String, String?> =
        withContext(Dispatchers.IO) {
            val sem = Semaphore(concurrency)
            val deferred = ids.associateWith { id ->
                async {
                    sem.withPermit {
                        runCatching {
                            var content: String? = null
                            s3Client.getObject(GetObjectRequest {
                                bucket = bucketName
                                key = id
                            }) { response ->
                                content = response.body?.toByteArray()?.toString(StandardCharsets.UTF_8)
                            }
                            content
                        }.getOrNull()
                    }
                }
            }
            deferred.mapValues { it.value.await() }
        }
}
