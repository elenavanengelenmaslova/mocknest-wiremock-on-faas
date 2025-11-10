package com.example.clean.architecture.service.wiremock.store.adapters

import com.example.clean.architecture.persistence.ObjectStorageInterface
import com.github.tomakehurst.wiremock.common.Json
import com.github.tomakehurst.wiremock.store.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*
import java.util.stream.Stream

/**
 * A Stores implementation that wires ObjectStorageInterface directly for FILES and MAPPINGS.
 *
 * - FILES: BlobStore implemented directly over ObjectStorageInterface (Base64 + "__files/" prefix)
 * - MAPPINGS: StubMappingStore implemented directly over ObjectStorageInterface (JSON + "mappings/" prefix)
 * - All other stores delegate to WireMock's in-memory implementations by returning null.
 */
class ObjectStorageWireMockStores(
    private val storage: ObjectStorageInterface,
) : Stores {

    private val logger = KotlinLogging.logger {}

    private val filesBlobStore: BlobStore = ObjectStorageBlobStore(storage)
    private val stubStore: StubMappingStore = StoreBackedStubMappingStore(storage)

    override fun getStubStore(): StubMappingStore = stubStore
    override fun getRequestJournalStore(): RequestJournalStore? = null
    override fun getSettingsStore(): SettingsStore?  = null
    override fun getScenariosStore(): ScenariosStore? = null
    override fun getRecorderStateStore(): RecorderStateStore? = null

    override fun getFilesBlobStore(): BlobStore = filesBlobStore
    override fun getBlobStore(name: String): BlobStore = filesBlobStore

    override fun getObjectStore(
        name: String?,
        persistenceTypeHint: Stores.PersistenceType?,
        maxSize: Int,
    ): ObjectStore? = null

    override fun start() {}
    override fun stop() {}
}


/** StubMappingStore implemented directly over ObjectStorageInterface. */
private class StoreBackedStubMappingStore(
    private val storage: ObjectStorageInterface,
) : StubMappingStore {
    private val prefix = "mappings/"
    private fun keyOf(id: UUID): String = "$prefix$id"

    override fun get(id: UUID): Optional<StubMapping> =
        Optional.ofNullable(storage.get(keyOf(id)))
            .map { json -> Json.read(json, StubMapping::class.java) }

    override fun getAll(): Stream<StubMapping> =
        storage.list().asSequence()
            .filter { it.startsWith(prefix) }
            .mapNotNull { key -> storage.get(key) }
            .mapNotNull { json -> runCatching { Json.read(json, StubMapping::class.java) }.getOrNull() }
            .toList()
            .stream()

    override fun add(stub: StubMapping) {
        storage.save(keyOf(stub.id), Json.write(stub))
    }

    override fun replace(existing: StubMapping, updated: StubMapping) {
        storage.save(keyOf(updated.id), Json.write(updated))
    }

    override fun remove(stubMapping: StubMapping) {
        storage.delete(keyOf(stubMapping.id))
    }

    override fun clear() {
        storage.list().filter { it.startsWith(prefix) }.forEach { storage.delete(it) }
    }
}
