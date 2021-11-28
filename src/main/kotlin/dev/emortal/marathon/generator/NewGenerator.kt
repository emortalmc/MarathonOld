package dev.emortal.marathon.generator

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import world.cepi.kstom.util.asPos
import world.cepi.kstom.util.roundToBlock
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object NewGenerator : Generator() {

    override fun getNextPosition(pos: Point, targetX: Int, targetY: Int, score: Int): Point {
        //val yChange = random.nextInt(-1, 2)
        val yChange = 0
        return pos
            .add(randomPointWithinDistance(1.0, maxDistanceFromYChange(yChange.toDouble())))
            .asPos()
            .roundToBlock()
            .add(0.0, yChange.toDouble(), 0.0)
    }

    fun possibleJump(distance: Double): Boolean {
        return distance <= 4.5
    }

    fun maxDistanceFromYChange(yChange: Double): Double {
        return 4.5 - (yChange / 2.0)
    }

    fun randomPointWithinDistance(
        minDistance: Double,
        maxDistance: Double,
        fov: Int = 90
    ): Point {
        val angle = (random.nextInt(fov) - (fov / 2)) * PI / 180
        println(angle)

        return Vec(cos(angle) + 0.5, 0.0, sin(angle) + 0.5)
            //.mul(random.nextDouble(minDistance, maxDistance))
            .mul(2.0)

    }
}