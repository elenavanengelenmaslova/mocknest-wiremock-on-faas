package com.example.clean.architecture.service.wiremock.store

import com.example.clean.architecture.persistence.ObjectStorageInterface
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.util.*
import java.util.stream.Stream

/**
 * WireMock 3.x Files Store backed by ObjectStorageInterface.
 *
 * Keys are relative paths below __files (e.g., "items/42.json").
 * Values are raw bytes. We base64-encode bytes to persist via the String-only ObjectStorageInterface.
 */
@Component("wiremockFilesStore")
class ObjectStorageFilesStore(
    private val storage: ObjectStorageInterface,
) : com.github.tomakehurst.wiremock.store.Store<String, ByteArray> {

    private val logger = KotlinLogging.logger {}
    private val prefix = "__files/"

    private fun fullKey(key: String) = if (key.startsWith(prefix)) key else prefix + key.trimStart('/')

    override fun get(key: String): Optional<ByteArray> {
        val k = fullKey(key)
        val s = storage.get(k) ?: return Optional.empty()
        return runCatching {
            Optional.of(Base64.getDecoder().decode(s))
        }.onFailure {
            logger.info { "content is plain text (not base64), return its bytes" }
        }.getOrDefault(
            // Backward-compat: if content is plain text (not base64), return its bytes
            Optional.of(s.toByteArray(Charsets.UTF_8))
        )
    }

    override fun put(key: String, value: ByteArray) {
        val k = fullKey(key)
        val encoded = Base64.getEncoder().encodeToString(value)
        storage.save(k, encoded)
    }

    override fun remove(key: String) {
        val k = fullKey(key)
        storage.delete(k)
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
