package com.example.clean.architecture.service.wiremock

import com.example.clean.architecture.persistence.ObjectStorageInterface
import com.github.tomakehurst.wiremock.common.Json
import com.github.tomakehurst.wiremock.standalone.MappingsSource
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.github.tomakehurst.wiremock.stubbing.StubMappings
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.util.*

/**
 * WireMock MappingsSource backed by ObjectStorageInterface.
 *
 * - Keys are stored under the [prefix] (default "mappings/") as JSON text of StubMapping
 * - Loads all mappings on startup using prefix-filtered listing and streaming concurrent reads
 */
class ObjectStorageMappingsSource(
    private val storage: ObjectStorageInterface,
    private val prefix: String = "mappings/",
    private val concurrency: Int = 32,
) : MappingsSource {

    private val logger = KotlinLogging.logger {}

    private fun keyOf(id: UUID) = "$prefix$id.json"

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun loadMappingsInto(stubMappings: StubMappings) {
        // WireMock expects blocking; we stream internally with bounded concurrency and block until done
        runBlocking {
            val keys = runCatching { storage.listPrefix(prefix) }
                .onFailure { e -> logger.warn(e) { "Failed to list mappings with prefix '$prefix'" } }
                .getOrDefault(emptyList())

            if (keys.isEmpty()) {
                logger.info { "No mappings found in storage (prefix='$prefix')." }
                return@runBlocking
            }

            logger.info { "Loading ${keys.size} mappings from storage (prefix='$prefix')..." }

            var loaded = 0
            keys
                .asFlow()
                .flatMapMerge(concurrency) { key ->
                    flow {
                        val json = runCatching { storage.get(key) }
                            .onFailure { e -> logger.debug { "Failed to get mapping '$key': $e" } }
                            .getOrNull()
                        if (!json.isNullOrBlank()) emit(key to json)
                    }
                }
                .collect { (key, json) ->
                    runCatching { Json.read(json, StubMapping::class.java) }
                        .onSuccess { mapping ->
                            stubMappings.addMapping(mapping)
                            loaded++
                        }
                        .onFailure { e ->
                            logger.error (e) { "Failed to parse mapping from '$key'" }
                        }
                }

            logger.info { "Finished loading ${loaded}/${keys.size} mappings from storage." }
        }
    }

    override fun save(mapping: StubMapping) {
        storage.save(keyOf(mapping.id), Json.write(mapping))
    }

    override fun save(mappings: List<StubMapping>) {
        mappings.forEach { save(it) }
    }

    override fun remove(mapping: StubMapping) {
        storage.delete(keyOf(mapping.id))
    }

    override fun removeAll() {
        storage.listPrefix(prefix).forEach { storage.delete(it) }
    }
}
