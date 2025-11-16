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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun loadMappingsInto(stubMappings: StubMappings) {
        logger.info { "Loading objects ObjectStorageMappingsSource: $stubMappings" }
        // WireMock expects blocking; we stream internally with bounded concurrency and block until done
        runBlocking {
            val keys = runCatching { storage.listPrefix(prefix) }
                .onFailure { e -> logger.error(e) { "Failed to list mappings with prefix '$prefix'" } }
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
                            .onFailure { e -> logger.error { "Failed to get mapping '$key': $e" } }
                            .getOrNull()
                        if (!json.isNullOrBlank()) emit(key to json)
                    }
                }
                .collect { (key, json) ->
                    runCatching { Json.read(json, StubMapping::class.java) }
                        .mapCatching { mapping ->
                            mapping.isPersistent = false
                            stubMappings.addMapping(mapping)
                            loaded++
                        }.onFailure { e ->
                            logger.error(e) { "Skipping mapping: $key: $e" }
                        }
                }

            logger.info { "Finished loading ${loaded}/${keys.size} mappings from storage." }
        }
    }

    // Persist mappings directly to object storage under the configured prefix
    override fun save(mapping: StubMapping) {
        val id = mapping.id
        val key = "$prefix$id.json"
        runCatching {
            storage.save(key, Json.write(mapping))
        }.onFailure { e ->
            logger.error(e) { "Failed to save mapping ${'$'}id to ${'$'}key" }
        }
    }

    override fun save(mappings: List<StubMapping>) {
        mappings.forEach { save(it) }
    }

    override fun remove(mapping: StubMapping) {
        val id = mapping.id
        val key = "$prefix$id.json"
        runCatching { storage.delete(key) }
            .onFailure { e -> logger.error(e) { "Failed to delete mapping ${'$'}id at ${'$'}key" } }
    }

    override fun removeAll() {
        runCatching { storage.listPrefix(prefix) }
            .onFailure { e -> logger.error(e) { "Failed to list mappings for removeAll with prefix '${'$'}prefix'" } }
            .getOrDefault(emptyList())
            .forEach { key ->
                runCatching { storage.delete(key) }
                    .onFailure { e -> logger.error(e) { "Failed to delete mapping key ${'$'}key" } }
            }
    }
}
