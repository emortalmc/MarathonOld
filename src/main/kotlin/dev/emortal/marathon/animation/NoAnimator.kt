package dev.emortal.marathon.animation

import dev.emortal.immortal.game.Game
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.block.Block

object NoAnimator : BlockAnimator() {
    override fun setBlockAnimated(game: Game, point: Point, block: Block, lastPoint: Point) {
        game.instance!!.setBlock(point, block)
    }
}