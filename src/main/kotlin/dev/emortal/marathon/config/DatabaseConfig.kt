package dev.emortal.marathon.config

import kotlinx.serialization.Serializable

@Serializable
class DatabaseConfig(
    val enabled: Boolean = false,
    val address: String = "",
    val port: String = "3306",
    val tableName: String = "",
    val username: String = "",
    val password: String = ""
)