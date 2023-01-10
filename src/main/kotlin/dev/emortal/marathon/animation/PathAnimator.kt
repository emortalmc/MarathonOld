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

    var lastSandEntity: WeakReference<Entity> = WeakReference(null)

    private fun getLastPos(): Point? {
        //if (lastSandEntity.get() == null) return null
        return lastSandEntity.get()?.position?.sub(0.5, 0.0, 0.5)
    }

    override fun setBlockAnimated(game: Game, point: Point, block: Block, lastPoint: Point) {
        val timeToAnimate = 0.4
        val actualLastPoint = getLastPos() ?: lastPoint

        val fallingBlock = NoPhysicsEntity(EntityType.FALLING_BLOCK)
        val fallingBlockMeta = fallingBlock.entityMeta as FallingBlockMeta

        fallingBlock.setNoGravity(true)
        fallingBlockMeta.block = block

        fallingBlock.velocity = point
            .sub(actualLastPoint)
            .asVec()
            .normalize()
            .mul((1 / timeToAnimate) * 1.15 * point.distance(actualLastPoint))

        fallingBlock.setInstance(game.instance!!, actualLastPoint.add(0.5, 0.0, 0.5)).thenRun {
            lastSandEntity = WeakReference(fallingBlock)
        }

        game.showParticle(
            Particle.particle(
                type = ParticleType.DUST,
                count = 1,
                data = OffsetAndSpeed(0f, 0f, 0f, 0f),
                extraData = Dust(1f, 0f, 1f, 1.25f)
            ), Vectors(point.asVec().add(0.5, 0.5, 0.5), actualLastPoint.asVec().add(0.5, 0.5, 0.5), 0.35)
        )

        game.instance?.scheduler()?.buildTask {
            game.instance?.setBlock(point, fallingBlockMeta.block)

            lastSandEntity.get().let {
                if (fallingBlock == it) {
                    null
                    lastSandEntity.clear()
                } else {
                    it
                }
            }

            fallingBlock.remove()
        }?.delay(TaskSchedule.millis((timeToAnimate * 1000L).toLong()))?.schedule()
    }
}