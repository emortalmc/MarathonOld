package dev.emortal.marathon.utils

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos

fun Point.roundToBlock(): Point {
    return Pos(blockX().toDouble(), blockY().toDouble(), blockZ().toDouble())
}