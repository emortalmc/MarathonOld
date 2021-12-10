package dev.emortal.marathon.db

import java.sql.Connection
import java.util.*

abstract class Storage {

    abstract fun setHighscore(player: UUID, highscore: Int, time: Long)
    abstract fun getHighscore(player: UUID): Int?
    abstract fun getTime(player: UUID): Long?
    abstract fun getHighscoreAndTime(player: UUID): Pair<Int, Long>?

    val connection = createConnection()

    abstract fun createConnection(): Connection

}