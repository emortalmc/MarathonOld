package dev.emortal.marathon.animation

import dev.emortal.immortal.game.Game
import dev.emortal.marathon.utils.setBlock
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.other.FallingBlockMeta
import net.minestom.server.instance.block.Block
import world.cepi.kstom.Manager
import java.time.Duration

class FallingSandAnimation(game: Game) : BlockAnimation(game) {
    override fun setBlockAnimated(point: Point, block: Block) {
        val fallingBlock = Entity(EntityType.FALLING_BLOCK)
        val fallingBlockMeta = fallingBlock.entityMeta as FallingBlockMeta
        fallingBlockMeta.block = block
        fallingBlock.velocity = Vec(0.0, -10.0, 0.0)
        fallingBlock.setInstance(game.instance, point.add(0.5, 12.5, 0.5))

        Manager.scheduler.buildTask {
            fallingBlock.remove()
            game.setBlock(point, block)
        }.delay(Duration.ofSeconds(1)).schedule()
    }

    override fun destroyBlockAnimated(point: Point, block: Block) {
        val fallingBlock = Entity(EntityType.FALLING_BLOCK)
        val fallingBlockMeta = fallingBlock.entityMeta as FallingBlockMeta
        game.setBlock(point, Block.AIR)

        fallingBlock.scheduleRemove(Duration.ofSeconds(2))
        fallingBlock.velocity = Vec(0.0, 10.0, 0.0)
        fallingBlockMeta.block = block
        fallingBlock.setInstance(game.instance, point.add(0.5, 0.5, 0.5))
    }
}