package dev.emortal.marathon.animation

import dev.emortal.immortal.game.Game
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.other.FallingBlockMeta
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import world.cepi.kstom.util.asVec
import world.cepi.particle.Particle
import world.cepi.particle.ParticleType
import world.cepi.particle.data.OffsetAndSpeed
import world.cepi.particle.extra.Dust
import world.cepi.particle.showParticle
import world.cepi.particle.util.Vectors
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.pow

class BamboozleAnimator : BlockAnimator() {

    override fun setBlockAnimated(game: Game, point: Point, block: Block, lastPoint: Point) {
        val timeToAnimate = 0.4

        val fallingBlock = Entity(EntityType.FALLING_BLOCK)
        val fallingBlockMeta = fallingBlock.entityMeta as FallingBlockMeta

        fallingBlock.setNoGravity(true)
        fallingBlockMeta.block = block

        fallingBlock.setInstance(game.instance!!, lastPoint.add(0.5, 0.0, 0.5))

        val distance = point.distance(lastPoint)
        var vec = point.sub(lastPoint).asVec()
        var i = 0
        fallingBlock.scheduler().submitTask {
            if (i >= 20) {
                game.instance?.setBlock(point, fallingBlockMeta.block)
                fallingBlock.remove()

                return@submitTask TaskSchedule.stop()
            }

            fallingBlock.velocity = vec.mul(40.0)

            vec = vec.mul(0.5)

            i += 1

            TaskSchedule.nextTick()
        }

        game.showParticle(
            Particle.particle(
                type = ParticleType.DUST,
                count = 1,
                data = OffsetAndSpeed(0f, 0f, 0f, 0f),
                extraData = Dust(1f, 0f, 1f, 1.25f)
            ), Vectors(point.asVec().add(0.5, 0.5, 0.5), lastPoint.asVec().add(0.5, 0.5, 0.5), 0.35)
        )
    }
}