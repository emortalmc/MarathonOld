package dev.emortal.marathon.animation

import dev.emortal.immortal.game.Game
import dev.emortal.marathon.utils.setBlock
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.block.Block

class NoAnimation(game: Game) : BlockAnimation(game) {
    override fun setBlockAnimated(point: Point, block: Block) {
        game.setBlock(point, block)
    }

    override fun destroyBlockAnimated(point: Point, block: Block) {
        game.setBlock(point, Block.AIR)
    }
}