package com.example.clean.architecture.azure.persistence

import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.models.ListBlobsOptions
import com.example.clean.architecture.persistence.ObjectStorageInterface
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

@Repository
@Primary
class BlobStorageObjectStore(
    private val containerClient: BlobContainerClient
) : ObjectStorageInterface {

    override fun save(id: String, content: String): String {
        logger.info { "Saving object with id: $id" }
        val blobClient = containerClient.getBlobClient(id)
        blobClient.upload(content.byteInputStream(), true)
        return blobClient.blobUrl
    }

    override fun get(id: String): String? {
        logger.info { "Getting object with id: $id" }
        val blobClient = containerClient.getBlobClient(id)
        return if (blobClient.exists()) {
            blobClient.downloadContent().toBytes().toString(StandardCharsets.UTF_8)
        } else {
            logger.info { "Mapping with id: $id not found" }
            null
        }
    }

    override fun delete(id: String) {
        logger.info { "Deleting object with id: $id" }
        val blobClient = containerClient.getBlobClient(id)
        if (blobClient.exists()) {
            blobClient.delete()
        } else {
            logger.info { "Mapping with id: $id not found, nothing to delete" }
        }
    }

    override fun list(): List<String> {
        logger.info { "Listing all object" }
        return containerClient.listBlobs()
            .map { it.name }
            .toList()
    }

    override fun listPrefix(prefix: String): List<String> {
        logger.info { "Listing objects with prefix: $prefix" }
        val options = ListBlobsOptions().setPrefix(prefix)
        return containerClient.listBlobs(options, null).map { it.name }.toList()
    }

    override suspend fun getMany(ids: Collection<String>, concurrency: Int): Map<String, String?> =
        withContext(Dispatchers.IO) {
            val sem = Semaphore(concurrency)
            val deferred = ids.associateWith { id ->
                async {
                    sem.withPermit {
                        val client = containerClient.getBlobClient(id)
                        if (!client.exists()) return@withPermit null
                        client.downloadContent().toBytes().toString(StandardCharsets.UTF_8)
                    }
                }
            }
            deferred.mapValues { it.value.await() }
        }
}
