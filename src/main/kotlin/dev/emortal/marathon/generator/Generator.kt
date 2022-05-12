package dev.emortal.marathon.generator

import net.minestom.server.coordinate.Point
import java.util.concurrent.ThreadLocalRandom

abstract class Generator {


    abstract fun getNextPosition(pos: Point, targetX: Int, targetY: Int, score: Int): Point

}