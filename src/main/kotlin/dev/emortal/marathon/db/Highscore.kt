package dev.emortal.marathon.db

@kotlinx.serialization.Serializable
data class Highscore(val uuid: String, val score: Int, val timeTaken: Long, val timeSubmitted: Long)
