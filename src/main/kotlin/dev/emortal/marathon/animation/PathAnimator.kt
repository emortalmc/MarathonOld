package dev.emortal.marathon.animation

import dev.emortal.immortal.game.Game
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import world.cepi.particle.Particle
import world.cepi.particle.ParticleType
import world.cepi.particle.data.OffsetAndSpeed
import world.cepi.particle.extra.Dust
import world.cepi.particle.showParticle
import world.cepi.particle.util.Vectors

class PathAnimator : BlockAnimator() {

    var lastRealPoint: Point? = null

    override fun onReset(game: Game) {
        lastRealPoint = null
    }

    override fun setBlockAnimated(game: Game, point: Point, block: Block, lastPoint: Point) {
        val pointVec = Vec.fromPoint(point)

        val ticksToAnimate = 13
        val actualLastPoint = lastRealPoint ?: lastPoint

        val displayEntity = NoPhysicsEntity(EntityType.BLOCK_DISPLAY)
        val meta = displayEntity.entityMeta as BlockDisplayMeta

        displayEntity.setNoGravity(true)
        meta.setNotifyAboutChanges(false)
        meta.setBlockState(block.stateId().toInt())
        meta.interpolationDuration = ticksToAnimate
        meta.setNotifyAboutChanges(true)

        displayEntity.setInstance(game.instance!!, actualLastPoint)

        displayEntity.scheduler().buildTask {
            meta.setNotifyAboutChanges(false)
            meta.setInterpolationStartDelta(0)
            meta.translation = point.sub(actualLastPoint)
            meta.setNotifyAboutChanges(true)

            game.showParticle(
                Particle.particle(
                    type = ParticleType.DUST,
                    count = 1,
                    data = OffsetAndSpeed(0f, 0f, 0f, 0f),
                    extraData = Dust(1f, 0f, 1f, 1.2f)
                ), Vectors(pointVec.add(0.5, 0.5, 0.5), Vec.fromPoint(actualLastPoint.add(0.5, 0.5, 0.5)), 0.45)
            )
        }.delay(TaskSchedule.tick(2)).schedule()

        game.instance?.scheduler()?.buildTask {
            lastRealPoint = point
            game.instance?.setBlock(point, block)
        }?.delay(TaskSchedule.tick(ticksToAnimate + 3))?.schedule()

        game.instance?.scheduler()?.buildTask {
            displayEntity.remove()
        }?.delay(TaskSchedule.tick(ticksToAnimate + 5))?.schedule()
    }
}