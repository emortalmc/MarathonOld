import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.6.20"
    kotlin("plugin.serialization") version "1.6.20"
    id("com.github.johnrengelman.shadow") version "7.1.2"

    java
}

repositories {
    mavenCentral()

    maven(url = "https://jitpack.io")
    maven(url = "https://repo.spongepowered.org/maven")
}

dependencies {
    compileOnly("com.github.Minestom:Minestom:c7bb9e14e5")
    compileOnly("com.github.EmortalMC:Immortal:c2131030f7")

    compileOnly("mysql:mysql-connector-java:8.0.28")
    compileOnly("com.zaxxer:HikariCP:5.0.1")

    compileOnly("com.github.EmortalMC:Acquaintance:b07cb2e120")

    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
}

tasks {
    processResources {
        filesMatching("extension.json") {
            expand(project.properties)
        }
    }

    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveBaseName.set(project.name)
        mergeServiceFiles()
        minimize {
            exclude(dependency("mysql:mysql-connector-java:8.0.28"))
        }
    }

    build { dependsOn(shadowJar) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}


val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()

compileKotlin.kotlinOptions {
    freeCompilerArgs = listOf("-Xinline-classes")
}
