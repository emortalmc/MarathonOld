package dev.emortal.marathon.config

import kotlinx.serialization.Serializable

@Serializable
class DatabaseConfig(
    val enabled: Boolean = false,
    val connectionString: String = "mongodb://172.17.0.1:27017"
)