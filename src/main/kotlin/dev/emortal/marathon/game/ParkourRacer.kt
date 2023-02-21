package dev.emortal.marathon.game

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.timer.Task

data class ParkourRacer(val spawnPos: Pos) {

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

    var breakingTask: Task? = null

}