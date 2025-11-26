package com.example.clean.architecture.azure.persistence

import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobContainerAsyncClient
import com.azure.storage.blob.batch.BlobBatchAsyncClient
import com.azure.storage.blob.models.DeleteSnapshotsOptionType
import com.azure.storage.blob.models.ListBlobsOptions
import com.example.clean.architecture.persistence.ObjectStorageInterface
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

@Repository
@Primary
class BlobStorageObjectStore(
    private val containerClient: BlobContainerAsyncClient,
    private val batchClient: BlobBatchAsyncClient,
) : ObjectStorageInterface {

    override suspend fun save(id: String, content: String): String {
        logger.info { "Saving object with id: $id" }
        val client = containerClient.getBlobAsyncClient(id)
        client.upload(BinaryData.fromString(content), true).awaitSingle()
        return client.blobUrl
    }

    override suspend fun get(id: String): String? {
        logger.info { "Getting object with id: $id" }
        val client = containerClient.getBlobAsyncClient(id)
        val exists = client.exists().awaitSingle()
        if (!exists) {
            logger.info { "Mapping with id: $id not found" }
            return null
        }
        return client.downloadContent()
            .map { it.toBytes().toString(StandardCharsets.UTF_8) }
            .awaitSingleOrNull()
    }

    override suspend fun delete(id: String) {
        logger.info { "Deleting object with id: $id" }
        val client = containerClient.getBlobAsyncClient(id)
        runCatching { client.delete().awaitSingle() }
            .onFailure { logger.info { "Error deleting mapping with id $id: $it" } }
            .getOrThrow()
    }

    override fun list(): Flow<String> =
        containerClient.listBlobs().asFlow().map { it.name }

    override fun listPrefix(prefix: String): Flow<String> =
        containerClient.listBlobs(ListBlobsOptions().setPrefix(prefix), null)
            .asFlow()
            .map { it.name }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getMany(ids: Flow<String>, concurrency: Int): Flow<Pair<String, String?>> =
        ids.flatMapMerge(concurrency) { id ->
            flow {
                val client = containerClient.getBlobAsyncClient(id)
                val exists = client.exists().awaitSingle()
                val value = if (!exists) null else client
                    .downloadContent()
                    .map { it.toBytes().toString(StandardCharsets.UTF_8) }
                    .awaitSingleOrNull()
                emit(id to value)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun deleteMany(ids: Flow<String>, concurrency: Int) {
        fun chunkedFlow(source: Flow<String>, batchSize: Int): Flow<List<String>> = flow {
            val buf = ArrayList<String>(batchSize)
            source.collect { id ->
                buf.add(id)
                if (buf.size >= batchSize) {
                    emit(ArrayList(buf))
                    buf.clear()
                }
            }
            if (buf.isNotEmpty()) emit(ArrayList(buf))
        }

        chunkedFlow(ids, 256)
            .flatMapMerge(concurrency) { batch ->
                flow {
                    val base = containerClient.blobContainerUrl.trimEnd('/')
                    val urls = batch.map { key -> "$base/$key" }
                    batchClient.deleteBlobs(urls, DeleteSnapshotsOptionType.INCLUDE)
                        .then() // Mono<Void>
                        .awaitFirstOrNull()
                    emit(Unit)
                }
            }
            .collect { }
    }
}
