package dev.emortal.marathon.generator

import net.minestom.server.coordinate.Point

object RacingGenerator : Generator() {
    override fun getNextPosition(pos: Point, targetX: Int, targetY: Int, score: Int): Point {
        val y = if (targetY == 0) random.nextInt(-1, 2) else if (targetY < pos.y()) -1 else 1
        val z = if (y == 1) random.nextInt(1, 4) else if (y == -1) random.nextInt(1, 6) else random.nextInt(1, 5)
        val x = if (pos.x() > targetX) random.nextInt(-3, 1) else random.nextInt(0, 4)

        return pos.add(x.toDouble(), y.toDouble(), z.toDouble())
    }
}