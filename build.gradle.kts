import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.21"
    kotlin("plugin.serialization") version "1.7.21"
    id("com.github.johnrengelman.shadow") version "7.1.2"

    java
}

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.Minestom:Minestom:d7feed23c8")
    implementation("com.github.EmortalMC:Immortal:d9e82ab687")

    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    implementation("com.github.EmortalMC:Acquaintance:95a47b101c")
}

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveBaseName.set(project.name)
        mergeServiceFiles()

        manifest {
            attributes (
                "Main-Class" to "dev.emortal.marathon.MarathonMainKt",
                "Multi-Release" to true
            )
        }

        transform(com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer::class.java)
    }

    withType<AbstractArchiveTask> {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
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
