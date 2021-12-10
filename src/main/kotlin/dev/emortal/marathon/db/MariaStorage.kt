package dev.emortal.marathon.db

import com.google.common.io.ByteStreams
import com.mysql.cj.jdbc.Driver
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.DriverManager
import java.util.*


class MariaStorage : Storage() {

    init {
        val statement =
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS marathon (`player` BINARY(16), `highscore` INT, `time` BIGINT)")
        statement.executeUpdate()
        statement.close()
    }

    override fun setHighscore(player: UUID, highscore: Int, time: Long) {
        val statement = connection.prepareStatement("REPLACE INTO marathon VALUES(?, ?, ?)")
        statement.setBinaryStream(1, player.toInputStream())
        statement.setInt(2, highscore)
        statement.setLong(3, time)
        statement.execute()
        statement.close()
    }

    override fun getHighscoreAndTime(player: UUID): Pair<Int, Long>? {
        val statement = connection.prepareStatement("SELECT highscore, time FROM marathon WHERE player=?")
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

        if (highscore == null || time == null) return null
        return Pair(highscore, time)
    }

    override fun getHighscore(player: UUID): Int? {
        val statement = connection.prepareStatement("SELECT highscore FROM marathon WHERE player=?")
        statement.setBinaryStream(1, player.toInputStream())

        val results = statement.executeQuery()

        var highscore: Int? = null
        if (results.next()) {
            highscore = results.getInt(1)
        }
        statement.close()
        results.close()

        return highscore
    }

    override fun getTime(player: UUID): Long? {
        val statement = connection.prepareStatement("SELECT time FROM marathon WHERE player=?")
        statement.setBinaryStream(1, player.toInputStream())

        val results = statement.executeQuery()

        var time: Long? = null
        if (results.next()) {
            time = results.getLong(1)
        }
        statement.close()
        results.close()

        return time
    }

    override fun createConnection(): Connection {
        Driver()

        val dbName = "s7_leaderboard"
        val dbUsername = "u7_QohLcpx1iS"
        val dbPassword = "WHe!tGQPQgTfT3cmX@pEHgKf"

        return DriverManager.getConnection("jdbc:mysql://172.17.0.1:3306/${dbName}?user=${dbUsername}&password=${dbPassword}&useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true")
    }

    fun UUID.toInputStream(): InputStream {
        val bytes = ByteArray(16)
        ByteBuffer.wrap(bytes)
            .putLong(mostSignificantBits)
            .putLong(leastSignificantBits)
        return ByteArrayInputStream(bytes)
    }

    fun InputStream.toUUID(): UUID? {
        val buffer = ByteBuffer.allocate(16)
        try {
            buffer.put(ByteStreams.toByteArray(this))
            buffer.flip()
            return UUID(buffer.long, buffer.long)
        } catch (e: IOException) {
        }
        return null
    }
}