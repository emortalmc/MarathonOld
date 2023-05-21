package dev.emortal.marathon.animation

import dev.emortal.immortal.game.Game
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.block.Block

abstract class BlockAnimator {

    open fun onReset(game: Game) {

    }

    abstract fun setBlockAnimated(game: Game, point: Point, block: Block, lastPoint: Point)

    open fun destroyBlockAnimated(game: Game, point: Point, block: Block) {
        game.instance?.setBlock(point, Block.AIR)
    }
}