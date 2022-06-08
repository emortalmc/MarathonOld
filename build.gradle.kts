import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.0"
    kotlin("plugin.serialization") version "1.7.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"

    java
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.Minestom:Minestom:58b6e90142")
    compileOnly("com.github.EmortalMC:Immortal:eab3ac8420")
    compileOnly("com.github.EmortalMC:NBStom:fd3da7bf91")


    implementation("org.litote.kmongo:kmongo-coroutine-serialization:4.6.0")

    //compileOnly("mysql:mysql-connector-java:8.0.29")
    //compileOnly("com.zaxxer:HikariCP:5.0.1")

    //implementation(files("libs/Blocky-1.0-SNAPSHOT.jar"))

    //implementation("com.github.KrystilizeNevaDies:Scaffolding:2303491258")
    compileOnly("com.github.EmortalMC:Acquaintance:b07cb2e120")

    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
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
