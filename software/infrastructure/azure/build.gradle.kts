import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot")
    kotlin("plugin.spring")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

extra["springCloudVersion"] = "2025.0.0"

dependencies {
    // Gradle-native BOM imports to manage versions
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}"))
    implementation(platform("com.azure:azure-sdk-bom:1.3.2"))

    implementation(project(":domain"))
    implementation(project(":application"))
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.cloud:spring-cloud-function-context")
    implementation("org.springframework.cloud:spring-cloud-function-adapter-azure")
    implementation("com.azure:azure-identity")
    implementation("com.azure:azure-storage-blob")
    implementation("com.azure:azure-storage-blob-batch")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.10.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

val copyKotlinClasses by tasks.registering(Copy::class) {
    from("build/classes/kotlin/main")
    into("build/classes/java/main")
    dependsOn("compileKotlin")
}

tasks.named<Task>("resolveMainClassName") {
    dependsOn(copyKotlinClasses)
}

tasks.bootJar {
    archiveClassifier.set("")
    enabled = true
    dependsOn(copyKotlinClasses)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    from(tasks.compileKotlin)
    from(tasks.processResources)
    into("lib") {
        from(configurations.runtimeClasspath)
    }
}

tasks.jar {
    enabled = true
    dependsOn(copyKotlinClasses)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.test {
    useJUnitPlatform()
}

// tasks.named("azureFunctionsPackage") {
//    dependsOn(copyKotlinClasses, "bootJar")
//    mustRunAfter(copyKotlinClasses)
// }

sourceSets {
    main {
        java.srcDirs("src/main/kotlin")
        resources {
            srcDirs("src/main/resources")
        }
    }
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.named("compileJava") {
    dependsOn("compileKotlin")
}

val azureStagingDir = layout.buildDirectory.dir("azure-functions")

val createAzureStagingDir by tasks.registering {
    dependsOn("bootJar")
    doLast {
        val stagingDir = azureStagingDir.get().asFile
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        // Copy host.json
        file("src/main/resources/host.json").copyTo(File(stagingDir, "host.json"))

        // Create RequestForwarder function.json
        val requestForwarderDir = File(stagingDir, "RequestForwarder")
        requestForwarderDir.mkdirs()
        File(requestForwarderDir, "function.json").writeText(
            """
            {
              "scriptFile": "../lib/${tasks.bootJar.get().archiveFileName.get()}",
              "entryPoint": "com.example.clean.architecture.azure.MockNestFunctions.forwardClientRequest",
              "bindings": [
                {
                  "type": "httpTrigger",
                  "direction": "in",
                  "name": "request",
                  "methods": ["POST", "GET", "PATCH", "PUT", "DELETE"],
                  "authLevel": "function",
                  "route": "mocknest/{*route}"
                },
                {
                  "type": "http",
                  "direction": "out",
                  "name": "${'$'}return"
                }
              ]
            }
            """.trimIndent()
        )

        // Create Admin function.json
        val adminDir = File(stagingDir, "Admin")
        adminDir.mkdirs()
        File(adminDir, "function.json").writeText(
            """
            {
              "scriptFile": "../lib/${tasks.bootJar.get().archiveFileName.get()}",
              "entryPoint": "com.example.clean.architecture.azure.MockNestFunctions.forwardAdminRequest",
              "bindings": [
                {
                  "type": "httpTrigger",
                  "direction": "in",
                  "name": "request",
                  "methods": ["POST", "GET", "PATCH", "PUT", "DELETE"],
                  "authLevel": "function",
                  "route": "__admin/{*route}"
                },
                {
                  "type": "http",
                  "direction": "out",
                  "name": "${'$'}return"
                }
              ]
            }
            """.trimIndent()
        )

        // Copy JAR to lib
        val libDir = File(stagingDir, "lib")
        libDir.mkdirs()
        tasks.bootJar.get().archiveFile.get().asFile.copyTo(File(libDir, tasks.bootJar.get().archiveFileName.get()))
    }
}

val azureFunctionsPackage by tasks.registering(Zip::class) {
    dependsOn(createAzureStagingDir)
    from(azureStagingDir)
    archiveFileName.set("azure-functions.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
}

/*
azurefunctions {
    resourceGroup = "DefaultResourceGroup-WEU"
    appName = "demo-spring-clean-architecture-fun"
    region = "westeurope"
    appSettings = mapOf(
        "WEBSITE_RUN_FROM_PACKAGE" to "1"
    )
}
*/
