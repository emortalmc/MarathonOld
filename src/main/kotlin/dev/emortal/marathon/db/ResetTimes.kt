package dev.emortal.marathon.db

@kotlinx.serialization.Serializable
data class ResetTimes(val dailyResetTimestamp: Long, val weeklyResetTimestamp: Long, val monthlyResetTimestamp: Long)