package com.example.clean.architecture.service.wiremock.store.adapters

import com.example.clean.architecture.persistence.ObjectStorageInterface
import com.github.tomakehurst.wiremock.common.InputStreamSource
import com.github.tomakehurst.wiremock.store.BlobStore
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Base64
import java.util.Optional
import java.util.stream.Stream

/**
 * BlobStore directly backed by ObjectStorageInterface.
 * - Keys are stored under the "__files/" prefix
 * - Values are Base64-encoded. On read, tries Base64 decode and falls back to UTF-8 bytes if not Base64
 */
class ObjectStorageBlobStore(
    private val storage: ObjectStorageInterface,
) : BlobStore {
    private val logger = KotlinLogging.logger {}
    private val prefix = "__files/"

    private fun fullKey(key: String) = if (key.startsWith(prefix)) key else prefix + key.trimStart('/')

    override fun get(key: String): Optional<ByteArray> {
        val raw = storage.get(fullKey(key)) ?: return Optional.empty()
        return runCatching {
            Optional.of(Base64.getDecoder().decode(raw))
        }.onFailure {
            logger.debug { "Non-Base64 content for key=$key; returning raw UTF-8 bytes" }
        }.getOrDefault(Optional.of(raw.toByteArray(Charsets.UTF_8)))
    }

    override fun getStream(key: String): Optional<InputStream> =
        get(key).map { bytes -> ByteArrayInputStream(bytes) }

    override fun getStreamSource(key: String): InputStreamSource =
        InputStreamSource { getStream(key).orElse(ByteArrayInputStream(ByteArray(0))) }

    override fun put(key: String, value: ByteArray) {
        val encoded = Base64.getEncoder().encodeToString(value)
        storage.save(fullKey(key), encoded)
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
