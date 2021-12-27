package dev.emortal.marathon.animation

import dev.emortal.immortal.game.Game
import dev.emortal.marathon.utils.breakBlock
import dev.emortal.marathon.utils.setBlock
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.block.Block

abstract class BlockAnimator(val game: Game) {

    abstract fun setBlockAnimated(point: Point, block: Block, lastPoint: Point, lastBlock: Block)

    open fun destroyBlockAnimated(point: Point, lastBlock: Block) {
        game.breakBlock(point, lastBlock)
        game.setBlock(point, Block.AIR)
    }

}