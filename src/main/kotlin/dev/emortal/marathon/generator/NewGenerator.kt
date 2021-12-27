package dev.emortal.marathon.generator

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import world.cepi.kstom.util.asPos
import world.cepi.kstom.util.roundToBlock
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

object NewGenerator : Generator() {

    override fun getNextPosition(pos: Point, targetX: Int, targetY: Int, score: Int): Point {
        //val yChange = random.nextInt(0, 1)
        val yChange = random.nextInt(-10, -5)
        return pos
            .add(randomPointWithinDistance(1.5, maxDistanceFromYChange(yChange.toDouble())))
            .asPos()
            .roundToBlock()
            .add(0.0, yChange.toDouble(), 0.0)
    }

    fun maxDistanceFromYChange(yChange: Double): Double {
        return floor(4.5 - (yChange / 3.0))
    }

    fun randomPointWithinDistance(
        minDistance: Double,
        maxDistance: Double,
        fov: Int = 120
    ): Point {
        val angle = Math.toRadians(random.nextInt(fov).toDouble())

        val vec = Vec(cos(angle), 0.0, sin(angle))

        return vec
            //.mul(random.nextDouble(minDistance, maxDistance))
            .mul(maxDistance)
            .rotateAroundY(Math.toRadians(270.0 + (fov / 2)))

    }
}