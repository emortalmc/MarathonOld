package dev.emortal.marathon.animation

import dev.emortal.immortal.game.Game
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.block.Block

abstract class BlockAnimation(val game: Game) {

    abstract fun setBlockAnimated(point: Point, block: Block)

    abstract fun destroyBlockAnimated(point: Point, block: Block)

}