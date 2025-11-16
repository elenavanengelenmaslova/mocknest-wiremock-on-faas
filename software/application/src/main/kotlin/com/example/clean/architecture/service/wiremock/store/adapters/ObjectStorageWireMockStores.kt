package com.example.clean.architecture.service.wiremock.store.adapters

import com.example.clean.architecture.persistence.ObjectStorageInterface
import com.github.tomakehurst.wiremock.common.Json
import com.github.tomakehurst.wiremock.store.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

/**
 * A Stores implementation that wires ObjectStorageInterface directly for FILES and MAPPINGS.
 *
 * - FILES: BlobStore implemented directly over ObjectStorageInterface (Base64 + "__files/" prefix)
 * - MAPPINGS: StubMappingStore implemented directly over ObjectStorageInterface (JSON + "mappings/" prefix)
 * - All other stores delegate to WireMock's in-memory implementations by returning null.
 */
private val logger = KotlinLogging.logger {}
class ObjectStorageWireMockStores(
    storage: ObjectStorageInterface,
) : Stores {

    private val filesBlobStore: BlobStore = ObjectStorageBlobStore(storage)
    private val stubStore: StubMappingStore = InMemoryStubMappingStore()
    private val settingsStore: SettingsStore = InMemorySettingsStore()

    private val requestJournalStore: RequestJournalStore = InMemoryRequestJournalStore()
    private val scenariosStore: ScenariosStore = InMemoryScenariosStore()
    private val recorderStateStore: RecorderStateStore = InMemoryRecorderStateStore()

    // Object stores by name (in-memory)
    private val objectStores = ConcurrentHashMap<String, ObjectStore>()

    override fun getStubStore(): StubMappingStore = stubStore
    override fun getRequestJournalStore(): RequestJournalStore = requestJournalStore
    override fun getSettingsStore(): SettingsStore = settingsStore
    override fun getScenariosStore(): ScenariosStore = scenariosStore
    override fun getRecorderStateStore(): RecorderStateStore = recorderStateStore

    override fun getFilesBlobStore(): BlobStore = filesBlobStore
    override fun getBlobStore(name: String): BlobStore = filesBlobStore

    override fun getObjectStore(
        name: String?,
        persistenceTypeHint: Stores.PersistenceType?,
        maxSize: Int,
    ): ObjectStore {
        val key = name ?: "default"
        return objectStores.computeIfAbsent(key) { InMemoryObjectStore(maxSize) }
    }

    override fun start() {}
    override fun stop() {}
}


/** StubMappingStore implemented directly over ObjectStorageInterface. */
private class StoreBackedStubMappingStore(
    private val storage: ObjectStorageInterface,
) : StubMappingStore {
    private val prefix = "mappings/"
    private fun keyJson(id: UUID): String = "$prefix$id.json"

    override fun get(id: UUID): Optional<StubMapping> =
        // Prefer new ".json" convention; fall back to legacy key without extension if present
        Optional.ofNullable(runCatching { storage.get(keyJson(id)) }.getOrNull()
            ?: runCatching { storage.get("$prefix$id") }.getOrNull())
            .map { json -> Json.read(json, StubMapping::class.java) }

    override fun getAll(): Stream<StubMapping> =
        storage.listPrefix(prefix).asSequence()
            .filter { it.endsWith(".json", ignoreCase = true) }
            .mapNotNull { key -> runCatching { storage.get(key) }.getOrNull() }
            .mapNotNull { json -> runCatching { Json.read(json, StubMapping::class.java) }.getOrNull() }
            .toList()
            .stream()

    override fun add(stub: StubMapping) {
        if (!stub.shouldBePersisted()) return
        // Persist only using the .json convention to avoid duplicate saves
        storage.save(keyJson(stub.id), Json.write(stub))
    }

    override fun replace(existing: StubMapping, updated: StubMapping) {
        if (!existing.shouldBePersisted()) return
        storage.save(keyJson(updated.id), Json.write(updated))
    }

    override fun remove(stubMapping: StubMapping) {
        runCatching { storage.delete(keyJson(stubMapping.id)) }
            .onFailure { logger.error(it) { "Error deleting ${stubMapping.id}" } }
    }

    override fun clear() {
        storage.listPrefix(prefix)
            .filter { it.startsWith(prefix) }
            .forEach { storage.delete(it) }
    }
}
