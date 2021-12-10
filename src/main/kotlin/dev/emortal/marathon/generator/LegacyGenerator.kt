package dev.emortal.marathon.generator

import net.minestom.server.coordinate.Point

object LegacyGenerator : Generator() {
    override fun getNextPosition(pos: Point, targetX: Int, targetY: Int, score: Int): Point {
        val y = if (targetY == 0) random.nextInt(-1, 2) else if (targetY < pos.y()) -1 else 1
        val z = if (y == 1) random.nextInt(1, 4) else if (y == -1) random.nextInt(1, 6) else random.nextInt(1, 5)
        val x = random.nextInt(-3, 4)

        return pos.add(x.toDouble(), y.toDouble(), z.toDouble())
    }
}