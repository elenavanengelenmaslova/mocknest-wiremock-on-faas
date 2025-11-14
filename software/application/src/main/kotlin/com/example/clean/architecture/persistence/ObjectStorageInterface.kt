package com.example.clean.architecture.persistence

interface ObjectStorageInterface {
    fun save(id: String, content: String): String
    fun get(id: String): String?
    fun delete(id: String)
    fun list(): List<String>

    /**
     * List keys starting with the provided prefix. Default implementation filters [list()].
     */
    fun listPrefix(prefix: String): List<String> = list().filter { it.startsWith(prefix) }

    /**
     * Fetch many objects, optionally in parallel. Default implementation does sequential gets.
     * Backend-specific implementations may override for efficiency.
     */
    suspend fun getMany(ids: Collection<String>, concurrency: Int = 16): Map<String, String?> {
        val result = LinkedHashMap<String, String?>(ids.size)
        for (id in ids) result[id] = get(id)
        return result
    }
}
