package com.example.clean.architecture.service.wiremock

import com.github.tomakehurst.wiremock.common.ClasspathFileSource
import com.github.tomakehurst.wiremock.common.filemaker.FilenameMaker
import com.github.tomakehurst.wiremock.standalone.JsonFileMappingsSource
import com.github.tomakehurst.wiremock.standalone.MappingsSource
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.github.tomakehurst.wiremock.stubbing.StubMappings
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}
/**
 * Composite MappingsSource that delegates to a primary source (object storage)
 * and then to WireMock's built-in classpath loader rooted at [rootDir].
 *
 * - Load order: primary first, then secondary (classpath).
 * - Persistence (save/remove) delegates ONLY to the primary to avoid
 *   filesystem/classpath writes in serverless environments.
 */
class CompositeMappingsSource(
    private val primary: MappingsSource,
    rootDir: String,
) : MappingsSource {

    // Secondary loader: standard WireMock classpath loader rooted at mocknest
    private val secondary = JsonFileMappingsSource(
        ClasspathFileSource(rootDir),
        FilenameMaker()
    )
    //fileSource(new ClasspathFileSource(path));

    override fun loadMappingsInto(stubMappings: StubMappings) {
        logger.info { "Load composite mappings" }
        // Load from storage first
        runCatching { primary.loadMappingsInto(stubMappings) }
            .onFailure { e -> logger.error(e) { "Primary mappings load failed; continuing with classpath" } }

        // Then load from classpath (mocknest/mappings)
        runCatching { secondary.loadMappingsInto(stubMappings) }
            .onFailure { e -> logger.error (e) { "Classpath mappings load failed; continuing" } }
    }

    // Delegate persistence to primary only to avoid writing to classpath
    override fun save(mapping: StubMapping) = primary.save(mapping)
    override fun save(mappings: List<StubMapping>) = primary.save(mappings)
    override fun remove(mapping: StubMapping) = primary.remove(mapping)
    override fun removeAll() = primary.removeAll()
}
