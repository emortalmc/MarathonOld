package dev.emortal.marathon.db

import com.mongodb.client.model.ReplaceOptions
import dev.emortal.immortal.util.CoroutineRunnable
import dev.emortal.immortal.util.MinestomRunnable
import dev.emortal.marathon.MarathonExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.eq
import org.litote.kmongo.gt
import org.litote.kmongo.reactivestreams.KMongo
import org.tinylog.kotlin.Logger
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.*

class MongoStorage {

    companion object {
        //172.17.0.1 <- docker
        var client: CoroutineClient? = null
        var database: CoroutineDatabase? = null

        var leaderboard: CoroutineCollection<Highscore>? = null

        var weekly: CoroutineCollection<Highscore>? = null
        var monthly: CoroutineCollection<Highscore>? = null

//        var playerSettings: CoroutineCollection<PlayerSettings>? = null

        var resetCollection: CoroutineCollection<ResetTimes>? = null
    }

    val mongoScope = CoroutineScope(Dispatchers.IO)

    fun init() {
        client = KMongo.createClient(MarathonExtension.databaseConfig.connectionString).coroutine
        database = client!!.getDatabase("Marathon")

        leaderboard = database!!.getCollection("leaderboard")
        weekly = database!!.getCollection("weeklylb")
        monthly = database!!.getCollection("monthlylb")

        resetCollection = database!!.getCollection("resetTimes")

        // Reset logic
        mongoScope.launch {
            val now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            val tomorrow = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).plusDays(1).toEpochSecond(ZoneOffset.UTC)
            val nextWeek = LocalDateTime.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS).toEpochSecond(ZoneOffset.UTC)
            val nextMonth = LocalDateTime.now().with(TemporalAdjusters.firstDayOfNextMonth()).truncatedTo(ChronoUnit.DAYS).toEpochSecond(ZoneOffset.UTC)

            if (resetCollection?.findOne() == null) {

                // First time DB init
                resetCollection?.insertOne(ResetTimes(tomorrow, nextWeek, nextMonth))

            } else {
                // Checks if leaderboards should have been reset, but the server was offline

                var resetTimes = resetCollection?.findOne()!!
                val nowSeconds = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
                if (resetTimes.weeklyResetTimestamp < nowSeconds) {
                    weekly?.drop() // Clear weekly
                    val newResetTimes = resetTimes.copy(weeklyResetTimestamp = nextWeek)
                    resetCollection?.replaceOne("{}", newResetTimes)
                    resetTimes = newResetTimes
                }
                if (resetTimes.monthlyResetTimestamp < nowSeconds) {
                    monthly?.drop() // Clear monthly
                    val newResetTimes = resetTimes.copy(monthlyResetTimestamp = nextMonth)
                    resetCollection?.replaceOne("{}", newResetTimes)
                }
            }

            val durationUntilNextWeek = Duration.ofSeconds(nextWeek - now)
            val durationUntilNextMonth = Duration.ofSeconds(nextMonth - now)

            Logger.info("Weekly will reset in ${nextWeek - now}s")
            Logger.info("Monthly will reset in ${nextMonth - now}s")

            var resetTimes = resetCollection?.findOne()!!

            object : MinestomRunnable(group = null, delay = durationUntilNextWeek, repeat = durationUntilNextWeek) {
                override fun run() {
                    mongoScope.launch {
                        val now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
                        val nextWeek = LocalDateTime.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS).toEpochSecond(ZoneOffset.UTC)

                        Logger.info("Cleared weekly leaderboard!")
                        weekly?.drop()
                        val newResetTimes = resetTimes.copy(weeklyResetTimestamp = nextWeek)
                        resetCollection?.replaceOne("{}", resetTimes.copy(weeklyResetTimestamp = nextWeek))

                        repeat = Duration.ofSeconds(nextWeek - now)
                        resetTimes = newResetTimes
                    }
                }
            }

            object : MinestomRunnable(group = null, delay = durationUntilNextMonth, repeat = durationUntilNextMonth) {
                override fun run() {
                    mongoScope.launch {
                        val now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
                        val nextMonth = LocalDateTime.now().with(TemporalAdjusters.firstDayOfNextMonth()).truncatedTo(ChronoUnit.DAYS).toEpochSecond(ZoneOffset.UTC)

                        Logger.info("Cleared monthly leaderboard!")
                        monthly?.drop()
                        val newResetTimes = resetTimes.copy(monthlyResetTimestamp = nextMonth)
                        resetCollection?.replaceOne("{}", resetTimes.copy(monthlyResetTimestamp = nextMonth))

                        repeat = Duration.ofSeconds(nextMonth - now)
                        resetTimes = newResetTimes
                    }
                }
            }
        }

    }

    fun setHighscore(highscore: Highscore, collection: CoroutineCollection<Highscore>?) {
        mongoScope.launch {
            collection?.replaceOne(Highscore::uuid eq highscore.uuid, highscore, ReplaceOptions().upsert(true))
        }
    }

    suspend fun getHighscore(uuid: UUID, collection: CoroutineCollection<Highscore>?): Highscore? =
        collection?.findOne(Highscore::uuid eq uuid.toString())

    suspend fun getTopHighscores(amount: Int = 10, collection: CoroutineCollection<Highscore>?) =
        collection?.find()
            ?.ascendingSort(Highscore::timeTaken) // but with lowest time
            ?.descendingSort(Highscore::score) // Find highest score

            ?.limit(amount)
            ?.toList()

    suspend fun getPlacementByScore(score: Int, collection: CoroutineCollection<Highscore>?): Int =
        // Count scores that are greater than (gt) score
        (collection?.find(Highscore::score gt score)?.toFlow()?.count() ?: 0) + 1

    suspend fun getPlacement(uuid: UUID, collection: CoroutineCollection<Highscore>?): Int? {
        // Count scores that are greater than (gt) score
        val highscore = getHighscore(uuid, collection) ?: return null
        return (collection?.find(Highscore::score gt highscore.score)?.toFlow()?.count() ?: 0) + 1
    }

//    suspend fun getSettings(uuid: UUID): PlayerSettings =
//        playerSettings?.findOne(PlayerSettings::uuid eq uuid.toString())
//        ?: PlayerSettings(uuid = uuid.toString())
//
//    fun saveSettings(uuid: UUID, settings: PlayerSettings) = runBlocking {
//        playerSettings?.replaceOne(PlayerSettings::uuid eq settings.uuid, settings, ReplaceOptions().upsert(true))
//    }
}