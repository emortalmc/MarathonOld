package dev.emortal.marathon.animation

import dev.emortal.immortal.game.Game
import dev.emortal.immortal.util.MinestomRunnable
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.other.FallingBlockMeta
import net.minestom.server.instance.block.Block
import world.cepi.kstom.util.asVec
import world.cepi.particle.Particle
import world.cepi.particle.ParticleType
import world.cepi.particle.data.OffsetAndSpeed
import world.cepi.particle.extra.Dust
import world.cepi.particle.showParticle
import world.cepi.particle.util.Vectors
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

class PathAnimator(game: Game) : BlockAnimator(game) {

    var lastSandEntity: AtomicReference<Entity> = AtomicReference(null)

    private fun getLastPos(): Point? {
        //if (lastSandEntity.get() == null) return null
        return lastSandEntity.get()?.position?.sub(0.5, 0.0, 0.5)
    }

    override fun setBlockAnimated(point: Point, block: Block, lastPoint: Point) {
        val timeToAnimate = 0.4

        val actualLastPoint = getLastPos() ?: lastPoint

        val fallingBlock = Entity(EntityType.FALLING_BLOCK)
        val fallingBlockMeta = fallingBlock.entityMeta as FallingBlockMeta

        fallingBlock.setNoGravity(true)
        fallingBlockMeta.block = block

        fallingBlock.velocity = point
            .sub(actualLastPoint)
            .asVec()
            .normalize()
            .mul((1 / timeToAnimate) * 1.15 * point.distance(actualLastPoint))
        fallingBlock.setInstance(game.instance, actualLastPoint.add(0.5, 0.0, 0.5)).thenRun {
            lastSandEntity.set(fallingBlock)
        }

        game.showParticle(
            Particle.particle(
                type = ParticleType.DUST,
                count = 1,
                data = OffsetAndSpeed(0f, 0f, 0f, 0f),
                extraData = Dust(1f, 0f, 1f, 1.25f)
            ), Vectors(point.asVec().add(0.5, 0.5, 0.5), actualLastPoint.asVec().add(0.5, 0.5, 0.5), 0.35)
        )

        object : MinestomRunnable(coroutineScope = game.coroutineScope, delay = Duration.ofMillis((timeToAnimate * 1000L).toLong())) {
            override suspend fun run() {
                game.instance.setBlock(point, fallingBlockMeta.block)
                fallingBlock.remove()

                lastSandEntity.getAndUpdate { if (fallingBlock == it) null else it }
            }
        }
    }
}