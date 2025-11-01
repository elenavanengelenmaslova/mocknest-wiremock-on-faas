package com.example.clean.architecture.service.wiremock.store

import com.example.clean.architecture.persistence.ObjectStorageInterface
import com.github.tomakehurst.wiremock.store.Store
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.springframework.stereotype.Component
import java.util.Optional
import java.util.UUID
import java.util.stream.Stream

/**
 * WireMock 3.x MAPPINGS Store backed by ObjectStorageInterface.
 * Keys are mapping IDs (UUID as String). Values are the full mapping JSON as String.
 */
@Component("wiremockMappingsStore")
class ObjectStorageMappingsStore(
    private val storage: ObjectStorageInterface
) : Store<UUID, StubMapping> {

    private val prefix = "mappings/"

    private fun fullKey(key: String): String = if (key.startsWith(prefix)) key else prefix + key

    override fun get(key: String): Optional<StubMapping> = Optional.ofNullable(storage.get(fullKey(key)))

    override fun put(key: String, value: String) {
        storage.save(fullKey(key), value)
    }

    override fun remove(key: String) {
        storage.delete(fullKey(key))
    }

    override fun getAllKeys(): Stream<String> =
        storage.list().asSequence()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .toList()
            .stream()

    override fun get(key: UUID?): Optional<StubMapping?>? {
        TODO("Not yet implemented")
    }

    override fun put(key: UUID?, content: StubMapping?) {
        TODO("Not yet implemented")
    }

    override fun remove(key: UUID?) {
        TODO("Not yet implemented")
    }

    override fun clear() {
        storage.list().filter { it.startsWith(prefix) }.forEach { storage.delete(it) }
    }
}
