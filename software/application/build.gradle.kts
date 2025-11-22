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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.mockk:mockk:1.13.8")
}