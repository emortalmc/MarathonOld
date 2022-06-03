package dev.emortal.marathon.generator

import net.minestom.server.coordinate.Point
import java.util.concurrent.ThreadLocalRandom

object LegacyGenerator : Generator() {
    override fun getNextPosition(pos: Point, targetX: Int, targetY: Int, score: Int): Point {
        val rand = ThreadLocalRandom.current()

        val y = if (targetY == 0) rand.nextInt(-1, 2) else if (targetY < pos.y()) -1 else 1
        val z = if (y == 1) rand.nextInt(1, 4) else if (y == -1) rand.nextInt(2, 6) else rand.nextInt(1, 5)
        val x = rand.nextInt(-3, 4)

        return pos.add(x.toDouble(), y.toDouble(), z.toDouble())
    }
}