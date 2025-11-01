plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

springBoot {
    mainClass.set("com.example.clean.architecture.Application")
}

dependencies {
    implementation(project(":domain"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // Direct WireMock dependency for application configuration and DirectCall server
    implementation("org.wiremock:wiremock-standalone:3.13.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.junit.jupiter:junit-jupiter")
}