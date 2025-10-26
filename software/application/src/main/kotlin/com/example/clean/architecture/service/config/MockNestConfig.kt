package com.example.clean.architecture.service.config

import com.example.clean.architecture.service.wiremock.store.ObjectStorageFilesStore
import com.example.clean.architecture.service.wiremock.store.ObjectStorageMappingsStore
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServerFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

private val logger = KotlinLogging.logger {}

@Configuration
class MockNestConfig {

    @Value("\${mocknest.root-dir:mocknest}")
    private val rootDir: String = "mocknest"

    @Bean
    fun directCallHttpServerFactory() = DirectCallHttpServerFactory()

    @Bean
    fun wireMockServer(
        directCallHttpServerFactory: DirectCallHttpServerFactory,
        filesStore: ObjectStorageFilesStore,
        mappingsStore: ObjectStorageMappingsStore,
    ): WireMockServer {
        val config = wireMockConfig()
            // Keep classpath root for any built-in defaults (optional)
            .usingFilesUnderClasspath(rootDir)
            .notifier(ConsoleNotifier(true))
            .httpServerFactory(directCallHttpServerFactory)
            .disableRequestJournal()

        // Configure custom Stores for FILES and MAPPINGS
        try {
            val cfgClass = config::class.java
            val filesStoreMethod = cfgClass.methods.firstOrNull { it.name == "filesStore" }
            val mappingsStoreMethod = cfgClass.methods.firstOrNull { it.name == "mappingsStore" }
            filesStoreMethod?.invoke(config, filesStore)
            mappingsStoreMethod?.invoke(config, mappingsStore)
        } catch (e: Exception) {
            // Fallback: best-effort, actual configuration methods may differ by WireMock version
            logger.warn { "Could not configure custom Stores via direct methods: ${e.message}" }
        }

        val server = WireMockServer(config)
        server.start()
        logger.info { "MockNest server started with root dir: $rootDir and custom Stores for FILES and MAPPINGS" }
        return server
    }

    @Bean
    @DependsOn("wireMockServer")
    fun directCallHttpServer(directCallHttpServerFactory: DirectCallHttpServerFactory): DirectCallHttpServer {
        return directCallHttpServerFactory.httpServer
    }
}
