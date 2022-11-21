package dev.emortal.marathon.game

import dev.emortal.immortal.util.MinestomRunnable
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player

class ParkourRacer(val player: Player, val spawnPos: Pos) {

    val playerPos = spawnPos.add(0.5, 0.0, 0.5)

    var score = -1
        set(value) {
            if (value > highscore) highscore = value
            field = value
        }
    var highscore = 0
    var combo = 0
    val blocks = ArrayDeque<Point>(8)

    init {
        blocks.add(spawnPos.sub(0.0, 1.0, 0.0))
    }

    var breakingTask: MinestomRunnable? = null

    override fun equals(other: Any?): Boolean {
        val other = other as? ParkourRacer ?: return false
        if (this.player.uuid == other.player.uuid) return true
        return false
    }

}