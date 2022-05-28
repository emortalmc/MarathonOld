package dev.emortal.marathon.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.emortal.marathon.MarathonExtension
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*


class MySQLStorage : Storage() {

    init {
        val conn = getConnection()
        val statement =
            conn.prepareStatement("CREATE TABLE IF NOT EXISTS marathon (`player` BINARY(16), `highscore` INT, `time` BIGINT)")
        statement.executeUpdate()
        statement.close()
        conn.close()
    }

    fun drop() {
        val conn = getConnection()
        val statement =
            conn.prepareStatement("DROP TABLE marathon")
        statement.executeUpdate()
        statement.close()
        conn.close()
    }

    override fun setHighscore(player: UUID, highscore: Highscore): Unit = runBlocking {
        launch {
            val conn = getConnection()

            val statement = conn.prepareStatement("DELETE FROM marathon WHERE player=?")

            statement.setBinaryStream(1, player.toInputStream())
            statement.executeUpdate()
            statement.close()

            val statement2 = conn.prepareStatement("INSERT INTO marathon VALUES(?, ?, ?)")

            statement2.setBinaryStream(1, player.toInputStream())
            statement2.setInt(2, highscore.score)
            statement2.setLong(3, highscore.time)

            statement2.executeUpdate()
            statement2.close()

            conn.close()
        }
    }

    override suspend fun getHighscoreAsync(player: UUID): Highscore? = coroutineScope {
        return@coroutineScope async {
            val conn = getConnection()
            val statement = conn.prepareStatement("SELECT highscore, time FROM marathon WHERE player=?")
            statement.setBinaryStream(1, player.toInputStream())

            val results = statement.executeQuery()

            var highscore: Int? = null
            var time: Long? = null
            if (results.next()) {
                highscore = results.getInt(1)
                time = results.getLong(2)
            }
            statement.close()
            results.close()
            conn.close()

            if (highscore == null || time == null) return@async null

            return@async Highscore(highscore, time)
        }.await()
    }

    override suspend fun getTopHighscoresAsync(highscoreCount: Int): Map<UUID, Highscore> = coroutineScope {
        return@coroutineScope async {
            val conn = getConnection()
            val statement = conn.prepareStatement("SELECT * FROM marathon ORDER BY highscore DESC, time ASC LIMIT $highscoreCount")

            val map = mutableMapOf<UUID, Highscore>()
            val results = statement.executeQuery()
            while (results.next()) {
                val uuid = results.getBinaryStream("player").toUUID()
                val score = results.getInt("highscore")
                val time = results.getLong("time")

                //println("${uuid.} - $score")

                map[uuid] = Highscore(score, time)
            }

            results.close()
            statement.close()
            conn.close()

            return@async map
        }.await()
    }

    override suspend fun getPlacementAsync(score: Int): Int? = coroutineScope {
        return@coroutineScope async {
            val conn = getConnection()
            val statement = conn.prepareStatement("SELECT COUNT(DISTINCT player) + 1 AS total FROM marathon WHERE highscore > ${score}")

            var result: Int? = null
            val results = statement.executeQuery()
            if (results.next()) {
                result = results.getInt("total")
            }

            statement.close()
            conn.close()

            return@async result
        }.await()
    }

    override fun createHikari(): HikariDataSource {
        val dbConfig = MarathonExtension.databaseConfig

        val dbName = URLEncoder.encode(dbConfig.tableName, StandardCharsets.UTF_8.toString())
        val dbUsername = URLEncoder.encode(dbConfig.username, StandardCharsets.UTF_8.toString())
        val dbPassword = URLEncoder.encode(dbConfig.password, StandardCharsets.UTF_8.toString())

        //172.17.0.1

        val hikariConfig = HikariConfig()
        hikariConfig.jdbcUrl = "jdbc:mysql://${dbConfig.address}:${dbConfig.port}/${dbName}?user=${dbUsername}&password=${dbPassword}"
        hikariConfig.driverClassName = "com.mysql.cj.jdbc.Driver"

        val hikariSource = HikariDataSource(hikariConfig)
        return hikariSource
    }

    fun UUID.toInputStream(): InputStream {
        val bb = ByteBuffer.wrap(ByteArray(16))
        bb.putLong(mostSignificantBits)
        bb.putLong(leastSignificantBits)
        return bb.array().inputStream()
    }

    fun InputStream.toUUID(): UUID {
        val byteBuffer = ByteBuffer.wrap(this.readAllBytes())
        val high = byteBuffer.long
        val low = byteBuffer.long
        return UUID(high, low)
    }
}