package dev.emortal.marathon.db

@kotlinx.serialization.Serializable
data class PlayerSettings(
    val uuid: String,
    var theme: String = "dark",
    var speedrunMode: Boolean = false,
    var noDistractions: Boolean = false,
    var noSounds: Boolean = false,
)
