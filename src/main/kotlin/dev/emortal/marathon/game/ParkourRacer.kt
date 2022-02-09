package dev.emortal.marathon.game

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player

class ParkourRacer(val player: Player, val spawnPos: Pos) {
    var score = 0
    var combo = 0
    val blocks = mutableListOf<Point>(spawnPos.sub(0.0, 1.0, 0.0))

    override fun equals(other: Any?): Boolean {
        val other = other as? ParkourRacer ?: return false
        if (this.player.uuid == other.player.uuid) return true
        return false
    }

}