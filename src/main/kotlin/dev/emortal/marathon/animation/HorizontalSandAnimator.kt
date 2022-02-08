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

class HorizontalSandAnimator(game: Game) : BlockAnimator(game) {
    override fun setBlockAnimated(point: Point, block: Block, lastPoint: Point, lastBlock: Block) {
        val fallingBlock = Entity(EntityType.FALLING_BLOCK)
        val fallingBlockMeta = fallingBlock.entityMeta as FallingBlockMeta
        fallingBlock.setNoGravity(true)
        fallingBlockMeta.block = block
        fallingBlock.velocity = Vec(0.0, 0.0, -8.5)
        fallingBlock.setInstance(game.instance, point.add(0.5, 0.0, 12.5))

        Manager.scheduler.buildTask {
            game.instance.setBlock(point, block)
            fallingBlock.remove()
        }.delay(Duration.ofSeconds(2)).schedule()
    }

    override fun destroyBlockAnimated(point: Point, block: Block) {
        val fallingBlock = Entity(EntityType.FALLING_BLOCK)
        val fallingBlockMeta = fallingBlock.entityMeta as FallingBlockMeta
        game.instance.setBlock(point, Block.AIR)

        fallingBlock.scheduleRemove(Duration.ofSeconds(2))
        fallingBlock.velocity = Vec(0.0, 0.0, -5.0)
        fallingBlockMeta.block = block
        fallingBlock.setInstance(game.instance, point.add(0.5, 0.0, 0.5))
    }
}