package dev.emortal.marathon.utils

import dev.emortal.marathon.db.Highscore
import dev.emortal.marathon.db.MongoStorage
import org.litote.kmongo.coroutine.CoroutineCollection

inline fun <reified T : Enum<*>> enumValueOrNull(name: String): T? =
    T::class.java.enumConstants.firstOrNull { it.name == name }

enum class TimeFrame(val lyName: String, val collection: CoroutineCollection<Highscore>?) {
    LIFETIME("lifetime", MongoStorage.leaderboard),
    DAILY("daily", MongoStorage.daily),
    WEEKLY("weekly", MongoStorage.weekly),
    MONTHLY("monthly", MongoStorage.monthly)
}