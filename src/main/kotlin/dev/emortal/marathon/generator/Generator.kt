package dev.emortal.marathon.generator

import net.minestom.server.coordinate.Point

abstract class Generator {


    abstract fun getNextPosition(pos: Point, targetX: Int, targetY: Int, score: Int): Point

}