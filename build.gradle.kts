plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("kapt") version "2.1.20"
    application
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "com.shiyi"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // https://mvnrepository.com/artifact/info.picocli/picocli
    implementation("info.picocli:picocli:4.7.7")
//    kapt("info.picocli:picocli-codegen:4.7.7")

    // Coroutines for high-performance concurrency
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Netty for high-performance networking
    implementation("io.netty:netty-all:4.1.108.Final")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.5.13")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.shiyi.MainKt")
}
tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "com.shiyi.MainKt"
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "21"
    }
}

// For Picocli annotation processing with KSP
kapt {
    arguments {
        arg("project", "${project.group}/${project.name}")
    }
}
