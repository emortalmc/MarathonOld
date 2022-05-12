package dev.emortal.marathon.animation

import dev.emortal.immortal.game.Game
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.other.FallingBlockMeta
import net.minestom.server.instance.block.Block
import world.cepi.kstom.Manager
import java.time.Duration

class FallingSandAnimator(game: Game) : BlockAnimator(game) {

    override fun setBlockAnimated(point: Point, block: Block, lastPoint: Point) {
        val distanceToFall = 2.0

        val fallingBlock = Entity(EntityType.FALLING_BLOCK)
        val fallingBlockMeta = fallingBlock.entityMeta as FallingBlockMeta
        fallingBlockMeta.block = block
        fallingBlock.velocity = Vec(0.0, -10.0, 0.0)
        fallingBlock.setInstance(game.instance, point.add(0.5, distanceToFall + 1, 0.5))

        tasks.add(Manager.scheduler.buildTask {
            game.instance.setBlock(point, block)
            fallingBlock.remove()
        }.delay(Duration.ofMillis((distanceToFall * 100).toLong())).schedule())
    }

}