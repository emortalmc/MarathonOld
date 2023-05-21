package dev.emortal.marathon.db

@kotlinx.serialization.Serializable
data class ResetTimes(val weeklyResetTimestamp: Long, val monthlyResetTimestamp: Long)