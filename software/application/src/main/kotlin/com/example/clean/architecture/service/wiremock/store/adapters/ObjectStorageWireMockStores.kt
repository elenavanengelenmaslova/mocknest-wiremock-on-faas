package com.example.clean.architecture.service.wiremock.store.adapters

import com.example.clean.architecture.service.wiremock.store.ObjectStorageFilesStore
import com.example.clean.architecture.service.wiremock.store.ObjectStorageMappingsStore
import com.github.tomakehurst.wiremock.common.InputStreamSource
import com.github.tomakehurst.wiremock.store.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*
import java.util.stream.Stream

/**
 * A Stores implementation that wires ObjectStorage-backed Stores for FILES and MAPPINGS.
 *
 * - FILES: backed by ObjectStorageFilesStore (Store<String, ByteArray>) via a BlobStore adapter
 * - MAPPINGS: backed by ObjectStorageMappingsStore (Store<String, String>) via a StubMappingStore adapter
 * - All other stores delegate to an ephemeral in-memory implementation.
 */
class ObjectStorageWireMockStores(
    private val filesStore: ObjectStorageFilesStore,
    private val mappingsStore: ObjectStorageMappingsStore,
) : Stores {

    private val logger = KotlinLogging.logger {}


    private val filesBlobStore: BlobStore = StoreBackedBlobStore(filesStore)
    private val stubStore: StubMappingStore = StoreBackedStubMappingStore(mappingsStore)


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

/** BlobStore adapter that delegates to a Store<String, ByteArray>. */
private class StoreBackedBlobStore(
    private val delegate: Store<String, ByteArray>,
) : BlobStore {
    override fun get(key: String): Optional<ByteArray> = delegate.get(key)
    override fun getStream(key: String): Optional<java.io.InputStream> =
        get(key).map { java.io.ByteArrayInputStream(it) }

    override fun getStreamSource(key: String): InputStreamSource =
        InputStreamSource { getStream(key).orElse(java.io.ByteArrayInputStream(ByteArray(0))) }

    override fun put(key: String, value: ByteArray) = delegate.put(key, value)
    override fun remove(key: String) = delegate.remove(key)
    override fun getAllKeys(): Stream<String> = delegate.getAllKeys()
    override fun clear() = delegate.clear()
}

/** StubMappingStore that serializes to/from JSON via a Store<String, String>. */
private class StoreBackedStubMappingStore(
    private val delegate: Store<UUID, StubMapping>,
) : StubMappingStore {
    override fun get(id: UUID): Optional<StubMapping> = delegate.get(id)

    override fun getAll(): Stream<StubMapping> = delegate.getAllKeys()
        .map { it }
        .map { key -> delegate.get(key).orElse(null) }
        .filter { it != null }

    override fun add(stub: StubMapping): Unit = delegate.put(stub.id, stub)
    override fun replace(existing: StubMapping, updated: StubMapping) = delegate.put(updated.id, updated)
    override fun remove(stubMapping: StubMapping) = delegate.remove(stubMapping.id)

    override fun clear() = delegate.clear()
}
