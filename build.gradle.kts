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
    compileOnly("com.github.Minestom:Minestom:1a013728fd")
//    compileOnly("com.github.EmortalMC:Immortal:5b2b3a057a")
    compileOnly("dev.emortal.immortal:Immortal:3.0.1")
    compileOnly("com.github.EmortalMC:NBStom:303d0ba5ba")
//    compileOnly("com.github.EmortalMC:TNT:f0680e2013")


    compileOnly("org.litote.kmongo:kmongo-coroutine-serialization:4.8.0")

    //implementation("com.github.KrystilizeNevaDies:Scaffolding:2303491258")
    compileOnly("com.github.EmortalMC:Acquaintance:95a47b101c")

    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
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
            //exclude(dependency("mysql:mysql-connector-java:8.0.29"))
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
