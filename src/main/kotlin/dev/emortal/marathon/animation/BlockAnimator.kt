package dev.emortal.marathon.animation

import dev.emortal.immortal.game.Game
import dev.emortal.marathon.utils.breakBlock
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.block.Block

abstract class BlockAnimator(val game: Game) {

    abstract fun setBlockAnimated(point: Point, block: Block, lastPoint: Point)

    open fun destroyBlockAnimated(point: Point) {
        //game.breakBlock(point, lastBlock)
        game.instance.setBlock(point, Block.AIR)
    }

}