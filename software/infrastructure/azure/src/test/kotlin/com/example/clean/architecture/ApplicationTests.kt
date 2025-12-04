package com.example.clean.architecture

import com.example.clean.architecture.test.config.AzureLocalTestConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("local")
@Import(AzureLocalTestConfiguration::class)
class ApplicationTests {

    @Test
    fun contextLoads() {
    }
}
