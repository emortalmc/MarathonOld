import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"

    java
}

repositories {
    // Use mavenCentral
    mavenCentral()

    maven(url = "https://jitpack.io")
    maven(url = "https://repo.spongepowered.org/maven")
}

dependencies {
    //compileOnly(kotlin("stdlib"))
    //compileOnly(kotlin("reflect"))

    compileOnly("com.github.Minestom:Minestom:71b6e8df90")
    compileOnly("com.github.EmortalMC:Immortal:3bf837efb2")

    compileOnly("mysql:mysql-connector-java:8.0.28")
    compileOnly("com.zaxxer:HikariCP:5.0.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")

    compileOnly("com.github.EmortalMC:Acquaintance:6987f0b3f2")

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
