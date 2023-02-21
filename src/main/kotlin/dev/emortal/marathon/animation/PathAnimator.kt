package dev.emortal.marathon.animation

import dev.emortal.immortal.game.Game
import dev.emortal.immortal.util.asVec
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.other.FallingBlockMeta
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import world.cepi.particle.Particle
import world.cepi.particle.ParticleType
import world.cepi.particle.data.OffsetAndSpeed
import world.cepi.particle.extra.Dust
import world.cepi.particle.showParticle
import world.cepi.particle.util.Vectors
import java.lang.ref.WeakReference

class PathAnimator : BlockAnimator() {

    val topper = listOf<Block>(Block.GRASS, Block.POPPY, Block.BLUE_ORCHID, Block.ALLIUM, Block.AZURE_BLUET, Block.RED_TULIP, Block.ORANGE_TULIP, Block.WHITE_TULIP, Block.PINK_TULIP, Block.OXEYE_DAISY, Block.CORNFLOWER, Block.LILY_OF_THE_VALLEY)
    var lastSandEntity: WeakReference<Entity> = WeakReference(null)

    private fun getLastPos(): Point? {
        //if (lastSandEntity.get() == null) return null
        return lastSandEntity.get()?.position?.sub(0.5, 0.0, 0.5)
    }

    override fun setBlockAnimated(game: Game, point: Point, block: Block, lastPoint: Point) {
        val timeToAnimate = 0.65
        val actualLastPoint = getLastPos() ?: lastPoint

        val fallingBlock = NoPhysicsEntity(EntityType.FALLING_BLOCK)
        val fallingBlockMeta = fallingBlock.entityMeta as FallingBlockMeta

        fallingBlock.setNoGravity(true)
        fallingBlockMeta.block = block

        fallingBlock.velocity = point
            .sub(actualLastPoint)
            .asVec()
            .normalize()
            .mul((1 / timeToAnimate) * 1.08 * point.distance(actualLastPoint))

        fallingBlock.setInstance(game.instance!!, actualLastPoint.add(0.5, 0.0, 0.5)).thenRun {
            lastSandEntity = WeakReference(fallingBlock)
        }

        game.showParticle(
            Particle.particle(
                type = ParticleType.DUST,
                count = 1,
                data = OffsetAndSpeed(0f, 0f, 0f, 0f),
                extraData = Dust(1f, 0f, 1f, 1.5f)
            ), Vectors(point.asVec().add(0.5, 0.5, 0.5), actualLastPoint.asVec().add(0.5, 0.5, 0.5), 0.35)
        )

        game.instance?.scheduler()?.buildTask {
            game.instance?.setBlock(point, fallingBlockMeta.block)
            if (fallingBlockMeta.block == Block.GRASS_BLOCK || fallingBlockMeta.block == Block.MOSS_BLOCK) {
                game.instance?.setBlock(point.add(0.0, 1.0, 0.0), topper.random())
            }

            lastSandEntity.get().let {
                if (fallingBlock == it) {
                    lastSandEntity.clear()
                } else {
                    it
                }
            }

            fallingBlock.remove()
        }?.delay(TaskSchedule.millis((timeToAnimate * 1000L).toLong()))?.schedule()
    }
}