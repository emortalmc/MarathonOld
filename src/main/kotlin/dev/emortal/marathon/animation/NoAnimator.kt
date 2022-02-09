package dev.emortal.marathon.animation

import dev.emortal.immortal.game.Game
import net.minestom.server.coordinate.Point
import net.minestom.server.instance.block.Block

class NoAnimator(game: Game) : BlockAnimator(game) {
    override fun setBlockAnimated(point: Point, block: Block, lastPoint: Point) {
        game.instance.setBlock(point, block)
    }
}