package dev.emortal.marathon.db

import com.mongodb.client.model.ReplaceOptions
import dev.emortal.marathon.MarathonMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.launch
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.metadata.minecart.MinecartMeta
import net.minestom.server.timer.TaskSchedule
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.eq
import org.litote.kmongo.gt
import org.litote.kmongo.reactivestreams.KMongo
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.*
import java.util.function.Supplier

private val LOGGER = LoggerFactory.getLogger(MongoStorage::class.java)

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
        client = KMongo.createClient(MarathonMain.databaseConfig.connectionString).coroutine
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
                    resetTimes = newResetTimes
                }
            }

            val durationUntilNextWeek = Duration.ofSeconds(nextWeek - now)
            val durationUntilNextMonth = Duration.ofSeconds(nextMonth - now)

            LOGGER.info("Weekly will reset in ${nextWeek - now}s")
            LOGGER.info("Monthly will reset in ${nextMonth - now}s")

            var resetTimes = resetCollection?.findOne()!!

            val scheduler = MinecraftServer.getSchedulerManager()


            scheduler.submitTask(object : Supplier<TaskSchedule> {
                var first = true;

                override fun get(): TaskSchedule {
                    if (first) {
                        first = false
                        return TaskSchedule.duration(durationUntilNextWeek)
                    }

                    val now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
                    val nextWeek = LocalDateTime.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS).toEpochSecond(ZoneOffset.UTC)

                    mongoScope.launch {
                        LOGGER.info("Cleared weekly leaderboard!")
                        weekly?.drop()
                        val newResetTimes = resetTimes.copy(weeklyResetTimestamp = nextWeek)
                        resetCollection?.replaceOne("{}", resetTimes.copy(weeklyResetTimestamp = nextWeek))

                        resetTimes = newResetTimes
                    }

                    return TaskSchedule.seconds(nextWeek - now)
                }
            })


            scheduler.submitTask(object : Supplier<TaskSchedule> {
                var first = true

                override fun get(): TaskSchedule {
                    if (first) {
                        first = false
                        return TaskSchedule.duration(durationUntilNextMonth)
                    }

                    val now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
                    val nextMonth = LocalDateTime.now().with(TemporalAdjusters.firstDayOfNextMonth()).truncatedTo(ChronoUnit.DAYS).toEpochSecond(ZoneOffset.UTC)

                    mongoScope.launch {
                        LOGGER.info("Cleared monthly leaderboard!")
                        monthly?.drop()
                        val newResetTimes = resetTimes.copy(monthlyResetTimestamp = nextMonth)
                        resetCollection?.replaceOne("{}", resetTimes.copy(monthlyResetTimestamp = nextMonth))

                        resetTimes = newResetTimes
                    }

                    return TaskSchedule.seconds(nextMonth - now)
                }
            })
        }

    }

    suspend fun setHighscore(highscore: Highscore, collection: CoroutineCollection<Highscore>?) =
        collection?.replaceOne(Highscore::uuid eq highscore.uuid, highscore, ReplaceOptions().upsert(true))


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
        val highscore = getHighscore(uuid, collection) ?: return null
        return getPlacementByScore(highscore.score, collection)
    }

//    suspend fun getSettings(uuid: UUID): PlayerSettings =
//        playerSettings?.findOne(PlayerSettings::uuid eq uuid.toString())
//        ?: PlayerSettings(uuid = uuid.toString())
//
//    fun saveSettings(uuid: UUID, settings: PlayerSettings) = runBlocking {
//        playerSettings?.replaceOne(PlayerSettings::uuid eq settings.uuid, settings, ReplaceOptions().upsert(true))
//    }
}