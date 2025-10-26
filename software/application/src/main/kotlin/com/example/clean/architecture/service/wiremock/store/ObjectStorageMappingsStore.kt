package com.example.clean.architecture.service.wiremock.store

import com.example.clean.architecture.persistence.ObjectStorageInterface
import org.springframework.stereotype.Component
import java.util.Optional
import java.util.stream.Stream

/**
 * WireMock 3.x MAPPINGS Store backed by ObjectStorageInterface.
 * Keys are mapping IDs (UUID as String). Values are the full mapping JSON as String.
 */
@Component("wiremockMappingsStore")
class ObjectStorageMappingsStore(
    private val storage: ObjectStorageInterface
) : com.github.tomakehurst.wiremock.store.Store<String, String> {

    private val prefix = "mappings/"

    private fun fullKey(key: String): String = if (key.startsWith(prefix)) key else prefix + key

    override fun get(key: String): Optional<String> = Optional.ofNullable(storage.get(fullKey(key)))

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

    override fun clear() {
        storage.list().filter { it.startsWith(prefix) }.forEach { storage.delete(it) }
    }
}
