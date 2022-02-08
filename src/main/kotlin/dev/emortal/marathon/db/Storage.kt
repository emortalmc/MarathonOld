package dev.emortal.marathon.db

import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.util.*

abstract class Storage {

    abstract fun setHighscore(player: UUID, highscore: Highscore)
    abstract suspend fun getHighscoreAsync(player: UUID): Highscore?
    abstract suspend fun getTopHighscoresAsync(highscoreCount: Int = 10): Map<UUID, Highscore>?
    abstract suspend fun getPlacementAsync(score: Int): Int?

    val hikari = createHikari()
    abstract fun createHikari(): HikariDataSource

    fun getConnection(): Connection = hikari.connection

}